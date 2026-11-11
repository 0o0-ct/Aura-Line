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
use crate::filters::biquad::{high_pass, high_shelf, low_pass, low_shelf, AtomicF64, SmoothedBiquad};
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

    // Auxiliary EQ Filters: LPF (extremo superior) y shelves (control amplio de extremos)
    pub lpf_enabled: AtomicBool,
    pub lpf_l: SmoothedBiquad,
    pub lpf_r: SmoothedBiquad,
    pub low_shelf_l: SmoothedBiquad,
    pub low_shelf_r: SmoothedBiquad,
    pub high_shelf_l: SmoothedBiquad,
    pub high_shelf_r: SmoothedBiquad,

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
            lpf_enabled: AtomicBool::new(false),
            lpf_l: SmoothedBiquad::new(),
            lpf_r: SmoothedBiquad::new(),
            low_shelf_l: SmoothedBiquad::new(),
            low_shelf_r: SmoothedBiquad::new(),
            high_shelf_l: SmoothedBiquad::new(),
            high_shelf_r: SmoothedBiquad::new(),
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

        // Initialize auxiliary filters: LPF por encima del espectro audible (deshabilitado),
        // shelves planas (ganancia 0 = transparentes).
        let lp_params = low_pass(fs, 22050.0, 0.7071);
        engine.lpf_l.set_immediate(lp_params);
        engine.lpf_r.set_immediate(lp_params);
        let flat_low_shelf = low_shelf(fs, 100.0, 0.0, 0.7071);
        engine.low_shelf_l.set_immediate(flat_low_shelf);
        engine.low_shelf_r.set_immediate(flat_low_shelf);
        let flat_high_shelf = high_shelf(fs, 12000.0, 0.0, 0.7071);
        engine.high_shelf_l.set_immediate(flat_high_shelf);
        engine.high_shelf_r.set_immediate(flat_high_shelf);

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

    /// Configura los filtros auxiliares del ecualizador (HPF, LPF y shelves).
    /// El HPF reutiliza la etapa subsónica; ganancia 0 en los shelves los deja planos.
    pub fn set_eq_filters(
        &self,
        hpf_enabled: bool,
        hpf_cutoff_hz: f32,
        lpf_enabled: bool,
        lpf_cutoff_hz: f32,
        low_shelf_hz: f32,
        low_shelf_db: f32,
        high_shelf_hz: f32,
        high_shelf_db: f32,
    ) {
        let fs = self.sample_rate;

        let hp_hz = (hpf_cutoff_hz as f64).clamp(5.0, 500.0);
        self.subsonic_enabled.store(hpf_enabled, Ordering::Relaxed);
        let hp = high_pass(fs, hp_hz, 0.7071);
        self.subsonic_hp_l.set_target(hp);
        self.subsonic_hp_r.set_target(hp);

        let lp_hz = (lpf_cutoff_hz as f64).clamp(4000.0, 24000.0);
        self.lpf_enabled.store(lpf_enabled, Ordering::Relaxed);
        let lp = low_pass(fs, lp_hz, 0.7071);
        self.lpf_l.set_target(lp);
        self.lpf_r.set_target(lp);

        let ls_hz = (low_shelf_hz as f64).clamp(20.0, 1000.0);
        let ls_db = (low_shelf_db as f64).clamp(-15.0, 15.0);
        let ls = low_shelf(fs, ls_hz, ls_db, 0.7071);
        self.low_shelf_l.set_target(ls);
        self.low_shelf_r.set_target(ls);

        let hs_hz = (high_shelf_hz as f64).clamp(1000.0, 20000.0);
        let hs_db = (high_shelf_db as f64).clamp(-15.0, 15.0);
        let hs = high_shelf(fs, hs_hz, hs_db, 0.7071);
        self.high_shelf_l.set_target(hs);
        self.high_shelf_r.set_target(hs);
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
        let lpf_active = self.lpf_enabled.load(Ordering::Relaxed);

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

                // Low Shelf (ganancia 0 = plano)
                cur_l = self.low_shelf_l.process_l(cur_l);
                cur_r = self.low_shelf_r.process_r(cur_r);

                // 25-Band EQ
                self.eq_cascade.process(&mut cur_l, &mut cur_r);

                // High Shelf (ganancia 0 = plano)
                cur_l = self.high_shelf_l.process_l(cur_l);
                cur_r = self.high_shelf_r.process_r(cur_r);

                if lpf_active {
                    cur_l = self.lpf_l.process_l(cur_l);
                    cur_r = self.lpf_r.process_r(cur_r);
                }

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
