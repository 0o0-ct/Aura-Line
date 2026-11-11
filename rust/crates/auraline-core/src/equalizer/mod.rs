//! 25-Band Graphic/Parametric Equalizer Cascade

use crate::filters::biquad::{peaking_eq, SmoothedBiquad};

pub const MAX_EQ_BANDS: usize = 25;

/// Q unificado para el ecualizador gráfico en todos los motores (Web Audio, Android nativo y Rust).
/// Valor coherente con la rejilla logarítmica de 25 bandas (~3.46, ancho de banda adaptado).
pub const EQ_BAND_Q: f64 = 3.46;

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
            // Q unificado: el valor por defecto de la rejilla logarítmica se usa si el llamador
            // no provee uno válido.
            let effective_q = if q.is_finite() && q > 0.0 { q } else { EQ_BAND_Q };
            let params = peaking_eq(sample_rate, freq, gain_db, effective_q);
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
