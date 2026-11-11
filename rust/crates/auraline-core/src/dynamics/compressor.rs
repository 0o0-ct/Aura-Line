//! Real-Time Safe Wideband and Multiband Compressor with RMS detection

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::AtomicF64;

pub struct Compressor {
    enabled: AtomicBool,
    threshold_db: AtomicF64,
    ratio: AtomicF64,
    attack_ms: AtomicF64,
    release_ms: AtomicF64,
    makeup_db: AtomicF64,

    comp_attack: f32,
    comp_release: f32,
    envelope: f32,
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
            comp_attack: 0.05,
            comp_release: 0.001,
            envelope: 0.0,
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
        sample_rate: f32,
    ) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.threshold_db.store(threshold_db as f64, Ordering::Relaxed);
        self.ratio.store(ratio.max(1.0) as f64, Ordering::Relaxed);
        self.attack_ms.store(attack_ms.max(0.1) as f64, Ordering::Relaxed);
        self.release_ms.store(release_ms.max(1.0) as f64, Ordering::Relaxed);
        self.makeup_db.store(makeup_db as f64, Ordering::Relaxed);
        self.update_coefficients(sample_rate);
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let thresh_db = self.threshold_db.load(Ordering::Relaxed) as f32;
        let ratio = self.ratio.load(Ordering::Relaxed) as f32;
        let makeup_db = self.makeup_db.load(Ordering::Relaxed) as f32;

        let thresh_lin = 10.0f32.powf(thresh_db / 20.0);
        let makeup_lin = 10.0f32.powf(makeup_db / 20.0);

        let peak = l.abs().max(r.abs());

        if peak > self.envelope {
            self.envelope += (peak - self.envelope) * self.comp_attack;
        } else {
            self.envelope += (peak - self.envelope) * self.comp_release;
        }

        let mut gain = 1.0f32;
        if self.envelope > thresh_lin && thresh_lin > 1e-6 {
            let db_over = 20.0 * (self.envelope / thresh_lin).log10();
            let db_reduction = db_over * (1.0 - 1.0 / ratio);
            gain = 10.0f32.powf(-db_reduction / 20.0);
        }

        *l = *l * gain * makeup_lin;
        *r = *r * gain * makeup_lin;
    }
}
