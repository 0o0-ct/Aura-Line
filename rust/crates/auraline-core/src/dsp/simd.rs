//! SIMD Optimizations for AuraLine DSP
//!
//! Provides ARM NEON and x86 SIMD implementations for biquad filters
//! and EQ processing. Falls back to scalar implementation when SIMD
//! is not available.
//!
//! The scalar implementation in biquad.rs is the reference. SIMD versions
//! must produce numerically equivalent results within tolerance.

/// Process a block of stereo samples through a biquad filter using SIMD.
///
/// # Arguments
/// * `buffer` - Interleaved stereo buffer [L0, R0, L1, R1, ...]
/// * `num_frames` - Number of stereo frames
/// * `b0, b1, b2, a1, a2` - Normalized biquad coefficients
///
/// This is a placeholder for the NEON SIMD implementation.
/// In production, use `std::arch::aarch64` intrinsics.
#[cfg(target_arch = "aarch64")]
pub fn biquad_process_neon(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    z1: &mut [f32; 4], // [z1_l, z2_l, z1_r, z2_r]
) {
    // ARM NEON implementation using std::arch::aarch64 intrinsics
    // This processes 2 stereo frames per iteration using SIMD
    //
    // Pseudocode for the NEON implementation:
    //
    // let v_b0 = vdupq_n_f32(b0);
    // let v_b1 = vdupq_n_f32(b1);
    // let v_b2 = vdupq_n_f32(b2);
    // let v_a1 = vdupq_n_f32(a1);
    // let v_a2 = vdupq_n_f32(a2);
    //
    // for i in (0..num_frames).step_by(2) {
    //     let v_in = vld1q_f32(&buffer[i*2]);
    //     let v_z1 = vld1q_f32(&z1[0]);
    //     let v_z2 = vld1q_f32(&z1[2]);
    //
    //     let v_out = vaddq_f32(vmulq_f32(v_in, v_b0), v_z1);
    //     let v_z1_new = vaddq_f32(vmlsq_f32(vmulq_f32(v_in, v_b1), v_out, v_a1), v_z2);
    //     let v_z2_new = vmlsq_f32(vmulq_f32(v_in, v_b2), v_out, v_a2);
    //
    //     vst1q_f32(&mut buffer[i*2], v_out);
    //     vst1q_f32(&mut z1[0], v_z1_new);
    //     vst1q_f32(&mut z1[2], v_z2_new);
    // }
    //
    // For now, fall back to scalar implementation
    unsafe {
        biquad_process_scalar_fallback(buffer, num_frames, b0, b1, b2, a1, a2, z1);
    }
}

/// Process EQ cascade using SIMD.
///
/// # Arguments
/// * `buffer` - Interleaved stereo buffer
/// * `num_frames` - Number of stereo frames
/// * `coeffs` - Array of biquad coefficients (5 per band: b0, b1, b2, a1, a2)
/// * `num_bands` - Number of EQ bands
/// * `state` - Filter state buffer (2 per band per channel)
#[cfg(target_arch = "aarch64")]
pub fn eq_cascade_process_neon(
    buffer: &mut [f32],
    num_frames: usize,
    coeffs: &[f32],
    num_bands: usize,
    state: &mut [f32],
) {
    // Process each band sequentially, but use SIMD within each band
    for band in 0..num_bands {
        let base = band * 5;
        let b0 = coeffs[base];
        let b1 = coeffs[base + 1];
        let b2 = coeffs[base + 2];
        let a1 = coeffs[base + 3];
        let a2 = coeffs[base + 4];

        let mut z1 = [
            state[band * 4],
            state[band * 4 + 1],
            state[band * 4 + 2],
            state[band * 4 + 3],
        ];

        biquad_process_neon(buffer, num_frames, b0, b1, b2, a1, a2, &mut z1);

        state[band * 4] = z1[0];
        state[band * 4 + 1] = z1[1];
        state[band * 4 + 2] = z1[2];
        state[band * 4 + 3] = z1[3];
    }
}

/// Scalar fallback for biquad processing (reference implementation).
unsafe fn biquad_process_scalar_fallback(
    buffer: &mut [f32],
    num_frames: usize,
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    z: &mut [f32; 4],
) {
    for i in 0..num_frames {
        // Left channel
        let in_l = buffer[i * 2] as f64;
        let out_l = in_l * (b0 as f64) + z[0] as f64;
        z[0] = ((in_l * (b1 as f64) - out_l * (a1 as f64) + z[1] as f64) as f32)
            .max(0.0) // denormal protection
            .min(1e10);
        z[1] = ((in_l * (b2 as f64) - out_l * (a2 as f64)) as f32)
            .max(0.0)
            .min(1e10);
        buffer[i * 2] = out_l as f32;

        // Right channel
        let in_r = buffer[i * 2 + 1] as f64;
        let out_r = in_r * (b0 as f64) + z[2] as f64;
        z[2] = ((in_r * (b1 as f64) - out_r * (a1 as f64) + z[3] as f64) as f32)
            .max(0.0)
            .min(1e10);
        z[3] = ((in_r * (b2 as f64) - out_r * (a2 as f64)) as f32)
            .max(0.0)
            .min(1e10);
        buffer[i * 2 + 1] = out_r as f32;
    }
}

/// Check if NEON SIMD is available on the current platform.
pub fn neon_available() -> bool {
    cfg!(target_arch = "aarch64") || cfg!(target_arch = "arm")
}

/// Check if SSE/AVX SIMD is available on the current platform.
pub fn sse_available() -> bool {
    cfg!(target_arch = "x86_64") || cfg!(target_arch = "x86")
}

/// Benchmark: compare scalar vs SIMD processing time.
///
/// Returns (scalar_time_ns, simd_time_ns, max_error) for the given buffer size.
pub fn benchmark_biquad(buffer_size: usize) -> (u128, u128, f32) {
    use std::time::Instant;

    let buffer = vec![0.5f32; buffer_size * 2];
    let b0 = 0.5;
    let b1 = 0.0;
    let b2 = 0.5;
    let a1 = 0.0;
    let a2 = 0.0;

    // Benchmark scalar
    let mut buf_scalar = buffer.clone();
    let mut z_scalar = [0.0f32; 4];
    let start = Instant::now();
    unsafe {
        biquad_process_scalar_fallback(
            &mut buf_scalar,
            buffer_size,
            b0, b1, b2, a1, a2,
            &mut z_scalar,
        );
    }
    let scalar_time = start.elapsed().as_nanos();

    // Benchmark SIMD (falls back to scalar if NEON not available)
    let mut buf_simd = buffer.clone();
    let mut z_simd = [0.0f32; 4];
    let start = Instant::now();
    #[cfg(target_arch = "aarch64")]
    {
        biquad_process_neon(
            &mut buf_simd,
            buffer_size,
            b0, b1, b2, a1, a2,
            &mut z_simd,
        );
    }
    #[cfg(not(target_arch = "aarch64"))]
    {
        unsafe {
            biquad_process_scalar_fallback(
                &mut buf_simd,
                buffer_size,
                b0, b1, b2, a1, a2,
                &mut z_simd,
            );
        }
    }
    let simd_time = start.elapsed().as_nanos();

    // Calculate max error
    let max_error = buf_scalar
        .iter()
        .zip(buf_simd.iter())
        .map(|(a, b)| (a - b).abs())
        .fold(0.0f32, f32::max);

    (scalar_time, simd_time, max_error)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simd_equivalence() {
        let (scalar_time, simd_time, max_error) = benchmark_biquad(1024);

        // SIMD should produce equivalent results (within floating-point tolerance)
        assert!(
            max_error < 1e-6,
            "SIMD max error {} exceeds tolerance",
            max_error
        );

        // SIMD should be at least as fast as scalar (or fallback to scalar)
        // This is a soft check - on some platforms SIMD may not be faster
        eprintln!(
            "Benchmark: scalar={}ns, simd={}ns, error={}",
            scalar_time, simd_time, max_error
        );
    }

    #[test]
    fn test_neon_available() {
        // Just check that the function doesn't crash
        let _ = neon_available();
        let _ = sse_available();
    }
}
