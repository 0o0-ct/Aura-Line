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
        name = "Voz",
        hint = "Cuerpo 200-400 · presencia 4-5k · corta nasal y raspa",
        gains = mapOf(60f to -6f, 250f to 3f, 400f to 2f, 1000f to -2f, 2000f to -2f, 4500f to 4f, 8000f to 2f)
    ),
    FactoryPreset(
        name = "Bombo",
        hint = "Profundidad 60-80 · ataque 2,5-5k · sin cartón 300-600",
        gains = mapOf(60f to 5f, 140f to 4f, 450f to -4f, 650f to -3f, 2500f to 4f, 5000f to 3f)
    ),
    FactoryPreset(
        name = "Bajo eléctrico",
        hint = "Cuerpo 60-80 · gruñido 700-1100 · define notas",
        gains = mapOf(60f to 3f, 140f to 4f, 250f to -2f, 800f to 3f, 1300f to 3f, 3000f to 1f)
    ),
    FactoryPreset(
        name = "Guitarra eléctrica",
        hint = "Cuerpo 240 · ataque 2,5-3,5k · corta retumbe",
        gains = mapOf(140f to -3f, 250f to 3f, 1600f to -2f, 2500f to 4f, 4000f to 2f)
    ),
    FactoryPreset(
        name = "Guitarra acústica",
        hint = "Graves 80-120 · cuerpo 240 · claridad 2,5-5k",
        gains = mapOf(100f to 3f, 250f to 3f, 2500f to 3f, 4000f to 2f, 8000f to 1f)
    ),
    FactoryPreset(
        name = "Piano",
        hint = "Graves 80-120 · corta retumbe 300 y metálico 1-2k",
        gains = mapOf(100f to 3f, 350f to -3f, 1600f to -2f, 4500f to 3f, 8000f to 2f)
    ),
    FactoryPreset(
        name = "Platillos",
        hint = "Corta gong 200 y molestia 1k · brillo 5-10k",
        gains = mapOf(60f to -6f, 250f to -4f, 1000f to -3f, 5500f to 3f, 8000f to 3f, 12000f to 2f)
    ),
    FactoryPreset(
        name = "Metales y cuerdas",
        hint = "Cuerpo 120-240 · filo 7,5-10k",
        gains = mapOf(140f to -3f, 650f to -2f, 3000f to 3f, 10000f to 3f)
    ),
    FactoryPreset(
        name = "Graves +",
        hint = "Refuerzo fuerte de sub-bajo y graves, con medios retraídos",
        gains = mapOf(60f to 8f, 120f to 6f, 250f to 3f, 900f to -2f, 2500f to 1f, 6000f to 2f)
    ),
    FactoryPreset(
        name = "Harman",
        hint = "Curva de referencia: graves +4/+5 · medios −1/−2 · agudos +2/+3",
        gains = mapOf(60f to 5f, 120f to 4f, 250f to 2f, 1000f to -1f, 2000f to -2f, 4000f to 2f, 6000f to 3f, 9000f to 2f)
    ),
    FactoryPreset(
        name = "JBL",
        hint = "Sonido JBL: graves firmes, medios retraídos y agudos brillantes",
        gains = mapOf(60f to 6f, 120f to 4f, 500f to -2f, 1000f to -2f, 2500f to 2f, 5000f to 4f, 8000f to 3f)
    ),
    FactoryPreset(
        name = "Batería completa",
        hint = "Bombo y caja con cuerpo, brillo sin hojalata",
        gains = mapOf(80f to 3f, 240f to 2f, 450f to -3f, 3000f to 2f, 5000f to 3f, 9000f to 2f)
    )
)

/** Referencia rápida de qué hace cada zona del espectro (tabla de electrofante.com). */
data class FrequencyGuide(
    val label: String,
    val uses: String
)

val FrequencyGuides: List<FrequencyGuide> = listOf(
    FrequencyGuide("50 Hz", "Sube para saturar graves de bombo, toms o bajo. Baja para escuchar mejor los armónicos del bajo."),
    FrequencyGuide("100 Hz", "Graves más duros y relleno de guitarras y cajas. Baja para dar claridad a la guitarra."),
    FrequencyGuide("200 Hz", "Relleno en voces, guitarras y cajas. Baja para quitar sonido 'de hojalata' en platillos."),
    FrequencyGuide("400 Hz", "Claridad del bajo a bajo volumen. Baja para evitar bombos y toms acartonados."),
    FrequencyGuide("800 Hz", "Claridad y pegada del bajo. Baja para quitar hojalata en guitarras."),
    FrequencyGuide("1,5 kHz", "Pegada y claridad en bajos. Baja para eliminar el sonido turbio de guitarras."),
    FrequencyGuide("3 kHz", "Pegada en bajos, ataque de guitarras y voces claras. Baja en coros para suavizar."),
    FrequencyGuide("5 kHz", "Presencia en voces y ataque de bombo y toms. Baja para alejar los fondos."),
    FrequencyGuide("7 kHz", "Ataque metálico de percusión. Baja para atenuar sibilancias."),
    FrequencyGuide("10 kHz", "Voces y guitarras brillantes. Baja para atenuar sibilantes."),
    FrequencyGuide("15 kHz", "Aire y brillo en voces, platillos y cuerdas.")
)

/** Preajustes de sala para la reverb (los mismos espacios del ecualizador original). */
data class ReverbPreset(
    val name: String,
    val params: ReverbParams
)

val ReverbPresets: List<ReverbPreset> = listOf(
    ReverbPreset("Ligera reverberación", ReverbParams(damping = 0.7f, filter = 0.4f, decay = 0.2f, preDelay = 0.01f, predelayMix = 0.2f, size = 0.2f, mix = 0.2f)),
    ReverbPreset("Sala pequeña", ReverbParams(damping = 0.6f, filter = 0.35f, decay = 0.3f, preDelay = 0.02f, predelayMix = 0.25f, size = 0.3f, mix = 0.3f)),
    ReverbPreset("Estudio", ReverbParams(damping = 0.5f, filter = 0.5f, decay = 0.35f, preDelay = 0.02f, predelayMix = 0.3f, size = 0.35f, mix = 0.35f)),
    ReverbPreset("Auditorio", ReverbParams(damping = 0.35f, filter = 0.7f, decay = 0.75f, preDelay = 0.06f, predelayMix = 0.55f, size = 0.8f, mix = 0.55f)),
    ReverbPreset("Escenario", ReverbParams(damping = 0.4f, filter = 0.6f, decay = 0.6f, preDelay = 0.05f, predelayMix = 0.5f, size = 0.7f, mix = 0.5f)),
    ReverbPreset("Gran salón", ReverbParams(damping = 0.35f, filter = 0.65f, decay = 0.8f, preDelay = 0.07f, predelayMix = 0.6f, size = 0.85f, mix = 0.6f)),
    ReverbPreset("Estadio", ReverbParams(damping = 0.2f, filter = 0.8f, decay = 0.95f, preDelay = 0.12f, predelayMix = 0.7f, size = 0.95f, mix = 0.7f)),
    ReverbPreset("Eco", ReverbParams(damping = 0.1f, filter = 0.2f, decay = 0.9f, preDelay = 0.15f, predelayMix = 0.8f, size = 0.6f, mix = 0.75f))
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
    val modules: ModuleState = ModuleState()
)

val UsageProfiles: List<UsageProfile> = listOf(
    UsageProfile(
        key = "mobile",
        name = "Altavoz del móvil",
        description = "Graves limitados por el propio altavoz. Protección activa y efectos apagados.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Plano"
    ),
    UsageProfile(
        key = "wired",
        name = "Auriculares con cable",
        description = "Sin códec de por medio: margen completo y curva de referencia Harman.",
        gainRangeDb = 12f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "Harman"
    ),
    UsageProfile(
        key = "bluetooth",
        name = "Auriculares Bluetooth",
        description = "El códec recorta: protección activa y curva Harman suavizada.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Harman"
    ),
    UsageProfile(
        key = "external",
        name = "Altavoces externos",
        description = "Equipo con amplificador propio. Curva plana y margen moderado.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Plano"
    ),
    UsageProfile(
        key = "surround",
        name = "Barra o sistema 5.1",
        description = "El equipo aplica su propio procesado: rango ±6 dB y protección desactivada.",
        gainRangeDb = 6f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "Plano"
    )
)
