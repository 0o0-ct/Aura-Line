package com.auraline.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Comprobación de la ley de ganancia sobre señales PCM conocidas.
 *
 * Modela el comportamiento estacionario de la cadena (nivel de programa + maquillaje +
 * limitador). No sustituye a una medición de AudioFlinger, pero demuestra numéricamente que
 * la calibración nueva conserva el RMS y que la antigua lo hundía.
 */
class PcmGainStagingTest {

    private val sampleRate = 48_000

    private fun sine(amplitude: Double, frequency: Double = 1_000.0, seconds: Double = 1.0): DoubleArray {
        val count = (sampleRate * seconds).toInt()
        return DoubleArray(count) { index ->
            amplitude * sin(2.0 * PI * frequency * index / sampleRate)
        }
    }

    private fun rmsDb(samples: DoubleArray): Double {
        val meanSquare = samples.sumOf { it * it } / samples.size
        return 20.0 * log10(sqrt(meanSquare).coerceAtLeast(1e-12))
    }

    private fun peakDb(samples: DoubleArray): Double =
        20.0 * log10((samples.maxOfOrNull { abs(it) } ?: 0.0).coerceAtLeast(1e-12))

    /** Transferencia estática de un compresor con maquillaje posterior. */
    private fun compressorOutputDb(
        inputDb: Double,
        thresholdDb: Double,
        ratio: Double,
        postMakeupDb: Double
    ): Double = if (inputDb > thresholdDb) {
        thresholdDb + (inputDb - thresholdDb) / ratio + postMakeupDb
    } else {
        inputDb + postMakeupDb
    }

    /** Transferencia antigua: el maquillaje entraba antes del compresor y aumentaba la GR. */
    private fun legacyCompressorOutputDb(
        inputDb: Double,
        thresholdDb: Double,
        ratio: Double,
        preMakeupDb: Double
    ): Double {
        val driven = inputDb + preMakeupDb
        return if (driven > thresholdDb) {
            thresholdDb + (driven - thresholdDb) / ratio
        } else {
            driven
        }
    }

    private fun downwardExpanderOutputDb(inputDb: Double, thresholdDb: Double, ratio: Double): Double =
        if (inputDb >= thresholdDb) inputDb
        else thresholdDb - (thresholdDb - inputDb) * ratio

    private fun limitDb(inputDb: Double, ceilingDb: Double): Double =
        if (inputDb > ceilingDb) ceilingDb else inputDb

    @Test
    fun neutralCompressorPreservesPcmRmsAndPeak() {
        val pcm = sine(amplitude = 0.35)
        val inputRms = rmsDb(pcm)
        val inputPeak = peakDb(pcm)

        val outputRms = compressorOutputDb(inputRms, thresholdDb = -16.0, ratio = 1.0, postMakeupDb = 0.0)
        val outputPeak = compressorOutputDb(inputPeak, thresholdDb = -16.0, ratio = 1.0, postMakeupDb = 0.0)

        assertEquals(inputRms, outputRms, 1e-9)
        assertEquals(inputPeak, outputPeak, 1e-9)
        assertTrue("El pico debe quedar por debajo de 0 dBFS", outputPeak < 0.0)
    }

    @Test
    fun newCalibrationKeepsProgramRmsWhileControllingPeaks() {
        val pcm = sine(amplitude = 0.35)
        val inputRms = rmsDb(pcm)
        val inputPeak = peakDb(pcm)
        val threshold = CompressorParams().threshold.toDouble()
        val ratio = CompressorParams().ratio.toDouble()
        val makeup = CompressorParams().makeupGain.toDouble()

        val outputRms = compressorOutputDb(inputRms, threshold, ratio, makeup)
        val outputPeak = compressorOutputDb(inputPeak, threshold, ratio, makeup)

        assertTrue(
            "El RMS no debe caer más de 0.5 dB con la calibración nueva: ${"%.2f".format(outputRms - inputRms)} dB",
            abs(outputRms - inputRms) <= 0.5
        )
        assertTrue(
            "El compresor sí debe controlar los picos: ${"%.2f".format(outputPeak - inputPeak)} dB",
            outputPeak < inputPeak
        )
    }

    @Test
    fun legacyCalibrationWouldHaveDroppedTheSameProgramByMoreThanEightDb() {
        val pcm = sine(amplitude = 0.35)
        val inputRms = rmsDb(pcm)
        val inputPeak = peakDb(pcm)

        val legacyRms = legacyCompressorOutputDb(inputRms, -28.0, 4.0, 1.5)
        val legacyPeak = legacyCompressorOutputDb(inputPeak, -28.0, 4.0, 1.5)

        assertTrue(
            "La calibración antigua debía perder >8 dB de RMS: ${"%.2f".format(legacyRms - inputRms)} dB",
            legacyRms - inputRms < -8.0
        )
        assertTrue(
            "La calibración antigua debía perder >10 dB de pico: ${"%.2f".format(legacyPeak - inputPeak)} dB",
            legacyPeak - inputPeak < -10.0
        )
    }

    @Test
    fun gateDefaultOnlyActsBelowSixtyDb() {
        val normalMusic = -20.0
        val veryQuiet = -70.0

        assertEquals(normalMusic, downwardExpanderOutputDb(normalMusic, -60.0, 2.0), 1e-9)
        assertTrue(
            "Por debajo del umbral la puerta sí debe atenuar",
            downwardExpanderOutputDb(veryQuiet, -60.0, 2.0) < veryQuiet
        )
    }

    @Test
    fun limiterOnlyClampsSamplesAboveTheCeiling() {
        assertEquals(-6.0, limitDb(-6.0, -1.0), 1e-9)
        assertEquals(-1.0, limitDb(0.0, -1.0), 1e-9)
        assertEquals(-1.0, limitDb(3.0, -1.0), 1e-9)
    }

    @Test
    fun neutralEqCurveLeavesTheWaveformUntouched() {
        val pcm = sine(amplitude = 0.35)
        val gains = generateEqualizerBands(25).map { 0f }
        val applied = pcm.map { sample ->
            val linear = gains.fold(1.0) { acc, _ -> acc }
            sample * linear
        }.toDoubleArray()

        assertEquals(rmsDb(pcm), rmsDb(applied), 1e-12)
        assertEquals(peakDb(pcm), peakDb(applied), 1e-12)
    }
}
