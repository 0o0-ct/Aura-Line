//! Acoustic Model & Instrument Profiles for Low Frequency Sources

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{peaking_eq, AtomicF64, BiquadParams, SmoothedBiquad};

pub struct LowInstrumentEnhancer {
    enabled: AtomicBool,
    fundamental_boost: SmoothedBiquad,
    attack_boost: SmoothedBiquad,
    mud_cut: SmoothedBiquad,
    harmonics_amount: AtomicF64,
}

impl LowInstrumentEnhancer {
    pub fn new() -> Self {
        Self {
            enabled: AtomicBool::new(false),
            fundamental_boost: SmoothedBiquad::new(),
            attack_boost: SmoothedBiquad::new(),
            mud_cut: SmoothedBiquad::new(),
            harmonics_amount: AtomicF64::new(0.0),
        }
    }

    pub fn set_profile(
        &mut self,
        enabled: bool,
        sample_rate: f64,
        fund_freq: f64,
        fund_gain_db: f64,
        attack_freq: f64,
        attack_gain_db: f64,
        mud_freq: f64,
        mud_cut_gain_db: f64,
        harmonics: f32,
    ) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.harmonics_amount.store(harmonics as f64, Ordering::Relaxed);

        if !enabled {
            let flat = BiquadParams::default();
            self.fundamental_boost.set_immediate(flat);
            self.attack_boost.set_immediate(flat);
            self.mud_cut.set_immediate(flat);
            return;
        }

        self.fundamental_boost.set_target(peaking_eq(sample_rate, fund_freq, fund_gain_db, 1.4));
        self.attack_boost.set_target(peaking_eq(sample_rate, attack_freq, attack_gain_db, 2.0));
        self.mud_cut.set_target(peaking_eq(sample_rate, mud_freq, mud_cut_gain_db, 1.2));
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        self.fundamental_boost.process_stereo_sample(l, r);
        self.attack_boost.process_stereo_sample(l, r);
        self.mud_cut.process_stereo_sample(l, r);

        let harm = self.harmonics_amount.load(Ordering::Relaxed) as f32;
        if harm > 0.01 {
            let saturate = |in_val: f32| -> f32 {
                let x = in_val * (1.0 + harm * 0.5);
                x - (x * x * x) * (harm * 0.15)
            };
            *l = saturate(*l);
            *r = saturate(*r);
        }
    }
}
