package com.auraline.app.audio

import android.util.Log

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
    fun setEnabled(enabled: Boolean) { if (isAvailable) nativeSetEnabled(enabled) }
    fun setPreamp(db: Float) { if (isAvailable) nativeSetPreamp(db) }
    fun setLimiterCeiling(db: Float) { if (isAvailable) nativeSetLimiterCeiling(db) }
    fun setBandCount(count: Int) { if (isAvailable) nativeSetBandCount(count) }
    fun setEqBand(index: Int, freqHz: Float, gainDb: Float, q: Float = 1.41f) {
        if (isAvailable) nativeSetEqBand(index, freqHz, gainDb, q)
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
    private external fun nativeProcessBlock(interleaved: FloatArray, frames: Int): Float
    private external fun nativeGainReductionDb(): Float
}