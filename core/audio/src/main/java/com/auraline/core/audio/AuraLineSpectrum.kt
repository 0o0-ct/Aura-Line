package com.auraline.core.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analizador de espectro sobre la mezcla de salida (sesión 0).
 *
 * `Visualizer` es de solo lectura: entrega la FFT de lo que está sonando, no procesa nada.
 * Necesita permiso de micrófono porque así lo impone el propio efecto.
 *
 * Se emite **una barra por banda del ecualizador**, normalizada respecto al pico de cada
 * trama y con caída lenta, para que cada barra se mueva por su cuenta.
 */
object AuraLineSpectrum {

    private const val TAG = "AuraLine"
    private const val DEFAULT_BARS = 25
    private const val MIN_HZ = 20.0
    private const val MAX_HZ = 20000.0
    private const val DYNAMIC_RANGE_DB = 58.0
    private const val SILENCE_DB = -78.0
    private const val DECAY = 0.82f

    private val _levels = MutableStateFlow(FloatArray(DEFAULT_BARS))
    val levels: StateFlow<FloatArray> = _levels.asStateFlow()

    private val _available = MutableStateFlow(true)
    /** false si el sistema no permite el analizador (sin permiso o sin soporte). */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile
    private var bandEdges: FloatArray = defaultEdges(DEFAULT_BARS)

    private var smoothed = FloatArray(DEFAULT_BARS)
    private var visualizer: Visualizer? = null

    /** Cortes de banda del ecualizador: una barra por banda, alineada con la curva. */
    fun setBandEdges(cutoffs: FloatArray) {
        if (cutoffs.isEmpty() || cutoffs.contentEquals(bandEdges)) return
        bandEdges = cutoffs.copyOf()
        smoothed = FloatArray(bandEdges.size)
        _levels.value = FloatArray(bandEdges.size)
    }

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
                        if (fft != null) _levels.value = computeBars(fft, samplingRate)
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
        smoothed = FloatArray(bandEdges.size)
        _levels.value = FloatArray(bandEdges.size)
    }

    private fun computeBars(fft: ByteArray, samplingRate: Int): FloatArray {
        // Visualizer entrega el sampling rate en milihercios (44100000 = 44,1 kHz).
        val rate = if (samplingRate > 0) samplingRate / 1000.0 else 44_100.0
        val bins = fft.size / 2
        val edges = bandEdges
        if (bins == 0 || edges.isEmpty()) return FloatArray(edges.size)

        val binHz = rate.toDouble() / (bins * 2)
        val raw = DoubleArray(edges.size)

        for (index in edges.indices) {
            val lowHz = if (index == 0) MIN_HZ else edges[index - 1].toDouble()
            val highHz = edges[index].toDouble().coerceAtLeast(lowHz + binHz)
            val startBin = (lowHz / binHz).toInt().coerceIn(0, bins - 1)
            val endBin = (highHz / binHz).toInt().coerceIn(startBin + 1, bins)

            var peak = 0.0
            for (bin in startBin until endBin) {
                val real = fft[bin * 2].toDouble()
                val imaginary = fft[bin * 2 + 1].toDouble()
                peak = maxOf(peak, sqrt(real * real + imaginary * imaginary))
            }
            raw[index] = 20.0 * log10(peak.coerceAtLeast(1e-6) / 128.0)
        }

        val result = FloatArray(edges.size)
        val framePeak = raw.maxOrNull() ?: SILENCE_DB
        if (framePeak <= SILENCE_DB) {
            for (index in result.indices) {
                smoothed[index] *= 0.82f
                result[index] = smoothed[index]
            }
            return result
        }

        // Normalizar contra el pico de la trama: así la forma del espectro es visible y las
        // barras no suben todas a la vez cuando la música es fuerte.
        val floor = framePeak - DYNAMIC_RANGE_DB
        for (index in result.indices) {
            val normalized = ((raw[index] - floor) / DYNAMIC_RANGE_DB).coerceIn(0.0, 1.0).toFloat()
            smoothed[index] = maxOf(normalized, smoothed[index] * DECAY)
            result[index] = smoothed[index]
        }
        return result
    }

    private fun defaultEdges(count: Int): FloatArray {
        val result = FloatArray(count)
        val logMin = log10(MIN_HZ)
        val logMax = log10(MAX_HZ)
        for (index in result.indices) {
            val fraction = (index + 1).toDouble() / count
            result[index] = Math.pow(10.0, logMin + (logMax - logMin) * fraction).toFloat()
        }
        return result
    }
}
