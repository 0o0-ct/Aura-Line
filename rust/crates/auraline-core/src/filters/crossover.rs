//! 4th-Order Linkwitz-Riley Crossover (LR4, 24 dB/octave)
//!
//! Composed of two 2nd-order Butterworth filters in cascade (Q = 1 / sqrt(2) = 0.7071067811865475).
//! Guarantees strictly flat magnitude sum (0 dB at crossover point) and 0° relative phase difference.

use super::biquad::{high_pass, low_pass, SmoothedBiquad};

pub struct LinkwitzRiley4Crossover {
    lp1: SmoothedBiquad,
    lp2: SmoothedBiquad,
    hp1: SmoothedBiquad,
    hp2: SmoothedBiquad,
    sample_rate: f64,
    cutoff_freq: f64,
}

impl LinkwitzRiley4Crossover {
    pub fn new(sample_rate: f64, cutoff_freq: f64) -> Self {
        let mut crossover = Self {
            lp1: SmoothedBiquad::new(),
            lp2: SmoothedBiquad::new(),
            hp1: SmoothedBiquad::new(),
            hp2: SmoothedBiquad::new(),
            sample_rate,
            cutoff_freq,
        };
        crossover.update_filters();
        crossover
    }

    pub fn set_cutoff(&mut self, cutoff_freq: f64) {
        self.cutoff_freq = cutoff_freq;
        self.update_filters();
    }

    pub fn update_filters(&mut self) {
        let q_butterworth = 0.7071067811865475; // Q = 1/sqrt(2)
        let lp_params = low_pass(self.sample_rate, self.cutoff_freq, q_butterworth);
        let hp_params = high_pass(self.sample_rate, self.cutoff_freq, q_butterworth);

        self.lp1.set_target(lp_params);
        self.lp2.set_target(lp_params);
        self.hp1.set_target(hp_params);
        self.hp2.set_target(hp_params);
    }

    #[inline(always)]
    pub fn process(&mut self, in_l: f32, in_r: f32) -> (f32, f32, f32, f32) {
        self.lp1.update_coefficients();
        self.lp2.update_coefficients();
        self.hp1.update_coefficients();
        self.hp2.update_coefficients();

        let l_lp = self.lp2.process_l(self.lp1.process_l(in_l));
        let r_lp = self.lp2.process_r(self.lp1.process_r(in_r));
        let l_hp = self.hp2.process_l(self.hp1.process_l(in_l));
        let r_hp = self.hp2.process_r(self.hp1.process_r(in_r));

        (l_lp, r_lp, l_hp, r_hp) // (low_l, low_r, high_l, high_r)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_lr4_complementary_sum() {
        let mut crossover = LinkwitzRiley4Crossover::new(48000.0, 1000.0);
        let in_sample = 0.8f32;

        // Process warm-up samples (allow filter transient step to settle)
        let mut sum_l = 0.0f32;
        for _ in 0..2000 {
            let (low_l, _, high_l, _) = crossover.process(in_sample, 0.0);
            sum_l = low_l + high_l;
        }

        // LR4 complementary sum should reconstruct signal smoothly
        assert!((sum_l - in_sample).abs() < 1e-3);
    }
}
