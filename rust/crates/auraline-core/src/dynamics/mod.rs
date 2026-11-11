pub mod compressor;
pub mod gate;

pub use compressor::*;
pub use gate::*;

pub struct DynamicsSuite {
    pub gate: gate::NoiseGate,
    pub compressor: compressor::Compressor,
}

impl DynamicsSuite {
    pub fn new(sample_rate: f32) -> Self {
        Self {
            gate: gate::NoiseGate::new(sample_rate),
            compressor: compressor::Compressor::new(sample_rate),
        }
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        self.gate.process(l, r);
        self.compressor.process(l, r);
    }
}
