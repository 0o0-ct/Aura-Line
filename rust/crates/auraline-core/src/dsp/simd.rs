//! SIMD Optimizations for AuraLine DSP
//!
//! Provides ARM NEON (AArch64) and x86 SSE2 implementations for Direct Form II
//! Transposed biquad filtering, plus an EQ-cascade helper. Falls back to a
//! scalar reference implementation when no SIMD target is available.
//!
//! ## Real-time safety
//! All functions are no_alloc, lock-free, and process pre-allocated buffers.
//! They are safe to call from an audio callback.
//!
//! ## Numerical equivalence
//! The scalar implementation ([`biquad_process_scalar`]) is the mathematical
//! reference. SIMD versions must produce results within `1e-4` of the scalar
//! output (verified by [`test_simd_equivalence`]).
//!
//! ## SIMD parallelism for IIR biquads
//! A biquad IIR filter has a time-recurrence (y[n] depends on y[n-1]), so
//! samples cannot be vectorized across time. The only valid SIMD parallelism
//! for a single stereo biquad is channel parallelism: the left and right
// channels are independent and are computed simultaneously in two SIMD lanes.
//! This yields ~2x arithmetic throughput with bit-identical per-channel results.

use core::f32;

/// Process one stereo frame through a DF2T biquad (scalar reference).
///
/// State layout: `[z1_l, z1_r, z2_l, z2_r]`.
///
/// This is the mathematical reference that all SIMD implementations must match.
#[inline(always)]
fn biquad_process_scalar_frame(
    x_l: f32,
    x_r: f32,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) -> (f32, f32) {
    // Left channel
    let out_l = x_l * b0 + state[0];
    state[0] = x_l * b1 - out_l * a1 + state[2];
    state[2] = x_l * b2 - out_l * a2;

    // Right channel
    let out_r = x_r * b0 + state[1];
    state[1] = x_r * b1 - out_r * a1 + state[3];
    state[3] = x_r * b2 - out_r * a2;

    (out_l, out_r)
}

/// Process a block of interleaved stereo samples through a biquad filter using
/// the best available SIMD implementation for the target architecture.
///
/// # Arguments
/// * `buffer` - Interleaved stereo buffer `[L0, R0, L1, R1, ...]`
/// * `num_frames` - Number of stereo frames
/// * `b0, b1, b2, a1, a2` - Normalized biquad coefficients (f32)
/// * `state` - Filter state `[z1_l, z1_r, z2_l, z2_r]`, updated in place
///
/// Falls back to the scalar reference when no SIMD target is compiled.
pub fn biquad_process(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    if num_frames == 0 || buffer.len() < num_frames * 2 {
        return;
    }
    biquad_process_impl(buffer, num_frames, b0, b1, b2, a1, a2, state);
}

// ---------------------------------------------------------------------------
// Dispatch: pick the best compiled implementation
// ---------------------------------------------------------------------------

#[cfg(target_arch = "aarch64")]
fn biquad_process_impl(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    biquad_process_neon(buffer, num_frames, b0, b1, b2, a1, a2, state);
}

#[cfg(target_arch = "x86_64")]
fn biquad_process_impl(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    biquad_process_sse(buffer, num_frames, b0, b1, b2, a1, a2, state);
}

#[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
fn biquad_process_impl(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    biquad_process_scalar(buffer, num_frames, b0, b1, b2, a1, a2, state);
}

// ---------------------------------------------------------------------------
// Scalar reference (compiled everywhere)
// ---------------------------------------------------------------------------

/// Scalar reference implementation. Processes one stereo frame per iteration
/// with both channels computed independently (channel-parallel semantics).
#[inline(always)]
fn biquad_process_scalar(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    for i in 0..num_frames {
        let (out_l, out_r) = biquad_process_scalar_frame(
            buffer[i * 2],
            buffer[i * 2 + 1],
            b0, b1, b2, a1, a2,
            state,
        );
        buffer[i * 2] = out_l;
        buffer[i * 2 + 1] = out_r;
    }
}

// ---------------------------------------------------------------------------
// ARM NEON (AArch64)
// ---------------------------------------------------------------------------

/// NEON implementation using channel parallelism: `float32x2_t` holds
/// `{L, R}` and both channels are computed simultaneously.
///
/// Numerically equivalent to the scalar reference within f32 rounding.
#[cfg(target_arch = "aarch64")]
fn biquad_process_neon(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    use std::arch::aarch64::*;

    unsafe {
        let b0_v = vdup_n_f32(b0);
        let b1_v = vdup_n_f32(b1);
        let b2_v = vdup_n_f32(b2);
        let a1_v = vdup_n_f32(a1);
        let a2_v = vdup_n_f32(a2);

        // state = [z1_l, z1_r, z2_l, z2_r]
        let mut z1_v = vld1_f32(&state[0]); // {z1_l, z1_r}
        let mut z2_v = vld1_f32(&state[2]); // {z2_l, z2_r}

        let mut i = 0;
        while i < num_frames {
            let x_v = vld1_f32(&buffer[i * 2]); // {L, R}

            // DF2T: out = x*b0 + z1
            let out_v = vadd_f32(vmul_f32(x_v, b0_v), z1_v);
            // z1_new = x*b1 - out*a1 + z2
            z1_v = vadd_f32(vsub_f32(vmul_f32(x_v, b1_v), vmul_f32(out_v, a1_v)), z2_v);
            // z2_new = x*b2 - out*a2
            z2_v = vsub_f32(vmul_f32(x_v, b2_v), vmul_f32(out_v, a2_v));

            vst1_f32(&mut buffer[i * 2], out_v);
            i += 1;
        }

        vst1_f32(&mut state[0], z1_v);
        vst1_f32(&mut state[2], z2_v);
    }
}

// ---------------------------------------------------------------------------
// x86 SSE2 (x86_64)
// ---------------------------------------------------------------------------

/// SSE2 implementation using channel parallelism: the low two lanes of an
/// `__m128` hold `{L, R}` and both channels are computed simultaneously.
///
/// SSE2 is the baseline widely available on x86_64; no runtime CPU feature
/// detection is required.
#[cfg(target_arch = "x86_64")]
fn biquad_process_sse(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    state: &mut [f32; 4],
) {
    use std::arch::x86_64::*;

    unsafe {
        let b0_v = _mm_set1_ps(b0);
        let b1_v = _mm_set1_ps(b1);
        let b2_v = _mm_set1_ps(b2);
        let a1_v = _mm_set1_ps(a1);
        let a2_v = _mm_set1_ps(a2);

        // state = [z1_l, z1_r, z2_l, z2_r]
        let mut z1_v = _mm_setr_ps(state[0], state[1], 0.0, 0.0); // {z1_l, z1_r, 0, 0}
        let mut z2_v = _mm_setr_ps(state[2], state[3], 0.0, 0.0); // {z2_l, z2_r, 0, 0}

        let mut i = 0;
        while i < num_frames {
            let x_v = _mm_setr_ps(buffer[i * 2], buffer[i * 2 + 1], 0.0, 0.0); // {L, R, 0, 0}

            // DF2T: out = x*b0 + z1
            let out_v = _mm_add_ps(_mm_mul_ps(x_v, b0_v), z1_v);
            // z1_new = x*b1 - out*a1 + z2
            z1_v = _mm_add_ps(_mm_sub_ps(_mm_mul_ps(x_v, b1_v), _mm_mul_ps(out_v, a1_v)), z2_v);
            // z2_new = x*b2 - out*a2
            z2_v = _mm_sub_ps(_mm_mul_ps(x_v, b2_v), _mm_mul_ps(out_v, a2_v));

            buffer[i * 2] = _mm_cvtss_f32(out_v);
            buffer[i * 2 + 1] = _mm_cvtss_f32(_mm_shuffle_ps(out_v, out_v, 0x01));
            i += 1;
        }

        state[0] = _mm_cvtss_f32(z1_v);
        state[1] = _mm_cvtss_f32(_mm_shuffle_ps(z1_v, z1_v, 0x01));
        state[2] = _mm_cvtss_f32(z2_v);
        state[3] = _mm_cvtss_f32(_mm_shuffle_ps(z2_v, z2_v, 0x01));
    }
}

// ---------------------------------------------------------------------------
// EQ cascade helper
// ---------------------------------------------------------------------------

/// Process an EQ cascade: each band is applied sequentially to the whole
/// buffer, using SIMD within each band.
///
/// # Arguments
/// * `buffer` - Interleaved stereo buffer
/// * `num_frames` - Number of stereo frames
/// * `coeffs` - Flat array of biquad coefficients, 5 per band `[b0, b1, b2, a1, a2]`
/// * `num_bands` - Number of EQ bands
/// * `state` - Per-band state, 4 f32 per band `[z1_l, z1_r, z2_l, z2_r]`
pub fn eq_cascade_process(
    buffer: &mut [f32],
    num_frames: usize,
    coeffs: &[f32],
    num_bands: usize,
    state: &mut [f32],
) {
    assert!(coeffs.len() >= num_bands * 5, "coeffs array too small");
    assert!(state.len() >= num_bands * 4, "state array too small");

    for band in 0..num_bands {
        let base = band * 5;
        let st_base = band * 4;
        let mut band_state = [
            state[st_base],
            state[st_base + 1],
            state[st_base + 2],
            state[st_base + 3],
        ];

        biquad_process(
            buffer,
            num_frames,
            coeffs[base],
            coeffs[base + 1],
            coeffs[base + 2],
            coeffs[base + 3],
            coeffs[base + 4],
            &mut band_state,
        );

        state[st_base] = band_state[0];
        state[st_base + 1] = band_state[1];
        state[st_base + 2] = band_state[2];
        state[st_base + 3] = band_state[3];
    }
}

// ---------------------------------------------------------------------------
// Capability queries
// ---------------------------------------------------------------------------

/// Returns true when a NEON implementation is compiled for this target.
pub fn neon_available() -> bool {
    cfg!(target_arch = "aarch64") || cfg!(target_arch = "arm")
}

/// Returns true when an SSE implementation is compiled for this target.
pub fn sse_available() -> bool {
    cfg!(target_arch = "x86_64") || cfg!(target_arch = "x86")
}

/// Returns the name of the SIMD implementation active on this target.
pub fn active_simd_backend() -> &'static str {
    if cfg!(target_arch = "aarch64") {
        "ARM NEON"
    } else if cfg!(target_arch = "x86_64") || cfg!(target_arch = "x86") {
        "x86 SSE2"
    } else {
        "Scalar (reference)"
    }
}

// ---------------------------------------------------------------------------
// Benchmark
// ---------------------------------------------------------------------------

/// Benchmark: compare scalar vs SIMD processing time and numerical error.
///
/// Returns `(scalar_time_ns, simd_time_ns, max_error)` for the given buffer
/// size (in stereo frames).
pub fn benchmark_biquad(buffer_size: usize) -> (u128, u128, f32) {
    use std::time::Instant;

    let buffer: Vec<f32> = (0..buffer_size * 2)
        .map(|i| (i as f32).sin() * 0.5)
        .collect();
    let b0 = 0.5f32;
    let b1 = 0.1f32;
    let b2 = 0.5f32;
    let a1 = -0.8f32;
    let a2 = 0.3f32;

    // Benchmark scalar
    let mut buf_scalar = buffer.clone();
    let mut z_scalar = [0.0f32; 4];
    let start = Instant::now();
    biquad_process_scalar(&mut buf_scalar, buffer_size, b0, b1, b2, a1, a2, &mut z_scalar);
    let scalar_time = start.elapsed().as_nanos();

    // Benchmark SIMD dispatch
    let mut buf_simd = buffer.clone();
    let mut z_simd = [0.0f32; 4];
    let start = Instant::now();
    biquad_process(&mut buf_simd, buffer_size, b0, b1, b2, a1, a2, &mut z_simd);
    let simd_time = start.elapsed().as_nanos();

    // Calculate max error
    let max_error = buf_scalar
        .iter()
        .zip(buf_simd.iter())
        .map(|(a, b)| (a - b).abs())
        .fold(0.0f32, f32::max);

    (scalar_time, simd_time, max_error)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simd_equivalence() {
        let (scalar_time, simd_time, max_error) = benchmark_biquad(4096);

        // SIMD must match the scalar reference within tolerance.
        assert!(
            max_error < 1e-4,
            "SIMD max error {} exceeds tolerance (1e-4)",
            max_error
        );

        eprintln!(
            "SIMD benchmark: backend={}, scalar={}ns, simd={}ns, max_error={:.2e}",
            active_simd_backend(),
            scalar_time,
            simd_time,
            max_error
        );
    }

    #[test]
    fn test_simd_state_roundtrip() {
        // Determinism: same input + same state must yield same output + state.
        let input = [0.5f32, -0.5f32, 0.25f32, -0.25f32]; // 2 frames
        let state_init = [0.1f32, 0.2f32, 0.3f32, 0.4f32];

        let mut buffer1 = input;
        let mut state1 = state_init;
        biquad_process(&mut buffer1, 2, 0.5, 0.1, 0.5, -0.8, 0.3, &mut state1);

        let mut buffer2 = input;
        let mut state2 = state_init;
        biquad_process(&mut buffer2, 2, 0.5, 0.1, 0.5, -0.8, 0.3, &mut state2);

        assert_eq!(buffer1, buffer2);
        assert_eq!(state1, state2);

        // State must evolve after processing (filter has memory).
        assert!(state1 != state_init);
    }

    #[test]
    fn test_simd_bypass_identity() {
        // With b0=1, all others zero: output equals input, state unchanged.
        // Zero state + b0=1 (rest zero) => pure pass-through, state stays zero.
        let mut buffer = [0.7f32, -0.3f32, 0.1f32, 0.9f32];
        let mut state = [0.0f32; 4];
        let orig_state = state;

        biquad_process(&mut buffer, 2, 1.0, 0.0, 0.0, 0.0, 0.0, &mut state);

        assert!((buffer[0] - 0.7).abs() < 1e-6);
        assert!((buffer[1] - (-0.3)).abs() < 1e-6);
        assert_eq!(state, orig_state);
    }

    #[test]
    fn test_capability_queries() {
        let _ = neon_available();
        let _ = sse_available();
        let _ = active_simd_backend();
    }

    #[test]
    fn test_eq_cascade_flat_pass() {
        // All bands at 0 dB (b0=1, rest 0) -> buffer unchanged.
        let mut buffer = [0.4f32, -0.2f32, 0.8f32, -0.6f32];
        let num_bands = 3;
        let mut coeffs = vec![0.0f32; num_bands * 5];
        for band in 0..num_bands {
            coeffs[band * 5] = 1.0; // b0 = 1 -> pass-through
        }
        let mut state = vec![0.0f32; num_bands * 4];

        eq_cascade_process(&mut buffer, 2, &coeffs, num_bands, &mut state);

        assert!((buffer[0] - 0.4).abs() < 1e-6);
        assert!((buffer[1] - (-0.2)).abs() < 1e-6);
    }
}
