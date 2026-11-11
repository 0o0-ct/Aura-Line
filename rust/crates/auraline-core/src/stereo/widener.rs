//! Mid/Side (M/S) Stereo Widener with Bass Mono Protection (< 120 Hz)

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{high_pass, AtomicF64, SmoothedBiquad};

pub struct StereoWidener {
    enabled: AtomicBool,
    width: AtomicF64,
    bass_mono_hp_l: SmoothedBiquad,
    bass_mono_hp_r: SmoothedBiquad,
}

impl StereoWidener {
    pub fn new(sample_rate: f64) -> Self {
        let mut widener = Self {
            enabled: AtomicBool::new(true),
            width: AtomicF64::new(1.0),
            bass_mono_hp_l: SmoothedBiquad::new(),
            bass_mono_hp_r: SmoothedBiquad::new(),
        };
        widener.init_filter(sample_rate);
        widener
    }

    pub fn init_filter(&mut self, sample_rate: f64) {
        let hp_params = high_pass(sample_rate, 120.0, 0.7071);
        self.bass_mono_hp_l.set_immediate(hp_params);
        self.bass_mono_hp_r.set_immediate(hp_params);
    }

    pub fn set_params(&self, enabled: bool, width: f32) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.width.store(width.clamp(0.0, 2.0) as f64, Ordering::Relaxed);
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let w = self.width.load(Ordering::Relaxed) as f32;
        if (w - 1.0).abs() < 0.01 {
            return;
        }

        let mid = (*l + *r) * 0.5;
        let side = (*l - *r) * 0.5;

        // Bass mono: only widen frequencies above 120 Hz
        let side_high = self.bass_mono_hp_l.process_l(side);
        let side_low = side - side_high;

        let widened_side = side_low + side_high * w;

        *l = mid + widened_side;
        *r = mid - widened_side;
    }
}
