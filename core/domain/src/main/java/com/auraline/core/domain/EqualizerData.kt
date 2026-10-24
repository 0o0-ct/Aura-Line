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
