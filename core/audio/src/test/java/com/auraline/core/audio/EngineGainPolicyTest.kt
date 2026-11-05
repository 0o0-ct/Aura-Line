package com.auraline.core.audio

import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.EqBand
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.GateParams
import com.auraline.core.domain.ModuleState
import com.auraline.core.domain.ToneControls
import com.auraline.core.domain.generateEqualizerBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión de la política de ganancia de la cadena principal.
 *
 * Estos tests no simulan AudioFlinger: fijan las decisiones de configuración que causaban la
 * pérdida de volumen (maquillaje pre-compresión, tono duplicado, MBC truncado a 9 kHz y
 * headroom que contaba reverb/maquillaje). La verificación acústica real se documenta aparte.
 */
class EngineGainPolicyTest {

    private fun flatState(
        effects: Boolean = true,
        tone: ToneControls = ToneControls(),
        compressor: CompressorParams = CompressorParams(),
        gate: GateParams = GateParams(),
        modules: ModuleState = ModuleState(),
        autoPreamp: Boolean = true,
        bypass: Boolean = false,
        bands: List<EqBand> = generateEqualizerBands(25)
    ): EqualizerState = EqualizerState(
        numBands = 25,
        bands = bands,
        tone = tone,
        compressor = compressor,
        gate = gate,
        modules = modules,
        effectsEnabled = effects,
        autoPreamp = autoPreamp,
        bypass = bypass
    )

    @Test
    fun mbcCoversTheWholeAudibleSpectrum() {
        val cutoffs = AuraLineEngine.mbcCutoffs(flatState())

        assertEquals("El MBC debe tener tres bandas", 3, cutoffs.size)
        assertTrue("Los cortes deben ser crecientes", cutoffs[0] < cutoffs[1] && cutoffs[1] < cutoffs[2])
        assertEquals(
            "La banda superior debe llegar a 20 kHz; antes terminaba en 9 kHz",
            20_000f,
            cutoffs.last(),
            0.01f
        )
    }

    @Test
    fun mbcCutoffsRespectMinimumSeparation() {
        val state = flatState(
            tone = ToneControls(bassFrequencyHz = 400f, trebleFrequencyHz = 2_000f)
        )
        val cutoffs = AuraLineEngine.mbcCutoffs(state)

        assertTrue(cutoffs[1] >= cutoffs[0] + 800f)
        assertTrue(cutoffs[2] > cutoffs[1])
    }

    @Test
    fun neutralMbcIsBitTransparent() {
        val neutral = CompressorParams(
            threshold = 0f,
            ratio = 1f,
            makeupGain = 0f
        )
        val settings = AuraLineEngine.compressorBandSettings(
            flatState(compressor = neutral, gate = GateParams(thresholdDb = -80f, ratio = 1f))
        )

        assertEquals(1f, settings.ratio, 0.001f)
        assertEquals(0f, settings.thresholdDb, 0.001f)
        assertEquals(0f, settings.preGainDb, 0.001f)
        assertEquals(0f, settings.postGainDb, 0.001f)
        assertEquals(-80f, settings.gateThresholdDb, 0.001f)
        assertEquals(1f, settings.expanderRatio, 0.001f)
    }

    @Test
    fun makeupIsAppliedAfterCompressionNotBefore() {
        val settings = AuraLineEngine.compressorBandSettings(
            flatState(
                compressor = CompressorParams(
                    threshold = -18f,
                    ratio = 3f,
                    makeupGain = 5f
                )
            )
        )

        assertEquals("El preGain del MBC no debe contener maquillaje", 0f, settings.preGainDb, 0.001f)
        assertEquals("El maquillaje debe aplicarse como postGain", 5f, settings.postGainDb, 0.001f)
        assertEquals(-18f, settings.thresholdDb, 0.001f)
        assertEquals(3f, settings.ratio, 0.001f)
    }

    @Test
    fun toneIsAppliedOnceInPreEqAndNeverInMbc() {
        val state = flatState(
            tone = ToneControls(bassDb = 6f, midDb = -2f, trebleDb = 4f),
            compressor = CompressorParams(makeupGain = 0f)
        )
        val settings = AuraLineEngine.compressorBandSettings(state)

        assertEquals("El MBC no debe volver a aplicar graves", 0f, settings.preGainDb, 0.001f)
        assertEquals("El MBC no debe volver a aplicar agudos", 0f, settings.postGainDb, 0.001f)
        assertEquals(
            "El PreEq sí aplica el tono de graves una sola vez",
            6f,
            AuraLineEngine.preEqGainDb(state, 0f, 100f, eqActive = true),
            0.001f
        )
        assertEquals(
            "El PreEq sí aplica el tono de agudos una sola vez",
            4f,
            AuraLineEngine.preEqGainDb(state, 0f, 10_000f, eqActive = true),
            0.001f
        )
    }

    @Test
    fun bypassDisablesAllGainStages() {
        val settings = AuraLineEngine.compressorBandSettings(
            flatState(bypass = true, compressor = CompressorParams(makeupGain = 12f))
        )

        assertEquals(1f, settings.ratio, 0.001f)
        assertEquals(0f, settings.thresholdDb, 0.001f)
        assertEquals(0f, settings.preGainDb, 0.001f)
        assertEquals(0f, settings.postGainDb, 0.001f)
        assertEquals(-80f, settings.gateThresholdDb, 0.001f)
        assertEquals(0f, AuraLineEngine.headroomDb(flatState(bypass = true), 24f, isBluetooth = true), 0.001f)
        assertEquals(
            0f,
            AuraLineEngine.preEqGainDb(flatState(bypass = true), 12f, 100f, eqActive = true),
            0.001f
        )
    }

    @Test
    fun neutralCurveNeedsNoInputAttenuationEvenWithMakeupAndReverb() {
        val state = flatState(
            compressor = CompressorParams(makeupGain = 12f),
            autoPreamp = true
        ).copy(reverb = com.auraline.core.domain.ReverbParams(mix = 1f))

        assertEquals(
            "Un compresor con maquillaje o una reverb no deben atenuar una curva plana",
            0f,
            AuraLineEngine.headroomDb(state, peakBoostDb = 0f, isBluetooth = true),
            0.001f
        )
        assertEquals(
            0f,
            AuraLineEngine.headroomDb(state, peakBoostDb = 1.5f, isBluetooth = false),
            0.001f
        )
    }

    @Test
    fun autoPreampOnlyCompensatesRealPositiveBoost() {
        val state = flatState(autoPreamp = true)

        assertEquals(0f, AuraLineEngine.headroomDb(state, 0f, false), 0.001f)
        assertEquals(0f, AuraLineEngine.headroomDb(state, 3f, false), 0.001f)
        assertEquals(9.5f, AuraLineEngine.headroomDb(state, 12f, false), 0.001f)
        assertEquals(11f, AuraLineEngine.headroomDb(state, 12f, true), 0.001f)
    }

    @Test
    fun manualModeStillProtectsBluetoothFromExtremeBoosts() {
        val state = flatState(autoPreamp = false)

        assertEquals(0f, AuraLineEngine.headroomDb(state, 12f, isBluetooth = false), 0.001f)
        assertEquals(0f, AuraLineEngine.headroomDb(state, 12f, isBluetooth = true), 0.001f)
        assertEquals(7.5f, AuraLineEngine.headroomDb(state, 24f, isBluetooth = true), 0.001f)
    }

    @Test
    fun limiterCeilingNeverExceedsUserCeiling() {
        val state = flatState(compressor = CompressorParams(limiterCeiling = -1f))
        assertEquals(-1.5f, AuraLineEngine.limiterCeilingDb(state, isBluetooth = true), 0.001f)
        assertEquals(-1f, AuraLineEngine.limiterCeilingDb(state, isBluetooth = false), 0.001f)

        val strict = flatState(compressor = CompressorParams(limiterCeiling = -6f))
        assertEquals(-6f, AuraLineEngine.limiterCeilingDb(strict, isBluetooth = true), 0.001f)
        assertEquals(-6f, AuraLineEngine.limiterCeilingDb(strict, isBluetooth = false), 0.001f)
    }

    @Test
    fun disabledModulesDoNotCompressOrGate() {
        val state = flatState(
            modules = ModuleState(compressor = false, gate = false, reverb = false, width = false)
        )
        val settings = AuraLineEngine.compressorBandSettings(state)

        assertEquals(1f, settings.ratio, 0.001f)
        assertEquals(0f, settings.thresholdDb, 0.001f)
        assertEquals(0f, settings.postGainDb, 0.001f)
        assertEquals(-80f, settings.gateThresholdDb, 0.001f)
    }

    @Test
    fun effectsOffLeavesDynamicsInBypass() {
        val settings = AuraLineEngine.compressorBandSettings(
            flatState(effects = false, compressor = CompressorParams(makeupGain = 8f))
        )

        assertEquals(1f, settings.ratio, 0.001f)
        assertEquals(0f, settings.thresholdDb, 0.001f)
        assertEquals(0f, settings.postGainDb, 0.001f)
        assertEquals(-80f, settings.gateThresholdDb, 0.001f)
    }

    @Test
    fun factoryCalibrationIsModerate() {
        val compressor = CompressorParams()
        val gate = GateParams()

        assertTrue(
            "El umbral de fábrica no debe comprimir toda la música: ${compressor.threshold} dB",
            compressor.threshold >= -20f
        )
        assertTrue("El ratio de fábrica debe ser moderado", compressor.ratio <= 2.5f)
        assertTrue("El maquillaje de fábrica no debe ser excesivo", compressor.makeupGain <= 3f)
        assertTrue("La puerta de fábrica no debe cortar música normal", gate.thresholdDb <= -50f)
        assertTrue("La expansión de fábrica debe ser suave", gate.ratio <= 2f)
        assertFalse("El limitador debe existir por defecto", compressor.limiterCeiling > 0f)
    }
}
