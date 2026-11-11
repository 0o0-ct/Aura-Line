//! Master Real-Time Safe DSP Audio Engine for AuraLine
//!
//! Integrates Preamp, Subsonic Protection, 25-Band EQ Cascade, Bass Processing
//! (Enhancement, Sub-bass, Bass Mono), Low Instrument Enhancer, Dynamics Suite
//! (Gate + RMS Compressor), Spatial (Widener + FDN Reverb), Lookahead True-Peak
//! Limiter with Polyphase Oversampling, Signal Generator, and Real-Time Telemetry
//! in a zero-allocation audio processing callback loop.

use std::sync::atomic::{AtomicBool, Ordering};
use crate::analyzer::signal_generator::{GeneratorMode, SignalGenerator};
use crate::bass::{BassEnhancement, BassMono, SubBassGenerator};
use crate::dynamics::DynamicsSuite;
use crate::equalizer::EqualizerCascade;
use crate::filters::biquad::{high_pass, AtomicF64, SmoothedBiquad};
use crate::limiter::true_peak::LookaheadTruePeakLimiter;
use crate::presets::LowInstrumentEnhancer;
use crate::reverb::fdn::StereoFDNReverb;
use crate::stereo::widener::StereoWidener;
use crate::telemetry::TelemetryCollector;

pub struct AuraLineCoreEngine {
    pub sample_rate: f64,
    pub is_bypass: AtomicBool,
    pub preamp_gain: AtomicF64,

    // Subsonic HPF
    pub subsonic_enabled: AtomicBool,
    pub subsonic_hp_l: SmoothedBiquad,
    pub subsonic_hp_r: SmoothedBiquad,

    // Modules
    pub eq_cascade: EqualizerCascade,
    pub generator: SignalGenerator,
    pub limiter: LookaheadTruePeakLimiter,
    pub reverb: StereoFDNReverb,
    pub widener: StereoWidener,
    pub enhancer: LowInstrumentEnhancer,
    pub dynamics: DynamicsSuite,
    pub telemetry: TelemetryCollector,

    // Bass Processing
    pub bass_enhancement: BassEnhancement,
    pub sub_bass: SubBassGenerator,
    pub bass_mono: BassMono,
}

impl AuraLineCoreEngine {
    pub fn new(sample_rate: f64) -> Self {
        let fs = if sample_rate <= 0.0 { 48000.0 } else { sample_rate };
        let fs_f32 = fs as f32;

        let mut engine = Self {
            sample_rate: fs,
            is_bypass: AtomicBool::new(false),
            preamp_gain: AtomicF64::new(1.0),
            subsonic_enabled: AtomicBool::new(true),
            subsonic_hp_l: SmoothedBiquad::new(),
            subsonic_hp_r: SmoothedBiquad::new(),
            eq_cascade: EqualizerCascade::new(),
            generator: SignalGenerator::new(fs),
            limiter: LookaheadTruePeakLimiter::new(fs_f32),
            reverb: StereoFDNReverb::new(fs_f32),
            widener: StereoWidener::new(fs),
            enhancer: LowInstrumentEnhancer::new(),
            dynamics: DynamicsSuite::new(fs_f32),
            telemetry: TelemetryCollector::new(),
            // Bass Processing
            bass_enhancement: BassEnhancement::new(fs),
            sub_bass: SubBassGenerator::new(fs),
            bass_mono: BassMono::new(fs),
        };

        // Initialize Subsonic 25 Hz 2nd order Butterworth HPF
        let hp_params = high_pass(fs, 25.0, 0.7071);
        engine.subsonic_hp_l.set_immediate(hp_params);
        engine.subsonic_hp_r.set_immediate(hp_params);

        engine
    }

    pub fn set_bypass(&self, bypass: bool) {
        self.is_bypass.store(bypass, Ordering::Relaxed);
    }

    pub fn set_preamp_gain_db(&self, gain_db: f32) {
        let clamped = gain_db.clamp(-36.0, 36.0);
        let lin = 10.0f64.powf((clamped as f64) / 20.0);
        self.preamp_gain.store(lin, Ordering::Relaxed);
    }

    #[inline(always)]
    pub fn process_block(&mut self, audio_buffer: &mut [f32]) {
        let num_samples = audio_buffer.len();
        if num_samples < 2 {
            return;
        }

        let num_frames = num_samples / 2;
        let bypass = self.is_bypass.load(Ordering::Relaxed);
        let preamp = self.preamp_gain.load(Ordering::Relaxed) as f32;
        let gen_mode = self.generator.get_mode();
        let subsonic_active = self.subsonic_enabled.load(Ordering::Relaxed);

        let mut sum_sq_l = 0.0f32;
        let mut sum_sq_r = 0.0f32;
        let mut peak_l = 0.0f32;
        let mut peak_r = 0.0f32;
        let mut clipped = false;

        for i in 0..num_frames {
            let mut in_l = audio_buffer[2 * i];
            let mut in_r = audio_buffer[2 * i + 1];

            // Generator or PCM input
            if gen_mode != GeneratorMode::Off {
                self.generator.generate_sample(&mut in_l, &mut in_r);
            }

            if bypass {
                audio_buffer[2 * i] = in_l;
                audio_buffer[2 * i + 1] = in_r;
            } else {
                let mut cur_l = in_l * preamp;
                let mut cur_r = in_r * preamp;

                if subsonic_active {
                    cur_l = self.subsonic_hp_l.process_l(cur_l);
                    cur_r = self.subsonic_hp_r.process_r(cur_r);
                }

                // 25-Band EQ
                self.eq_cascade.process(&mut cur_l, &mut cur_r);

                // Bass Processing (before dynamics for proper level control)
                self.bass_mono.process(&mut cur_l, &mut cur_r);
                self.bass_enhancement.process(&mut cur_l, &mut cur_r);
                self.sub_bass.process(&mut cur_l, &mut cur_r);

                // Acoustic Enhancer
                self.enhancer.process(&mut cur_l, &mut cur_r);

                // Dynamics (Gate + RMS Compressor)
                self.dynamics.process(&mut cur_l, &mut cur_r);

                // Spatial (Widener + Reverb)
                self.widener.process(&mut cur_l, &mut cur_r);
                self.reverb.process(&mut cur_l, &mut cur_r);

                // True Peak Limiter with Polyphase Oversampling
                self.limiter.process(&mut cur_l, &mut cur_r, &mut clipped);

                audio_buffer[2 * i] = cur_l;
                audio_buffer[2 * i + 1] = cur_r;
            }

            let out_l = audio_buffer[2 * i];
            let out_r = audio_buffer[2 * i + 1];

            sum_sq_l += out_l * out_l;
            sum_sq_r += out_r * out_r;
            peak_l = peak_l.max(out_l.abs());
            peak_r = peak_r.max(out_r.abs());
        }

        let rms_l = (sum_sq_l / (num_frames as f32)).sqrt();
        let rms_r = (sum_sq_r / (num_frames as f32)).sqrt();

        self.telemetry.update(rms_l, rms_r, peak_l, peak_r, clipped);
    }
}
