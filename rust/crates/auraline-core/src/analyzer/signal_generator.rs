//! Internal Audio Signal Generator for instant A/B testing
//!
//! Modes: Pink Noise (Paul Kellet 7-stage filter), White Noise, Sine Sweep (20Hz-20kHz)

use std::f64::consts::PI;
use std::sync::atomic::{AtomicI32, Ordering};
use crate::filters::biquad::AtomicF64;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum GeneratorMode {
    Off = 0,
    PinkNoise = 1,
    WhiteNoise = 2,
    SineSweep = 3,
}

pub struct SignalGenerator {
    mode: AtomicI32,
    level: AtomicF64,
    sample_rate: f64,

    // Pink noise filter state
    b0: f32,
    b1: f32,
    b2: f32,
    b3: f32,
    b4: f32,
    b5: f32,
    b6: f32,
    seed: u32,

    // Sine sweep parameters
    sweep_phase: f64,
    sweep_time: f64,
    sweep_duration: f64,
}

impl SignalGenerator {
    pub fn new(sample_rate: f64) -> Self {
        Self {
            mode: AtomicI32::new(GeneratorMode::Off as i32),
            level: AtomicF64::new(0.35), // -9 dBFS default safe test level
            sample_rate: if sample_rate <= 0.0 { 48000.0 } else { sample_rate },
            b0: 0.0,
            b1: 0.0,
            b2: 0.0,
            b3: 0.0,
            b4: 0.0,
            b5: 0.0,
            b6: 0.0,
            seed: 22222,
            sweep_phase: 0.0,
            sweep_time: 0.0,
            sweep_duration: 6.0, // 6s sweep
        }
    }

    pub fn set_mode(&self, mode: GeneratorMode) {
        self.mode.store(mode as i32, Ordering::Relaxed);
    }

    pub fn set_level(&self, level: f32) {
        self.level.store(level.clamp(0.0, 1.0) as f64, Ordering::Relaxed);
    }

    pub fn get_mode(&self) -> GeneratorMode {
        match self.mode.load(Ordering::Relaxed) {
            1 => GeneratorMode::PinkNoise,
            2 => GeneratorMode::WhiteNoise,
            3 => GeneratorMode::SineSweep,
            _ => GeneratorMode::Off,
        }
    }

    #[inline(always)]
    fn rand_uniform(&mut self) -> f32 {
        self.seed = self.seed.wrapping_mul(1664525).wrapping_add(1013904223);
        ((self.seed >> 9) as f32 / 8388608.0) - 1.0
    }

    #[inline(always)]
    pub fn generate_sample(&mut self, out_l: &mut f32, out_r: &mut f32) {
        let m = self.mode.load(Ordering::Relaxed);
        if m == 0 {
            *out_l = 0.0;
            *out_r = 0.0;
            return;
        }

        let amp = self.level.load(Ordering::Relaxed) as f32;

        if m == 2 {
            // White noise
            let white = self.rand_uniform() * amp;
            *out_l = white;
            *out_r = white;
        } else if m == 1 {
            // Pink noise (Paul Kellet's algorithm)
            let white = self.rand_uniform();
            self.b0 = 0.99886 * self.b0 + white * 0.0555179;
            self.b1 = 0.99332 * self.b1 + white * 0.0750759;
            self.b2 = 0.96900 * self.b2 + white * 0.1538520;
            self.b3 = 0.86650 * self.b3 + white * 0.3104856;
            self.b4 = 0.55000 * self.b4 + white * 0.5329522;
            self.b5 = -0.7616 * self.b5 - white * 0.0168980;
            let pink = (self.b0 + self.b1 + self.b2 + self.b3 + self.b4 + self.b5 + self.b6 + white * 0.5362) * 0.11;
            self.b6 = white * 0.115926;

            let val = pink * amp;
            *out_l = val;
            *out_r = val;
        } else if m == 3 {
            // Logarithmic sine sweep 20Hz -> 20,000Hz
            let t = self.sweep_time / self.sweep_duration;
            let f = 20.0 * 1000.0f64.powf(t);
            self.sweep_phase += 2.0 * PI * f / self.sample_rate;
            if self.sweep_phase >= 2.0 * PI {
                self.sweep_phase -= 2.0 * PI;
            }

            let val = (self.sweep_phase.sin() as f32) * amp;
            *out_l = val;
            *out_r = val;

            self.sweep_time += 1.0 / self.sample_rate;
            if self.sweep_time >= self.sweep_duration {
                self.sweep_time = 0.0;
            }
        }
    }
}
