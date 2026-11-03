package com.auraline.core.domain

/**
 * Preajustes de fábrica construidos con las tablas de ecualización por instrumento
 * (electrofante.com) y la tabla de frecuencias de Cetear.
 * Cada entrada se aplica a la banda más cercana, sea cual sea el número de bandas.
 */
data class FactoryPreset(
    val name: String,
    val hint: String,
    val gains: Map<Float, Float>
)

val FactoryPresets: List<FactoryPreset> = listOf(
    FactoryPreset(
        name = "Plano",
        hint = "Sin procesado",
        gains = emptyMap()
    ),
    FactoryPreset(
        name = "Cálido y Profundo",
        hint = "Sub-bajo masivo, graves profundos, medios ricos y agudos suaves",
        gains = mapOf(60f to 7f, 120f to 6f, 250f to 4f, 500f to 3f, 1000f to 2f, 2500f to 2f, 6000f to 0f, 10000f to 0f)
    ),
    FactoryPreset(
        name = "Voz",
        hint = "Cuerpo cálido 200-400 · presencia natural sin estridencias",
        gains = mapOf(60f to 2f, 120f to 2f, 250f to 4f, 500f to 3f, 1000f to 2f, 2500f to 3f, 5000f to 2f)
    ),
    FactoryPreset(
        name = "Bombo",
        hint = "Profundidad 60-80 · pegada cálida sin chillidos",
        gains = mapOf(60f to 6f, 140f to 5f, 300f to 2f, 1000f to 1f, 3000f to 2f)
    ),
    FactoryPreset(
        name = "Bajo eléctrico",
        hint = "Cuerpo masivo 60-120 · gruñido rico en medios",
        gains = mapOf(60f to 6f, 140f to 6f, 300f to 3f, 800f to 4f, 1500f to 2f)
    ),
    FactoryPreset(
        name = "Guitarra eléctrica",
        hint = "Cuerpo cálido y presencia balanceada",
        gains = mapOf(100f to 3f, 250f to 4f, 800f to 2f, 2500f to 3f, 5000f to 1f)
    ),
    FactoryPreset(
        name = "Guitarra acústica",
        hint = "Graves redondos, madera y claridad natural",
        gains = mapOf(100f to 4f, 250f to 3f, 1000f to 2f, 3000f to 3f, 6000f to 1f)
    ),
    FactoryPreset(
        name = "Piano",
        hint = "Graves envolventes y medios ricos en armónicos",
        gains = mapOf(80f to 5f, 200f to 3f, 600f to 2f, 1500f to 2f, 4000f to 2f)
    ),
    FactoryPreset(
        name = "Graves +",
        hint = "Refuerzo total de sub-bajo y graves profundos con cuerpo denso",
        gains = mapOf(60f to 9f, 120f to 7f, 250f to 5f, 500f to 3f, 1000f to 1f)
    ),
    FactoryPreset(
        name = "Harman",
        hint = "Curva equilibrada: graves cálidos, medios presentes y agudos musicales",
        gains = mapOf(60f to 5f, 120f to 4f, 250f to 3f, 500f to 2f, 1000f to 2f, 2500f to 2f, 6000f to 1f)
    ),
    FactoryPreset(
        name = "JBL",
        hint = "Sonido envolvente: graves firmes y medios cálidos sin estridencias",
        gains = mapOf(60f to 6f, 120f to 5f, 300f to 3f, 1000f to 2f, 3000f to 3f, 7000f to 1f)
    )
)

/** Referencia rápida de qué hace cada zona del espectro (tabla de electrofante.com). */
data class FrequencyGuide(
    val label: String,
    val uses: String
)

val FrequencyGuides: List<FrequencyGuide> = listOf(
    FrequencyGuide("50 Hz", "Sube para dar profundidad masiva al sub-bajo, bombo y bajo."),
    FrequencyGuide("100 Hz", "Graves contundentes, cuerpo de guitarras y calidez general."),
    FrequencyGuide("200 Hz", "Relleno y cuerpo en voces y baja frecuencia de instrumentos."),
    FrequencyGuide("400 Hz", "Cuerpo y plenitud de medios-bajos. Evita sonido hueco."),
    FrequencyGuide("800 Hz", "Claridad y presencia cálida en bajos y guitarras."),
    FrequencyGuide("1,5 kHz", "Cuerpo y definición en medios."),
    FrequencyGuide("3 kHz", "Presencia vocal y claridad de instrumentos sin fatiga."),
    FrequencyGuide("5 kHz", "Brillo natural y definición vocal limpia."),
    FrequencyGuide("7 kHz", "Definición y presencia de platillos sin chillidos molestos."),
    FrequencyGuide("10 kHz", "Aire y apertura suave."),
    FrequencyGuide("15 kHz", "Extensión de brillo superior.")
)

/** Preajustes de sala para la reverb (los mismos espacios del ecualizador original). */
data class ReverbPreset(
    val name: String,
    val params: ReverbParams
)

val ReverbPresets: List<ReverbPreset> = listOf(
    ReverbPreset("Ligera reverberación", ReverbParams(damping = 0.7f, filter = 0.4f, decay = 0.2f, preDelay = 0.01f, predelayMix = 0.2f, size = 0.2f, mix = 0.15f)),
    ReverbPreset("Sala pequeña", ReverbParams(damping = 0.6f, filter = 0.35f, decay = 0.3f, preDelay = 0.02f, predelayMix = 0.25f, size = 0.3f, mix = 0.2f)),
    ReverbPreset("Estudio", ReverbParams(damping = 0.5f, filter = 0.5f, decay = 0.35f, preDelay = 0.02f, predelayMix = 0.3f, size = 0.35f, mix = 0.25f)),
    ReverbPreset("Auditorio", ReverbParams(damping = 0.35f, filter = 0.7f, decay = 0.75f, preDelay = 0.06f, predelayMix = 0.55f, size = 0.8f, mix = 0.4f)),
    ReverbPreset("Escenario", ReverbParams(damping = 0.4f, filter = 0.6f, decay = 0.6f, preDelay = 0.05f, predelayMix = 0.5f, size = 0.7f, mix = 0.35f)),
    ReverbPreset("Gran salón", ReverbParams(damping = 0.35f, filter = 0.65f, decay = 0.8f, preDelay = 0.07f, predelayMix = 0.6f, size = 0.85f, mix = 0.4f)),
    ReverbPreset("Estadio", ReverbParams(damping = 0.2f, filter = 0.8f, decay = 0.95f, preDelay = 0.12f, predelayMix = 0.7f, size = 0.95f, mix = 0.45f)),
    ReverbPreset("Eco", ReverbParams(damping = 0.1f, filter = 0.2f, decay = 0.9f, preDelay = 0.15f, predelayMix = 0.8f, size = 0.6f, mix = 0.4f))
)

/**
 * Perfiles de uso: cada equipo necesita compromisos distintos de rango, margen y efectos.
 * Al elegir uno se ajustan de golpe el rango, la protección de nivel, los efectos y la curva base.
 */
data class UsageProfile(
    val key: String,
    val name: String,
    val description: String,
    val gainRangeDb: Float,
    val levelProtection: Boolean,
    val effectsEnabled: Boolean,
    val basePreset: String,
    /** Margen extra de realce aplicado a Graves y Sub-bajo en la pantalla Tono. */
    val toneBoostMarginDb: Float = 12f,
    /** Corte inicial del paso bajo del sub-bajo al elegir este perfil (Hz). */
    val subCutoffHz: Float = 60f,
    val modules: ModuleState = ModuleState()
)

val UsageProfiles: List<UsageProfile> = listOf(
    UsageProfile(
        key = "mobile",
        name = "Altavoz del móvil",
        description = "Graves ricos y medios cálidos optimizados para altavoces integrados.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Cálido y Profundo",
        // El altavoz interno corta por debajo de ~150-200 Hz: se realza la banda que sí
        // reproduce (graves altos) y el sub se centra en 70-100 Hz para que algo se note.
        toneBoostMarginDb = 18f,
        subCutoffHz = 90f
    ),
    UsageProfile(
        key = "wired",
        name = "Auriculares con cable",
        description = "Respuesta completa con sub-bajo masivo y medios nítidos.",
        gainRangeDb = 12f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "Harman",
        toneBoostMarginDb = 18f,
        subCutoffHz = 45f
    ),
    UsageProfile(
        key = "bluetooth",
        name = "Auriculares Bluetooth",
        description = "Sonido envolvente con graves firmes y agudos limpios.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "JBL",
        toneBoostMarginDb = 15f,
        subCutoffHz = 50f
    ),
    UsageProfile(
        key = "external",
        name = "Altavoces externos",
        description = "Cuerpo completo con graves profundos y medios presentes.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Cálido y Profundo",
        toneBoostMarginDb = 24f,
        subCutoffHz = 50f
    ),
    UsageProfile(
        key = "surround",
        name = "Barra o sistema 5.1",
        description = "Rango equilibrado y sonido cinematográfico cálido.",
        gainRangeDb = 6f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "JBL",
        toneBoostMarginDb = 12f,
        // Con .1 el subwoofer hace todo el trabajo por debajo de 80 Hz (corte estándar LFE).
        subCutoffHz = 80f
    )
)

/** Perfil elegido por el usuario; [UsageProfiles.first] si no ha seleccionado ninguno. */
fun usageProfileByKey(key: String): UsageProfile =
    UsageProfiles.firstOrNull { it.key == key } ?: UsageProfiles.first()
