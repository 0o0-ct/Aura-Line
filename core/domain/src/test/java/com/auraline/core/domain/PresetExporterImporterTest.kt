package com.auraline.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PresetExporterImporterTest {

    @Test
    fun testExportAndImportJsonPreset() {
        val bands = generateEqualizerBands(25).mapIndexed { i, b ->
            if (i == 0) b.copy(gainDb = 6f) else b
        }
        val state = EqualizerState(numBands = 25, preampDb = 3f, bands = bands)

        val json = PresetExporterImporter.exportPresetToJson("Test Preset", state)
        assertNotNull(json)

        val imported = PresetExporterImporter.importPresetFromJson(json)
        assertEquals("Test Preset", imported.name)
        assertEquals(3f, imported.state.preampDb, 0.1f)
        assertEquals(6f, imported.state.bands.first().gainDb, 0.1f)
    }

    @Test
    fun testExportImportPreservesEqFilters() {
        val state = EqualizerState(
            numBands = 25,
            preampDb = 3f,
            bands = generateEqualizerBands(25),
            filters = EqFilters(
                hpfEnabled = true,
                hpfCutoffHz = 120f,
                lpfEnabled = true,
                lpfCutoffHz = 12000f,
                lowShelfHz = 90f,
                lowShelfGainDb = 6f,
                highShelfHz = 10000f,
                highShelfGainDb = -3f
            ),
            presetName = "Filtros Test"
        )

        val json = PresetExporterImporter.exportPresetToJson("Filtros Test", state)
        val imported = PresetExporterImporter.importPresetFromJson(json)

        assertEquals(true, imported.state.filters.hpfEnabled)
        assertEquals(120f, imported.state.filters.hpfCutoffHz, 0.01f)
        assertEquals(true, imported.state.filters.lpfEnabled)
        assertEquals(12000f, imported.state.filters.lpfCutoffHz, 0.01f)
        assertEquals(90f, imported.state.filters.lowShelfHz, 0.01f)
        assertEquals(6f, imported.state.filters.lowShelfGainDb, 0.01f)
        assertEquals(10000f, imported.state.filters.highShelfHz, 0.01f)
        assertEquals(-3f, imported.state.filters.highShelfGainDb, 0.01f)
    }

    @Test
    fun testParseAutoEqTextFormat() {
        val autoEqText = """
            Preamp: -3.0 dB
            Filter 1: ON PK Fc 60 Hz Gain 5.0 dB Q 1.41
            Filter 2: ON PK Fc 1000 Hz Gain -2.0 dB Q 1.41
        """.trimIndent()

        val bands = generateEqualizerBands(25)
        val gains = PresetExporterImporter.parseAutoEqText(autoEqText, bands)

        assertNotNull(gains)
    }
}
