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
