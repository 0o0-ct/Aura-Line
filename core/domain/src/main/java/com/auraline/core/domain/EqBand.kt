package com.auraline.core.domain

data class EqBand(
    val id: Int,
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 0.707f,
    val enabled: Boolean = true
)

data class ToneParametricBand(
    val id: String,
    val name: String,
    val frequencyHz: Float,
    val gainDb: Float = 0f,
    val q: Float = 0.707f,
    val filterType: String = "PEAKING", // LOW_SHELF, HIGH_SHELF, PEAKING, LOW_PASS, HIGH_PASS, NOTCH
    val enabled: Boolean = true,
    val colorHex: Long = 0xFF2196F3L
)

fun defaultToneParametricBands(): List<ToneParametricBand> = listOf(
    ToneParametricBand("sub", "Subgraves", 30f, 0f, 0.707f, "LOW_SHELF", true, 0xFF9C27B0L),
    ToneParametricBand("deep_bass", "Grave profundo", 50f, 0f, 1.0f, "PEAKING", true, 0xFF3F51B5L),
    ToneParametricBand("bass", "Graves", 100f, 0f, 0.707f, "LOW_SHELF", true, 0xFF2196F3L),
    ToneParametricBand("low_mid", "Medios graves", 250f, 0f, 1.0f, "PEAKING", true, 0xFF00BCD4L),
    ToneParametricBand("mid", "Medios", 1000f, 0f, 1.0f, "PEAKING", true, 0xFF4CAF50L),
    ToneParametricBand("upper_mid", "Medios altos", 2500f, 0f, 1.0f, "PEAKING", true, 0xFFFFC107L),
    ToneParametricBand("presence", "Presencia", 4000f, 0f, 1.2f, "PEAKING", true, 0xFFFF9800L),
    ToneParametricBand("brilliance", "Brillo", 8000f, 0f, 1.0f, "PEAKING", true, 0xFFE91E63L),
    ToneParametricBand("treble", "Agudos", 12000f, 0f, 0.707f, "HIGH_SHELF", true, 0xFFF50057L),
    ToneParametricBand("air", "Aire", 18000f, 0f, 0.707f, "HIGH_SHELF", true, 0xFFAA00FFL)
)

data class ToneControls(
    /** Refuerzo extra de la zona sub-baja (0-100 Hz). */
    val subDb: Float = 0f,
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    /** Medios: cuerpo (200-400 Hz) y presencia (2,5 kHz). */
    val midDb: Float = 0f,
    /** Frontera graves/medios. */
    val bassFrequencyHz: Float = 100f,
    /** Frontera medios/agudos. */
    val trebleFrequencyHz: Float = 8000f,
    /**
     * Corte del paso bajo del sub-bajo (Hz). El refuerzo de sub no se limita ya a la primera
     * banda del ecualizador: se aplica como shelf propio hasta esta frecuencia, ajustable.
     */
    val subCutoffHz: Float = 60f,
    /** Bandas paramétricas detalladas para el módulo de tono avanzado. */
    val bands: List<ToneParametricBand> = defaultToneParametricBands().map { band ->
        when (band.id) {
            "sub" -> if (subDb != 0f) band.copy(gainDb = subDb) else band
            "bass" -> if (bassDb != 0f) band.copy(gainDb = bassDb) else band
            "mid" -> if (midDb != 0f) band.copy(gainDb = midDb) else band
            "treble" -> if (trebleDb != 0f) band.copy(gainDb = trebleDb) else band
            else -> band
        }
    }
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
    /**
     * Umbral moderado: solo comprime los picos y las partes más fuertes. Un umbral de -28 dB
     * con ratio 4 comprimía toda la música y era la principal causa de la caída de volumen.
     */
    val threshold: Float = -16f,
    val ratio: Float = 2f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 180f,
    /**
     * Ganancia de maquillaje aplicada DESPUÉS de comprimir. Restaura el nivel medio sin
     * aumentar la reducción de ganancia del propio compresor.
     */
    val makeupGain: Float = 2f,
    /** Techo del limitador final en dBFS (≤ 0): ningún pico debe superarlo. */
    val limiterCeiling: Float = -1f
)

/** Cada etapa de la cadena se puede desactivar por separado. */
data class ModuleState(
    val equalizer: Boolean = true,
    val gate: Boolean = true,
    val compressor: Boolean = true,
    val limiter: Boolean = true,
    val reverb: Boolean = true,
    val width: Boolean = true
)

/** Puerta de ruido y expansor, aplicados en las bandas del compresor multibanda. */
data class GateParams(
    /** Por debajo de este nivel la señal se atenúa. */
    val thresholdDb: Float = -60f,
    /** 1 = puerta cerrada; valores mayores expansión más agresiva. */
    val ratio: Float = 2f
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
    val gate: GateParams = GateParams(),
    val modules: ModuleState = ModuleState(),
    val motorEnabled: Boolean = false,
    val effectsEnabled: Boolean = false,
    /** Comparación A/B: procesa la cadena pero con la curva plana. */
    val bypass: Boolean = false,
    val autoPreamp: Boolean = true,
    val selectedRoute: Int = 0, // 0=speaker, 1=headphones, 2=bluetooth, 3=usb
    val presetName: String = "Plano",
    /** Margen de realce de Graves y Sub-bajo según el perfil de uso activo. */
    val toneBoostMarginDb: Float = 12f
)

// ---------------------------------------------------------------------------
// Rangos seguros: la UI y el repositorio usan SIEMPRE estas funciones. Así un
// slider no puede guardar valores imposibles y compile/aplicar recibe datos
// válidos por construcción (dB reales, Hz reales, nunca valores normalizados).
// ---------------------------------------------------------------------------

fun clampPreampDb(db: Float): Float = db.coerceIn(-36f, 36f)
fun clampBandGainDb(db: Float, rangeDb: Float): Float = db.coerceIn(-rangeDb, rangeDb)
fun clampToneDb(db: Float, marginDb: Float): Float = db.coerceIn(-marginDb, marginDb)
fun clampBassCrossHz(hz: Float): Float = hz.coerceIn(40f, 400f)
fun clampTrebleCrossHz(hz: Float): Float = hz.coerceIn(2_000f, 16_000f)
fun clampSubCutoffHz(hz: Float): Float = hz.coerceIn(20f, 100f)
fun clampRatio(ratio: Float): Float = ratio.coerceIn(1f, 20f)
fun clampThresholdDb(db: Float): Float = db.coerceIn(-60f, 0f)
fun clampAttackMs(ms: Float): Float = ms.coerceIn(0.1f, 100f)
fun clampReleaseMs(ms: Float): Float = ms.coerceIn(10f, 1_000f)
fun clampMakeupDb(db: Float): Float = db.coerceIn(-12f, 24f)
fun clampLimiterCeilingDb(db: Float): Float = db.coerceIn(-20f, 0f)
fun clampGateThresholdDb(db: Float): Float = db.coerceIn(-90f, -20f)
fun clampGateRatio(ratio: Float): Float = ratio.coerceIn(1f, 10f)
fun clampReverbMix(mix: Float): Float = mix.coerceIn(0f, 1f)
fun clampStereoExpansion(width: Float): Float = width.coerceIn(0f, 2f)

/** Margen de realce de Graves/Sub que corresponde al perfil de uso elegido. */
fun toneBoostMarginFor(usageProfileKey: String): Float =
    usageProfileByKey(usageProfileKey).toneBoostMarginDb

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
    /** Perfil de uso elegido por el usuario (ver [UsageProfiles]). */
    val usageProfile: String = "",
    val language: String = "system",
    val theme: AppTheme = AppTheme(),
    /** Modo captura (experimental): captura el audio de otras apps y lo procesa con el motor nativo. */
    val captureMode: Boolean = false
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
