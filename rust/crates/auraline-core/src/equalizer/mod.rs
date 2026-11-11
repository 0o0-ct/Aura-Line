//! 25-Band Graphic/Parametric Equalizer Cascade

use crate::filters::biquad::{peaking_eq, SmoothedBiquad};

pub const MAX_EQ_BANDS: usize = 25;

pub struct EqualizerCascade {
    pub bands: [SmoothedBiquad; MAX_EQ_BANDS],
}

impl EqualizerCascade {
    pub fn new() -> Self {
        Self {
            bands: std::array::from_fn(|_| SmoothedBiquad::new()),
        }
    }

    pub fn set_band(&mut self, index: usize, sample_rate: f64, freq: f64, gain_db: f64, q: f64) {
        if index < MAX_EQ_BANDS {
            let params = peaking_eq(sample_rate, freq, gain_db, q);
            self.bands[index].set_target(params);
        }
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        for band in self.bands.iter_mut() {
            band.process_stereo_sample(l, r);
        }
    }
}
