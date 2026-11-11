//! Feedback Delay Network (FDN) Stereo Reverb
//!
//! Uses 4 delay lines with prime lengths, normalized 4x4 Hadamard unitary feedback matrix,
//! one-pole lowpass damping, and dry/wet acoustic space blending.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::filters::biquad::{sanitize_denormal, AtomicF64};

const MAX_DELAY: usize = 4800; // ~100ms at 48kHz

pub struct StereoFDNReverb {
    enabled: AtomicBool,
    room_size: AtomicF64,
    damping: AtomicF64,
    mix: AtomicF64,

    delay_buffers: [[f32; MAX_DELAY]; 4],
    write_indices: [usize; 4],
    delay_lengths: [usize; 4],
    last_filter_outputs: [f32; 4],
}

impl StereoFDNReverb {
    pub fn new(sample_rate: f32) -> Self {
        let scale = if sample_rate <= 0.0 { 1.0 } else { sample_rate / 48000.0 };
        let mut rev = Self {
            enabled: AtomicBool::new(false),
            room_size: AtomicF64::new(0.5),
            damping: AtomicF64::new(0.5),
            mix: AtomicF64::new(0.25),
            delay_buffers: [[0.0; MAX_DELAY]; 4],
            write_indices: [0; 4],
            delay_lengths: [
                ((1777.0 * scale) as usize).clamp(100, MAX_DELAY - 1),
                ((1997.0 * scale) as usize).clamp(100, MAX_DELAY - 1),
                ((2179.0 * scale) as usize).clamp(100, MAX_DELAY - 1),
                ((2383.0 * scale) as usize).clamp(100, MAX_DELAY - 1),
            ],
            last_filter_outputs: [0.0; 4],
        };
        rev.reset();
        rev
    }

    pub fn reset(&mut self) {
        self.delay_buffers = [[0.0; MAX_DELAY]; 4];
        self.write_indices = [0; 4];
        self.last_filter_outputs = [0.0; 4];
    }

    pub fn set_params(&self, enabled: bool, room_size: f32, damping: f32, mix: f32) {
        self.enabled.store(enabled, Ordering::Relaxed);
        self.room_size.store(room_size.clamp(0.0, 0.95) as f64, Ordering::Relaxed);
        self.damping.store(damping.clamp(0.0, 0.9) as f64, Ordering::Relaxed);
        self.mix.store(mix.clamp(0.0, 1.0) as f64, Ordering::Relaxed);
    }

    #[inline(always)]
    pub fn process(&mut self, l: &mut f32, r: &mut f32) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }

        let m = self.mix.load(Ordering::Relaxed) as f32;
        if m <= 0.001 {
            return;
        }

        let size = self.room_size.load(Ordering::Relaxed) as f32;
        let damp = self.damping.load(Ordering::Relaxed) as f32;
        let mono_in = (*l + *r) * 0.5;

        let mut d = [0.0f32; 4];
        for i in 0..4 {
            let mut read_idx = (self.write_indices[i] as i32) - (self.delay_lengths[i] as i32);
            if read_idx < 0 {
                read_idx += MAX_DELAY as i32;
            }
            d[i] = self.delay_buffers[i][read_idx as usize];

            // Lowpass one-pole damping
            d[i] = self.last_filter_outputs[i] + (d[i] - self.last_filter_outputs[i]) * (1.0 - damp);
            self.last_filter_outputs[i] = sanitize_denormal(d[i]);
        }

        // Hadamard 4x4 unitary matrix
        let g = 0.5f32;
        let f0 = ( d[0] + d[1] + d[2] + d[3]) * g;
        let f1 = ( d[0] - d[1] + d[2] - d[3]) * g;
        let f2 = ( d[0] + d[1] - d[2] - d[3]) * g;
        let f3 = ( d[0] - d[1] - d[2] + d[3]) * g;

        self.delay_buffers[0][self.write_indices[0]] = sanitize_denormal(mono_in + f0 * size);
        self.delay_buffers[1][self.write_indices[1]] = sanitize_denormal(mono_in + f1 * size);
        self.delay_buffers[2][self.write_indices[2]] = sanitize_denormal(mono_in + f2 * size);
        self.delay_buffers[3][self.write_indices[3]] = sanitize_denormal(mono_in + f3 * size);

        for i in 0..4 {
            self.write_indices[i] = (self.write_indices[i] + 1) % MAX_DELAY;
        }

        let wet_l = (d[0] + d[2]) * 0.7;
        let wet_r = (d[1] + d[3]) * 0.7;

        *l = *l * (1.0 - m) + wet_l * m;
        *r = *r * (1.0 - m) + wet_r * m;
    }
}
