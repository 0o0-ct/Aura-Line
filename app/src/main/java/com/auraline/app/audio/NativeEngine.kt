package com.auraline.app.audio

import android.util.Log
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.generateEqualizerBands

/**
 * Puente JNI con el motor DSP nativo (`libauralinedsp.so`).
 *
 * Estado honesto: el motor nativo todavía **no** procesa la salida del sistema. Sin root no
 * hay forma de insertarlo en la mezcla global, así que la cadena real sigue siendo
 * `DynamicsProcessing` en `AuraLineEngine`. Este objeto permite:
 *  - verificar que la librería carga en el dispositivo,
 *  - procesar bloques en memoria (`processBlock`) para pruebas de clipping/respuesta,
 *  - y servir de base al futuro modo de captura + salida propia.
 *
 * Nunca abre streams de audio, así que es inofensivo llamarlo.
 *
 * Un único punto de escritura: [setState] recibe el estado completo desde AudioParams
 * (`EqualizerState`). Cualquier otro valor de audio sale de aquí; ningún caller escribe
 * parámetros sueltos del motor.
 */
object NativeEngine {

    private const val TAG = "AuraLine"

    /** `true` si `libauralinedsp.so` cargó; si no, todo lo demás es no-op. */
    val isAvailable: Boolean = try {
        System.loadLibrary("auralinedsp")
        true
    } catch (error: Throwable) {
        Log.w(TAG, "Motor nativo no disponible: ${error.message}")
        false
    }

    fun init(sampleRate: Int) { if (isAvailable) nativeInit(sampleRate) }

    /**
     * Aplica un estado completo desde AudioParams.
     *
     * - El bypass (A/B) se traduce a cadena plana: el motor se desactiva (paso por alto),
     *   que es la referencia comparativa de la curva procesada.
     * - Los valores por defecto de la rejilla se generan si el estado no trae bandas.
     */
    fun setState(state: EqualizerState) {
        if (!isAvailable) return
        val bypassed = state.bypass
        nativeSetEnabled(state.motorEnabled && !bypassed)
        nativeSetPreamp(if (bypassed) 0f else state.preampDb)
        nativeSetLimiterCeiling(state.compressor.limiterCeiling.coerceIn(-20f, 0f))
        val bands = if (state.bands.isNotEmpty()) state.bands else generateEqualizerBands(state.numBands.coerceIn(20, 25))
        val bandCount = bands.size.coerceIn(20, 25)
        nativeSetBandCount(bandCount)
        for (index in 0 until bandCount) {
            val band = bands[index]
            nativeSetEqBand(index, band.frequencyHz, if (bypassed) 0f else band.gainDb, band.q)
        }

        // Tone
        val tone = state.tone
        if (bypassed) {
            nativeSetTone(0f, tone.bassFrequencyHz, 0f, tone.trebleFrequencyHz, 0f, tone.subCutoffHz)
        } else {
            nativeSetTone(tone.bassDb, tone.bassFrequencyHz, tone.trebleDb, tone.trebleFrequencyHz, tone.subDb, tone.subCutoffHz)
        }

        // Compressor
        val comp = state.compressor
        if (bypassed || !state.effectsEnabled || !state.modules.compressor) {
            nativeSetCompressor(-60f, 1f, 10f, 100f, 0f)
        } else {
            nativeSetCompressor(comp.threshold, comp.ratio, comp.attackMs, comp.releaseMs, comp.makeupGain)
        }

        // Gate
        val gate = state.gate
        if (bypassed || !state.effectsEnabled || !state.modules.gate) {
            nativeSetGate(-80f, 1f, 10f, 100f, 0f)
        } else {
            nativeSetGate(gate.thresholdDb, gate.ratio, 10f, 100f, 0f)
        }

        // Reverb
        val rev = state.reverb
        if (bypassed || !state.effectsEnabled || !state.modules.reverb) {
            nativeSetReverb(0f, 0.3f)
        } else {
            nativeSetReverb(rev.mix, rev.decay)
        }

        // Stereo Width
        val width = if (bypassed || !state.modules.width) 1.0f else state.stereo.expansion
        nativeSetStereoWidth(width)
    }

    /**
     * Procesa in situ un bloque estéreo intercalado float32 y devuelve el pico de salida
     * (lineal, 1.0 = 0 dBFS). Pensado para pruebas, no para el camino en tiempo real.
     */
    fun processBlock(interleaved: FloatArray, frames: Int): Float =
        if (isAvailable) nativeProcessBlock(interleaved, frames) else 0f

    fun gainReductionDb(): Float = if (isAvailable) nativeGainReductionDb() else 0f

    private external fun nativeInit(sampleRate: Int)
    private external fun nativeSetEnabled(enabled: Boolean)
    private external fun nativeSetPreamp(db: Float)
    private external fun nativeSetLimiterCeiling(db: Float)
    private external fun nativeSetBandCount(count: Int)
    private external fun nativeSetEqBand(index: Int, freqHz: Float, gainDb: Float, q: Float)
    private external fun nativeSetTone(bassDb: Float, bassHz: Float, trebleDb: Float, trebleHz: Float, subDb: Float, subHz: Float)
    private external fun nativeSetCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float)
    private external fun nativeSetGate(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupDb: Float)
    private external fun nativeSetStereoWidth(widthFactor: Float)
    private external fun nativeSetReverb(mix: Float, decaySeconds: Float)
    private external fun nativeProcessBlock(interleaved: FloatArray, frames: Int): Float
    private external fun nativeGainReductionDb(): Float
}
