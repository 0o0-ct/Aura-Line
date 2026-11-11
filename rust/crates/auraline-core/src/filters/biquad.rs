//! Real-Time Safe Biquad Filter (Direct Form II Transposed)
//!
//! Implements double-precision coefficient calculation with single-precision PCM sample
//! processing, exponential coefficient smoothing for zipper-noise elimination,
//! denormal number sanitization, and Robert Bristow-Johnson (RBJ) Audio EQ Cookbook filter design.

use std::f64::consts::PI;
use std::sync::atomic::{AtomicU64, Ordering};

/// Sanitize denormal floating-point numbers to prevent CPU performance degradation.
#[inline(always)]
pub fn sanitize_denormal(val: f32) -> f32 {
    if val.abs() < 1e-15 {
        0.0
    } else {
        val
    }
}

/// Filter coefficients calculated with f64 precision.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BiquadParams {
    pub b0: f64,
    pub b1: f64,
    pub b2: f64,
    pub a1: f64,
    pub a2: f64,
}

impl Default for BiquadParams {
    fn default() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
        }
    }
}

/// Atomic wrapper for f64 values (used for lock-free parameter updates from control thread to audio thread).
pub struct AtomicF64 {
    storage: AtomicU64,
}

impl AtomicF64 {
    pub fn new(val: f64) -> Self {
        Self {
            storage: AtomicU64::new(val.to_bits()),
        }
    }

    #[inline(always)]
    pub fn store(&self, val: f64, order: Ordering) {
        self.storage.store(val.to_bits(), order);
    }

    #[inline(always)]
    pub fn load(&self, order: Ordering) -> f64 {
        f64::from_bits(self.storage.load(order))
    }
}

/// Lock-free, real-time safe smoothed biquad filter using Direct Form II Transposed topology.
pub struct SmoothedBiquad {
    // Current active coefficients
    b0: f64,
    b1: f64,
    b2: f64,
    a1: f64,
    a2: f64,

    // Target coefficients for exponential smoothing
    target_b0: AtomicF64,
    target_b1: AtomicF64,
    target_b2: AtomicF64,
    target_a1: AtomicF64,
    target_a2: AtomicF64,

    // Delay state variables
    z1_l: f32,
    z2_l: f32,
    z1_r: f32,
    z2_r: f32,

    smoothing_factor: f64,
}

impl SmoothedBiquad {
    pub fn new() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
            target_b0: AtomicF64::new(1.0),
            target_b1: AtomicF64::new(0.0),
            target_b2: AtomicF64::new(0.0),
            target_a1: AtomicF64::new(0.0),
            target_a2: AtomicF64::new(0.0),
            z1_l: 0.0,
            z2_l: 0.0,
            z1_r: 0.0,
            z2_r: 0.0,
            smoothing_factor: 0.01, // ~10ms exponential smoothing
        }
    }

    pub fn reset(&mut self) {
        self.z1_l = 0.0;
        self.z2_l = 0.0;
        self.z1_r = 0.0;
        self.z2_r = 0.0;
    }

    pub fn set_target(&self, params: BiquadParams) {
        self.target_b0.store(params.b0, Ordering::Relaxed);
        self.target_b1.store(params.b1, Ordering::Relaxed);
        self.target_b2.store(params.b2, Ordering::Relaxed);
        self.target_a1.store(params.a1, Ordering::Relaxed);
        self.target_a2.store(params.a2, Ordering::Relaxed);
    }

    pub fn set_immediate(&mut self, params: BiquadParams) {
        self.b0 = params.b0;
        self.b1 = params.b1;
        self.b2 = params.b2;
        self.a1 = params.a1;
        self.a2 = params.a2;
        self.set_target(params);
    }

    #[inline(always)]
    pub fn update_coefficients(&mut self) {
        let tb0 = self.target_b0.load(Ordering::Relaxed);
        let tb1 = self.target_b1.load(Ordering::Relaxed);
        let tb2 = self.target_b2.load(Ordering::Relaxed);
        let ta1 = self.target_a1.load(Ordering::Relaxed);
        let ta2 = self.target_a2.load(Ordering::Relaxed);

        self.b0 += (tb0 - self.b0) * self.smoothing_factor;
        self.b1 += (tb1 - self.b1) * self.smoothing_factor;
        self.b2 += (tb2 - self.b2) * self.smoothing_factor;
        self.a1 += (ta1 - self.a1) * self.smoothing_factor;
        self.a2 += (ta2 - self.a2) * self.smoothing_factor;
    }

    #[inline(always)]
    pub fn process_l(&mut self, input: f32) -> f32 {
        let ind = input as f64;
        let out = ind * self.b0 + (self.z1_l as f64);
        self.z1_l = sanitize_denormal((ind * self.b1 - out * self.a1 + (self.z2_l as f64)) as f32);
        self.z2_l = sanitize_denormal((ind * self.b2 - out * self.a2) as f32);
        out as f32
    }

    #[inline(always)]
    pub fn process_r(&mut self, input: f32) -> f32 {
        let ind = input as f64;
        let out = ind * self.b0 + (self.z1_r as f64);
        self.z1_r = sanitize_denormal((ind * self.b1 - out * self.a1 + (self.z2_r as f64)) as f32);
        self.z2_r = sanitize_denormal((ind * self.b2 - out * self.a2) as f32);
        out as f32
    }

    #[inline(always)]
    pub fn process_stereo_sample(&mut self, l: &mut f32, r: &mut f32) {
        self.update_coefficients();
        *l = self.process_l(*l);
        *r = self.process_r(*r);
    }
}

// --- RBJ Filter Design Functions ---

pub fn peaking_eq(sample_rate: f64, freq: f64, gain_db: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = freq.clamp(10.0, safe_fs * 0.49);
    let safe_gain = gain_db.clamp(-36.0, 36.0);
    let safe_q = q.clamp(0.1, 20.0);

    let a = 10.0f64.powf(safe_gain / 40.0);
    let w0 = 2.0 * PI * safe_f / safe_fs;
    let alpha = w0.sin() / (2.0 * safe_q);

    let b0 = 1.0 + alpha * a;
    let b1 = -2.0 * w0.cos();
    let b2 = 1.0 - alpha * a;
    let a0 = 1.0 + alpha / a;
    let a1 = -2.0 * w0.cos();
    let a2 = 1.0 - alpha / a;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn low_pass(sample_rate: f64, cutoff_freq: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = cutoff_freq.clamp(10.0, safe_fs * 0.49);
    let safe_q = q.clamp(0.1, 20.0);

    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);

    let b0 = (1.0 - cosw) / 2.0;
    let b1 = 1.0 - cosw;
    let b2 = (1.0 - cosw) / 2.0;
    let a0 = 1.0 + alpha;
    let a1 = -2.0 * cosw;
    let a2 = 1.0 - alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn high_pass(sample_rate: f64, cutoff_freq: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = cutoff_freq.clamp(10.0, safe_fs * 0.49);
    let safe_q = q.clamp(0.1, 20.0);

    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);

    let b0 = (1.0 + cosw) / 2.0;
    let b1 = -(1.0 + cosw);
    let b2 = (1.0 + cosw) / 2.0;
    let a0 = 1.0 + alpha;
    let a1 = -2.0 * cosw;
    let a2 = 1.0 - alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn low_shelf(sample_rate: f64, freq: f64, gain_db: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = freq.clamp(10.0, safe_fs * 0.49);
    let safe_gain = gain_db.clamp(-36.0, 36.0);
    let safe_q = q.clamp(0.1, 20.0);

    let a = 10.0f64.powf(safe_gain / 40.0);
    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);
    let sqrt_a = a.sqrt();

    let b0 = a * ((a + 1.0) - (a - 1.0) * cosw + 2.0 * sqrt_a * alpha);
    let b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosw);
    let b2 = a * ((a + 1.0) - (a - 1.0) * cosw - 2.0 * sqrt_a * alpha);
    let a0 = (a + 1.0) + (a - 1.0) * cosw + 2.0 * sqrt_a * alpha;
    let a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosw);
    let a2 = (a + 1.0) + (a - 1.0) * cosw - 2.0 * sqrt_a * alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn high_shelf(sample_rate: f64, freq: f64, gain_db: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = freq.clamp(10.0, safe_fs * 0.49);
    let safe_gain = gain_db.clamp(-36.0, 36.0);
    let safe_q = q.clamp(0.1, 20.0);

    let a = 10.0f64.powf(safe_gain / 40.0);
    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);
    let sqrt_a = a.sqrt();

    let b0 = a * ((a + 1.0) + (a - 1.0) * cosw + 2.0 * sqrt_a * alpha);
    let b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosw);
    let b2 = a * ((a + 1.0) + (a - 1.0) * cosw - 2.0 * sqrt_a * alpha);
    let a0 = (a + 1.0) - (a - 1.0) * cosw + 2.0 * sqrt_a * alpha;
    let a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosw);
    let a2 = (a + 1.0) - (a - 1.0) * cosw - 2.0 * sqrt_a * alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn notch(sample_rate: f64, freq: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = freq.clamp(10.0, safe_fs * 0.49);
    let safe_q = q.clamp(0.1, 20.0);

    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);

    let b0 = 1.0;
    let b1 = -2.0 * cosw;
    let b2 = 1.0;
    let a0 = 1.0 + alpha;
    let a1 = -2.0 * cosw;
    let a2 = 1.0 - alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

pub fn band_pass(sample_rate: f64, freq: f64, q: f64) -> BiquadParams {
    let safe_fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
    let safe_f = freq.clamp(10.0, safe_fs * 0.49);
    let safe_q = q.clamp(0.1, 20.0);

    let w0 = 2.0 * PI * safe_f / safe_fs;
    let cosw = w0.cos();
    let alpha = w0.sin() / (2.0 * safe_q);

    let b0 = alpha;
    let b1 = 0.0;
    let b2 = -alpha;
    let a0 = 1.0 + alpha;
    let a1 = -2.0 * cosw;
    let a2 = 1.0 - alpha;

    BiquadParams {
        b0: b0 / a0,
        b1: b1 / a0,
        b2: b2 / a0,
        a1: a1 / a0,
        a2: a2 / a0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_biquad_flat_pass() {
        let mut filter = SmoothedBiquad::new();
        let params = peaking_eq(48000.0, 1000.0, 0.0, 1.0);
        filter.set_immediate(params);

        let mut l = 0.5f32;
        let mut r = -0.5f32;
        filter.process_stereo_sample(&mut l, &mut r);

        assert!((l - 0.5).abs() < 1e-4);
        assert!((r - (-0.5)).abs() < 1e-4);
    }

    #[test]
    fn test_denormal_sanitization() {
        assert_eq!(sanitize_denormal(1e-16), 0.0);
        assert_eq!(sanitize_denormal(-1e-16), 0.0);
        assert_ne!(sanitize_denormal(1e-3), 0.0);
    }

    fn rms_gain_for(params: BiquadParams, tone_hz: f64, sample_rate: f64, frames: usize) -> f64 {
        let mut filter = SmoothedBiquad::new();
        filter.set_immediate(params);
        let mut sum_in = 0.0f64;
        let mut sum_out = 0.0f64;
        for i in 0..frames {
            let t = i as f64 / sample_rate;
            let sample = (2.0 * std::f64::consts::PI * tone_hz * t).sin() as f32;
            let y = filter.process_l(sample);
            sum_in += (sample as f64) * (sample as f64);
            sum_out += (y as f64) * (y as f64);
        }
        (sum_out / sum_in).sqrt()
    }

    #[test]
    fn test_low_shelf_amplifies_lows_not_highs() {
        let fs = 48000.0;
        let boost = low_shelf(fs, 100.0, 12.0, 0.7071);
        let flat = low_shelf(fs, 100.0, 0.0, 0.7071);

        let ratio_low = rms_gain_for(boost, 50.0, fs, 24000);
        let ratio_high = rms_gain_for(boost, 8000.0, fs, 24000);
        let ratio_flat = rms_gain_for(flat, 50.0, fs, 24000);

        assert!(ratio_low > 2.0, "Low Shelf +12 dB debe amplificar 50 Hz (ratio {ratio_low})");
        assert!((ratio_high - 1.0).abs() < 0.05, "Low Shelf no debe tocar 8 kHz (ratio {ratio_high})");
        assert!((ratio_flat - 1.0).abs() < 0.01, "Low Shelf a 0 dB debe ser plano (ratio {ratio_flat})");
    }

    #[test]
    fn test_high_shelf_amplifies_highs_not_lows() {
        let fs = 48000.0;
        let boost = high_shelf(fs, 12000.0, 12.0, 0.7071);
        let flat = high_shelf(fs, 12000.0, 0.0, 0.7071);

        let ratio_high = rms_gain_for(boost, 15000.0, fs, 24000);
        let ratio_low = rms_gain_for(boost, 200.0, fs, 24000);
        let ratio_flat = rms_gain_for(flat, 15000.0, fs, 24000);

        assert!(ratio_high > 2.0, "High Shelf +12 dB debe amplificar 15 kHz (ratio {ratio_high})");
        assert!((ratio_low - 1.0).abs() < 0.05, "High Shelf no debe tocar 200 Hz (ratio {ratio_low})");
        assert!((ratio_flat - 1.0).abs() < 0.01, "High Shelf a 0 dB debe ser plano (ratio {ratio_flat})");
    }

    #[test]
    fn test_high_pass_and_low_pass_cut_their_sides() {
        let fs = 48000.0;
        let hp = high_pass(fs, 100.0, 0.7071);
        let lp = low_pass(fs, 18000.0, 0.7071);

        let below_cut = rms_gain_for(hp, 40.0, fs, 24000);
        let above_cut = rms_gain_for(hp, 1000.0, fs, 24000);
        let lp_high = rms_gain_for(lp, 19000.0, fs, 24000);
        let lp_low = rms_gain_for(lp, 200.0, fs, 24000);

        assert!(below_cut < 0.5, "HPF debe cortar 40 Hz (gain {below_cut})");
        assert!(above_cut > 0.9, "HPF no debe cortar 1 kHz (gain {above_cut})");
        assert!(lp_high < 1.0, "LPF debe empujar 19 kHz hacia abajo (gain {lp_high})");
        assert!(lp_low > 0.9, "LPF no debe cortar 200 Hz (gain {lp_low})");
    }
}
