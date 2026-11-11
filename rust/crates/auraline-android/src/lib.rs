//! Android NDK JNI and C FFI Layer for AuraLine Rust Core Engine
//!
//! Exposes thread-safe, panic-safe C ABI functions and JNI bindings for NativeEngine.

use auraline_core::analyzer::GeneratorMode;
use auraline_core::AuraLineCoreEngine;
use std::os::raw::{c_float, c_int};
use std::sync::{Mutex, OnceLock};

static G_ENGINE: OnceLock<Mutex<AuraLineCoreEngine>> = OnceLock::new();

fn get_engine(sample_rate: f64) -> &'static Mutex<AuraLineCoreEngine> {
    G_ENGINE.get_or_init(|| Mutex::new(AuraLineCoreEngine::new(sample_rate)))
}

// ============================================================================
// C FFI API (Oboe / C++ Interop)
// ============================================================================

#[no_mangle]
pub extern "C" fn auraline_engine_create(sample_rate: f64) -> *mut AuraLineCoreEngine {
    let engine = Box::new(AuraLineCoreEngine::new(sample_rate));
    Box::into_raw(engine)
}

#[no_mangle]
pub unsafe extern "C" fn auraline_engine_destroy(engine: *mut AuraLineCoreEngine) {
    if !engine.is_null() {
        drop(Box::from_raw(engine));
    }
}

#[no_mangle]
pub unsafe extern "C" fn auraline_engine_process(
    engine: *mut AuraLineCoreEngine,
    buffer: *mut c_float,
    length: usize,
) {
    if engine.is_null() || buffer.is_null() || length < 2 {
        return;
    }
    let audio_slice = std::slice::from_raw_parts_mut(buffer, length);
    (*engine).process_block(audio_slice);
}

#[no_mangle]
pub unsafe extern "C" fn auraline_engine_set_bypass(engine: *mut AuraLineCoreEngine, bypass: bool) {
    if !engine.is_null() {
        (*engine).set_bypass(bypass);
    }
}

#[no_mangle]
pub unsafe extern "C" fn auraline_engine_set_preamp_gain(engine: *mut AuraLineCoreEngine, gain_db: f32) {
    if !engine.is_null() {
        (*engine).set_preamp_gain_db(gain_db);
    }
}

// ============================================================================
// JNI API Implementation for com.auraline.app.audio.NativeEngine
// ============================================================================

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_init(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    sample_rate: c_int,
) {
    let sr = if sample_rate <= 0 { 48000.0 } else { sample_rate as f64 };
    let engine = get_engine(sr);
    if let Ok(mut guard) = engine.lock() {
        guard.sample_rate = sr;
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setBypass(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    bypass: u8,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.set_bypass(bypass != 0);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setGeneratorMode(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    mode: c_int,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            let gen_mode = match mode {
                1 => GeneratorMode::PinkNoise,
                2 => GeneratorMode::WhiteNoise,
                3 => GeneratorMode::SineSweep,
                _ => GeneratorMode::Off,
            };
            guard.generator.set_mode(gen_mode);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setGeneratorLevel(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    level: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.generator.set_level(level);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setPreampGain(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    gain_db: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.set_preamp_gain_db(gain_db);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setEqBand(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    band_index: c_int,
    freq: c_float,
    gain: c_float,
    q: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate;
            guard.eq_cascade.set_band(
                band_index as usize,
                sr,
                freq as f64,
                gain as f64,
                q as f64,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setSubsonic(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    _cutoff_hz: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.subsonic_enabled.store(enabled != 0, std::sync::atomic::Ordering::Relaxed);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setInstrumentEnhancer(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    fundamental_hz: c_float,
    body_db: c_float,
    punch_db: c_float,
    mud_cut_db: c_float,
    harmonics: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate;
            guard.enhancer.set_profile(
                enabled != 0,
                sr,
                fundamental_hz as f64,
                body_db as f64,
                (fundamental_hz * 3.0) as f64,
                punch_db as f64,
                400.0,
                mud_cut_db as f64,
                harmonics,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setCompressor(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    threshold_db: c_float,
    ratio: c_float,
    attack_ms: c_float,
    release_ms: c_float,
    makeup_gain_db: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate as f32;
            guard.dynamics.compressor.set_params(
                enabled != 0,
                threshold_db,
                ratio,
                attack_ms,
                release_ms,
                makeup_gain_db,
                6.0, // knee_db default
                sr,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setNoiseGate(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    threshold_db: c_float,
    ratio: c_float,
    attack_ms: c_float,
    release_ms: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate as f32;
            guard.dynamics.gate.set_params(
                enabled != 0,
                threshold_db,
                ratio,
                attack_ms,
                release_ms,
                sr,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setReverb(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    room_size: c_float,
    damping: c_float,
    _decay: c_float,
    _predelay: c_float,
    mix: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.reverb.set_params(enabled != 0, room_size, damping, mix);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setStereoWidener(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    width: c_float,
    _bass_mono_cutoff_hz: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            guard.widener.set_params(enabled != 0, width);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setLimiter(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    ceiling_db: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            guard.limiter.set_enabled(enabled != 0);
            guard.limiter.set_ceiling_db(ceiling_db);
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setBluetoothRoute(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    is_bluetooth: u8,
) {
    if is_bluetooth != 0 {
        if let Some(mutex) = G_ENGINE.get() {
            if let Ok(mut guard) = mutex.lock() {
                if guard.limiter.get_ceiling_db() > -0.5 {
                    guard.limiter.set_ceiling_db(-0.5);
                }
            }
        }
    }
}

// --- Bass Processing JNI ---

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setBassEnhancement(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    amount: c_float,
    cutoff_hz: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate;
            guard.bass_enhancement.set_params(
                enabled != 0,
                amount,
                cutoff_hz,
                sr,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setSubBass(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    amount: c_float,
    cutoff_hz: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate;
            guard.sub_bass.set_params(
                enabled != 0,
                amount,
                cutoff_hz,
                sr,
            );
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_setBassMono(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    enabled: u8,
    cutoff_hz: c_float,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let sr = guard.sample_rate;
            guard.bass_mono.set_params(
                enabled != 0,
                cutoff_hz,
                sr,
            );
        }
    }
}

// --- Headphone Profile & Target Curve JNI ---

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_applyHeadphoneProfile(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    profile_index: i32,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let profiles = auraline_core::presets::HEADPHONE_PROFILES;
            let idx = (profile_index as usize).min(profiles.len().saturating_sub(1));
            let profile = &profiles[idx];
            let sr = guard.sample_rate;

            // Reset all EQ bands first
            for i in 0..auraline_core::equalizer::MAX_EQ_BANDS {
                guard.eq_cascade.set_band(i, sr, 1000.0, 0.0, 1.0);
            }

            // Apply profile EQ bands
            for (i, &(freq, gain_db, q)) in profile.eq_bands.iter().enumerate() {
                if i < auraline_core::equalizer::MAX_EQ_BANDS {
                    guard.eq_cascade.set_band(i, sr, freq, gain_db, q);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_auraline_app_audio_NativeEngine_applyTargetCurve(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    curve_index: i32,
) {
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let curves = auraline_core::presets::TARGET_CURVES;
            let idx = (curve_index as usize).min(curves.len().saturating_sub(1));
            let curve = &curves[idx];
            let sr = guard.sample_rate;

            // Reset all EQ bands first
            for i in 0..auraline_core::equalizer::MAX_EQ_BANDS {
                guard.eq_cascade.set_band(i, sr, 1000.0, 0.0, 1.0);
            }

            // Apply target curve EQ bands
            for (i, &(freq, gain_db, q)) in curve.eq_bands.iter().enumerate() {
                if i < auraline_core::equalizer::MAX_EQ_BANDS {
                    guard.eq_cascade.set_band(i, sr, freq, gain_db, q);
                }
            }
        }
    }
}

// --- PCM Input & Telemetry JNI ---

#[no_mangle]
pub unsafe extern "C" fn Java_com_auraline_app_audio_NativeEngine_writeInputPcm(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    samples: *mut c_float,
    offset: c_int,
    length: c_int,
) -> c_int {
    if samples.is_null() || length <= 0 || offset < 0 {
        return 0;
    }
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(mut guard) = mutex.lock() {
            let slice = std::slice::from_raw_parts_mut(samples.add(offset as usize), length as usize);
            guard.process_block(slice);
            return length;
        }
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_auraline_app_audio_NativeEngine_getTelemetry(
    _env: *mut std::ffi::c_void,
    _this: *mut std::ffi::c_void,
    out: *mut c_float,
) {
    if out.is_null() {
        return;
    }
    if let Some(mutex) = G_ENGINE.get() {
        if let Ok(guard) = mutex.lock() {
            let tel = guard.telemetry.read_snapshot();
            let slice = std::slice::from_raw_parts_mut(out, 6);
            slice[0] = tel.0; // rms_l
            slice[1] = tel.1; // rms_r
            slice[2] = tel.2; // peak_l
            slice[3] = tel.3; // peak_r
            slice[4] = tel.4 as f32; // clipping_count
            slice[5] = tel.5 as f32; // processed_blocks
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_c_ffi_lifecycle() {
        unsafe {
            let engine = auraline_engine_create(48000.0);
            assert!(!engine.is_null());
            auraline_engine_set_bypass(engine, true);
            auraline_engine_set_preamp_gain(engine, 3.0);

            let mut buf = [0.1f32; 256];
            auraline_engine_process(engine, buf.as_mut_ptr(), buf.len());
            auraline_engine_destroy(engine);
        }
    }
}
