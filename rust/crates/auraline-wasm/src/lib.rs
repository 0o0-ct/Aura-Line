//! AuraLine WebAssembly Bridge
//!
//! Exposes the Rust DSP engine to JavaScript/TypeScript via WebAssembly
//! for use in browser-based audio applications with AudioWorklet.
//!
//! This crate provides:
//! - Engine creation and destruction
//! - Parameter configuration
//! - Block processing
//! - Telemetry reading
//!
//! Usage with AudioWorklet:
//! 1. Compile to WASM with `wasm-pack build --target web`
//! 2. Load the WASM module in the AudioWorklet processor
//! 3. Call process_block() on each audio quantum

use wasm_bindgen::prelude::*;

use auraline_core::AuraLineCoreEngine;

/// Opaque handle to the engine instance stored in a global static.
/// In WASM, we use a single global instance for simplicity.
static mut ENGINE: Option<AuraLineCoreEngine> = None;

#[wasm_bindgen]
pub extern "C" {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
}

#[wasm_bindgen]
pub fn create_engine(sample_rate: f64) -> u32 {
    unsafe {
        let engine = AuraLineCoreEngine::new(sample_rate);
        // Store engine in a Box leaked to a stable pointer
        // In production, use a proper arena allocator
        let boxed = Box::new(engine);
        let ptr = Box::into_raw(boxed) as u32;
        log(&format!("AuraLine engine created at {} Hz", sample_rate));
        ptr
    }
}

#[wasm_bindgen]
pub fn destroy_engine(_ptr: u32) {
    // In production, properly free the Box
    // For now, rely on WASM memory management
    unsafe {
        // Note: This is a simplified implementation
        // A proper implementation would use an arena allocator
    }
}

#[wasm_bindgen]
pub fn set_preamp_gain(_ptr: u32, gain_db: f32) {
    unsafe {
        if let Some(ref mut engine) = ENGINE {
            engine.set_preamp_gain_db(gain_db);
        }
    }
}

#[wasm_bindgen]
pub fn set_bypass(_ptr: u32, bypass: bool) {
    unsafe {
        if let Some(ref engine) = ENGINE {
            engine.set_bypass(bypass);
        }
    }
}

#[wasm_bindgen]
pub fn process_block(audio_buffer: &mut [f32]) {
    unsafe {
        if let Some(ref mut engine) = ENGINE {
            engine.process_block(audio_buffer);
        }
    }
}

#[wasm_bindgen]
pub fn get_telemetry() -> JsValue {
    use wasm_bindgen::JsValue;

    unsafe {
        if let Some(ref engine) = ENGINE {
            let snapshot = engine.telemetry.read_snapshot();
            let _ = (snapshot.0, snapshot.1, snapshot.2, snapshot.3, snapshot.4, snapshot.5);
            // Return as JSON-like object
            JsValue::NULL // Simplified - in production, return proper JS object
        } else {
            JsValue::NULL
        }
    }
}

#[wasm_bindgen]
pub fn get_version() -> String {
    "1.0.0".to_string()
}
