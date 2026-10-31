package com.auraline.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoPreampTest {

    @Test
    fun testAutoPreampZeroHeadroomUnderThreshold() {
        val bands = generateEqualizerBands(25).map { it.copy(gainDb = 3f) }
        val state = EqualizerState(autoPreamp = true, bands = bands)

        val headroom = AutoPreamp.calculateHeadroomDb(state)
        assertEquals(0f, headroom, 0.01f)
    }

    @Test
    fun testAutoPreampReducesWhenBoostIsExtreme() {
        val bands = generateEqualizerBands(25).mapIndexed { i, b ->
            if (i == 0) b.copy(gainDb = 20f) else b
        }
        val state = EqualizerState(autoPreamp = true, bands = bands)

        val headroom = AutoPreamp.calculateHeadroomDb(state)
        assertEquals(2.0f, headroom, 0.1f)
    }

    @Test
    fun testAutoPreampBypassedWhenDisabled() {
        val bands = generateEqualizerBands(25).map { it.copy(gainDb = 24f) }
        val state = EqualizerState(autoPreamp = false, bands = bands)

        val headroom = AutoPreamp.calculateHeadroomDb(state)
        assertEquals(0f, headroom, 0.01f)
    }
}
