package com.auraline.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerAcousticsAndPresetsTest {

    @Test
    fun testEqFiltersDefaultsArePresentAndNeutral() {
        val state = EqualizerState()
        assertEquals(true, state.filters.hpfEnabled)
        assertEquals(25f, state.filters.hpfCutoffHz, 0.01f)
        assertEquals(false, state.filters.lpfEnabled)
        assertEquals(0f, state.filters.lowShelfGainDb, 0.01f)
        assertEquals(0f, state.filters.highShelfGainDb, 0.01f)
        assertEquals(100f, state.filters.lowShelfHz, 0.01f)
        assertEquals(12000f, state.filters.highShelfHz, 0.01f)
    }

    @Test
    fun testAcousticRegionsMapping() {
        assertEquals(AcousticRegion.SUBBASS, getAcousticRegionFor(30f))
        assertEquals(AcousticRegion.DEEP_BASS, getAcousticRegionFor(100f))
        assertEquals(AcousticRegion.UPPER_BASS, getAcousticRegionFor(200f))
        assertEquals(AcousticRegion.LOW_MID, getAcousticRegionFor(500f))
        assertEquals(AcousticRegion.MID, getAcousticRegionFor(1000f))
        assertEquals(AcousticRegion.HIGH_MID, getAcousticRegionFor(3000f))
        assertEquals(AcousticRegion.PRESENCE, getAcousticRegionFor(5000f))
        assertEquals(AcousticRegion.TREBLE, getAcousticRegionFor(10000f))
        assertEquals(AcousticRegion.AIR, getAcousticRegionFor(15000f))
    }

    @Test
    fun testBandAcousticInfo() {
        val info100 = getBandAcousticInfo(100f)
        assertNotNull(info100)
        assertEquals(AcousticRegion.DEEP_BASS, info100.region)
        assertTrue(info100.primaryInstrument.contains("Bombo") || info100.primaryInstrument.contains("Bajo"))

        val info3000 = getBandAcousticInfo(3000f)
        assertEquals(AcousticRegion.HIGH_MID, info3000.region)
        assertTrue(info3000.boostEffect.isNotEmpty())
    }

    @Test
    fun testFactoryPresetsCategoriesAndGains() {
        assertTrue(FactoryPresets.size >= 25)

        PresetCategory.values().forEach { category ->
            val count = FactoryPresets.count { it.category == category }
            assertTrue("Category ${category.label} should have presets", count > 0)
        }

        FactoryPresets.forEach { preset ->
            assertTrue(preset.name.isNotEmpty())
            preset.gains.forEach { (freq, gain) ->
                assertTrue(freq in 20f..20000f)
                assertTrue(gain in -36f..36f)
            }
        }
    }

    @Test
    fun testLowFrequencySourcesCountAndMetadata() {
        assertEquals(15, LowFrequencySources.size)

        LowFrequencySources.forEach { source ->
            assertTrue(source.key.isNotEmpty())
            assertTrue(source.name.isNotEmpty())
            assertTrue(source.shortName.isNotEmpty())
            assertTrue(source.detail.isNotEmpty())
            assertTrue(source.tip.isNotEmpty())
            assertNotNull(source.category)
        }
    }

    @Test
    fun testLowSourceFocusResolution() {
        val bands = generateEqualizerBands(25)
        assertEquals(25, bands.size)

        val subFocus = focusFor("sub_bass")
        assertNotNull(subFocus)

        val resolved = resolveFocus(bands, subFocus!!)
        assertTrue(resolved.isNotEmpty())
        val (index, delta) = resolved.first()
        assertTrue(index in bands.indices)
        assertEquals(4f, delta)
    }

    @Test
    fun testPresetExportAndImportWithCategories() {
        val bands = generateEqualizerBands(25)
        val originalState = EqualizerState(
            numBands = 25,
            preampDb = 3.0f,
            bands = bands.mapIndexed { idx, b -> if (idx == 0) b.copy(gainDb = 6f) else b },
            presetName = "Mi Preajuste Test"
        )

        val exportedJson = PresetExporterImporter.exportPresetToJson("Mi Preajuste Test", originalState)
        assertTrue(exportedJson.contains("Mi Preajuste Test"))
        assertTrue(exportedJson.contains("preampDb"))

        val imported = PresetExporterImporter.importPresetFromJson(exportedJson)
        assertEquals("Mi Preajuste Test", imported.name)
        assertEquals(3.0f, imported.state.preampDb, 0.01f)
        assertEquals(25, imported.state.numBands)
        assertEquals(6f, imported.state.bands[0].gainDb, 0.01f)
    }
}
