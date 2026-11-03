package com.auraline.app.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.bandCenterHz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GeneratorMode(val id: Int, val label: String) {
    OFF(0, "Desactivado"),
    PINK_NOISE(1, "Ruido Rosa (Pink Noise)"),
    WHITE_NOISE(2, "Ruido Blanco (White Noise)"),
    SINE_SWEEP(3, "Barrido Senoidal (20Hz–20kHz)")
}

data class DiagnosticTelemetry(
    val activeMode: String = "DynamicsProcessing (Sesión)",
    val sampleRateHz: Int = 48000,
    val channels: Int = 2,
    val rmsL: Float = 0f,
    val rmsR: Float = 0f,
    val peakL: Float = 0f,
    val peakR: Float = 0f,
    val peakL_dBFS: Float = -90f,
    val peakR_dBFS: Float = -90f,
    val rmsL_dBFS: Float = -90f,
    val rmsR_dBFS: Float = -90f,
    val clippingCount: Int = 0,
    val processedBlocks: Long = 0L,
    val isBypass: Boolean = false,
    val generatorMode: GeneratorMode = GeneratorMode.OFF,
    val generatorLevel: Float = 0.35f,
    val isNativeRunning: Boolean = false
)

object NativeEngineBridge {
    private const val TAG = "NativeEngineBridge"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var telemetryJob: Job? = null

    private val _telemetry = MutableStateFlow(DiagnosticTelemetry())
    val telemetry: StateFlow<DiagnosticTelemetry> = _telemetry.asStateFlow()

    private var initialized = false
    private var activeSampleRate = 48000

    fun initialize(context: Context) {
        if (initialized) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rate = manager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        activeSampleRate = rate

        if (NativeEngine.isAvailable()) {
            try {
                NativeEngine.init(rate)
                initialized = true
                Log.i(TAG, "NativeEngine inicializado a $rate Hz")
                startTelemetryPoller()
            } catch (t: Throwable) {
                Log.e(TAG, "Error al inicializar NativeEngine: ${t.message}")
            }
        }
    }

    private fun startTelemetryPoller() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                if (NativeEngine.isAvailable() && initialized) {
                    val raw = NativeEngine.readTelemetry()
                    val toDb = { lin: Float ->
                        if (lin > 0.000001f) (20.0f * kotlin.math.log10(lin)).coerceIn(-90f, 6f) else -90f
                    }
                    val current = _telemetry.value
                    _telemetry.value = current.copy(
                        rmsL = raw.rmsL,
                        rmsR = raw.rmsR,
                        peakL = raw.peakL,
                        peakR = raw.peakR,
                        rmsL_dBFS = toDb(raw.rmsL),
                        rmsR_dBFS = toDb(raw.rmsR),
                        peakL_dBFS = toDb(raw.peakL),
                        peakR_dBFS = toDb(raw.peakR),
                        clippingCount = raw.clippingCount,
                        processedBlocks = raw.processedBlocks,
                        isNativeRunning = true,
                        sampleRateHz = activeSampleRate
                    )
                }
                delay(80) // 12.5 Hz polling for UI meters
            }
        }
    }

    fun syncState(state: EqualizerState) {
        if (!NativeEngine.isAvailable()) return

        try {
            NativeEngine.setBypass(state.bypass)
            NativeEngine.setPreampGain(state.preampDb)

            val bands = state.bands
            val numBands = state.numBands.coerceIn(20, 25)
            for (i in 0 until numBands) {
                val band = bands.getOrNull(i)
                if (band != null) {
                    val center = bandCenterHz(bands, i)
                    val toneGain = if (state.bypass) {
                        0f
                    } else {
                        toneOffset(state, center) + subBoostAt(state, center)
                    }
                    NativeEngine.setEqBand(
                        i,
                        center,
                        (if (state.bypass) 0f else band.gainDb + toneGain)
                            .coerceIn(-36f, 36f),
                        band.q
                    )
                }
            }
            NativeEngine.setSubsonic(
                enabled = !state.bypass,
                cutoffHz = state.tone.subCutoffHz.coerceIn(20f, 100f)
            )

            // Dynamics
            val comp = state.compressor
            NativeEngine.setCompressor(
                enabled = state.modules.compressor && !state.bypass,
                thresholdDb = comp.threshold,
                ratio = comp.ratio,
                attackMs = comp.attackMs,
                releaseMs = comp.releaseMs,
                makeupGainDb = comp.makeupGain
            )

            val gate = state.gate
            NativeEngine.setNoiseGate(
                enabled = state.modules.gate && !state.bypass,
                thresholdDb = gate.thresholdDb,
                ratio = gate.ratio
            )

            // Spatial
            val rev = state.reverb
            NativeEngine.setReverb(
                enabled = state.modules.reverb && state.effectsEnabled && !state.bypass,
                roomSize = rev.size,
                damping = rev.damping,
                decay = rev.decay,
                predelay = rev.preDelay,
                mix = rev.mix
            )

            val stereo = state.stereo
            NativeEngine.setStereoWidener(
                enabled = state.modules.width && !state.bypass,
                width = if (stereo.mono) 0f else stereo.expansion,
                bassMonoCutoffHz = 120f
            )

            // Limiter
            NativeEngine.setLimiter(
                enabled = state.modules.limiter && !state.bypass,
                ceilingDb = comp.limiterCeiling
            )

            _telemetry.value = _telemetry.value.copy(isBypass = state.bypass)
        } catch (t: Throwable) {
            Log.w(TAG, "Error sincronizando estado con NativeEngine: ${t.message}")
        }
    }

    fun setGeneratorMode(mode: GeneratorMode) {
        if (!NativeEngine.isAvailable()) return
        NativeEngine.setGeneratorMode(mode.id)
        val modeLabel = if (mode == GeneratorMode.OFF) {
            "DynamicsProcessing (Sesión)"
        } else {
            "Generador Interno (${mode.label})"
        }
        _telemetry.value = _telemetry.value.copy(
            generatorMode = mode,
            activeMode = modeLabel
        )
    }

    fun setGeneratorLevel(level: Float) {
        if (!NativeEngine.isAvailable()) return
        val clamped = level.coerceIn(0f, 1f)
        NativeEngine.setGeneratorLevel(clamped)
        _telemetry.value = _telemetry.value.copy(generatorLevel = clamped)
    }

    fun toggleBypass(bypass: Boolean) {
        if (!NativeEngine.isAvailable()) return
        NativeEngine.setBypass(bypass)
        _telemetry.value = _telemetry.value.copy(isBypass = bypass)
    }

    fun setBluetoothRoute(isBluetooth: Boolean) {
        if (!NativeEngine.isAvailable()) return
        NativeEngine.setBluetoothRoute(isBluetooth)
    }

    private fun toneOffset(state: EqualizerState, centerHz: Float): Float {
        return when {
            centerHz <= state.tone.bassFrequencyHz -> state.tone.bassDb
            centerHz >= state.tone.trebleFrequencyHz -> state.tone.trebleDb
            else -> state.tone.midDb
        }.coerceIn(-36f, 36f)
    }

    private fun subBoostAt(state: EqualizerState, centerHz: Float): Float {
        val sub = state.tone.subDb
        if (sub == 0f || centerHz > 100f) return 0f
        val cutoff = state.tone.subCutoffHz.coerceIn(20f, 100f)
        val shaped = if (centerHz <= cutoff) {
            sub
        } else {
            sub * (1f - (centerHz - cutoff) / (100f - cutoff))
        }
        return shaped.coerceIn(-36f, 36f)
    }
}
