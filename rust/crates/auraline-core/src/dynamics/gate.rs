//! Real-Time Safe Noise Gate / Expander
//!
//! Implements a noise gate with configurable threshold, ratio, attack and release times.
//! Uses peak detection with smooth gain transitions to prevent clicking artifacts.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::AtomicF64;

pub struct NoiseGate {
    enabled: AtomicBool,
    threshold_db: AtomicF64,
    ratio: AtomicF64,
    attack_ms: AtomicF64,
    release_ms: AtomicF64,
    gate_attack: f32,
    gate_release: f32,
    gate_gain: f32,
}

impl NoiseGate {
    pub fn new(sample_rate: f32) -> Self {
        let mut gate = Self {
            enabled: AtomicBool::new(true),
            threshold_db: AtomicF64::new(-60.0),
            ratio: AtomicF64::new(2.0),
            attack_ms: AtomicF64::new(5.0),
            release_ms: AtomicF64::new(100.0),
            gate_attack: 0.05,
            gate_release: 0.01,
            gate_gain: 1.0,
        };
        gate.update_coefficients(sample_rate);
        gate
    }

    pub fn update_coefficients(&mut self, sample_rate: f32) {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let atk = (self.attack_ms.load(Ordering::Relaxed) as f32).max(0.1) * 0.001;
        let rel = (self.release_ms.load(Ordering::Relaxed) as f32).max(1.0) * 0.001;

        self.gate_attack = 1.0 - (-1.0 / (atk * fs)).exp();
        self.gate_release = 1.0 - (-1.0 / (rel * fs)).exp();
    }

    pub fn set_params(
        &mut self,
        enabled: bool,
        threshold_db: f32,
        ratio: f32,
        attack_ms: f32,
        release_ms: f32,
        sample_rate: f32,
    ) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.threshold_db.store(threshold_db as f64, Ordering::Relaxed);
        self.ratio.store(ratio.max(1.0) as f64, Ordering::Relaxed);
        self.attack_ms.store(attack_ms.max(0.1) as f64, Ordering::Relaxed);
        self.release_ms.store(release_ms.max(1.0) as f64, Ordering::Relaxed);
        self.update_coefficients(sample_rate);
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let thresh_db = self.threshold_db.load(Ordering::Relaxed) as f32;
        let ratio = self.ratio.load(Ordering::Relaxed) as f32;
        let thresh_lin = 10.0f32.powf(thresh_db / 20.0);
        let peak = l.abs().max(r.abs());

        let target_gain = if peak < thresh_lin {
            // Below threshold: apply ratio (expander behavior)
            if thresh_lin > 1e-6 && peak > 1e-6 {
                let db_sig = 20.0 * peak.log10();
                let db_thresh = 20.0 * thresh_lin.log10();
                let db_over = db_sig - db_thresh;
                let db_reduction = db_over / ratio;
                (10.0f32.powf(db_reduction / 20.0)).min(1.0)
            } else {
                0.0
            }
        } else {
            1.0
        };

        // Smooth gain transitions
        if target_gain < self.gate_gain {
            self.gate_gain -= (self.gate_gain - target_gain) * self.gate_release;
        } else {
            self.gate_gain += (target_gain - self.gate_gain) * self.gate_attack;
        }

        *l *= self.gate_gain;
        *r *= self.gate_gain;
    }
}
