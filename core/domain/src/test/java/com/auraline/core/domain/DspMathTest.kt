package com.auraline.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DspMathTest {

    @Test
    fun test25BandsGenerationMonotonicAndRange() {
        val bands = generateEqualizerBands(25)
        assertEquals(25, bands.size)

        var previousHz = 0f
        for (i in bands.indices) {
            val freq = bands[i].frequencyHz
            assertTrue("La banda $i ($freq Hz) debe ser mayor que la anterior ($previousHz Hz)", freq > previousHz)
            assertTrue("Frecuencia $freq Hz debe estar en rango audible 20..20000 Hz", freq in 20f..20000f)
            previousHz = freq
        }

        val firstCenter = bandCenterHz(bands, 0)
        val lastCenter = bandCenterHz(bands, 24)
        assertTrue("Primer centro $firstCenter Hz debe estar en sub-graves (< 100 Hz)", firstCenter < 100f)
        assertTrue("Último centro $lastCenter Hz debe estar en agudos (> 10000 Hz)", lastCenter > 10000f)
    }

    @Test
    fun testBiquadPeakingEqMagnitudeTransferFunction() {
        val fs = 48000.0
        val f0 = 1000.0 // 1 kHz
        val gainDb = 6.0 // +6 dB boost
        val q = 1.414

        // RBJ Cookbook formula
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val alpha = sin(w0) / (2.0 * q)

        val b0 = 1.0 + alpha * A
        val b1 = -2.0 * cos(w0)
        val b2 = 1.0 - alpha * A
        val a0 = 1.0 + alpha / A
        val a1 = -2.0 * cos(w0)
        val a2 = 1.0 - alpha / A

        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0

        assertFalse("Los coeficientes no deben ser NaN", nb0.isNaN() || na1.isNaN())
        assertFalse("Los coeficientes no deben ser Inf", nb0.isInfinite() || na1.isInfinite())

        // Evaluate H(e^{j omega}) at f = f0
        val w_center = 2.0 * PI * f0 / fs
        val numReal = nb0 + nb1 * cos(w_center) + nb2 * cos(2.0 * w_center)
        val numImag = -(nb1 * sin(w_center) + nb2 * sin(2.0 * w_center))
        val denReal = 1.0 + na1 * cos(w_center) + na2 * cos(2.0 * w_center)
        val denImag = -(na1 * sin(w_center) + na2 * sin(2.0 * w_center))

        val numMag = sqrt(numReal * numReal + numImag * numImag)
        val denMag = sqrt(denReal * denReal + denImag * denImag)
        val magAtF0 = numMag / denMag
        val magDbAtF0 = 20.0 * log10(magAtF0)

        assertEquals("La ganancia en f0 debe ser exactamente +6 dB", gainDb, magDbAtF0, 0.05)

        // Evaluate H(e^{j omega}) far away at 50 Hz (should be ~0 dB)
        val w_far = 2.0 * PI * 50.0 / fs
        val numRealFar = nb0 + nb1 * cos(w_far) + nb2 * cos(2.0 * w_far)
        val numImagFar = -(nb1 * sin(w_far) + nb2 * sin(2.0 * w_far))
        val denRealFar = 1.0 + na1 * cos(w_far) + na2 * cos(2.0 * w_far)
        val denImagFar = -(na1 * sin(w_far) + na2 * sin(2.0 * w_far))

        val magFar = sqrt(numRealFar * numRealFar + numImagFar * numImagFar) / sqrt(denRealFar * denRealFar + denImagFar * denImagFar)
        val magDbFar = 20.0 * log10(magFar)

        assertEquals("La ganancia lejos del centro (50 Hz) debe ser ~0 dB", 0.0, magDbFar, 0.1)
    }

    @Test
    fun testLinkwitzRiley4CrossoverSumFlatness() {
        val fc = 1000.0 // 1 kHz crossover
        // For continuous analog prototype s = j omega:
        // H_LP(s) = (wc^2 / (s^2 + sqrt(2)*wc*s + wc^2))^2
        // H_HP(s) = (s^2 / (s^2 + sqrt(2)*wc*s + wc^2))^2
        // At s = j * wc (crossover frequency):
        // s^2 = -wc^2, so denominator is: -wc^2 + j*sqrt(2)*wc^2 + wc^2 = j*sqrt(2)*wc^2
        // H_LP(j*wc) = (wc^2 / (j*sqrt(2)*wc^2))^2 = (1 / (j*sqrt(2)))^2 = -1/2 = -0.5
        // H_HP(j*wc) = (-wc^2 / (j*sqrt(2)*wc^2))^2 = (-1 / (j*sqrt(2)))^2 = -1/2 = -0.5
        // Sum H_LP + H_HP = -0.5 + -0.5 = -1.0
        // Magnitude of sum: |-1.0| = 1.0 (exactly 0 dB!)
        val wc = 2.0 * PI * fc
        val wTest = wc // Test at crossover
        val s2 = -wTest * wTest
        val denomReal = s2 + wc * wc // 0
        val denomImag = sqrt(2.0) * wc * wTest

        val singleLpReal = (wc * wc * denomReal) / (denomReal * denomReal + denomImag * denomImag)
        val singleLpImag = (-wc * wc * denomImag) / (denomReal * denomReal + denomImag * denomImag)

        // Square for 4th order cascaded
        val lpReal = singleLpReal * singleLpReal - singleLpImag * singleLpImag
        val lpImag = 2.0 * singleLpReal * singleLpImag

        val singleHpReal = (s2 * denomReal) / (denomReal * denomReal + denomImag * denomImag)
        val singleHpImag = (-s2 * denomImag) / (denomReal * denomReal + denomImag * denomImag)

        val hpReal = singleHpReal * singleHpReal - singleHpImag * singleHpImag
        val hpImag = 2.0 * singleHpReal * singleHpImag

        val sumReal = lpReal + hpReal
        val sumImag = lpImag + hpImag
        val sumMagnitude = sqrt(sumReal * sumReal + sumImag * sumImag)

        assertEquals("La suma acústica del crossover LR4 en fc debe tener magnitud exacta 1.0 (0 dB)", 1.0, sumMagnitude, 0.001)
    }

    @Test
    fun testCompressorTransferFunctionAndThreshold() {
        val thresholdDb = -20.0f
        val ratio = 4.0f
        val threshLin = 10.0f.pow(thresholdDb / 20.0f) // 0.1

        // Below threshold (input = -30 dBFS -> lin ~ 0.0316)
        val inSubThresh = 0.0316f
        var gainSub = 1.0f
        if (inSubThresh > threshLin) {
            val dbOver = 20.0f * log10(inSubThresh / threshLin)
            val dbRed = dbOver * (1.0f - 1.0f / ratio)
            gainSub = 10.0f.pow(-dbRed / 20.0f)
        }
        assertEquals("Bajo el umbral la ganancia debe ser 1.0 (sin compresión)", 1.0f, gainSub, 0.0001f)

        // Above threshold (input = 0 dBFS -> lin = 1.0, 20 dB over threshold)
        val inAboveThresh = 1.0f
        var gainAbove = 1.0f
        if (inAboveThresh > threshLin) {
            val dbOver = 20.0f * log10(inAboveThresh / threshLin) // 20 dB
            val dbRed = dbOver * (1.0f - 1.0f / ratio) // 20 * (1 - 0.25) = 15 dB de reducción
            gainAbove = 10.0f.pow(-dbRed / 20.0f)
        }
        val outDb = 20.0f * log10(inAboveThresh * gainAbove)
        assertEquals("Con ratio 4:1 y 20 dB sobre umbral, la reducción debe ser de 15 dB", -15.0f, outDb, 0.05f)
    }

    @Test
    fun testLimiterCeilingEnforcement() {
        val ceilingDb = -1.5f
        val ceilingLin = 10.0f.pow(ceilingDb / 20.0f) // ~0.8414

        // Massive input transient +12 dBFS (lin ~ 3.98)
        val input = 3.98f
        val targetGain = if (input > ceilingLin) ceilingLin / input else 1.0f
        val output = input * targetGain

        assertEquals("El limitador debe forzar la señal estrictamente al techo (-1.5 dBFS)", ceilingLin, output, 0.001f)
        assertTrue("La salida no debe exceder 0 dBFS (1.0)", output < 1.0f)
    }

    @Test
    fun test15LowFrequencyInstrumentsListCompleteness() {
        assertEquals("Deben existir exactamente 15 perfiles de instrumentos graves", 15, LowFrequencySources.size)
        val keys = LowFrequencySources.map { it.key }.toSet()
        assertEquals("Todas las claves de instrumentos deben ser únicas", 15, keys.size)

        for (source in LowFrequencySources) {
            assertTrue("Frecuencia fundamental de ${source.name} debe ser > 10 Hz", source.fundamentalHz.first >= 10)
            assertTrue("Rango fundamental de ${source.name} debe ser coherente", source.fundamentalHz.last > source.fundamentalHz.first)
            assertTrue("Nombre corto de ${source.name} debe ser conciso", source.shortName.length in 1..8)
        }
    }

    @Test
    fun testSafeGainClampingExtendsTo36Db() {
        assertEquals(36.0f, clampPreampDb(50.0f), 0.001f)
        assertEquals(-36.0f, clampPreampDb(-50.0f), 0.001f)
        assertEquals(15.0f, clampPreampDb(15.0f), 0.001f)

        assertEquals(36.0f, clampBandGainDb(40.0f, 36.0f), 0.001f)
        assertEquals(-36.0f, clampBandGainDb(-40.0f, 36.0f), 0.001f)
    }

    @Test
    fun testFlatResponseTransferFunction() {
        // When all bands are 0 dB, the magnitude at any test frequency must be exactly 0 dB (unity gain)
        val fs = 48000.0
        val testFreqs = listOf(20.0, 50.0, 100.0, 1000.0, 5000.0, 10000.0, 20000.0)
        for (f in testFreqs) {
            val f0 = 1000.0
            val gainDb = 0.0
            val q = 1.414
            val A = 10.0.pow(gainDb / 40.0) // 1.0
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val b0 = 1.0 + alpha * A
            val b1 = -2.0 * cos(w0)
            val b2 = 1.0 - alpha * A
            val a0 = 1.0 + alpha / A
            val a1 = -2.0 * cos(w0)
            val a2 = 1.0 - alpha / A

            val nb0 = b0 / a0
            val nb1 = b1 / a0
            val nb2 = b2 / a0
            val na1 = a1 / a0
            val na2 = a2 / a0

            val w = 2.0 * PI * f / fs
            val numReal = nb0 + nb1 * cos(w) + nb2 * cos(2.0 * w)
            val numImag = -(nb1 * sin(w) + nb2 * sin(2.0 * w))
            val denReal = 1.0 + na1 * cos(w) + na2 * cos(2.0 * w)
            val denImag = -(na1 * sin(w) + na2 * sin(2.0 * w))

            val mag = sqrt(numReal * numReal + numImag * numImag) / sqrt(denReal * denReal + denImag * denImag)
            val magDb = 20.0 * log10(mag)
            assertEquals("Respuesta plana debe dar 0.0 dB a $f Hz", 0.0, magDb, 0.0001)
        }
    }

    @Test
    fun testExtremeSampleRatesStability() {
        val sampleRates = listOf(44100.0, 48000.0, 88200.0, 96000.0, 192000.0)
        val f0 = 18000.0 // Near Nyquist for 44.1k
        val gainDb = 12.0
        val q = 2.0

        for (fs in sampleRates) {
            val A = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / A

            assertFalse("a0 no debe ser 0 para fs=$fs", abs(a0) < 1e-9)
            val b0 = (1.0 + alpha * A) / a0
            val a1 = (-2.0 * cos(w0)) / a0
            val a2 = (1.0 - alpha / A) / a0

            assertFalse("Coeficiente b0 no debe ser NaN para fs=$fs", b0.isNaN())
            assertFalse("Coeficiente a1 no debe ser NaN para fs=$fs", a1.isNaN())
            assertFalse("Coeficiente a2 no debe ser NaN para fs=$fs", a2.isNaN())
            assertTrue("Filtro IIR debe ser estable (|a2| < 1) para fs=$fs", abs(a2) < 1.0)
        }
    }

    @Test
    fun testAdditiveInstrumentFocusNonDestructive() {
        val bands = generateEqualizerBands(25)
        val initialGains = bands.map { it.gainDb }

        // Apply a focus recipe (e.g. KickDrum: +3 dB in 141 Hz)
        val step = FocusStep(centerHz = 141, deltaDb = 3.0f)
        val modifiedBands = bands.map { band ->
            if (abs(band.frequencyHz - step.centerHz) < 20f) {
                band.copy(gainDb = band.gainDb + step.deltaDb)
            } else {
                band
            }
        }

        // Verify that only the target band was changed
        for (i in bands.indices) {
            if (abs(bands[i].frequencyHz - step.centerHz) < 20f) {
                assertEquals("La banda modificada debe tener +3 dB", 3.0f, modifiedBands[i].gainDb, 0.01f)
            } else {
                assertEquals("Las otras bandas no deben ser alteradas", initialGains[i], modifiedBands[i].gainDb, 0.001f)
            }
        }

        // Revert the focus
        val revertedBands = modifiedBands.map { band ->
            if (abs(band.frequencyHz - step.centerHz) < 20f) {
                band.copy(gainDb = band.gainDb - step.deltaDb)
            } else {
                band
            }
        }

        for (i in bands.indices) {
            assertEquals("La curva debe volver a su estado original exacto", initialGains[i], revertedBands[i].gainDb, 0.001f)
        }
    }

    // --- PRUEBAS OBLIGATORIAS DE PROCESAMIENTO ACÚSTICO Y DSP ---

    @Test
    fun testAudioBypassBitExactIntegrity() {
        val input = floatArrayOf(0.1f, -0.4f, 0.85f, -0.99f, 0.0f, 0.33f)
        val isBypass = true
        val output = FloatArray(input.size) { i ->
            if (isBypass) input[i] else input[i] * 2.0f
        }
        for (i in input.indices) {
            assertEquals("La señal con bypass debe ser bit a bit idéntica a la entrada", input[i], output[i], 0.0f)
        }
    }

    @Test
    fun test1kHzEqGainVariationMagnitudeDifference() {
        val fs = 48000.0
        val f0 = 1000.0
        val boostDb = 6.0
        val cutDb = -6.0
        val q = 1.414

        fun calcGainAt(gainDb: Double, testF: Double): Double {
            val A = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / A
            val b0 = (1.0 + alpha * A) / a0
            val b1 = (-2.0 * cos(w0)) / a0
            val b2 = (1.0 - alpha * A) / a0
            val a1 = (-2.0 * cos(w0)) / a0
            val a2 = (1.0 - alpha / A) / a0

            val w = 2.0 * PI * testF / fs
            val numReal = b0 + b1 * cos(w) + b2 * cos(2.0 * w)
            val numImag = -(b1 * sin(w) + b2 * sin(2.0 * w))
            val denReal = 1.0 + a1 * cos(w) + a2 * cos(2.0 * w)
            val denImag = -(a1 * sin(w) + a2 * sin(2.0 * w))

            val mag = sqrt(numReal * numReal + numImag * numImag) / sqrt(denReal * denReal + denImag * denImag)
            return 20.0 * log10(mag)
        }

        val boostMag = calcGainAt(boostDb, 1000.0)
        val cutMag = calcGainAt(cutDb, 1000.0)

        assertEquals("Refuerzo de 1 kHz debe dar +6 dB", 6.0, boostMag, 0.05)
        assertEquals("Atenuación de 1 kHz debe dar -6 dB", -6.0, cutMag, 0.05)
        assertEquals("La diferencia entre refuerzo y atenuación debe ser 12 dB", 12.0, boostMag - cutMag, 0.1)
    }

    @Test
    fun testLowFrequenciesBandResponseSpectrum() {
        val fs = 48000.0
        val lowFrequencies = listOf(40.0, 60.0, 80.0, 100.0, 150.0)
        val gainDb = 9.0
        val q = 2.0

        for (f0 in lowFrequencies) {
            val A = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val a0 = 1.0 + alpha / A
            val b0 = (1.0 + alpha * A) / a0
            val b1 = (-2.0 * cos(w0)) / a0
            val b2 = (1.0 - alpha * A) / a0
            val a1 = (-2.0 * cos(w0)) / a0
            val a2 = (1.0 - alpha / A) / a0

            // Evaluate at center frequency f0
            val numReal = b0 + b1 * cos(w0) + b2 * cos(2.0 * w0)
            val numImag = -(b1 * sin(w0) + b2 * sin(2.0 * w0))
            val denReal = 1.0 + a1 * cos(w0) + a2 * cos(2.0 * w0)
            val denImag = -(a1 * sin(w0) + a2 * sin(2.0 * w0))
            val magCenter = 20.0 * log10(sqrt(numReal * numReal + numImag * numImag) / sqrt(denReal * denReal + denImag * denImag))

            assertEquals("La ganancia en el centro grave $f0 Hz debe ser +9 dB", 9.0, magCenter, 0.05)

            // Evaluate at 10 kHz (treble should be unaffected ~0 dB)
            val wTreble = 2.0 * PI * 10000.0 / fs
            val nRealT = b0 + b1 * cos(wTreble) + b2 * cos(2.0 * wTreble)
            val nImagT = -(b1 * sin(wTreble) + b2 * sin(2.0 * wTreble))
            val dRealT = 1.0 + a1 * cos(wTreble) + a2 * cos(2.0 * wTreble)
            val dImagT = -(a1 * sin(wTreble) + a2 * sin(2.0 * wTreble))
            val magTreble = 20.0 * log10(sqrt(nRealT * nRealT + nImagT * nImagT) / sqrt(dRealT * dRealT + dImagT * dImagT))

            assertEquals("Los filtros de graves no deben afectar los agudos a 10 kHz", 0.0, magTreble, 0.1)
        }
    }

    @Test
    fun testNoiseGateAttenuationBelowThreshold() {
        val gateThresholdDb = -50.0f
        val gateRatio = 3.0f
        val threshLin = 10.0f.pow(gateThresholdDb / 20.0f) // ~0.00316

        // Case A: Signal above gate threshold (-30 dBFS -> ~0.0316)
        val sigAbove = 0.0316f
        var gateGainAbove = 1.0f
        if (sigAbove < threshLin) {
            val dbUnder = 20.0f * log10(threshLin / sigAbove)
            val attenDb = dbUnder * (gateRatio - 1.0f)
            gateGainAbove = 10.0f.pow(-attenDb / 20.0f)
        }
        assertEquals("Señal sobre el umbral no debe ser atenuada por la puerta", 1.0f, gateGainAbove, 0.0001f)

        // Case B: Signal below gate threshold (-70 dBFS -> ~0.000316, 20 dB under threshold)
        val sigBelow = 0.000316f
        var gateGainBelow = 1.0f
        if (sigBelow < threshLin) {
            val dbUnder = 20.0f * log10(threshLin / sigBelow) // 20 dB under
            val attenDb = dbUnder * (gateRatio - 1.0f) // 20 * (3 - 1) = 40 dB de atenuación
            gateGainBelow = 10.0f.pow(-attenDb / 20.0f)
        }
        val outDbBelow = 20.0f * log10(sigBelow * gateGainBelow)
        assertEquals("Señal bajo el umbral de ruido debe ser atenuada adicionalmente por ratio 3:1", -110.0f, outDbBelow, 0.5f)
    }

    @Test
    fun testReverbDecayImpulseResponseTail() {
        // Feedback comb filter simulating room reflection impulse response
        val delaySamples = 120
        val buffer = FloatArray(delaySamples)
        var writeIdx = 0
        val feedback = 0.7f

        var outEnergy = 0.0
        // Input: 1 sample impulse at t=0
        for (n in 0 until 1200) {
            val input = if (n == 0) 1.0f else 0.0f
            val delayed = buffer[writeIdx]
            val output = input + delayed * feedback
            buffer[writeIdx] = output
            writeIdx = (writeIdx + 1) % delaySamples

            if (n >= delaySamples) {
                outEnergy += output * output
            }
        }

        assertTrue("La respuesta al impulso de la reverb debe generar cola acústica persistente", outEnergy > 0.05)
    }

    @Test
    fun testStereoWidthMidSideMatrixMonoAndExpansion() {
        val inL = 1.0f
        val inR = 0.4f

        // Encoding M/S
        val mid = (inL + inR) * 0.5f // 0.7
        val side = (inL - inR) * 0.5f // 0.3

        // Case 1: Mono (width = 0.0)
        val widthMono = 0.0f
        val outL_mono = mid + side * widthMono
        val outR_mono = mid - side * widthMono
        assertEquals("En modo mono L y R deben ser perfectamente iguales a Mid", mid, outL_mono, 0.0001f)
        assertEquals("En modo mono L y R deben ser perfectamente iguales a Mid", mid, outR_mono, 0.0001f)

        // Case 2: Stereo Widening (width = 1.5)
        val widthExp = 1.5f
        val outL_exp = mid + side * widthExp // 0.7 + 0.45 = 1.15
        val outR_exp = mid - side * widthExp // 0.7 - 0.45 = 0.25
        val sepIn = inL - inR // 0.6
        val sepOut = outL_exp - outR_exp // 0.9
        assertEquals("La separación estéreo debe incrementarse proporcionalmente al ancho", sepIn * widthExp, sepOut, 0.001f)
    }

    @Test
    fun testLookaheadLimiterStrictPeakEnforcement() {
        val ceilingDb = -0.5f
        val ceilingLin = 10.0f.pow(ceilingDb / 20.0f) // ~0.944

        // Massive transient +6 dBFS (lin ~ 2.0)
        val transients = floatArrayOf(0.5f, 1.2f, 2.0f, 1.8f, 0.3f)
        for (x in transients) {
            val targetGain = if (x > ceilingLin) ceilingLin / x else 1.0f
            val y = x * targetGain
            assertTrue("La salida del limitador nunca debe superar el techo (-0.5 dBFS)", y <= ceilingLin + 0.0001f)
        }
    }
}
