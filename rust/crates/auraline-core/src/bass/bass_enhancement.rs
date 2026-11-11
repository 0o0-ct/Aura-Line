//! Bass Enhancement with Harmonic Generation
//!
//! Adds perceptual bass by generating harmonics of the low-frequency content.
//! Uses a soft-knee waveshaper to create 2nd and 3rd harmonics, then high-passes
//! the generated content so it blends with the original signal for small speakers
//! that cannot reproduce sub-bass frequencies.
//!
//! This is a well-known technique used in many consumer audio products to make
//! bass "feel" present even on limited transducers.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{high_pass, low_pass, AtomicF64, SmoothedBiquad};

pub struct BassEnhancement {
    enabled: AtomicBool,
    amount: AtomicF64,
    /// Cutoff for extracting bass content
    cutoff_hz: AtomicF64,
    /// HPF to isolate generated harmonics from the original bass
    harmonic_hp_l: SmoothedBiquad,
    harmonic_hp_r: SmoothedBiquad,
    /// LPF to extract only bass content for harmonic generation
    bass_lp_l: SmoothedBiquad,
    bass_lp_r: SmoothedBiquad,
    /// Previous sample for derivative estimation
    prev_l: f32,
    prev_r: f32,
}

impl BassEnhancement {
    pub fn new(sample_rate: f64) -> Self {
        let mut enh = Self {
            enabled: AtomicBool::new(false),
            amount: AtomicF64::new(0.5),
            cutoff_hz: AtomicF64::new(150.0),
            harmonic_hp_l: SmoothedBiquad::new(),
            harmonic_hp_r: SmoothedBiquad::new(),
            bass_lp_l: SmoothedBiquad::new(),
            bass_lp_r: SmoothedBiquad::new(),
            prev_l: 0.0,
            prev_r: 0.0,
        };
        enh.update_filters(sample_rate);
        enh
    }

    fn update_filters(&mut self, sample_rate: f64) {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let cutoff = (self.cutoff_hz.load(Ordering::Relaxed) as f64).clamp(40.0, fs * 0.45);

        // LPF to extract bass content
        let lp_params = low_pass(fs, cutoff, 0.7071);
        self.bass_lp_l.set_immediate(lp_params);
        self.bass_lp_r.set_immediate(lp_params);

        // HPF at 2x cutoff to isolate harmonics (2nd harmonic starts here)
        let hp_params = high_pass(fs, cutoff * 2.0, 0.7071);
        self.harmonic_hp_l.set_immediate(hp_params);
        self.harmonic_hp_r.set_immediate(hp_params);
    }

    pub fn set_params(&mut self, enabled: bool, amount: f32, cutoff_hz: f32, sample_rate: f64) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.amount.store(amount.clamp(0.0, 1.0) as f64, Ordering::Relaxed);
        self.cutoff_hz.store(cutoff_hz.clamp(40.0, 300.0) as f64, Ordering::Relaxed);
        self.update_filters(sample_rate);
    }

    /// Soft-knee odd+even harmonic waveshaper.
    /// Generates 2nd and 3rd harmonics proportional to the input amplitude.
    #[inline(always)]
    fn harmonic_waveshaper(&self, x: f32, amount: f32) -> f32 {
        // Normalize to [-1, 1] range
        let clamped = x.clamp(-1.0, 1.0);

        // 2nd harmonic (even) — creates octave-up perception
        let h2 = clamped * clamped;

        // 3rd harmonic (odd) — preserves polarity
        let h3 = clamped * clamped * clamped;

        // Blend harmonics with soft knee
        let harmonics = (h2 * 0.6 + h3 * 0.4) * amount;

        // Apply smooth tanh-like saturation to prevent harshness
        let saturated = (harmonics / (1.0 + harmonics.abs())).clamp(-1.0, 1.0);

        saturated
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            self.prev_l = *l;
            self.prev_r = *r;
            return;
        }

        let amount = self.amount.load(Ordering::Relaxed) as f32;
        if amount < 0.01 {
            self.prev_l = *l;
            self.prev_r = *r;
            return;
        }

        // Extract bass content via LPF
        let mut bass_l = *l;
        let mut bass_r = *r;
        bass_l = self.bass_lp_l.process_l(bass_l);
        bass_r = self.bass_lp_r.process_r(bass_r);

        // Generate harmonics from bass content
        let harm_l = self.harmonic_waveshaper(bass_l, amount);
        let harm_r = self.harmonic_waveshaper(bass_r, amount);

        // HPF the harmonics to remove any residual fundamental
        let harm_hp_l = self.harmonic_hp_l.process_l(harm_l);
        let harm_hp_r = self.harmonic_hp_r.process_r(harm_r);

        // Blend harmonics with original signal
        *l = *l + harm_hp_l * amount;
        *r = *r + harm_hp_r * amount;

        // Soft clip to prevent overshoot
        *l = (*l / (1.0 + l.abs())).clamp(-1.0, 1.0);
        *r = (*r / (1.0 + r.abs())).clamp(-1.0, 1.0);
        // Note: l and r are &mut f32, so l.abs() works on the dereferenced value

        self.prev_l = *l;
        self.prev_r = *r;
    }

    pub fn reset(&mut self) {
        self.bass_lp_l.reset();
        self.bass_lp_r.reset();
        self.harmonic_hp_l.reset();
        self.harmonic_hp_r.reset();
        self.prev_l = 0.0;
        self.prev_r = 0.0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bass_enhancement_no_crash() {
        let mut enh = BassEnhancement::new(48000.0);
        enh.set_params(true, 0.5, 150.0, 48000.0);

        for _ in 0..1024 {
            let mut l = 0.3f32;
            let mut r = -0.3f32;
            enh.process(&mut l, &mut r);
            assert!(l.is_finite());
            assert!(r.is_finite());
        }
    }

    #[test]
    fn test_bass_enhancement_disabled_passes_through() {
        let mut enh = BassEnhancement::new(48000.0);
        enh.set_params(false, 0.0, 150.0, 48000.0);

        let mut l = 0.5f32;
        let mut r = -0.5f32;
        enh.process(&mut l, &mut r);

        assert!((l - 0.5).abs() < 1e-6);
        assert!((r - (-0.5)).abs() < 1e-6);
    }

    #[test]
    fn test_bass_enhancement_bounded_output() {
        let mut enh = BassEnhancement::new(48000.0);
        enh.set_params(true, 1.0, 100.0, 48000.0);

        // Warm up
        for _ in 0..256 {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            enh.process(&mut l, &mut r);
        }

        // Send full-scale signal
        let mut l = 0.99f32;
        let mut r = 0.99f32;
        enh.process(&mut l, &mut r);

        assert!(l.abs() <= 1.0, "Output L {} exceeds unity", l);
        assert!(r.abs() <= 1.0, "Output R {} exceeds unity", r);
    }
}
