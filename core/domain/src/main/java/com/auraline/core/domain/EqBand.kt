package com.auraline.core.domain

data class EqBand(
    val id: Int,
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 0.707f,
    val enabled: Boolean = true
)

data class ToneControls(
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    /** Medios: cuerpo (200-400 Hz) y presencia (2,5 kHz). */
    val midDb: Float = 0f,
    /** Frontera graves/medios. */
    val bassFrequencyHz: Float = 100f,
    /** Frontera medios/agudos. */
    val trebleFrequencyHz: Float = 8000f
)

data class StereoControls(
    val balance: Float = 0f,        // -100..100
    val expansion: Float = 1.0f,    // 1.0..2.0
    val mono: Boolean = false,
    val swapChannels: Boolean = false,
    val invertPhase: Boolean = false
)

data class VolumeControls(
    val percent: Float = 100f,
    val linkedToSystem: Boolean = true
)

data class ReverbParams(
    val damping: Float = 0.5f,
    val filter: Float = 0.5f,
    val decay: Float = 0.5f,
    val preDelay: Float = 0.0f,
    val predelayMix: Float = 0.5f,
    val size: Float = 0.5f,
    val mix: Float = 0.25f
)

data class CompressorParams(
    val threshold: Float = -18f,
    val ratio: Float = 3f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 120f,
    val makeupGain: Float = 0f,
    val limiterCeiling: Float = -0.1f
)

data class EqualizerState(
    val numBands: Int = 25,
    val gainRangeDb: Float = 12f,
    val preampDb: Float = 0f,
    val bands: List<EqBand> = emptyList(),
    val tone: ToneControls = ToneControls(),
    val stereo: StereoControls = StereoControls(),
    val volume: VolumeControls = VolumeControls(),
    val reverb: ReverbParams = ReverbParams(),
    val compressor: CompressorParams = CompressorParams(),
    val motorEnabled: Boolean = false,
    val effectsEnabled: Boolean = false,
    /** Comparación A/B: procesa la cadena pero con la curva plana. */
    val bypass: Boolean = false,
    val autoPreamp: Boolean = true,
    val selectedRoute: Int = 0, // 0=speaker, 1=headphones, 2=bluetooth, 3=usb
    val presetName: String = "Plano"
)

data class UserPreset(
    val name: String,
    val state: EqualizerState,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppTheme(
    val paletteKey: String = "aura",
    val accentColor: Int = 0xFF00E5CC.toInt(),
    val isDark: Boolean = true,
    val followSystem: Boolean = true,
    val highContrast: Boolean = false
)

data class AppSettings(
    val numBands: Int = 25,
    val gainRangeDb: Float = 12f,
    val motorMode: String = "automatic",
    val autoStart: Boolean = false,
    val hapticFeedback: Boolean = true,
    val showValues: Boolean = true,
    val spectrumAnalyzer: String = "off",
    val profilesByOutput: Boolean = true,
    val excludedApps: List<String> = emptyList(),
    /** Si está activo, la app verifica el motor al encenderlo. */
    val autoCheck: Boolean = false,
    val language: String = "system",
    val theme: AppTheme = AppTheme()
)

/**
 * Rejilla de cortes para un ecualizador gráfico realizable.
 *
 * `DynamicsProcessing` reparte el espectro en bins de FFT (~47 Hz con bloque de 1024 a 48 kHz)
 * y descarta en silencio toda banda cuyo rango quede vacío. Por eso la rejilla es lineal hasta
 * 1 kHz (pasos de 100 Hz, dos bins) y logarítmica por encima: así ninguna banda queda muerta.
 */
fun generateEqualizerBands(count: Int, gainRangeDb: Float = 12f): List<EqBand> {
    val bands = count.coerceIn(20, 25)
    val lowBands = Math.round(bands * 0.40f).coerceIn(6, 12)
    val crossover = 1000f

    val cutoffs = ArrayList<Float>(bands)
    for (index in 1..lowBands) {
        cutoffs.add(crossover * index / lowBands)
    }
    val highBands = bands - lowBands
    if (highBands > 0) {
        val ratio = Math.pow((20000.0 / crossover), 1.0 / highBands)
        for (index in 1..highBands) {
            cutoffs.add((crossover * Math.pow(ratio, index.toDouble())).toFloat())
        }
    }

    return cutoffs.mapIndexed { index, cutoff ->
        EqBand(id = index, frequencyHz = cutoff.coerceIn(20f, 20000f), gainDb = 0f)
    }
}

/**
 * Frecuencia de sondeo dentro de la parte alta de la banda.
 *
 * El DSP cuantiza los cortes a bins (~50 Hz con bloque de 960), así que el borde real de una
 * banda no coincide exactamente con su corte nominal. Sondeando cerca del corte pero dentro
 * de la banda, la medida cae siempre en el rango que de verdad se procesa.
 */
fun bandProbeHz(bands: List<EqBand>, index: Int): Float {
    val cutoff = bands.getOrNull(index)?.frequencyHz ?: return 0f
    val previous = if (index == 0) 0f else bands.getOrNull(index - 1)?.frequencyHz ?: 0f
    val width = (cutoff - previous).coerceAtLeast(cutoff * 0.1f)
    return cutoff - width * 0.15f
}

/** Centro audible de la banda, el valor que tiene sentido mostrar al usuario. */
fun bandCenterHz(bands: List<EqBand>, index: Int): Float {
    val cutoff = bands.getOrNull(index)?.frequencyHz ?: return 0f
    val previous = if (index == 0) 0f else bands.getOrNull(index - 1)?.frequencyHz ?: 0f
    return if (previous <= 0f) cutoff / 2f else kotlin.math.sqrt(previous * cutoff)
}
