//! Real-Time Safe Wideband and Multiband Compressor with RMS Detection
//!
//! Uses RMS (Root Mean Square) envelope detection for more accurate
//! gain reduction that tracks the perceived loudness of the signal
//! rather than instantaneous peaks. This provides smoother compression
//! that is less prone to pumping and breathing artifacts.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::AtomicF64;

pub struct Compressor {
    enabled: AtomicBool,
    threshold_db: AtomicF64,
    ratio: AtomicF64,
    attack_ms: AtomicF64,
    release_ms: AtomicF64,
    makeup_db: AtomicF64,
    knee_db: AtomicF64,

    comp_attack: f32,
    comp_release: f32,
    /// RMS envelope follower
    rms_envelope: f32,
    /// Current gain value for smooth transitions
    current_gain: f32,
}

impl Compressor {
    pub fn new(sample_rate: f32) -> Self {
        let mut comp = Self {
            enabled: AtomicBool::new(true),
            threshold_db: AtomicF64::new(-20.0),
            ratio: AtomicF64::new(4.0),
            attack_ms: AtomicF64::new(10.0),
            release_ms: AtomicF64::new(150.0),
            makeup_db: AtomicF64::new(0.0),
            knee_db: AtomicF64::new(6.0),
            comp_attack: 0.05,
            comp_release: 0.001,
            rms_envelope: 0.0,
            current_gain: 1.0,
        };
        comp.update_coefficients(sample_rate);
        comp
    }

    pub fn update_coefficients(&mut self, sample_rate: f32) {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let atk = (self.attack_ms.load(Ordering::Relaxed) as f32).max(0.1) * 0.001;
        let rel = (self.release_ms.load(Ordering::Relaxed) as f32).max(1.0) * 0.001;

        self.comp_attack = 1.0 - (-1.0 / (atk * fs)).exp();
        self.comp_release = 1.0 - (-1.0 / (rel * fs)).exp();
    }

    pub fn set_params(
        &mut self,
        enabled: bool,
        threshold_db: f32,
        ratio: f32,
        attack_ms: f32,
        release_ms: f32,
        makeup_db: f32,
        knee_db: f32,
        sample_rate: f32,
    ) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.threshold_db.store(threshold_db.clamp(-80.0, 0.0) as f64, Ordering::Relaxed);
        self.ratio.store(ratio.max(1.0).min(100.0) as f64, Ordering::Relaxed);
        self.attack_ms.store(attack_ms.max(0.1).min(500.0) as f64, Ordering::Relaxed);
        self.release_ms.store(release_ms.max(1.0).min(5000.0) as f64, Ordering::Relaxed);
        self.makeup_db.store(makeup_db.clamp(-36.0, 36.0) as f64, Ordering::Relaxed);
        self.knee_db.store(knee_db.clamp(0.0, 40.0) as f64, Ordering::Relaxed);
        self.update_coefficients(sample_rate);
    }

    /// Calculate gain reduction with soft-knee compression from RMS envelope.
    #[inline(always)]
    fn calculate_gain(&self, rms: f32, thresh_db: f32, ratio: f32, knee_db: f32) -> f32 {
        if rms < 1e-10 {
            return 1.0;
        }

        let rms_db = 20.0 * rms.log10().max(-100.0);
        let thresh_lin = 10.0f32.powf(thresh_db / 20.0);

        // No compression needed below threshold - knee region
        if rms_db <= thresh_db - knee_db {
            return 1.0;
        }

        let knee_half = knee_db * 0.5;

        let gain_db = if rms_db > thresh_db + knee_half {
            // Hard knee region: full ratio applies
            let db_over = rms_db - thresh_db;
            -db_over * (1.0 - 1.0 / ratio)
        } else if rms_db > thresh_db - knee_half {
            // Soft knee region: gradual transition
            let x = (rms_db - thresh_db + knee_half) / knee_db;
            let x2 = x * x;
            -x2 * knee_half / (2.0 * ratio)
        } else {
            0.0
        };

        10.0f32.powf(gain_db / 20.0)
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let thresh_db = self.threshold_db.load(Ordering::Relaxed) as f32;
        let ratio = self.ratio.load(Ordering::Relaxed) as f32;
        let makeup_db = self.makeup_db.load(Ordering::Relaxed) as f32;
        let knee_db = self.knee_db.load(Ordering::Relaxed) as f32;
        let makeup_lin = 10.0f32.powf(makeup_db / 20.0);

        // RMS envelope detection: square the samples and average
        let sq_l = *l * *l;
        let sq_r = *r * *r;
        let rms_sample = ((sq_l + sq_r) * 0.5).sqrt();

        // Smooth RMS envelope follower with asymmetric attack/release
        if rms_sample > self.rms_envelope {
            self.rms_envelope += (rms_sample - self.rms_envelope) * self.comp_attack;
        } else {
            self.rms_envelope += (rms_sample - self.rms_envelope) * self.comp_release;
        }

        // Calculate target gain from RMS envelope
        let target_gain = self.calculate_gain(self.rms_envelope, thresh_db, ratio, knee_db);

        // Smooth gain transitions to avoid pumping
        let gain_diff = target_gain - self.current_gain;
        self.current_gain += gain_diff * self.comp_attack.max(0.1);

        // Apply gain and makeup
        *l = *l * self.current_gain * makeup_lin;
        *r = *r * self.current_gain * makeup_lin;
    }

    /// Reset compressor state (e.g., on pause/resume)
    pub fn reset(&mut self) {
        self.rms_envelope = 0.0;
        self.current_gain = 1.0;
    }
}
