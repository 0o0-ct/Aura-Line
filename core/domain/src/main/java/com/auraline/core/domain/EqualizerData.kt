package com.auraline.core.domain

enum class PresetCategory(val label: String) {
    GENERAL("General"),
    GENRES("Géneros musicales"),
    INSTRUMENTS("Instrumentos"),
    VOCAL("Voz y contenido"),
    LISTENING("Escucha y reproducción"),
    STUDIO("Estudio y mezcla")
}

/**
 * Preajustes de fábrica construidos con las tablas de ecualización por instrumento
 * y la tabla de frecuencias acústicas.
 * Cada entrada se aplica a la banda más cercana, sea cual sea el número de bandas.
 */
data class FactoryPreset(
    val name: String,
    val hint: String,
    val gains: Map<Float, Float>,
    val category: PresetCategory = PresetCategory.GENERAL,
    val description: String = hint,
    val recommendedUse: String = "Escucha general",
    val contentType: String = "Música / Audio general",
    val tonalCurveSummary: String = "Curva equilibrada",
    val preampDb: Float = 0f,
    val headroomWarning: String? = null
)

val FactoryPresets: List<FactoryPreset> = listOf(
    // A. GENERAL
    FactoryPreset(
        name = "Plano",
        hint = "Sin procesado (Respuesta plana 0 dB)",
        gains = emptyMap(),
        category = PresetCategory.GENERAL,
        description = "Respuesta en frecuencia completamente plana sin alteración tímbrica.",
        recommendedUse = "Referencia auditiva neutra, pruebas de equipamiento y mezcla plana.",
        contentType = "Todo tipo de contenido",
        tonalCurveSummary = "0 dB en todas las bandas"
    ),
    FactoryPreset(
        name = "Sonido equilibrado",
        hint = "Balance armónico natural para uso diario",
        gains = mapOf(60f to 2f, 120f to 2f, 250f to 1f, 1000f to 0f, 3000f to 1.5f, 8000f to 2f, 12000f to 2f),
        category = PresetCategory.GENERAL,
        description = "Ligero realce en extremos del espectro para compensar la respuesta perceptiva a bajo volumen.",
        recommendedUse = "Uso cotidiano en altavoces y auriculares equilibrados.",
        contentType = "Música variada y reproducción diaria",
        tonalCurveSummary = "Graves +2 dB, medios neutros, agudos +2 dB"
    ),
    FactoryPreset(
        name = "Natural",
        hint = "Tono orgánico sin coloración artificial",
        gains = mapOf(60f to 1.5f, 120f to 1.5f, 500f to 0.5f, 2000f to 1f, 6000f to 1.5f, 12000f to 1f),
        category = PresetCategory.GENERAL,
        description = "Ajuste sutil para preservar la textura acústica de los instrumentos.",
        recommendedUse = "Grabaciones en vivo, música acústica y audiófilos.",
        contentType = "Acústica, Clásica, Jazz",
        tonalCurveSummary = "Realce sutil +1.5 dB en graves y agudos suaves"
    ),
    FactoryPreset(
        name = "Cálido",
        hint = "Graves envolventes, medios ricos y agudos suaves",
        gains = mapOf(60f to 5f, 120f to 4.5f, 250f to 3f, 500f to 2f, 1000f to 1f, 3000f to 0f, 8000f to -1.5f, 12000f to -2.5f),
        category = PresetCategory.GENERAL,
        description = "Realza la zona baja y baja-media para eliminar la estridencia y dar calidez analógica.",
        recommendedUse = "Escucha prolongada para evitar fatiga auditiva.",
        contentType = "Rock clásico, Vinilos, Jazz",
        tonalCurveSummary = "Graves +5 dB, agudos suavizados -2.5 dB"
    ),
    FactoryPreset(
        name = "Brillante",
        hint = "Agudos definidos, presencia cristalina y alta claridad",
        gains = mapOf(60f to -1f, 250f to 0f, 1000f to 1.5f, 3000f to 3.5f, 7000f to 5f, 12000f to 6f),
        category = PresetCategory.GENERAL,
        description = "Aumenta la claridad de armónicos superiores y la presencia frontal.",
        recommendedUse = "Auriculares oscuros o altavoces con agudos deficientes.",
        contentType = "Pop, Electrónica detallada",
        tonalCurveSummary = "Presencia +3.5 dB, agudos +6 dB"
    ),
    FactoryPreset(
        name = "Detallado",
        hint = "Máxima separación instrumental y nitidez micro-acústica",
        gains = mapOf(60f to 2f, 120f to 1f, 500f to -1f, 2500f to 3f, 6000f to 4f, 12000f to 5f),
        category = PresetCategory.GENERAL,
        description = "Limpia la zona baja-media y resalta los medios-altos para extraer detalles sutiles.",
        recommendedUse = "Análisis crítico de pistas y escucha analítica.",
        contentType = "Acústico, Instrumental, Vocal",
        tonalCurveSummary = "Medios-bajos -1 dB, presencia +3 dB, aire +5 dB"
    ),

    // B. GÉNEROS MUSICALES
    FactoryPreset(
        name = "Pop",
        hint = "Vocales brillantes, graves rítmicos y agudos chispeantes",
        gains = mapOf(60f to 6f, 120f to 5f, 250f to 2f, 1000f to 1f, 3000f to 5f, 7000f to 6f, 12000f to 5f),
        category = PresetCategory.GENRES,
        description = "Curva en 'V' moderada con énfasis en la voz principal y el ritmo.",
        recommendedUse = "Éxitos pop modernos y música comercial.",
        contentType = "Pop internacional y latino",
        tonalCurveSummary = "Graves +6 dB, voz +5 dB, agudos +6 dB"
    ),
    FactoryPreset(
        name = "Rock / Metal",
        hint = "Energía cruda: cuerpo en baja media y agudos afilados para guitarras",
        gains = mapOf(60f to 5f, 120f to 6f, 300f to 3f, 1000f to 4f, 3000f to 6f, 6000f to 5f, 10000f to 3f),
        category = PresetCategory.GENRES,
        description = "Resalta el ataque de las guitarras eléctricas y el cuerpo de la batería.",
        recommendedUse = "Rock alternativo, hard rock y heavy metal.",
        contentType = "Guitarras distorsionadas y batería potente",
        tonalCurveSummary = "Graves +6 dB, medios 1 kHz +4 dB, ataque +6 dB"
    ),
    FactoryPreset(
        name = "Heavy Metal",
        hint = "Guitarras contundentes, doble bombo rápido y ataque rasgado",
        gains = mapOf(60f to 6f, 120f to 7f, 250f to 2f, 800f to -2f, 2500f to 5f, 5000f to 6f, 10000f to 4f),
        category = PresetCategory.GENRES,
        description = "Scoop sutil en medios para dar espacio a la distorsión y al bombo veloz.",
        recommendedUse = "Thrash metal, death metal y rock pesado.",
        contentType = "Metal extremo y metal progresivo",
        tonalCurveSummary = "Graves +7 dB, medios -2 dB, agudos +6 dB"
    ),
    FactoryPreset(
        name = "Hip-Hop / Rap",
        hint = "Graves masivos y presencia vocal nítida",
        gains = mapOf(60f to 9f, 120f to 7f, 250f to 4f, 1000f to 2f, 3000f to 4f, 6000f to 3f, 12000f to 2f),
        category = PresetCategory.GENRES,
        description = "Potente refuerzo de sub-bajo manteniendo la voz del MC clara e inteligible.",
        recommendedUse = "Hip-hop clásico, rap y trap.",
        contentType = "Bases pesadas y vocal rap",
        tonalCurveSummary = "Sub-bajo +9 dB, voces +4 dB"
    ),
    FactoryPreset(
        name = "Trap",
        hint = "Sub-bajo vibrante de 808 y charles hi-hats cristalinos",
        gains = mapOf(40f to 10f, 80f to 8f, 200f to 2f, 1000f to 0f, 4000f to 4f, 8000f to 7f, 14000f to 8f),
        category = PresetCategory.GENRES,
        description = "Máxima extensión en los 40 Hz de sub-bajo y agudos afilados para ritmos rápidos.",
        recommendedUse = "Trap, Drill y Phonk.",
        contentType = "Sub-bass sintetizado y hi-hats rápidos",
        tonalCurveSummary = "Sub +10 dB, agudos superiores +8 dB"
    ),
    FactoryPreset(
        name = "EDM",
        hint = "Sub-bajo retumbante y agudos sintetizados de club",
        gains = mapOf(60f to 9f, 120f to 7f, 250f to 2f, 1000f to 0f, 3000f to 4f, 6000f to 7f, 12000f to 6f),
        category = PresetCategory.GENRES,
        description = "Diseñado para el impacto rítmico en sintetizadores de fiesta y festivales.",
        recommendedUse = "Electrónica bailable, Progressive House, Trance.",
        contentType = "Música electrónica de baile",
        tonalCurveSummary = "Graves +9 dB, brillo sintetizado +7 dB"
    ),
    FactoryPreset(
        name = "House",
        hint = "Bombo 4x4 profundo y groove rítmico envolvente",
        gains = mapOf(50f to 8f, 100f to 7f, 250f to 3f, 1000f to 1f, 3000f to 3f, 7000f to 5f, 12000f to 4f),
        category = PresetCategory.GENRES,
        description = "Graves profundos en 50-100 Hz con calidez en la sección rítmica.",
        recommendedUse = "Deep House, Tech House y Funky House.",
        contentType = "House y música club",
        tonalCurveSummary = "Bombo +8 dB, hi-hats +5 dB"
    ),
    FactoryPreset(
        name = "Techno",
        hint = "Bombo retumbante de baja frecuencia y sintetizadores industriales",
        gains = mapOf(45f to 9f, 90f to 7f, 200f to 1f, 800f to 2f, 2500f to 4f, 6000f to 6f, 11000f to 5f),
        category = PresetCategory.GENRES,
        description = "Énfasis en el bombo sub-oscilante y texturas metálicas pesadas.",
        recommendedUse = "Peak Time Techno, Industrial Techno y Minimal.",
        contentType = "Techno y sonidos sintéticos oscuros",
        tonalCurveSummary = "Sub-bomba +9 dB, percusión metálica +6 dB"
    ),
    FactoryPreset(
        name = "Reggaetón",
        hint = "Dembow contundente y voces frontales con gran presencia",
        gains = mapOf(60f to 8f, 120f to 7f, 250f to 3f, 1000f to 2f, 3000f to 5f, 7000f to 4f, 12000f to 3f),
        category = PresetCategory.GENRES,
        description = "Ajustado para resaltar la base de dembow rítmica y la presencia vocal latina.",
        recommendedUse = "Reggaetón urbano, Dancehall y Latin Pop.",
        contentType = "Ritmo urbano y vocal",
        tonalCurveSummary = "Dembow +8 dB, voces +5 dB"
    ),
    FactoryPreset(
        name = "Salsa",
        hint = "Campana y conga articuladas con bajo caminante claro",
        gains = mapOf(60f to 5f, 140f to 6f, 300f to 3f, 1000f to 3f, 3500f to 5f, 7000f to 6f, 12000f to 4f),
        category = PresetCategory.GENRES,
        description = "Resalta la timbaleta, conga, campana y sección de metales sin ahogar el bajo.",
        recommendedUse = "Salsa brava, Salsa romántica y Son montuno.",
        contentType = "Percusión latina y sección de vientos",
        tonalCurveSummary = "Percusión +6 dB, metales +5 dB"
    ),
    FactoryPreset(
        name = "Bachata",
        hint = "Guitarra requinto brillante y bajo suave y marcado",
        gains = mapOf(60f to 6f, 120f to 5f, 300f to 2f, 1200f to 3f, 3500f to 5f, 8000f to 6f, 12000f to 4f),
        category = PresetCategory.GENRES,
        description = "Brillo especial para el requinto metálico y graves redondos para el bongo.",
        recommendedUse = "Bachata tradicional y moderna.",
        contentType = "Requinto metálico y bongo",
        tonalCurveSummary = "Requinto +6 dB, bajo +6 dB"
    ),
    FactoryPreset(
        name = "Jazz",
        hint = "Contrabajo cálido, piano rico y agudos de platillos sedosos",
        gains = mapOf(60f to 5f, 120f to 4f, 250f to 3f, 1000f to 2f, 3000f to 3f, 6000f to 4f, 12000f to 3f),
        category = PresetCategory.GENRES,
        description = "Respuesta cálida y orgánica manteniendo el grano natural de las escobillas y el contrabajo.",
        recommendedUse = "Jazz trío, Bebop y Smooth Jazz.",
        contentType = "Instrumental orgánico y acústico",
        tonalCurveSummary = "Contrabajo +5 dB, escobillas +4 dB"
    ),
    FactoryPreset(
        name = "Blues",
        hint = "Guitarra expressiva con cuerpo en medios y bajo caminante cálido",
        gains = mapOf(60f to 4f, 120f to 5f, 300f to 4f, 1000f to 3f, 2500f to 4f, 6000f to 3f, 10000f to 2f),
        category = PresetCategory.GENRES,
        description = "Aporta la calidez de válvulas a la guitarra eléctrica y a la armónica.",
        recommendedUse = "Electric Blues, Chicago Blues y Acoustic Blues.",
        contentType = "Guitarra blues y voz",
        tonalCurveSummary = "Cuerpo +5 dB, medios expresivos +4 dB"
    ),
    FactoryPreset(
        name = "Clásica / Orquesta",
        hint = "Rango dinámico amplio, aire superior y naturalidad acústica",
        gains = mapOf(60f to 4f, 120f to 3f, 500f to 0f, 2000f to 3f, 5000f to 5f, 10000f to 6f),
        category = PresetCategory.GENRES,
        description = "Preserva la profundidad escénica, el timbre de las violas y el aire de la sala.",
        recommendedUse = "Música sinfónica, de cámara y ópera.",
        contentType = "Orquesta filarmónica y acústica",
        tonalCurveSummary = "Aire sinfónico +6 dB, medios limpios"
    ),
    FactoryPreset(
        name = "Música acústica",
        hint = "Guitarras de madera, voces íntimas y calidez acústica",
        gains = mapOf(80f to 4f, 200f to 3.5f, 600f to 2f, 2000f to 3f, 5000f to 4f, 10000f to 3f),
        category = PresetCategory.GENRES,
        description = "Cuerpo orgánico sin coloración agresiva para instrumentos de cuerda y voz.",
        recommendedUse = "Folk, Singer-songwriter y acústico desenchufado.",
        contentType = "Guitarra acústica y voz",
        tonalCurveSummary = "Madera +3.5 dB, nitidez +4 dB"
    ),
    FactoryPreset(
        name = "Electrónica / Ambient",
        hint = "Atmósferas profundas, sintetizadores envolventes y espacialidad",
        gains = mapOf(40f to 7f, 100f to 5f, 400f to 1f, 1500f to 2f, 4000f to 5f, 9000f to 7f, 15000f to 8f),
        category = PresetCategory.GENRES,
        description = "Resalta los sub-graves de paisaje sonoro y el brillo espacial superior.",
        recommendedUse = "Ambient, Downtempo y Synthwave.",
        contentType = "Sintetizadores atmosféricos y paisaje sonoro",
        tonalCurveSummary = "Sub +7 dB, brillo ambiental +8 dB"
    ),

    // C. INSTRUMENTOS
    FactoryPreset(
        name = "Bombo",
        hint = "Profundidad 60-80 Hz y pegada cálida de maza",
        gains = mapOf(60f to 7f, 140f to 6f, 300f to -2f, 1000f to 1f, 3000f to 4f),
        category = PresetCategory.INSTRUMENTS,
        description = "Realza el peso en 60 Hz y el 'click' de la maza en 3 kHz, limpiando el sonido fofo en 300 Hz.",
        recommendedUse = "Optimizar pistas o grabaciones con bombo apagado.",
        contentType = "Batería y percusión",
        tonalCurveSummary = "Peso +7 dB, pegada +4 dB, receso en 300 Hz"
    ),
    FactoryPreset(
        name = "Bajo eléctrico",
        hint = "Cuerpo masivo 60-120 Hz y gruñido rico en medios",
        gains = mapOf(60f to 7f, 140f to 7f, 300f to 4f, 800f to 5f, 1500f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Resalta la fundamental de las cuerdas y la articulación del traste en medios.",
        recommendedUse = "Realzar el bajo eléctrico en cualquier mezcla.",
        contentType = "Bajo de 4 y 5 cuerdas",
        tonalCurveSummary = "Fundamental +7 dB, gruñido 800 Hz +5 dB"
    ),
    FactoryPreset(
        name = "Sub-bajo",
        hint = "Refuerzo puro de infrasonidos (20-60 Hz)",
        gains = mapOf(30f to 9f, 50f to 8f, 100f to 3f, 250f to 0f),
        category = PresetCategory.INSTRUMENTS,
        description = "Enfocado exclusivamente en la energía sub-baja sin ensuciar los medios.",
        recommendedUse = "Optimización de sintetizadores sub y subwoofers.",
        contentType = "Sub-bass sintético",
        tonalCurveSummary = "Subgraves puros +9 dB"
    ),
    FactoryPreset(
        name = "Contrabajo",
        hint = "Resonancia profunda de madera de caja acoplada",
        gains = mapOf(40f to 6f, 100f to 5f, 250f to -2f, 600f to 3f, 2500f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Aporta peso acústico y el ataque de los dedos en el diapasón.",
        recommendedUse = "Contrabajo de jazz y clásico.",
        contentType = "Cuerda frotada y pulsada grave",
        tonalCurveSummary = "Cuerpo +6 dB, ataque de dedo +3 dB"
    ),
    FactoryPreset(
        name = "Guitarra eléctrica",
        hint = "Cuerpo cálido y presencia balanceada de amplificador",
        gains = mapOf(100f to 3f, 250f to 4f, 800f to 3f, 2500f to 5f, 5000f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Realza el rango dinámico de las pastillas y el rugido del cono del altavoz.",
        recommendedUse = "Guitarras limpias, crujientes o distorsionadas.",
        contentType = "Guitarra eléctrica",
        tonalCurveSummary = "Cuerpo +4 dB, presencia +5 dB"
    ),
    FactoryPreset(
        name = "Guitarra acústica",
        hint = "Graves redondos, resonancia de madera y claridad de cuerdas",
        gains = mapOf(100f to 5f, 250f to 4f, 1000f to 2f, 3000f to 4f, 6000f to 5f, 12000f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Combina la calidez de la caja de madera con el rasgueo metálico limpio.",
        recommendedUse = "Guitarras españolas, criollas y acústicas de acero.",
        contentType = "Instrumentos de cuerda acústicos",
        tonalCurveSummary = "Madera +5 dB, rasgueo +5 dB"
    ),
    FactoryPreset(
        name = "Piano",
        hint = "Graves envolventes y medios ricos en armónicos de cola",
        gains = mapOf(80f to 6f, 200f to 4f, 600f to 2f, 1500f to 3f, 4000f to 4f, 10000f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Resalta el cuerpo de la tabla armónica y la percusión del martillo en los agudos.",
        recommendedUse = "Pianos acústicos de cola y verticales.",
        contentType = "Teclados y piano",
        tonalCurveSummary = "Tabla de piano +6 dB, martillo +4 dB"
    ),
    FactoryPreset(
        name = "Teclado / Sintetizador",
        hint = "Sintetizadores polifónicos con cuerpo y brillo espacial",
        gains = mapOf(60f to 5f, 200f to 2f, 1000f to 3f, 3500f to 4f, 8000f to 6f),
        category = PresetCategory.INSTRUMENTS,
        description = "Resalta las ondas de sintetizador, filtros dinámicos y modulaciones stereo.",
        recommendedUse = "Teclados digitales, pads y leads.",
        contentType = "Sintetizador y pianos eléctricos",
        tonalCurveSummary = "Cuerpo +5 dB, modulación brillante +6 dB"
    ),
    FactoryPreset(
        name = "Batería",
        hint = "Bombo potente, caja seca y platillos cristalinos",
        gains = mapOf(60f to 7f, 140f to 5f, 400f to -2f, 3000f to 5f, 7000f to 6f, 12000f to 5f),
        category = PresetCategory.INSTRUMENTS,
        description = "Ecualización global para el set completo de batería acústica.",
        recommendedUse = "Pistas de batería y percusión completa.",
        contentType = "Batería acústica",
        tonalCurveSummary = "Bombo +7 dB, caja y platillos +6 dB"
    ),
    FactoryPreset(
        name = "Percusión",
        hint = "Congas, bongós y timbales con ataque seco y presencia",
        gains = mapOf(80f to 4f, 200f to 5f, 800f to 3f, 3000f to 5f, 7000f to 6f),
        category = PresetCategory.INSTRUMENTS,
        description = "Resalta la pal palmada del parche y la madera de la percusión afrolatina.",
        recommendedUse = "Congas, bongó, djembe y percusión de mano.",
        contentType = "Percusión acústica",
        tonalCurveSummary = "Parche +5 dB, chasquido +6 dB"
    ),
    FactoryPreset(
        name = "Metales",
        hint = "Trompetas y trombones con brillo brillante sin estridencia",
        gains = mapOf(120f to 4f, 400f to 2f, 1200f to 4f, 3500f to 6f, 8000f to 4f),
        category = PresetCategory.INSTRUMENTS,
        description = "Resalta la fuerza del metal y la proyección de armónicos.",
        recommendedUse = "Secciones de vientos y solistas de trompeta.",
        contentType = "Metales y brass",
        tonalCurveSummary = "Proyección metálica +6 dB"
    ),
    FactoryPreset(
        name = "Cuerdas",
        hint = "Violines y violonchelos con calidez de madera y seda",
        gains = mapOf(100f to 4f, 300f to 3f, 1000f to 2f, 4000f to 4f, 10000f to 6f),
        category = PresetCategory.INSTRUMENTS,
        description = "Frotado suave del arco y resonancia armónica de la sección de cuerdas.",
        recommendedUse = "Cuartetos de cuerda y secciones orquestales.",
        contentType = "Violín, viola, violonchelo",
        tonalCurveSummary = "Madera +4 dB, seda agudo +6 dB"
    ),
    FactoryPreset(
        name = "Voz principal",
        hint = "Claridad vocal al frente con calidez y nitidez de silabeo",
        gains = mapOf(80f to -2f, 160f to 1f, 1000f to 3f, 3000f to 6f, 7000f to 4f, 12000f to 3f),
        category = PresetCategory.INSTRUMENTS,
        description = "Corta el retumbe por debajo de 100 Hz y resalta la zona de 3 kHz para articular la voz.",
        recommendedUse = "Resaltar cantantes principales en cualquier canción.",
        contentType = "Voz solista",
        tonalCurveSummary = "Retumbe -2 dB, presencia vocal +6 dB"
    ),
    FactoryPreset(
        name = "Coros",
        hint = "Vocal múltiple envolvente con separación de frecuencias",
        gains = mapOf(120f to 2f, 400f to 3f, 1500f to 2f, 4000f to 5f, 9000f to 5f),
        category = PresetCategory.INSTRUMENTS,
        description = "Aporta apertura espacial y brillo coral sin competir con la voz solista.",
        recommendedUse = "Acompañamientos vocales y coros.",
        contentType = "Voces secundarias y coros",
        tonalCurveSummary = "Cuerpo coral +3 dB, presencia +5 dB"
    ),

    // D. VOZ Y CONTENIDO
    FactoryPreset(
        name = "Voz clara",
        hint = "Inteligibilidad máxima para conversaciones y llamadas",
        gains = mapOf(80f to -4f, 160f to -2f, 500f to 1f, 1500f to 4f, 3500f to 6f, 7000f to 3f),
        category = PresetCategory.VOCAL,
        description = "Elimina frecuencias graves retumbantes y eleva la zona de consonantes para máxima claridad.",
        recommendedUse = "Llamadas de voz, clases online y reuniones.",
        contentType = "Voz hablada y reuniones",
        tonalCurveSummary = "Graves -4 dB, inteligibilidad +6 dB"
    ),
    FactoryPreset(
        name = "Podcast / Voz",
        hint = "Sonido de estudio de radio: cuerpo cálido y articulación limpia",
        gains = mapOf(60f to -4f, 120f to -3f, 250f to 1f, 500f to 2f, 1000f to 4f, 3000f to 6f, 6000f to 3f),
        category = PresetCategory.VOCAL,
        description = "Calibración estándar de locución de radio comercial.",
        recommendedUse = "Escucha de podcasts y programas de radio.",
        contentType = "Podcasts y locución profesional",
        tonalCurveSummary = "Corte de retumbe, voz de radio +6 dB"
    ),
    FactoryPreset(
        name = "Audiolibros",
        hint = "Voz descansada sin picos agudos para horas de escucha",
        gains = mapOf(100f to -3f, 250f to 1f, 800f to 3f, 2000f to 3.5f, 5000f to 1f, 10000f to -2f),
        category = PresetCategory.VOCAL,
        description = "Suaviza las 'S' agudas y sostiene los medios cálidos para evitar el cansancio auditivo.",
        recommendedUse = "Escucha continua de historias y libros leídos.",
        contentType = "Audiolibros y narración",
        tonalCurveSummary = "Medios cálidos +3.5 dB, agudos suavizados -2 dB"
    ),
    FactoryPreset(
        name = "Conversación",
        hint = "Optimizado para la comprensión rápida de la palabra",
        gains = mapOf(100f to -5f, 300f to 0f, 1000f to 4f, 2500f to 5f, 5000f to 2f),
        category = PresetCategory.VOCAL,
        description = "Filtro pasa-altos suave para enfocar el audio en el canal de la palabra.",
        recommendedUse = "Entrevistas, noticias y diálogos.",
        contentType = "Entrevistas y noticias",
        tonalCurveSummary = "Graves -5 dB, palabra +5 dB"
    ),
    FactoryPreset(
        name = "Voz profunda",
        hint = "Añade peso de pecho a locuciones y narradores",
        gains = mapOf(100f to 4f, 200f to 4.5f, 600f to 2f, 2000f to 3f, 5000f to 2f),
        category = PresetCategory.VOCAL,
        description = "Aumenta la resonancia del pecho del locutor dando sonoridad madura.",
        recommendedUse = "Locución de voz masculina o tonos profundos.",
        contentType = "Narración y podcast profundo",
        tonalCurveSummary = "Resonancia de pecho +4.5 dB"
    ),
    FactoryPreset(
        name = "Voz brillante",
        hint = "Brillo y aire para voces femeninas y agudas",
        gains = mapOf(100f to -2f, 500f to 1f, 2000f to 4f, 4000f to 6f, 9000f to 5f),
        category = PresetCategory.VOCAL,
        description = "Realza el timbre agudo y la articulación limpia de voces claras.",
        recommendedUse = "Cantantes femeninas y narradoras agudas.",
        contentType = "Voz femenina y agudos",
        tonalCurveSummary = "Articulación agudo +6 dB"
    ),

    // E. ESCUCHA Y REPRODUCCIÓN
    FactoryPreset(
        name = "Auriculares",
        hint = "Compensación in-ear: graves con impacto y espacialidad",
        gains = mapOf(60f to 6f, 120f to 4f, 250f to 1f, 1000f to 1f, 3000f to 4f, 8000f to 5f, 12000f to 4f),
        category = PresetCategory.LISTENING,
        description = "Compensa la falta de espacio físico en auriculares ajustando el impacto y brillo.",
        recommendedUse = "Auriculares intrauditivos (In-ear) y de diadema.",
        contentType = "Todo tipo de reproducción en auriculares",
        tonalCurveSummary = "Impacto +6 dB, espacialidad +5 dB"
    ),
    FactoryPreset(
        name = "Altavoces pequeños",
        hint = "Corte de sub-graves inútiles y refuerzo de medios-bajos",
        gains = mapOf(40f to -6f, 80f to -2f, 160f to 5f, 400f to 3f, 1200f to 2f, 4000f to 4f),
        category = PresetCategory.LISTENING,
        description = "Protege altavoces de móvil o portátiles del choque de subgraves y eleva los 160 Hz.",
        recommendedUse = "Altavoz del teléfono, altavoces Bluetooth pequeños.",
        contentType = "Audio en altavoces compactos",
        tonalCurveSummary = "Sub -6 dB (protección), armónicos +5 dB"
    ),
    FactoryPreset(
        name = "Altavoces grandes",
        hint = "Aprovechamiento de subwoofers y respuesta amplia de sala",
        gains = mapOf(40f to 6f, 80f to 5f, 200f to 2f, 1000f to 0f, 4000f to 3f, 10000f to 5f),
        category = PresetCategory.LISTENING,
        description = "Aprovecha los conos de gran tamaño para entregar subgraves profundos y aire amplio.",
        recommendedUse = "Equipos Hi-Fi de salón y monitores de estudio de campo medio.",
        contentType = "Música Hi-Fi y cine de casa",
        tonalCurveSummary = "Subwoofer +6 dB, aire +5 dB"
    ),
    FactoryPreset(
        name = "Reproducción portátil",
        hint = "Sonido penetrante para ambientes ruidosos exteriores",
        gains = mapOf(80f to 4f, 200f to 3f, 1000f to 3f, 3000f to 5f, 7000f to 6f),
        category = PresetCategory.LISTENING,
        description = "Eleva las frecuencias que cortan el ruido ambiente en exteriores.",
        recommendedUse = "Escucha en la calle, viajes o ambientes ruidosos.",
        contentType = "Música en exteriores",
        tonalCurveSummary = "Presencia penetrante +6 dB"
    ),
    FactoryPreset(
        name = "Escucha nocturna",
        hint = "Dinámica controlada sin picos ni graves molestos para vecinos",
        gains = mapOf(50f to -5f, 100f to -3f, 250f to 0f, 1000f to 2f, 3000f to 2f, 7000f to -2f),
        category = PresetCategory.LISTENING,
        description = "Reduce la energía retumbante de paredes y mantiene la voz comprensible a bajo volumen.",
        recommendedUse = "Reproducción tarde en la noche.",
        contentType = "Cine y música nocturna",
        tonalCurveSummary = "Retumbe de pared -5 dB, voz neutra"
    ),
    FactoryPreset(
        name = "Bajo moderado",
        hint = "Grave firme y presente sin tapar las frecuencias medias",
        gains = mapOf(60f to 4f, 120f to 3.5f, 250f to 1f, 1000f to 0f, 3000f to 2f),
        category = PresetCategory.LISTENING,
        description = "Proporciona peso rítmico controlado sin invadir el espectro vocal.",
        recommendedUse = "Usuarios que desean graves sin exageración.",
        contentType = "Cualquier género",
        tonalCurveSummary = "Graves moderados +4 dB"
    ),
    FactoryPreset(
        name = "Máximo detalle",
        hint = "Resolución micro-acústica para descubrir capas ocultas",
        gains = mapOf(60f to 2f, 250f to -1f, 1500f to 2.5f, 4000f to 4.5f, 10000f to 6f, 16000f to 7f),
        category = PresetCategory.LISTENING,
        description = "Saca a la superficie matices de reverberación, respiración y textura de instrumentos.",
        recommendedUse = "Escucha atenta con auriculares de alta resolución.",
        contentType = "Grabaciones de alta fidelidad",
        tonalCurveSummary = "Detalle armónico +7 dB"
    ),

    // F. ESTUDIO Y MEZCLA
    FactoryPreset(
        name = "Referencia plana",
        hint = "Curva de calibración neutra para verificación de mezcla",
        gains = emptyMap(),
        category = PresetCategory.STUDIO,
        description = "Línea base plana para comprobar el balance tonal verdadero.",
        recommendedUse = "Monitorización crítica de estudio y mezcla.",
        contentType = "Producción musical",
        tonalCurveSummary = "Respuesta plana de estudio 0 dB"
    ),
    FactoryPreset(
        name = "Monitorización",
        hint = "Claridad en medios y control estricto de resonancias",
        gains = mapOf(60f to 1f, 250f to -1.5f, 1000f to 1f, 3000f to 2.5f, 8000f to 2f),
        category = PresetCategory.STUDIO,
        description = "Limpia la zona de 'barro' entre 200 y 300 Hz para revelar problemas de mezcla.",
        recommendedUse = "Revisión de mezclas en proceso.",
        contentType = "Archivos multipista",
        tonalCurveSummary = "Limpieza de medios-bajos -1.5 dB, nitidez +2.5 dB"
    ),
    FactoryPreset(
        name = "Control de graves",
        hint = "Filtro de inspección para detectar retumbe y sobrecarga de sub",
        gains = mapOf(30f to -6f, 60f to -4f, 120f to -2f, 250f to 0f),
        category = PresetCategory.STUDIO,
        description = "Permite escuchar cómo suena la pista si se recortan los sub-graves excesivos.",
        recommendedUse = "Diagnóstico de problemas en frecuencias bajas.",
        contentType = "Control de mezcla",
        tonalCurveSummary = "Recorte de retumbe sub -6 dB"
    ),
    FactoryPreset(
        name = "Control de medios",
        hint = "Aislamiento de la banda central (300 Hz - 3 kHz)",
        gains = mapOf(60f to -4f, 120f to -3f, 500f to 3f, 1500f to 4f, 3000f to 3f, 8000f to -3f),
        category = PresetCategory.STUDIO,
        description = "Enfoca la atención auditiva en la zona donde compiten la mayoría de instrumentos.",
        recommendedUse = "Ajustar el balance entre voz, guitarras y caja.",
        contentType = "Mezcla de voces e instrumentos",
        tonalCurveSummary = "Enfoque en medios +4 dB"
    ),
    FactoryPreset(
        name = "Control de agudos",
        hint = "Inspección de sibilancias y aspereza en altas frecuencias",
        gains = mapOf(100f to 0f, 1000f to 0f, 4000f to 4f, 8000f to 6f, 14000f to 4f),
        category = PresetCategory.STUDIO,
        description = "Resalta los agudos para detectar siseos, chasquidos y sibilancia antes de masterizar.",
        recommendedUse = "Audición de de-essing y brillo final.",
        contentType = "Masterización y control de calidad",
        tonalCurveSummary = "Inspección de agudos +6 dB"
    ),
    FactoryPreset(
        name = "Mezcla equilibrada",
        hint = "Curva objetivo suave estilo curva de estudio de masterización",
        gains = mapOf(60f to 3f, 120f to 2.5f, 250f to 1f, 1000f to 0f, 3000f to 2f, 8000f to 3f, 12000f to 2.5f),
        category = PresetCategory.STUDIO,
        description = "Una suave curva comercial para darle a la mezcla una pincelada final pulida.",
        recommendedUse = "Masterización ligera y escucha de prueba.",
        contentType = "Mezclas terminadas",
        tonalCurveSummary = "Pincelada comercial +3 dB"
    )
)

/** Referencia rápida de qué hace cada zona del espectro. */
data class FrequencyGuide(
    val label: String,
    val uses: String
)

val FrequencyGuides: List<FrequencyGuide> = listOf(
    FrequencyGuide("50 Hz", "Sube para dar profundidad masiva al sub-bajo, bombo y bajo. Un exceso retumba en la habitación."),
    FrequencyGuide("100 Hz", "Graves contundentes, cuerpo de guitarras y calidez general. Define la pegada del bombo."),
    FrequencyGuide("200 Hz", "Relleno y cuerpo en voces. Si la mezcla suena 'encajonada', atenúa esta frecuencia."),
    FrequencyGuide("400 Hz", "Cuerpo y plenitud de medios-bajos. Da madera a guitarras acústicas y pianos."),
    FrequencyGuide("800 Hz", "Claridad y presencia cálida en bajos y guitarras. Evita que la voz suene nasal."),
    FrequencyGuide("1,5 kHz", "Cuerpo y definición en medios. Adelanta o aleja los instrumentos principales."),
    FrequencyGuide("3 kHz", "Presencia vocal e inteligibilidad de palabras sin causar fatiga auditiva."),
    FrequencyGuide("5 kHz", "Brillo natural, ataque de batería y definición vocal limpia y cercana."),
    FrequencyGuide("7 kHz", "Definición y presencia de platillos. Zona crítica para evitar sibilancias duras en voces."),
    FrequencyGuide("10 kHz", "Aire y apertura suave. Proporciona sensación de espacio tridimensional."),
    FrequencyGuide("15 kHz", "Extensión de brillo superior holográfico y detalles micro-acústicos.")
)

/** Preajustes de sala para la reverberación. */
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
    ReverbPreset("Gran salón", ReverbParams(damping = 0.35f, filter = 0.65f, decay = 0.8f, preDelay = 0.8f, predelayMix = 0.6f, size = 0.85f, mix = 0.4f)),
    ReverbPreset("Estadio", ReverbParams(damping = 0.2f, filter = 0.8f, decay = 0.95f, preDelay = 0.12f, predelayMix = 0.7f, size = 0.95f, mix = 0.45f)),
    ReverbPreset("Eco", ReverbParams(damping = 0.1f, filter = 0.2f, decay = 0.9f, preDelay = 0.15f, predelayMix = 0.8f, size = 0.6f, mix = 0.4f))
)

/** Perfiles de uso del sistema. */
data class UsageProfile(
    val key: String,
    val name: String,
    val description: String,
    val gainRangeDb: Float,
    val levelProtection: Boolean,
    val effectsEnabled: Boolean,
    val basePreset: String,
    val toneBoostMarginDb: Float = 12f,
    val subCutoffHz: Float = 60f,
    val bassFrequencyHz: Float = 100f,
    val trebleFrequencyHz: Float = 8000f,
    val defaultSubDb: Float = 0f,
    val defaultBassDb: Float = 0f,
    val defaultMidDb: Float = 0f,
    val defaultTrebleDb: Float = 0f,
    val modules: ModuleState = ModuleState()
)

val UsageProfiles: List<UsageProfile> = listOf(
    UsageProfile(
        key = "mobile",
        name = "Altavoz del móvil",
        description = "Graves ricos y medios cálidos optimizados para altavoces integrados.",
        gainRangeDb = 12f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "Plano",
        toneBoostMarginDb = 18f,
        subCutoffHz = 90f,
        bassFrequencyHz = 220f,
        trebleFrequencyHz = 5500f,
        defaultSubDb = -4f,
        defaultBassDb = 3f,
        defaultMidDb = 0f,
        defaultTrebleDb = 2f
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
        subCutoffHz = 35f,
        bassFrequencyHz = 120f,
        trebleFrequencyHz = 7500f,
        defaultSubDb = 2f,
        defaultBassDb = 2f,
        defaultMidDb = 0f,
        defaultTrebleDb = 1.5f
    ),
    UsageProfile(
        key = "bluetooth",
        name = "Auriculares Bluetooth",
        description = "Sonido envolvente con graves firmes y agudos limpios.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Harman",
        toneBoostMarginDb = 15f,
        subCutoffHz = 45f,
        bassFrequencyHz = 140f,
        trebleFrequencyHz = 6500f,
        defaultSubDb = 1.5f,
        defaultBassDb = 2f,
        defaultMidDb = -1f,
        defaultTrebleDb = 2f
    ),
    UsageProfile(
        key = "external",
        name = "Altavoces externos",
        description = "Cuerpo completo con graves profundos y medios presentes.",
        gainRangeDb = 12f,
        levelProtection = true,
        effectsEnabled = false,
        basePreset = "Plano",
        toneBoostMarginDb = 24f,
        subCutoffHz = 45f,
        bassFrequencyHz = 100f,
        trebleFrequencyHz = 5000f,
        defaultSubDb = 2f,
        defaultBassDb = 1.5f,
        defaultMidDb = 0f,
        defaultTrebleDb = 1f
    ),
    UsageProfile(
        key = "surround",
        name = "Barra o sistema 5.1",
        description = "Rango equilibrado y sonido cinematográfico cálido.",
        gainRangeDb = 6f,
        levelProtection = false,
        effectsEnabled = false,
        basePreset = "Plano",
        toneBoostMarginDb = 12f,
        subCutoffHz = 80f,
        bassFrequencyHz = 80f,
        trebleFrequencyHz = 4000f,
        defaultSubDb = 3f,
        defaultBassDb = 2f,
        defaultMidDb = -1f,
        defaultTrebleDb = 2f
    )
)

fun usageProfileByKey(key: String): UsageProfile =
    UsageProfiles.firstOrNull { it.key == key } ?: UsageProfiles.first()
