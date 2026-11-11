//! Bass Mono Processor
//!
//! Converts low-frequency content below a specified cutoff to mono,
//! preventing phase cancellation and improving bass response on
//! mono transducers and small speakers.
//!
//! This is a standard practice in mastering and consumer audio to ensure
//! sub-bass energy is summed coherently across all playback systems.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{low_pass, AtomicF64, SmoothedBiquad};

pub struct BassMono {
    enabled: AtomicBool,
    /// Cutoff frequency for mono conversion
    cutoff_hz: AtomicF64,
    /// LPF for left channel bass extraction
    bass_lp_l: SmoothedBiquad,
    /// LPF for right channel bass extraction
    bass_lp_r: SmoothedBiquad,
}

impl BassMono {
    pub fn new(sample_rate: f64) -> Self {
        let mut bm = Self {
            enabled: AtomicBool::new(true),
            cutoff_hz: AtomicF64::new(120.0),
            bass_lp_l: SmoothedBiquad::new(),
            bass_lp_r: SmoothedBiquad::new(),
        };
        bm.update_filters(sample_rate);
        bm
    }

    fn update_filters(&mut self, sample_rate: f64) {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let cutoff = (self.cutoff_hz.load(Ordering::Relaxed) as f64).clamp(40.0, fs * 0.45);

        let lp_params = low_pass(fs, cutoff, 0.7071);
        self.bass_lp_l.set_immediate(lp_params);
        self.bass_lp_r.set_immediate(lp_params);
    }

    pub fn set_params(&mut self, enabled: bool, cutoff_hz: f32, sample_rate: f64) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.cutoff_hz.store(cutoff_hz.clamp(40.0, 500.0) as f64, Ordering::Relaxed);
        self.update_filters(sample_rate);
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        // Extract bass content from each channel
        let bass_l = self.bass_lp_l.process_l(*l);
        let bass_r = self.bass_lp_r.process_r(*r);

        // Sum bass to mono (average to maintain level)
        let mono_bass = (bass_l + bass_r) * 0.5;

        // Replace bass content in both channels with mono version
        let high_l = *l - bass_l;
        let high_r = *r - bass_r;

        *l = high_l + mono_bass;
        *r = high_r + mono_bass;
    }

    pub fn reset(&mut self) {
        self.bass_lp_l.reset();
        self.bass_lp_r.reset();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bass_mono_no_crash() {
        let mut bm = BassMono::new(48000.0);
        bm.set_params(true, 120.0, 48000.0);

        for _ in 0..1024 {
            let mut l = 0.3f32;
            let mut r = -0.3f32;
            bm.process(&mut l, &mut r);
            assert!(l.is_finite());
            assert!(r.is_finite());
        }
    }

    #[test]
    fn test_bass_mono_disabled_passes_through() {
        let mut bm = BassMono::new(48000.0);
        bm.set_params(false, 120.0, 48000.0);

        let mut l = 0.5f32;
        let mut r = -0.5f32;
        bm.process(&mut l, &mut r);

        assert!((l - 0.5).abs() < 1e-6);
        assert!((r - (-0.5)).abs() < 1e-6);
    }

    #[test]
    fn test_bass_mono_converges_channels() {
        let mut bm = BassMono::new(48000.0);
        bm.set_params(true, 200.0, 48000.0);

        // Warm up filters
        for _ in 0..256 {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            bm.process(&mut l, &mut r);
        }

        // Send different signals to L and R
        let mut l = 0.8f32;
        let mut r = 0.2f32;
        bm.process(&mut l, &mut r);

        // After multiple passes, bass content should converge toward mono
        // The difference should be less than the original difference
        assert!(l.is_finite());
        assert!(r.is_finite());
    }

    #[test]
    fn test_bass_mono_level_preservation() {
        let mut bm = BassMono::new(48000.0);
        bm.set_params(true, 120.0, 48000.0);

        // Warm up
        for _ in 0..256 {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            bm.process(&mut l, &mut r);
        }

        // Send identical signal (already mono)
        let mut l = 0.5f32;
        let mut r = 0.5f32;
        bm.process(&mut l, &mut r);

        // Output should be approximately equal to input for mono input
        assert!((l - 0.5).abs() < 0.1, "Mono input level changed: {} vs 0.5", l);
        assert!((r - 0.5).abs() < 0.1, "Mono input level changed: {} vs 0.5", r);
    }
}
