//! AuraLine Core DSP Library in pure Rust
//!
//! Provides real-time safe audio algorithms, biquad filters, Linkwitz-Riley crossovers,
//! FDN stereo reverb, Mid/Side stereo widener, Dynamics suite (Gate + RMS Compressor),
//! Bass Processing (Enhancement, Sub-bass, Bass Mono), Lookahead True-Peak limiter with
//! polyphase oversampling, signal generator, headphone profiles, and telemetry.

pub mod analyzer;
pub mod bass;
pub mod dsp;
pub mod dynamics;
pub mod engine;
pub mod equalizer;
pub mod filters;
pub mod limiter;
pub mod presets;
pub mod reverb;
pub mod stereo;
pub mod telemetry;

pub use engine::AuraLineCoreEngine;
pub use bass::{BassEnhancement, BassMono, SubBassGenerator};
pub use presets::{HEADPHONE_PROFILES, TARGET_CURVES, HeadphoneProfile, TargetCurve};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_core_engine_pipeline() {
        let mut engine = AuraLineCoreEngine::new(48000.0);
        let mut buffer = [0.1f32; 512]; // 256 stereo frames
        engine.process_block(&mut buffer);

        let (_rms_l, _rms_r, peak_l, peak_r, _clipping, blocks) = engine.telemetry.read_snapshot();
        assert!(blocks == 1);
        assert!(peak_l > 0.0);
        assert!(peak_r > 0.0);
    }
}
