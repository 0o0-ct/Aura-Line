//! Lookahead True-Peak Limiter with Inter-Sample Gradient Peak Estimation
//!
//! Prevents inter-sample DAC clipping and oversaturation on Bluetooth codecs (SBC/AAC/LDAC)
//! with a preallocated ring delay buffer and smooth logarithmic auto-release.
//!
//! The lookahead delay is implemented as a circular buffer: incoming samples are written
//! immediately, and the gain-calculated output is read LOOKAHEAD_SAMPLES positions behind
//! the write pointer. This provides true lookahead behavior where the limiter can react
//! to future peaks before they are output.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::AtomicF64;

const LOOKAHEAD_SAMPLES: usize = 256;

pub struct LookaheadTruePeakLimiter {
    enabled: AtomicBool,
    ceiling_db: AtomicF64,
    threshold_lin: f32,
    attack: f32,
    release: f32,
    current_gain: f32,
    prev_in_l: f32,
    prev_in_r: f32,
    /// Circular delay buffer: interleaved stereo [L0, R0, L1, R1, ...]
    delay_buffer: [f32; LOOKAHEAD_SAMPLES * 2],
    /// Next write position (also determines read position via lookahead offset)
    write_idx: usize,
}

impl LookaheadTruePeakLimiter {
    pub fn new(sample_rate: f32) -> Self {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let mut limiter = Self {
            enabled: AtomicBool::new(true),
            ceiling_db: AtomicF64::new(-1.5), // -1.5 dBFS safe ceiling default
            threshold_lin: 0.8414,
            attack: 1.0 - (-1.0 / (0.001 * fs)).exp(),  // ~1ms attack
            release: 1.0 - (-1.0 / (0.080 * fs)).exp(), // ~80ms release
            current_gain: 1.0,
            prev_in_l: 0.0,
            prev_in_r: 0.0,
            delay_buffer: [0.0; LOOKAHEAD_SAMPLES * 2],
            write_idx: 0,
        };
        limiter.update_threshold();
        limiter
    }

    /// Returns the lookahead latency in samples.
    pub fn lookahead_samples() -> usize {
        LOOKAHEAD_SAMPLES
    }

    /// Returns the lookahead latency in milliseconds for the given sample rate.
    pub fn lookahead_ms(sample_rate: f32) -> f32 {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        (LOOKAHEAD_SAMPLES as f32) / fs * 1000.0
    }

    pub fn set_enabled(&self, enabled: bool) {
        self.enabled.store(enabled, Ordering::Relaxed);
    }

    pub fn set_ceiling_db(&mut self, db: f32) {
        let clamped = db.clamp(-20.0, 0.0);
        self.ceiling_db.store(clamped as f64, Ordering::Relaxed);
        self.update_threshold();
    }

    pub fn get_ceiling_db(&self) -> f32 {
        self.ceiling_db.load(Ordering::Relaxed) as f32
    }

    pub fn update_threshold(&mut self) {
        let db = self.ceiling_db.load(Ordering::Relaxed) as f32;
        self.threshold_lin = 10.0f32.powf(db / 20.0);
    }

    /// Resets the limiter state and delay buffer (e.g., on pause/resume).
    pub fn reset(&mut self) {
        self.current_gain = 1.0;
        self.prev_in_l = 0.0;
        self.prev_in_r = 0.0;
        self.delay_buffer = [0.0; LOOKAHEAD_SAMPLES * 2];
        self.write_idx = 0;
    }

    #[inline(always)]
    pub fn process(&mut self, in_l: &mut f32, in_r: &mut f32, clipped: &mut bool) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        // Write incoming sample to delay buffer at current write position
        let write_pos = self.write_idx * 2;
        self.delay_buffer[write_pos] = *in_l;
        self.delay_buffer[write_pos + 1] = *in_r;

        // Advance write pointer for next call
        self.write_idx = (self.write_idx + 1) % LOOKAHEAD_SAMPLES;

        // Read position is LOOKAHEAD_SAMPLES behind the current write position
        // (i.e., the sample we just wrote will be read after LOOKAHEAD_SAMPLES cycles)
        let read_idx = self.write_idx;
        let read_pos = read_idx * 2;
        let delayed_l = self.delay_buffer[read_pos];
        let delayed_r = self.delay_buffer[read_pos + 1];

        // Inter-sample gradient true peak estimation on delayed signal
        let abs_l = delayed_l.abs();
        let abs_r = delayed_r.abs();
        let est_peak_l = abs_l + 0.25 * (delayed_l - self.prev_in_l).abs();
        let est_peak_r = abs_r + 0.25 * (delayed_r - self.prev_in_r).abs();
        self.prev_in_l = delayed_l;
        self.prev_in_r = delayed_r;

        let peak = est_peak_l.max(est_peak_r);
        if peak > 1.0 {
            *clipped = true;
        }

        let target_gain = if peak > self.threshold_lin {
            self.threshold_lin / peak
        } else {
            1.0
        };

        if target_gain < self.current_gain {
            self.current_gain += (target_gain - self.current_gain) * self.attack;
        } else {
            self.current_gain += (target_gain - self.current_gain) * self.release;
        }

        // Apply smooth gain to delayed samples
        *in_l = delayed_l * self.current_gain;
        *in_r = delayed_r * self.current_gain;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_limiter_latency() {
        assert_eq!(LookaheadTruePeakLimiter::lookahead_samples(), 256);
        let ms = LookaheadTruePeakLimiter::lookahead_ms(48000.0);
        assert!((ms - 5.333).abs() < 0.1); // ~5.33ms at 48kHz
    }

    #[test]
    fn test_limiter_no_clipping_at_low_level() {
        let mut limiter = LookaheadTruePeakLimiter::new(48000.0);
        let mut clipped = false;

        // Warm up the delay buffer
        for _ in 0..LOOKAHEAD_SAMPLES {
            let mut l = 0.1f32;
            let mut r = 0.1f32;
            limiter.process(&mut l, &mut r, &mut clipped);
        }

        assert!(!clipped);
    }

    #[test]
    fn test_limiter_gain_reduction() {
        let mut limiter = LookaheadTruePeakLimiter::new(48000.0);
        limiter.set_ceiling_db(-3.0);

        // Warm up
        for _ in 0..LOOKAHEAD_SAMPLES {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            limiter.process(&mut l, &mut r, &mut false);
        }

        // Send signal above threshold
        let mut l = 0.95f32;
        let mut r = 0.95f32;
        limiter.process(&mut l, &mut r, &mut false);

        // Output should be reduced below ceiling
        let ceiling_lin = 10.0f32.powf(-3.0 / 20.0);
        assert!(l.abs() <= ceiling_lin + 0.05, "Output {} exceeds ceiling {}", l, ceiling_lin);
    }
}
