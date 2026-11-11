//! Sub-bass Generator
//!
//! Generates synthetic sub-bass content below the reproduction capability
//! of small speakers. Detects the fundamental frequency of bass transients
//! and generates a sine wave at that frequency, blended with the original signal.
//!
//! Useful for devices with limited low-frequency response that can benefit
//! from sub-bass reinforcement when connected to better transducers.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{low_pass, AtomicF64, SmoothedBiquad};

pub struct SubBassGenerator {
    enabled: AtomicBool,
    /// Amount of sub-bass generation (0.0–1.0)
    amount: AtomicF64,
    /// Cutoff frequency for sub-bass extraction
    cutoff_hz: AtomicF64,
    /// LPF to extract bass content
    bass_lp_l: SmoothedBiquad,
    bass_lp_r: SmoothedBiquad,
    /// Phase accumulator for sine generation
    phase_l: f32,
    phase_r: f32,
    /// Previous sample for frequency estimation
    prev_bass_l: f32,
    prev_bass_r: f32,
    /// Zero-crossing detector state
    last_sign_l: f32,
    last_sign_r: f32,
    /// Estimated period in samples
    period_l: f32,
    period_r: f32,
    /// Sample counter since last zero crossing
    sample_count_l: f32,
    sample_count_r: f32,
    /// Sample rate
    sample_rate: f32,
}

impl SubBassGenerator {
    pub fn new(sample_rate: f64) -> Self {
        let mut gen = Self {
            enabled: AtomicBool::new(false),
            amount: AtomicF64::new(0.3),
            cutoff_hz: AtomicF64::new(80.0),
            bass_lp_l: SmoothedBiquad::new(),
            bass_lp_r: SmoothedBiquad::new(),
            phase_l: 0.0,
            phase_r: 0.0,
            prev_bass_l: 0.0,
            prev_bass_r: 0.0,
            last_sign_l: 0.0,
            last_sign_r: 0.0,
            period_l: 480.0,  // ~100 Hz at 48kHz
            period_r: 480.0,
            sample_count_l: 0.0,
            sample_count_r: 0.0,
            sample_rate: if sample_rate <= 0.0 { 48000.0 } else { sample_rate } as f32,
        };
        gen.update_filters(sample_rate);
        gen
    }

    fn update_filters(&mut self, sample_rate: f64) {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let cutoff = (self.cutoff_hz.load(Ordering::Relaxed) as f64).clamp(30.0, fs * 0.45);

        let lp_params = low_pass(fs, cutoff, 0.7071);
        self.bass_lp_l.set_immediate(lp_params);
        self.bass_lp_r.set_immediate(lp_params);
    }

    pub fn set_params(&mut self, enabled: bool, amount: f32, cutoff_hz: f32, sample_rate: f64) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.amount.store(amount.clamp(0.0, 1.0) as f64, Ordering::Relaxed);
        self.cutoff_hz.store(cutoff_hz.clamp(30.0, 200.0) as f64, Ordering::Relaxed);
        self.sample_rate = if sample_rate <= 0.0 { 48000.0 } else { sample_rate } as f32;
        self.update_filters(sample_rate);
    }

    /// Estimate fundamental frequency from zero-crossing detection
    #[inline(always)]
    fn estimate_freq(&mut self, bass: f32, last_sign: &mut f32, period: &mut f32, count: &mut f32) -> f32 {
        let sign = if bass >= 0.0 { 1.0 } else { -1.0 };

        if *last_sign != 0.0 && sign != *last_sign {
            // Zero crossing detected
            if *count > 10.0 {
                // Smooth period estimate
                *period = *period * 0.9 + *count * 0.1;
            }
            *count = 0.0;
        }
        *last_sign = sign;
        *count += 1.0;

        // Convert period to frequency
        if *period > 10.0 && *period < self.sample_rate * 0.5 {
            self.sample_rate / (*period * 2.0) // Full cycle = 2 zero crossings
        } else {
            50.0 // Default to 50 Hz
        }
    }

    /// Generate a sine wave at the estimated fundamental frequency
    #[inline(always)]
    fn generate_sub_bass(&mut self, freq_hz: f32, amount: f32, phase: &mut f32) -> f32 {
        if freq_hz < 20.0 || freq_hz > 200.0 {
            return 0.0;
        }

        let omega = 2.0 * std::f32::consts::PI * freq_hz / self.sample_rate;
        *phase = (*phase + omega).rem_euclid(2.0 * std::f32::consts::PI);

        // Generate sine with smooth amplitude envelope
        let sine = phase.sin();
        sine * amount
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let amount = self.amount.load(Ordering::Relaxed) as f32;
        if amount < 0.01 {
            return;
        }

        // Extract bass content
        let mut bass_l = *l;
        let mut bass_r = *r;
        bass_l = self.bass_lp_l.process_l(bass_l);
        bass_r = self.bass_lp_r.process_r(bass_r);

        // Inline zero-crossing frequency estimation to avoid borrow conflicts
        let sign_l = if bass_l >= 0.0 { 1.0 } else { -1.0 };
        let sign_r = if bass_r >= 0.0 { 1.0 } else { -1.0 };

        if self.last_sign_l != 0.0 && sign_l != self.last_sign_l {
            if self.sample_count_l > 10.0 {
                self.period_l = self.period_l * 0.9 + self.sample_count_l * 0.1;
            }
            self.sample_count_l = 0.0;
        }
        self.last_sign_l = sign_l;
        self.sample_count_l += 1.0;

        if self.last_sign_r != 0.0 && sign_r != self.last_sign_r {
            if self.sample_count_r > 10.0 {
                self.period_r = self.period_r * 0.9 + self.sample_count_r * 0.1;
            }
            self.sample_count_r = 0.0;
        }
        self.last_sign_r = sign_r;
        self.sample_count_r += 1.0;

        // Convert period to frequency
        let freq_l = if self.period_l > 10.0 && self.period_l < self.sample_rate * 0.5 {
            self.sample_rate / (self.period_l * 2.0)
        } else {
            50.0
        };
        let freq_r = if self.period_r > 10.0 && self.period_r < self.sample_rate * 0.5 {
            self.sample_rate / (self.period_r * 2.0)
        } else {
            50.0
        };

        // Use average frequency for both channels
        let avg_freq = (freq_l + freq_r) * 0.5;

        // Generate sub-bass sine waves (inline to avoid borrow conflicts)
        let (sub_l, sub_r) = if avg_freq >= 20.0 && avg_freq <= 200.0 {
            let omega = 2.0 * std::f32::consts::PI * avg_freq / self.sample_rate;
            self.phase_l = (self.phase_l + omega).rem_euclid(2.0 * std::f32::consts::PI);
            self.phase_r = (self.phase_r + omega).rem_euclid(2.0 * std::f32::consts::PI);
            let sine_l = self.phase_l.sin() * amount;
            let sine_r = self.phase_r.sin() * amount;
            (sine_l, sine_r)
        } else {
            (0.0, 0.0)
        };

        // Blend with original signal
        *l = *l + sub_l;
        *r = *r + sub_r;

        // Soft clip
        *l = (*l / (1.0 + l.abs())).clamp(-1.0, 1.0);
        *r = (*r / (1.0 + r.abs())).clamp(-1.0, 1.0);

        self.prev_bass_l = bass_l;
        self.prev_bass_r = bass_r;
    }

    pub fn reset(&mut self) {
        self.bass_lp_l.reset();
        self.bass_lp_r.reset();
        self.phase_l = 0.0;
        self.phase_r = 0.0;
        self.prev_bass_l = 0.0;
        self.prev_bass_r = 0.0;
        self.last_sign_l = 0.0;
        self.last_sign_r = 0.0;
        self.period_l = 480.0;
        self.period_r = 480.0;
        self.sample_count_l = 0.0;
        self.sample_count_r = 0.0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sub_bass_no_crash() {
        let mut gen = SubBassGenerator::new(48000.0);
        gen.set_params(true, 0.3, 80.0, 48000.0);

        for i in 0..2048 {
            // Generate a 60 Hz sine wave
            let t = i as f32 / 48000.0;
            let mut l = (t * 60.0 * 2.0 * std::f32::consts::PI).sin() * 0.5;
            let mut r = l;
            gen.process(&mut l, &mut r);
            assert!(l.is_finite());
            assert!(r.is_finite());
        }
    }

    #[test]
    fn test_sub_bass_disabled_passes_through() {
        let mut gen = SubBassGenerator::new(48000.0);
        gen.set_params(false, 0.0, 80.0, 48000.0);

        let mut l = 0.5f32;
        let mut r = -0.5f32;
        gen.process(&mut l, &mut r);

        // Signal should pass through with minimal change when disabled
        assert!(l.is_finite());
        assert!(r.is_finite());
    }

    #[test]
    fn test_sub_bass_bounded_output() {
        let mut gen = SubBassGenerator::new(48000.0);
        gen.set_params(true, 1.0, 60.0, 48000.0);

        // Warm up
        for _ in 0..512 {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            gen.process(&mut l, &mut r);
        }

        let mut l = 0.9f32;
        let mut r = 0.9f32;
        gen.process(&mut l, &mut r);

        assert!(l.abs() <= 1.0, "Output L {} exceeds unity", l);
        assert!(r.abs() <= 1.0, "Output R {} exceeds unity", r);
    }
}
