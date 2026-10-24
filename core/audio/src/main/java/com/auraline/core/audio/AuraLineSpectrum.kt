package com.auraline.core.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analizador de espectro sobre la mezcla de salida (sesión 0).
 *
 * `Visualizer` es de solo lectura: no procesa, solo entrega la FFT de lo que está sonando.
 * Necesita permiso de micrófono (es una limitación del propio efecto).
 */
object AuraLineSpectrum {

    const val BARS = 32

    private const val TAG = "AuraLine"
    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 16000.0
    private const val MIN_DB = -90.0
    private const val MAX_DB = -10.0

    private val _levels = MutableStateFlow(FloatArray(BARS))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    private val _available = MutableStateFlow(true)
    /** false si el sistema no permite el analizador (sin permiso o sin soporte). */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var visualizer: Visualizer? = null

    fun start() {
        if (visualizer != null) return
        runCatching {
            val effect = Visualizer(0)
            effect.captureSize = Visualizer.getCaptureSizeRange()[1]
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft != null) _levels.value = toBars(fft, samplingRate)
                    }
                },
                Visualizer.getMaxCaptureRate() / 4,
                false,
                true
            )
            effect.enabled = true
            visualizer = effect
            _available.value = true
        }.onFailure {
            Log.w(TAG, "Analizador no disponible: ${it.message}")
            _available.value = false
        }
    }

    fun stop() {
        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null
        _levels.value = FloatArray(BARS)
    }

    /** Convierte la FFT en barras logarítmicas normalizadas de 0 a 1. */
    private fun toBars(fft: ByteArray, samplingRate: Int): FloatArray {
        val rate = if (samplingRate > 0) samplingRate else 44_100
        val bins = fft.size / 2
        if (bins == 0) return FloatArray(BARS)

        val binHz = rate.toDouble() / (bins * 2)
        val result = FloatArray(BARS)
        val logMin = log10(MIN_HZ)
        val logMax = log10(MAX_HZ)

        for (bar in 0 until BARS) {
            val lowHz = Math.pow(10.0, logMin + (logMax - logMin) * bar / BARS)
            val highHz = Math.pow(10.0, logMin + (logMax - logMin) * (bar + 1) / BARS)
            var start = (lowHz / binHz).toInt()
            var end = (highHz / binHz).toInt()
            start = start.coerceIn(0, bins - 1)
            end = end.coerceIn(start + 1, bins)

            var peak = 0.0
            for (bin in start until end) {
                val real = fft[bin * 2].toDouble()
                val imaginary = fft[bin * 2 + 1].toDouble()
                peak = maxOf(peak, sqrt(real * real + imaginary * imaginary))
            }
            val db = 20.0 * log10(peak.coerceAtLeast(1e-6) / 128.0)
            val normalized = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0)
            result[bar] = normalized.toFloat()
        }
        return result
    }

    @Suppress("unused")
    private fun Byte.signed(): Int = abs(this.toInt())
}
