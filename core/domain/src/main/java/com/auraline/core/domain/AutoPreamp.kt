package com.auraline.core.domain

/**
 * Calculador de Preamp Automático (Anti-Clipping / Protección de Nivel).
 * Analiza la energía combinada de las bandas de ecualización, tono, compresor y reverb
 * para calcular la reducción exacta de ganancia de entrada necesaria para prevenir la
 * saturación digital en el DAC / códec Bluetooth.
 */
object AutoPreamp {

    fun calculateHeadroomDb(state: EqualizerState): Float {
        if (!state.autoPreamp || state.bypass) return 0f

        val peakEq = state.bands.maxOfOrNull { it.gainDb.coerceAtLeast(0f) } ?: 0f
        val peakTone = maxOf(
            state.tone.subDb.coerceAtLeast(0f),
            state.tone.bassDb.coerceAtLeast(0f),
            state.tone.midDb.coerceAtLeast(0f),
            state.tone.trebleDb.coerceAtLeast(0f)
        )

        val compressorMakeup = if (state.effectsEnabled && state.modules.compressor) {
            state.compressor.makeupGain.coerceAtLeast(0f)
        } else {
            0f
        }

        val totalBoostDb = maxOf(peakEq + peakTone, compressorMakeup)

        // Solo se aplica reducción si el refuerzo total supera el umbral seguro de 12 dB
        return if (totalBoostDb > 12f) {
            ((totalBoostDb - 12f) * 0.25f).coerceIn(0f, 8f)
        } else {
            0f
        }
    }
}
