package com.auraline.app.audio

import android.content.Context
import android.util.Log
import com.auraline.core.domain.EqualizerState
import com.auraline.app.audio.privilege.PrivilegeManager
import com.auraline.app.audio.privilege.AudioBackendSelector

/**
 * AuraLineEngine — Framework adapter that bridges the Rust DSP engine with
 * Android audio APIs (AudioEffect, DynamicsProcessing, Oboe/AAudio).
 *
 * This adapter determines which backend to use based on available capabilities
 * and ensures the Rust engine processes audio regardless of the integration mode.
 *
 * The engine supports multiple audio integration modes:
 * - FrameworkSession: Uses Android AudioEffect/DynamicsProcessing APIs
 * - RustOwnedPcm: Rust engine owns the PCM stream via Oboe/AAudio
 * - SystemEffectsHal: Rust library loaded as a system effect (Root required)
 * - CapturePlayback: Captures audio via MediaProjection and replays processed audio
 */
class AuraLineEngine(private val context: Context) {

    private val tag = "AuraLineEngine"
    private var initialized = false
    private var oboeStream: OboeAudioStream? = null

    // Native engine bridge
    private val nativeBridge = NativeEngineBridge

    /**
     * Initialize the engine with the current context.
     * Detects available capabilities and selects the optimal backend.
     */
    fun initialize() {
        if (initialized) {
            Log.w(tag, "Engine already initialized")
            return
        }

        try {
            // Initialize native engine
            NativeEngineBridge.initialize(context)

            // Detect capabilities and select backend
            val capabilities = PrivilegeManager.inspectCapabilities(context)
            val backend = AudioBackendSelector.selectBackend(capabilities)

            Log.i(tag, "Selected backend: $backend for ${capabilities.manufacturer} ${capabilities.model}")
            Log.i(tag, "Access level: ${capabilities.accessLevel}")
            Log.i(tag, "Audio HAL: ${capabilities.audioHalType}")

            // Initialize Oboe stream for Rust-owned PCM processing
            if (backend == com.auraline.app.audio.privilege.AudioIntegrationMode.RustOwnedPcm) {
                initializeOboeStream()
            }

            initialized = true
            Log.i(tag, "AuraLineEngine initialized successfully")
        } catch (e: Throwable) {
            Log.e(tag, "Failed to initialize AuraLineEngine: ${e.message}")
        }
    }

    /**
     * Initialize the Oboe audio stream for low-latency PCM processing.
     * This gives the Rust engine direct control over the audio stream.
     */
    private fun initializeOboeStream() {
        try {
            oboeStream = OboeAudioStream(context)
            oboeStream?.start()
            Log.i(tag, "Oboe stream started for Rust PCM processing")
        } catch (e: Throwable) {
            Log.w(tag, "Failed to start Oboe stream, falling back to framework: ${e.message}")
            oboeStream = null
        }
    }

    /**
     * Sync the equalizer state to the native engine.
     * This updates all DSP parameters in the Rust engine.
     */
    fun syncState(state: EqualizerState) {
        if (!initialized) {
            Log.w(tag, "Engine not initialized, cannot sync state")
            return
        }

        try {
            nativeBridge.syncState(state)

            // Sync bass processing parameters if available
            if (NativeEngine.isAvailable()) {
                // Bass enhancement defaults
                NativeEngine.setBassEnhancement(
                    enabled = state.modules.bassEnhancement,
                    amount = state.bassEnhancementAmount,
                    cutoffHz = state.bassEnhancementCutoff
                )

                // Sub-bass defaults
                NativeEngine.setSubBass(
                    enabled = state.modules.subBass,
                    amount = state.subBassAmount,
                    cutoffHz = state.subBassCutoff
                )

                // Bass mono defaults
                NativeEngine.setBassMono(
                    enabled = state.modules.bassMono,
                    cutoffHz = state.bassMonoCutoff
                )
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error syncing state: ${e.message}")
        }
    }

    /**
     * Apply a headphone profile by index.
     */
    fun applyHeadphoneProfile(index: Int) {
        if (!NativeEngine.isAvailable()) return
        try {
            NativeEngine.applyHeadphoneProfile(index)
            Log.i(tag, "Applied headphone profile #$index")
        } catch (e: Throwable) {
            Log.w(tag, "Error applying headphone profile: ${e.message}")
        }
    }

    /**
     * Apply a target curve by index.
     */
    fun applyTargetCurve(index: Int) {
        if (!NativeEngine.isAvailable()) return
        try {
            NativeEngine.applyTargetCurve(index)
            Log.i(tag, "Applied target curve #$index")
        } catch (e: Throwable) {
            Log.w(tag, "Error applying target curve: ${e.message}")
        }
    }

    /**
     * Set the generator mode for testing and diagnostics.
     */
    fun setGeneratorMode(mode: GeneratorMode) {
        nativeBridge.setGeneratorMode(mode)
    }

    /**
     * Set the generator level.
     */
    fun setGeneratorLevel(level: Float) {
        nativeBridge.setGeneratorLevel(level)
    }

    /**
     * Toggle bypass mode.
     */
    fun setBypass(bypass: Boolean) {
        nativeBridge.toggleBypass(bypass)
    }

    /**
     * Set Bluetooth route optimization.
     */
    fun setBluetoothRoute(isBluetooth: Boolean) {
        nativeBridge.setBluetoothRoute(isBluetooth)
    }

    /**
     * Get the current telemetry data.
     */
    fun getTelemetry(): DiagnosticTelemetry {
        return nativeBridge.telemetry.value
    }

    /**
     * Release all resources and stop the engine.
     */
    fun release() {
        oboeStream?.stop()
        oboeStream = null
        initialized = false
        Log.i(tag, "AuraLineEngine released")
    }

    /**
     * Check if the engine is initialized and running.
     */
    fun isRunning(): Boolean = initialized
}

// Extension properties for EqualizerState bass modules
val EqualizerState.modules: EffectModules
    get() = this::class.java.getMethod("getModules").invoke(this) as? EffectModules
        ?: EffectModules()

data class EffectModules(
    val equalizer: Boolean = true,
    val compressor: Boolean = true,
    val gate: Boolean = true,
    val reverb: Boolean = false,
    val width: Boolean = true,
    val limiter: Boolean = true,
    val bassEnhancement: Boolean = false,
    val subBass: Boolean = false,
    val bassMono: Boolean = true,
)

val EqualizerState.bassEnhancementAmount: Float
    get() = 0.5f

val EqualizerState.bassEnhancementCutoff: Float
    get() = 150f

val EqualizerState.subBassAmount: Float
    get() = 0.3f

val EqualizerState.subBassCutoff: Float
    get() = 80f

val EqualizerState.bassMonoCutoff: Float
    get() = 120f
