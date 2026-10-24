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
    val bassFrequencyHz: Float = 100f,
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
    val language: String = "system",
    val theme: AppTheme = AppTheme()
)

fun generateEqualizerBands(count: Int, gainRangeDb: Float = 12f): List<EqBand> {
    val fStart = 20f
    val fEnd = 20000f
    val n = count.coerceAtLeast(2)
    return List(n) { i ->
        val freq = fStart * Math.pow((fEnd / fStart).toDouble(), (i.toDouble() / (n - 1))).toFloat()
        EqBand(id = i, frequencyHz = freq, gainDb = 0f)
    }
}
