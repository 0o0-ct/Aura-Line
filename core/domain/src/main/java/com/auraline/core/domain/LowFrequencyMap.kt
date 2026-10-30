package com.auraline.core.domain

/** Familia instrumental de una fuente grave. */
enum class SourceCategory(val label: String) {
    PERCUSION("Percusión"),
    ELECTRONIC("Electrónicos"),
    STRING("Cuerdas"),
    WIND("Vientos")
}

/**
 * Una fuente grave que el usuario puede manipular de forma independiente.
 *
 * [fundamentalHz] es el registro real del instrumento (su nota más grave útil),
 * no la frecuencia del filtro: sirve para saber dónde tocar y qué esperar al escuchar.
 */
data class LowSource(
    val key: String,
    val name: String,
    /** Nombre corto que cabe bajo el fader (máx. 7 caracteres). */
    val shortName: String,
    val category: SourceCategory,
    val fundamentalHz: IntRange,
    /** Qué es y cómo se oye. */
    val detail: String,
    /** Qué hacer con él en el ecualizador. */
    val tip: String
)

/**
 * Rol musical de una banda: el instrumento que la posee y los que la comparten.
 *
 * Se define por la **ventana de frecuencia centro** y no por el índice de banda,
 * así el mapa sigue siendo válido si cambia el número de bandas (20/22/25) o la rejilla.
 */
data class BandRole(
    val centerWindow: IntRange,
    val owner: LowSource,
    val companions: List<LowSource>
)

/** Un movimiento de EQ asociado a una fuente: banda objetivo y cuánto sumar. */
data class FocusStep(
    val centerHz: Int,
    val deltaDb: Float
)

/**
 * Receta de foco de una fuente: qué bandas tocar y cuánto, para que el instrumento
 * escuche como debe **sin borrar el resto de la curva del usuario**.
 */
data class SourceFocus(
    val source: LowSource,
    val steps: List<FocusStep>
)

// ─────────────────────────────────────────────────────────────────────────────
// Fuentes graves
// ─────────────────────────────────────────────────────────────────────────────

val SubBass = LowSource(
    key = "sub_bass",
    name = "Sub-bajo",
    shortName = "SUB",
    category = SourceCategory.ELECTRONIC,
    fundamentalHz = 20..60,
    detail = "Por debajo de 60 Hz no se oye, se siente en el pecho y en la mesa. " +
        "Es la base de trap, hip-hop, dubstep y EDM.",
    tip = "+4 dB en 50 da peso al sub sin tocar el bombo. En altavoces de móvil, -6 dB quita el retumbe."
)

val OrganPedal = LowSource(
    key = "organ_pedal",
    name = "Órgano de tubos",
    shortName = "ORGAN",
    category = SourceCategory.WIND,
    fundamentalHz = 16..130,
    detail = "La pedalera llega a 16 Hz, casi infrasónica, y el manual de 8 pies ronda los 65 Hz. " +
        "Es el instrumento más grave que puede tocar una persona.",
    tip = "+3 dB en 50 da el soplo grave del pedal; -4 dB lo hace indefinido, cuidado con el retumbe."
)

val DrumMachine = LowSource(
    key = "drum_machine",
    name = "Caja de ritmos (TR-808)",
    shortName = "808",
    category = SourceCategory.ELECTRONIC,
    fundamentalHz = 40..1000,
    detail = "La Roland TR-808 mezcla percusión y línea de bajo: su bombo suena largo y grave, " +
        "y el charles y el clap aportan el borde agudo.",
    tip = "El sub está en 50 y el charles en 848: sube ambos para que la caja pique sin embarrarse."
)

val KickDrum = LowSource(
    key = "kick_drum",
    name = "Bombo de batería",
    shortName = "BOMBO",
    category = SourceCategory.PERCUSION,
    fundamentalHz = 50..200,
    detail = "El corazón rítmico de la música moderna. Golpe seco y contundente entre 50 y 100 Hz, " +
        "con el cuerpo entre 100 y 200 Hz.",
    tip = "+3 dB en 141 engorda el bombo. Si suena a cartón, baja 244 y 346; el ataque va en 2,5-5 kHz."
)

val FloorTom = LowSource(
    key = "floor_tom",
    name = "Toms de piso (granaderos)",
    shortName = "TOM",
    category = SourceCategory.PERCUSION,
    fundamentalHz = 80..150,
    detail = "Los tambores más grandes y profundos de la batería acústica: generan frecuencias " +
        "resonantes y graves.",
    tip = "+3 dB en 141 lo saca del bombo. Si se come a la caja, baja 244."
)

val Timpani = LowSource(
    key = "timpani",
    name = "Timbales sinfónicos",
    shortName = "TIMBAL",
    category = SourceCategory.PERCUSION,
    fundamentalHz = 65..120,
    detail = "Calyces de cobre afinables que producen los graves más potentes y dramáticos " +
        "de la música orquestal.",
    tip = "Los timbales afinados en Fa grave responden sobre 100 Hz: +3 dB en 141 y +2 dB en 447."
)

val FolkDrums = LowSource(
    key = "folk_drums",
    name = "Tambores tradicionales",
    shortName = "TAIKO",
    category = SourceCategory.PERCUSION,
    fundamentalHz = 55..150,
    detail = "Bombo legüero, tambora y taiko japonés: parches grandes diseñados para mover mucho " +
        "aire y generar frecuencias muy bajas.",
    tip = "El golpe seco está en 50 y el cuerpo en 141: sube los dos un punto y el parche respira."
)

val SynthBass = LowSource(
    key = "synth_bass",
    name = "Sintetizadores de bajo",
    shortName = "SYNTH",
    category = SourceCategory.ELECTRONIC,
    fundamentalHz = 32..900,
    detail = "Generadores de graves puros u oscuros: senoidal, sierra o cuadrada. " +
        "El Minimoog es el estándar histórico de este sonido.",
    tip = "El cuerpo del sinte vive en 400-700 Hz: +3 dB en 547 y +2 dB en 648 lo hacen redondo."
)

val ElectricBass = LowSource(
    key = "electric_bass",
    name = "Bajo eléctrico",
    shortName = "BAJO",
    category = SourceCategory.STRING,
    fundamentalHz = 41..300,
    detail = "El pilar de la música moderna (pop, rock, funk). Su fundamental ronda E1 (41 Hz) y " +
        "su cuerpo se concentra entre 200 y 300 Hz.",
    tip = "+3 dB en 244 define el cuerpo, +2 dB en 141 refuerza el fundamental y 648-848 da la pegada."
)

val DoubleBass = LowSource(
    key = "double_bass",
    name = "Contrabajo",
    shortName = "CONTRA",
    category = SourceCategory.STRING,
    fundamentalHz = 32..400,
    detail = "El gigante acústico del jazz, el blues y la música clásica. Su fundamental es C1 (32 Hz).",
    tip = "Corta 244 si suena a madera hueca; sube 346 para que el arco destaque sobre el piano."
)

val AcousticBass = LowSource(
    key = "acoustic_bass",
    name = "Bajo acústico",
    shortName = "ACUST",
    category = SourceCategory.STRING,
    fundamentalHz = 41..200,
    detail = "Guitarra de caja grande afinada en octavas graves. Cubre el hueco entre el contrabajo " +
        "y la guitarra eléctrica.",
    tip = "Comparte banda con el bajo eléctrico: +2 dB en 244 lo hace más orgánico y menos puntero."
)

val FretlessBass = LowSource(
    key = "fretless_bass",
    name = "Bajo fretless",
    shortName = "FLUIDO",
    category = SourceCategory.STRING,
    fundamentalHz = 41..500,
    detail = "El mismo registro que el bajo eléctrico pero con un sonido melódico mucho más fluido, " +
        "sin el armónico picudo de los trastes.",
    tip = "+3 dB en 447 le da cuerpo melódico; baja 141 si se enturbia con el bombo."
)

val Guitarrón = LowSource(
    key = "guitarron",
    name = "Guitarrón mexicano",
    shortName = "GUITAR",
    category = SourceCategory.STRING,
    fundamentalHz = 40..600,
    detail = "Instrumento de cuerda grande que provee la base armónica y grave de los mariachis.",
    tip = "El pellizco está entre 244 y 447: +3 dB en 547 lo saca del fondo de la mezcla."
)

val Tuba = LowSource(
    key = "tuba",
    name = "Tuba y sousafón",
    shortName = "TUBA",
    category = SourceCategory.WIND,
    fundamentalHz = 29..700,
    detail = "Los metales más pesados y graves, encargados del soporte armónico en orquestas " +
        "y bandas de marcha.",
    tip = "El pedal está en 50 y el cuerpo en 648: +3 dB en 648 y el metal se oye redondo."
)

val LowWoodwinds = LowSource(
    key = "low_woodwinds",
    name = "Clarinete bajo y fagot",
    shortName = "VIENTO",
    category = SourceCategory.WIND,
    fundamentalHz = 58..800,
    detail = "Los registros más profundos de las maderas. El fagot y el clarinete bajo " +
        "llegan a Bb1 (58 Hz) en los modelos extendidos.",
    tip = "Corta 346 para quitar nasalidad y sube 748 +2 dB para darle el cuerpo de madera."
)

/** Las quince fuentes graves que el usuario puede manipular por separado. */
val LowFrequencySources: List<LowSource> = listOf(
    SubBass,
    OrganPedal,
    DrumMachine,
    KickDrum,
    FloorTom,
    Timpani,
    FolkDrums,
    SynthBass,
    ElectricBass,
    DoubleBass,
    AcousticBass,
    FretlessBass,
    Guitarrón,
    Tuba,
    LowWoodwinds
)

// ─────────────────────────────────────────────────────────────────────────────
// Roles por banda
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Las diez bandas graves de la rejilla de 25 bandas (centros 50 → 948 Hz) y el
 * instrumento que cada una posee.
 *
 * La ventana de centro cubre también la rejilla de 20 bandas (20..25): si una banda
 * cae fuera de toda ventana, simplemente no muestra dueño.
 */
val LowFrequencyRoles: List<BandRole> = listOf(
    BandRole(
        centerWindow = 25..90,
        owner = SubBass,
        companions = listOf(OrganPedal, DrumMachine, FolkDrums, Tuba)
    ),
    BandRole(
        centerWindow = 91..195,
        owner = KickDrum,
        companions = listOf(FloorTom, Timpani, FolkDrums, ElectricBass)
    ),
    BandRole(
        centerWindow = 196..320,
        owner = ElectricBass,
        companions = listOf(AcousticBass, FretlessBass, Guitarrón, DrumMachine)
    ),
    BandRole(
        centerWindow = 321..420,
        owner = DoubleBass,
        companions = listOf(AcousticBass, LowWoodwinds)
    ),
    BandRole(
        centerWindow = 396..495,
        owner = FretlessBass,
        companions = listOf(LowWoodwinds, SynthBass, AcousticBass)
    ),
    BandRole(
        centerWindow = 496..595,
        owner = Guitarrón,
        companions = listOf(SynthBass, Tuba, ElectricBass)
    ),
    BandRole(
        centerWindow = 596..695,
        owner = Tuba,
        companions = listOf(SynthBass, ElectricBass, OrganPedal)
    ),
    BandRole(
        centerWindow = 696..795,
        owner = LowWoodwinds,
        companions = listOf(FretlessBass, ElectricBass)
    ),
    BandRole(
        centerWindow = 796..895,
        owner = SynthBass,
        companions = listOf(DrumMachine, ElectricBass)
    ),
    BandRole(
        centerWindow = 896..1000,
        owner = DrumMachine,
        companions = listOf(ElectricBass, DoubleBass, FloorTom)
    )
)

/** Dueño de la banda que cae en esta frecuencia centro, si lo hay. */
fun bandRoleFor(centerHz: Float): BandRole? =
    LowFrequencyRoles.firstOrNull { centerHz >= it.centerWindow.first && centerHz <= it.centerWindow.last }

/** Nombre corto del dueño de la banda, para pintar bajo el fader. */
fun ownerShortNameFor(centerHz: Float): String? = bandRoleFor(centerHz)?.owner?.shortName

// ─────────────────────────────────────────────────────────────────────────────
// Focos por fuente
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Receta de foco de cada fuente: qué banda tocar y cuánto.
 *
 * Son **aditivas**: suman sobre la curva que ya tenga el usuario, sin borrarla.
 * Las frecuencias objetivo son centros de banda, así que se recalculan solas
 * si cambia el número de bandas.
 */
val SourceFocuses: List<SourceFocus> = listOf(
    SourceFocus(SubBass, listOf(FocusStep(50, 4f))),
    SourceFocus(OrganPedal, listOf(FocusStep(50, 3f), FocusStep(141, 2f))),
    SourceFocus(DrumMachine, listOf(FocusStep(50, 3f), FocusStep(848, 2f), FocusStep(141, -2f))),
    SourceFocus(KickDrum, listOf(FocusStep(141, 3f), FocusStep(244, -3f), FocusStep(2500, 2f))),
    SourceFocus(FloorTom, listOf(FocusStep(141, 3f), FocusStep(244, 2f))),
    SourceFocus(Timpani, listOf(FocusStep(141, 3f), FocusStep(447, 2f))),
    SourceFocus(FolkDrums, listOf(FocusStep(50, 3f), FocusStep(141, 2f))),
    SourceFocus(SynthBass, listOf(FocusStep(547, 3f), FocusStep(848, 2f), FocusStep(244, -2f))),
    SourceFocus(ElectricBass, listOf(FocusStep(244, 3f), FocusStep(141, 2f), FocusStep(648, 1f))),
    SourceFocus(DoubleBass, listOf(FocusStep(346, 3f), FocusStep(244, -2f), FocusStep(848, 2f))),
    SourceFocus(AcousticBass, listOf(FocusStep(244, 2f), FocusStep(346, 2f))),
    SourceFocus(FretlessBass, listOf(FocusStep(447, 3f), FocusStep(141, -2f), FocusStep(848, 1f))),
    SourceFocus(Guitarrón, listOf(FocusStep(547, 3f), FocusStep(244, 1f))),
    SourceFocus(Tuba, listOf(FocusStep(648, 3f), FocusStep(50, 1f))),
    SourceFocus(LowWoodwinds, listOf(FocusStep(748, 2f), FocusStep(346, -2f)))
)

fun focusFor(key: String): SourceFocus? = SourceFocuses.firstOrNull { it.source.key == key }

/**
 * Resuelve la receta de foco contra las bandas reales: cada paso se aplica a la banda
 * cuyo centro está más cerca del objetivo.
 *
 * Devuelve pares `índice de banda → delta en dB`, ya filtrados a los pasos que
 * han encontrado banda (por ejemplo 2,5 kHz solo existe si hay banda alta).
 */
fun resolveFocus(bands: List<EqBand>, focus: SourceFocus): List<Pair<Int, Float>> {
    if (bands.isEmpty()) return emptyList()
    val centers = bands.indices.map { bandCenterHz(bands, it) }
    return focus.steps.mapNotNull { step ->
        val index = centers.indices.minByOrNull { kotlin.math.abs(centers[it] - step.centerHz) }
            ?: return@mapNotNull null
        val distance = kotlin.math.abs(centers[index] - step.centerHz)
        // Más de medio salto entre bandas: el objetivo no existe en esta rejilla.
        if (distance > step.centerHz * 0.35f) null else index to step.deltaDb
    }.distinctBy { it.first }
}
