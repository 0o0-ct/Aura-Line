//! Acoustic Model & Instrument Profiles for Low Frequency Sources
//!
//! Includes headphone profiles for compensating the frequency response
//! of common headphone models based on measured data.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{peaking_eq, AtomicF64, BiquadParams, SmoothedBiquad};

/// Headphone frequency response correction profile.
/// Based on measured deviations from target response.
#[derive(Debug, Clone, Copy)]
pub struct HeadphoneProfile {
    /// Display name
    pub name: &'static str,
    /// Manufacturer
    pub manufacturer: &'static str,
    /// EQ bands to apply for correction: (frequency_hz, gain_db, q)
    pub eq_bands: &'static [(f64, f64, f64)],
}

/// Predefined headphone profiles based on common measurements.
/// These are approximate corrections and should be tuned per unit.
pub const HEADPHONE_PROFILES: &[HeadphoneProfile] = &[
    HeadphoneProfile {
        name: "Sony WH-1000XM4",
        manufacturer: "Sony",
        eq_bands: &[
            (60.0, 2.0, 1.0),    // Boost sub-bass
            (150.0, -1.5, 1.2),  // Reduce bass boom
            (3000.0, -2.0, 1.5), // Reduce sibilance
            (8000.0, 1.5, 2.0),  // Boost air
        ],
    },
    HeadphoneProfile {
        name: "AirPods Pro",
        manufacturer: "Apple",
        eq_bands: &[
            (80.0, 1.0, 1.0),    // Slight bass boost
            (250.0, -1.0, 1.2),  // Reduce muddiness
            (5000.0, -1.5, 1.5), // Reduce harshness
            (10000.0, 1.0, 2.0), // Boost air
        ],
    },
    HeadphoneProfile {
        name: "Sennheiser HD 599",
        manufacturer: "Sennheiser",
        eq_bands: &[
            (50.0, 1.5, 1.0),    // Boost sub-bass
            (200.0, -0.5, 1.2),  // Slight bass reduction
            (4000.0, -1.0, 1.5), // Reduce presence peak
            (12000.0, 0.5, 2.0), // Slight air boost
        ],
    },
    HeadphoneProfile {
        name: "Bose QC35",
        manufacturer: "Bose",
        eq_bands: &[
            (70.0, 1.5, 1.0),    // Boost bass
            (400.0, -1.0, 1.2),  // Reduce lower mid
            (3500.0, -2.0, 1.5), // Reduce sibilance
            (9000.0, 1.0, 2.0),  // Boost air
        ],
    },
    HeadphoneProfile {
        name: "Generic IEM",
        manufacturer: "Generic",
        eq_bands: &[
            (80.0, 2.0, 1.0),    // Boost bass
            (250.0, -1.5, 1.2),  // Reduce muddiness
            (6000.0, -1.0, 1.5), // Reduce harshness
            (10000.0, 1.5, 2.0), // Boost air
        ],
    },
];

/// Harmonic target curves for different listening preferences.
#[derive(Debug, Clone, Copy)]
pub struct TargetCurve {
    /// Display name
    pub name: &'static str,
    /// Description
    pub description: &'static str,
    /// EQ bands: (frequency_hz, gain_db, q)
    pub eq_bands: &'static [(f64, f64, f64)],
}

pub const TARGET_CURVES: &[TargetCurve] = &[
    TargetCurve {
        name: "Harman Target",
        description: "Popular target curve based on Harman research",
        eq_bands: &[
            (35.0, 4.0, 0.5),    // Sub-bass boost
            (80.0, 3.0, 1.0),    // Bass boost
            (200.0, 1.0, 1.2),   // Lower mid slight boost
            (1000.0, 0.0, 1.0),  // Reference
            (3000.0, 2.0, 1.5),  // Presence boost
            (8000.0, 3.0, 2.0),  // Brilliance boost
        ],
    },
    TargetCurve {
        name: "Flat Reference",
        description: "Flat response for critical listening",
        eq_bands: &[],
    },
    TargetCurve {
        name: "V-Shape",
        description: "Enhanced bass and treble for energetic sound",
        eq_bands: &[
            (60.0, 6.0, 1.0),    // Strong bass boost
            (250.0, -3.0, 1.2),  // Mid scoop
            (1000.0, -2.0, 1.0), // Mid reduction
            (4000.0, 2.0, 1.5),  // Presence boost
            (10000.0, 5.0, 2.0), // Strong treble boost
        ],
    },
    TargetCurve {
        name: "Warm",
        description: "Warm, relaxed sound for casual listening",
        eq_bands: &[
            (80.0, 3.0, 1.0),    // Bass boost
            (200.0, 1.5, 1.2),   // Lower mid warmth
            (4000.0, -1.0, 1.5), // Reduce harshness
            (10000.0, -2.0, 2.0), // Roll back treble
        ],
    },
];

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
