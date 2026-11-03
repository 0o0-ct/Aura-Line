import {
  AuraPalette,
  BandRole,
  EqBand,
  FactoryPreset,
  FrequencyGuide,
  LowSource,
  ReverbPreset,
  SourceCategory,
  SourceFocus,
  UsageProfile,
} from "./types";

// 30 Monochrome & Neumorphic Palettes
export const AuraPalettes: AuraPalette[] = [
  { key: "p1", label: "Océano Cían", colors: ["#A7EBF2", "#54ACBF", "#26658C", "#023859", "#011C40"], accent: "#A7EBF2" },
  { key: "p2", label: "Turquesa Profundo", colors: ["#6DA5C0", "#0F969C", "#0C7075", "#072E33", "#05161A"], accent: "#0F969C" },
  { key: "p3", label: "Lavanda Malva", colors: ["#C57E81", "#A58F9E", "#6C5E82", "#3F5B8B", "#2E355A"], accent: "#C57E81" },
  { key: "p4", label: "Esmeralda Menta", colors: ["#7AB37C", "#4E977A", "#418B7E", "#062223", "#041516"], accent: "#4E977A" },
  { key: "p5", label: "Bosque Salvia", colors: ["#AEC3B0", "#6B9071", "#375534", "#0F2A1D", "#081810"], accent: "#6B9071" },
  { key: "p6", label: "Azul Pizarra", colors: ["#B4C5D8", "#98A9BE", "#728495", "#4A5B6A", "#263A47"], accent: "#98A9BE" },
  { key: "p7", label: "Coral Atardecer", colors: ["#EAAC8B", "#E88C7D", "#E56B6F", "#B56576", "#355070"], accent: "#EAAC8B" },
  { key: "p8", label: "Ciruela Cósmico", colors: ["#E2CAD8", "#C18DB4", "#87A7D0", "#27425D", "#0E1B48"], accent: "#C18DB4" },
  { key: "p9", label: "Púrpura Real", colors: ["#E7DBEF", "#A56ABD", "#6E3482", "#49225B", "#1D0928"], accent: "#A56ABD" },
  { key: "p10", label: "Hielo Ártico", colors: ["#E1E5E9", "#92B4CE", "#6C8CA9", "#1E3A5F", "#0E1C24"], accent: "#92B4CE" },
  { key: "p11", label: "Grafito Oscuro", colors: ["#B4C5D8", "#728495", "#4A5B6A", "#263A47", "#121D24"], accent: "#728495" },
  { key: "p12", label: "Amatista", colors: ["#DCD7D5", "#BA96C1", "#9C8CB9", "#6C5F8D", "#4B3F6E"], accent: "#BA96C1" },
  { key: "p13", label: "Verde Neón", colors: ["#BBE053", "#65DC91", "#4A6D10", "#1E472A", "#0A1F11"], accent: "#BBE053" },
  { key: "p14", label: "Azales Acero", colors: ["#B1C9EF", "#8AAEE0", "#638ECB", "#395886", "#1A2A42"], accent: "#8AAEE0" },
  { key: "p15", label: "Ámbar Dorado", colors: ["#FFB32B", "#F47F6B", "#BB5098", "#7A5197", "#5344A9"], accent: "#FFB32B" },
  { key: "p16", label: "Sol Naranja", colors: ["#FFB701", "#FC8500", "#8ECAE6", "#209EBB", "#023047"], accent: "#FFB701" },
  { key: "p17", label: "Oliva Tierra", colors: ["#A0BDB4", "#557752", "#435F42", "#5B4E3F", "#222B21"], accent: "#A0BDB4" },
  { key: "p18", label: "Frambuesa", colors: ["#FA7876", "#EA4F88", "#C0369D", "#872CA2", "#4B2991"], accent: "#FA7876" },
  { key: "p19", label: "Lima Eléctrica", colors: ["#C1FF1C", "#78D23D", "#3AA346", "#007542", "#003820"], accent: "#C1FF1C" },
  { key: "p20", label: "Azul Eléctrico", colors: ["#56E1E9", "#BB63FF", "#5B58EB", "#112C70", "#0A2353"], accent: "#56E1E9" },
  { key: "p21", label: "Rosa Pastel", colors: ["#FF99BE", "#FFC2D9", "#C2E1FD", "#97CBFB", "#4E8BC4"], accent: "#FF99BE" },
  { key: "p22", label: "Dulce Algodón", colors: ["#FFAFCC", "#FFC8DD", "#CDB4DB", "#BDE0FE", "#A2D2FF"], accent: "#FFAFCC" },
  { key: "p23", label: "Lila Ensueño", colors: ["#A594F9", "#CDC1FF", "#E5D9F2", "#7371FC", "#2E2B80"], accent: "#A594F9" },
  { key: "p24", label: "Rosa Suave", colors: ["#E27396", "#EA9AB2", "#EFCFE3", "#B3DEE2", "#2A3A3D"], accent: "#EA9AB2" },
  { key: "p25", label: "Ciber Turquesa", colors: ["#B0FFFF", "#8FFAF1", "#6FF4E3", "#2DE9C7", "#0D473D"], accent: "#2DE9C7" },
  { key: "p26", label: "Cian Profundo", colors: ["#12C5DA", "#0EB3C8", "#09A1B6", "#058EA3", "#007C91"], accent: "#12C5DA" },
  { key: "p27", label: "Índigo Resplandor", colors: ["#A7ABFF", "#8B94F0", "#6F7EE1", "#5267D2", "#3650C3"], accent: "#8B94F0" },
  { key: "p28", label: "Cuádruple Vibrante", colors: ["#FFCC33", "#EA0770", "#00A565", "#035EAD", "#022646"], accent: "#00E5CC" },
  { key: "p29", label: "Bruma Magenta", colors: ["#F1BFE2", "#F49BD5", "#CFC0BD", "#379FAC", "#095761"], accent: "#F49BD5" },
  { key: "p30", label: "Brisa Marina", colors: ["#98FF98", "#ADD8E6", "#B39DDB", "#4682B4", "#008080"], accent: "#98FF98" },
];

export function paletteFor(key: string): AuraPalette {
  return (
    AuraPalettes.find((p) => p.key === key) ||
    AuraPalettes.find((p) => p.key === `p${key}`) ||
    AuraPalettes.find((p) => p.label.toLowerCase() === key.toLowerCase()) ||
    AuraPalettes[0]
  );
}

// Factory Presets
export const FactoryPresets: FactoryPreset[] = [
  { name: "Plano", hint: "Sin procesado", gains: {} },
  {
    name: "Voz",
    hint: "Cuerpo 200-400 · presencia 4-5k · corta nasal y raspa",
    gains: { 60: -6, 250: 3, 400: 2, 1000: -2, 2000: -2, 4500: 4, 8000: 2 },
  },
  {
    name: "Bombo",
    hint: "Profundidad 60-80 · ataque 2,5-5k · sin cartón 300-600",
    gains: { 60: 5, 140: 4, 450: -4, 650: -3, 2500: 4, 5000: 3 },
  },
  {
    name: "Bajo eléctrico",
    hint: "Cuerpo 60-80 · gruñido 700-1100 · define notas",
    gains: { 60: 3, 140: 4, 250: -2, 800: 3, 1300: 3, 3000: 1 },
  },
  {
    name: "Guitarra eléctrica",
    hint: "Cuerpo 240 · ataque 2,5-3,5k · corta retumbe",
    gains: { 140: -3, 250: 3, 1600: -2, 2500: 4, 4000: 2 },
  },
  {
    name: "Guitarra acústica",
    hint: "Graves 80-120 · cuerpo 240 · claridad 2,5-5k",
    gains: { 100: 3, 250: 3, 2500: 3, 4000: 2, 8000: 1 },
  },
  {
    name: "Piano",
    hint: "Graves 80-120 · corta retumbe 300 y metálico 1-2k",
    gains: { 100: 3, 350: -3, 1600: -2, 4500: 3, 8000: 2 },
  },
  {
    name: "Platillos",
    hint: "Corta gong 200 y molestia 1k · brillo 5-10k",
    gains: { 60: -6, 250: -4, 1000: -3, 5500: 3, 8000: 3, 12000: 2 },
  },
  {
    name: "Metales y cuerdas",
    hint: "Cuerpo 120-240 · filo 7,5-10k",
    gains: { 140: -3, 650: -2, 3000: 3, 10000: 3 },
  },
  {
    name: "Graves +",
    hint: "Refuerzo fuerte de sub-bajo y graves, con medios retraídos",
    gains: { 60: 8, 120: 6, 250: 3, 900: -2, 2500: 1, 6000: 2 },
  },
  {
    name: "Harman",
    hint: "Curva de referencia: graves +4/+5 · medios −1/−2 · agudos +2/+3",
    gains: { 60: 5, 120: 4, 250: 2, 1000: -1, 2000: -2, 4000: 2, 6000: 3, 9000: 2 },
  },
  {
    name: "JBL",
    hint: "Sonido JBL: graves firmes, medios retraídos y agudos brillantes",
    gains: { 60: 6, 120: 4, 500: -2, 1000: -2, 2500: 2, 5000: 4, 8000: 3 },
  },
  {
    name: "Batería completa",
    hint: "Bombo y caja con cuerpo, brillo sin hojalata",
    gains: { 80: 3, 240: 2, 450: -3, 3000: 2, 5000: 3, 9000: 2 },
  },
];

// Frequency Guides
export const FrequencyGuides: FrequencyGuide[] = [
  { label: "50 Hz", uses: "Sube para saturar graves de bombo, toms o bajo. Baja para escuchar mejor los armónicos del bajo." },
  { label: "100 Hz", uses: "Graves más duros y relleno de guitarras y cajas. Baja para dar claridad a la guitarra." },
  { label: "200 Hz", uses: "Relleno en voces, guitarras y cajas. Baja para quitar sonido 'de hojalata' en platillos." },
  { label: "400 Hz", uses: "Claridad del bajo a bajo volumen. Baja para evitar bombos y toms acartonados." },
  { label: "800 Hz", uses: "Claridad y pegada del bajo. Baja para quitar hojalata en guitarras." },
  { label: "1,5 kHz", uses: "Pegada y claridad en bajos. Baja para eliminar el sonido turbio de guitarras." },
  { label: "3 kHz", uses: "Pegada en bajos, ataque de guitarras y voces claras. Baja en coros para suavizar." },
  { label: "5 kHz", uses: "Presencia en voces y ataque de bombo y toms. Baja para alejar los fondos." },
  { label: "7 kHz", uses: "Ataque metálico de percusión. Baja para atenuar sibilancias." },
  { label: "10 kHz", uses: "Voces y guitarras brillantes. Baja para atenuar sibilantes." },
  { label: "15 kHz", uses: "Aire y brillo en voces, platillos y cuerdas." },
];

// Reverb Presets
export const ReverbPresets: ReverbPreset[] = [
  { name: "Ligera reverberación", params: { damping: 0.7, filter: 0.4, decay: 0.2, preDelay: 0.01, predelayMix: 0.2, size: 0.2, mix: 0.2 } },
  { name: "Sala pequeña", params: { damping: 0.6, filter: 0.35, decay: 0.3, preDelay: 0.02, predelayMix: 0.25, size: 0.3, mix: 0.3 } },
  { name: "Estudio", params: { damping: 0.5, filter: 0.5, decay: 0.35, preDelay: 0.02, predelayMix: 0.3, size: 0.35, mix: 0.35 } },
  { name: "Auditorio", params: { damping: 0.35, filter: 0.7, decay: 0.75, preDelay: 0.06, predelayMix: 0.55, size: 0.8, mix: 0.55 } },
  { name: "Escenario", params: { damping: 0.4, filter: 0.6, decay: 0.6, preDelay: 0.05, predelayMix: 0.5, size: 0.7, mix: 0.5 } },
  { name: "Gran salón", params: { damping: 0.35, filter: 0.65, decay: 0.8, preDelay: 0.07, predelayMix: 0.6, size: 0.85, mix: 0.6 } },
  { name: "Estadio", params: { damping: 0.2, filter: 0.8, decay: 0.95, preDelay: 0.12, predelayMix: 0.7, size: 0.95, mix: 0.7 } },
  { name: "Eco", params: { damping: 0.1, filter: 0.2, decay: 0.9, preDelay: 0.15, predelayMix: 0.8, size: 0.6, mix: 0.75 } },
];

// Usage Profiles
export const UsageProfiles: UsageProfile[] = [
  {
    key: "mobile",
    name: "Altavoz del móvil",
    description: "Graves limitados por el propio altavoz. Protección activa y efectos apagados.",
    gainRangeDb: 12,
    levelProtection: true,
    effectsEnabled: false,
    basePreset: "Plano",
    toneBoostMarginDb: 18,
    subCutoffHz: 90,
    bassFrequencyHz: 220,
    trebleFrequencyHz: 5500,
    defaultTone: { subDb: -4, bassDb: 3, midDb: 0, trebleDb: 2 },
    modules: { equalizer: true, gate: false, compressor: false, limiter: true, reverb: false, width: false },
  },
  {
    key: "wired",
    name: "Auriculares con cable",
    description: "Sin códec de por medio: margen completo y curva de referencia Harman.",
    gainRangeDb: 12,
    levelProtection: false,
    effectsEnabled: false,
    basePreset: "Harman",
    toneBoostMarginDb: 18,
    subCutoffHz: 35,
    bassFrequencyHz: 120,
    trebleFrequencyHz: 7500,
    defaultTone: { subDb: 2, bassDb: 2, midDb: 0, trebleDb: 1.5 },
    modules: { equalizer: true, gate: true, compressor: true, limiter: true, reverb: true, width: true },
  },
  {
    key: "bluetooth",
    name: "Auriculares Bluetooth",
    description: "El códec recorta: protección activa y curva Harman suavizada.",
    gainRangeDb: 12,
    levelProtection: true,
    effectsEnabled: false,
    basePreset: "Harman",
    toneBoostMarginDb: 15,
    subCutoffHz: 45,
    bassFrequencyHz: 140,
    trebleFrequencyHz: 6500,
    defaultTone: { subDb: 1.5, bassDb: 2, midDb: -1, trebleDb: 2 },
    modules: { equalizer: true, gate: true, compressor: true, limiter: true, reverb: true, width: true },
  },
  {
    key: "external",
    name: "Altavoces externos",
    description: "Equipo con amplificador propio. Curva plana y margen moderado.",
    gainRangeDb: 12,
    levelProtection: true,
    effectsEnabled: false,
    basePreset: "Plano",
    toneBoostMarginDb: 24,
    subCutoffHz: 45,
    bassFrequencyHz: 100,
    trebleFrequencyHz: 5000,
    defaultTone: { subDb: 2, bassDb: 1.5, midDb: 0, trebleDb: 1 },
    modules: { equalizer: true, gate: true, compressor: true, limiter: true, reverb: true, width: true },
  },
  {
    key: "surround",
    name: "Barra o sistema 5.1",
    description: "El equipo aplica su propio procesado: rango ±6 dB y protección desactivada.",
    gainRangeDb: 6,
    levelProtection: false,
    effectsEnabled: false,
    basePreset: "Plano",
    toneBoostMarginDb: 12,
    subCutoffHz: 80,
    bassFrequencyHz: 80,
    trebleFrequencyHz: 4000,
    defaultTone: { subDb: 3, bassDb: 2, midDb: -1, trebleDb: 2 },
    modules: { equalizer: true, gate: false, compressor: true, limiter: true, reverb: true, width: true },
  },
];

export function usageProfileByKey(key: string): UsageProfile {
  return UsageProfiles.find((p) => p.key === key) || UsageProfiles[0];
}

export function toneBoostMarginFor(usageProfileKey: string): number {
  return usageProfileByKey(usageProfileKey).toneBoostMarginDb;
}

// Low frequency instruments
export const SubBass: LowSource = {
  key: "sub_bass",
  name: "Sub-bajo",
  shortName: "SUB",
  category: SourceCategory.ELECTRONIC,
  fundamentalHz: [20, 60],
  detail: "Por debajo de 60 Hz no se oye, se siente en el pecho y en la mesa. Es la base de trap, hip-hop, dubstep y EDM.",
  tip: "+4 dB en 50 da peso al sub sin tocar el bombo. En altavoces de móvil, -6 dB quita el retumbe.",
};

export const OrganPedal: LowSource = {
  key: "organ_pedal",
  name: "Órgano de tubos",
  shortName: "ORGAN",
  category: SourceCategory.WIND,
  fundamentalHz: [16, 130],
  detail: "La pedalera llega a 16 Hz, casi infrasónica, y el manual de 8 pies ronda los 65 Hz. Es el instrumento más grave que puede tocar una persona.",
  tip: "+3 dB en 50 da el soplo grave del pedal; -4 dB lo hace indefinido, cuidado con el retumbe.",
};

export const DrumMachine: LowSource = {
  key: "drum_machine",
  name: "Caja de ritmos (TR-808)",
  shortName: "808",
  category: SourceCategory.ELECTRONIC,
  fundamentalHz: [40, 1000],
  detail: "La Roland TR-808 mezcla percusión y línea de bajo: su bombo suena largo y grave, y el charles y el clap aportan el borde agudo.",
  tip: "El sub está en 50 y el charles en 848: sube ambos para que la caja pique sin embarrarse.",
};

export const KickDrum: LowSource = {
  key: "kick_drum",
  name: "Bombo de batería",
  shortName: "BOMBO",
  category: SourceCategory.PERCUSION,
  fundamentalHz: [50, 200],
  detail: "El corazón rítmico de la música moderna. Golpe seco y contundente entre 50 y 100 Hz, con el cuerpo entre 100 y 200 Hz.",
  tip: "+3 dB en 141 engorda el bombo. Si suena a cartón, baja 244 y 346; el ataque va en 2,5-5 kHz.",
};

export const FloorTom: LowSource = {
  key: "floor_tom",
  name: "Toms de piso (granaderos)",
  shortName: "TOM",
  category: SourceCategory.PERCUSION,
  fundamentalHz: [80, 150],
  detail: "Los tambores más grandes y profundos de la batería acústica: generan frecuencias resonantes y graves.",
  tip: "+3 dB en 141 lo saca del bombo. Si se come a la caja, baja 244.",
};

export const Timpani: LowSource = {
  key: "timpani",
  name: "Timbales sinfónicos",
  shortName: "TIMBAL",
  category: SourceCategory.PERCUSION,
  fundamentalHz: [65, 120],
  detail: "Calyces de cobre afinables que producen los graves más potentes y dramáticos de la música orquestal.",
  tip: "Los timbales afinados en Fa grave responden sobre 100 Hz: +3 dB en 141 y +2 dB en 447.",
};

export const FolkDrums: LowSource = {
  key: "folk_drums",
  name: "Tambores tradicionales",
  shortName: "TAIKO",
  category: SourceCategory.PERCUSION,
  fundamentalHz: [55, 150],
  detail: "Bombo legüero, tambora y taiko japonés: parches grandes diseñados para mover mucho aire y generar frecuencias muy bajas.",
  tip: "El golpe seco está en 50 y el cuerpo en 141: sube los dos un punto y el parche respira.",
};

export const SynthBass: LowSource = {
  key: "synth_bass",
  name: "Sintetizadores de bajo",
  shortName: "SYNTH",
  category: SourceCategory.ELECTRONIC,
  fundamentalHz: [32, 900],
  detail: "Generadores de graves puros u oscuros: senoidal, sierra o cuadrada. El Minimoog es el estándar histórico de este sonido.",
  tip: "El cuerpo del sinte vive en 400-700 Hz: +3 dB en 547 y +2 dB en 648 lo hacen redondo.",
};

export const ElectricBass: LowSource = {
  key: "electric_bass",
  name: "Bajo eléctrico",
  shortName: "BAJO",
  category: SourceCategory.STRING,
  fundamentalHz: [41, 300],
  detail: "El pilar de la música moderna (pop, rock, funk). Su fundamental ronda E1 (41 Hz) y su cuerpo se concentra entre 200 y 300 Hz.",
  tip: "+3 dB en 244 define el cuerpo, +2 dB en 141 refuerza el fundamental y 648-848 da la pegada.",
};

export const DoubleBass: LowSource = {
  key: "double_bass",
  name: "Contrabajo",
  shortName: "CONTRA",
  category: SourceCategory.STRING,
  fundamentalHz: [32, 400],
  detail: "El gigante acústico del jazz, el blues y la música clásica. Su fundamental es C1 (32 Hz).",
  tip: "Corta 244 si suena a madera hueca; sube 346 para que el arco destaque sobre el piano.",
};

export const AcousticBass: LowSource = {
  key: "acoustic_bass",
  name: "Bajo acústico",
  shortName: "ACUST",
  category: SourceCategory.STRING,
  fundamentalHz: [41, 200],
  detail: "Guitarra de caja grande afinada en octavas graves. Cubre el hueco entre el contrabajo y la guitarra eléctrica.",
  tip: "Comparte banda con el bajo eléctrico: +2 dB en 244 lo hace más orgánico y menos puntero.",
};

export const FretlessBass: LowSource = {
  key: "fretless_bass",
  name: "Bajo fretless",
  shortName: "FLUIDO",
  category: SourceCategory.STRING,
  fundamentalHz: [41, 500],
  detail: "El mismo registro que el bajo eléctrico pero con un sonido melódico mucho más fluido, sin el armónico picudo de los trastes.",
  tip: "+3 dB en 447 le da cuerpo melódico; baja 141 si se enturbia con el bombo.",
};

export const Guitarrón: LowSource = {
  key: "guitarron",
  name: "Guitarrón mexicano",
  shortName: "GUITAR",
  category: SourceCategory.STRING,
  fundamentalHz: [40, 600],
  detail: "Instrumento de cuerda grande que provee la base armónica y grave de los mariachis.",
  tip: "El pellizco está entre 244 y 447: +3 dB en 547 lo saca del fondo de la mezcla.",
};

export const Tuba: LowSource = {
  key: "tuba",
  name: "Tuba y sousafón",
  shortName: "TUBA",
  category: SourceCategory.WIND,
  fundamentalHz: [29, 700],
  detail: "Los metales más pesados y graves, encargados del soporte armónico en orquestas y bandas de marcha.",
  tip: "El pedal está en 50 y el cuerpo en 648: +3 dB en 648 y el metal se oye redondo.",
};

export const LowWoodwinds: LowSource = {
  key: "low_woodwinds",
  name: "Clarinete bajo y fagot",
  shortName: "VIENTO",
  category: SourceCategory.WIND,
  fundamentalHz: [58, 800],
  detail: "Los registros más profundos de las maderas. El fagot y el clarinete bajo llegan a Bb1 (58 Hz) en los modelos extendidos.",
  tip: "Corta 346 para quitar nasalidad y sube 748 +2 dB para darle el cuerpo de madera.",
};

export const LowFrequencySources: LowSource[] = [
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
  LowWoodwinds,
];

// Roles by center frequency window
export const LowFrequencyRoles: BandRole[] = [
  { centerWindow: [25, 90], owner: SubBass, companions: [OrganPedal, DrumMachine, FolkDrums, Tuba] },
  { centerWindow: [91, 195], owner: KickDrum, companions: [FloorTom, Timpani, FolkDrums, ElectricBass] },
  { centerWindow: [196, 320], owner: ElectricBass, companions: [AcousticBass, FretlessBass, Guitarrón, DrumMachine] },
  { centerWindow: [321, 420], owner: DoubleBass, companions: [AcousticBass, LowWoodwinds] },
  { centerWindow: [396, 495], owner: FretlessBass, companions: [LowWoodwinds, SynthBass, AcousticBass] },
  { centerWindow: [496, 595], owner: Guitarrón, companions: [SynthBass, Tuba, ElectricBass] },
  { centerWindow: [596, 695], owner: Tuba, companions: [SynthBass, ElectricBass, OrganPedal] },
  { centerWindow: [696, 795], owner: LowWoodwinds, companions: [FretlessBass, ElectricBass] },
  { centerWindow: [796, 895], owner: SynthBass, companions: [DrumMachine, ElectricBass] },
  { centerWindow: [896, 1000], owner: DrumMachine, companions: [ElectricBass, DoubleBass, FloorTom] },
];

export function bandRoleFor(centerHz: number): BandRole | undefined {
  return LowFrequencyRoles.find(
    (role) => centerHz >= role.centerWindow[0] && centerHz <= role.centerWindow[1]
  );
}

export function ownerShortNameFor(centerHz: number): string | undefined {
  return bandRoleFor(centerHz)?.owner?.shortName;
}

// 15 Additive Source Focus Recipes
export const SourceFocuses: SourceFocus[] = [
  { source: SubBass, steps: [{ centerHz: 50, deltaDb: 4 }] },
  { source: OrganPedal, steps: [{ centerHz: 50, deltaDb: 3 }, { centerHz: 141, deltaDb: 2 }] },
  { source: DrumMachine, steps: [{ centerHz: 50, deltaDb: 3 }, { centerHz: 848, deltaDb: 2 }, { centerHz: 141, deltaDb: -2 }] },
  { source: KickDrum, steps: [{ centerHz: 141, deltaDb: 3 }, { centerHz: 244, deltaDb: -3 }, { centerHz: 2500, deltaDb: 2 }] },
  { source: FloorTom, steps: [{ centerHz: 141, deltaDb: 3 }, { centerHz: 244, deltaDb: 2 }] },
  { source: Timpani, steps: [{ centerHz: 141, deltaDb: 3 }, { centerHz: 447, deltaDb: 2 }] },
  { source: FolkDrums, steps: [{ centerHz: 50, deltaDb: 3 }, { centerHz: 141, deltaDb: 2 }] },
  { source: SynthBass, steps: [{ centerHz: 547, deltaDb: 3 }, { centerHz: 848, deltaDb: 2 }, { centerHz: 244, deltaDb: -2 }] },
  { source: ElectricBass, steps: [{ centerHz: 244, deltaDb: 3 }, { centerHz: 141, deltaDb: 2 }, { centerHz: 648, deltaDb: 1 }] },
  { source: DoubleBass, steps: [{ centerHz: 346, deltaDb: 3 }, { centerHz: 244, deltaDb: -2 }, { centerHz: 848, deltaDb: 2 }] },
  { source: AcousticBass, steps: [{ centerHz: 244, deltaDb: 2 }, { centerHz: 346, deltaDb: 2 }] },
  { source: FretlessBass, steps: [{ centerHz: 447, deltaDb: 3 }, { centerHz: 141, deltaDb: -2 }, { centerHz: 848, deltaDb: 1 }] },
  { source: Guitarrón, steps: [{ centerHz: 547, deltaDb: 3 }, { centerHz: 244, deltaDb: 1 }] },
  { source: Tuba, steps: [{ centerHz: 648, deltaDb: 3 }, { centerHz: 50, deltaDb: 1 }] },
  { source: LowWoodwinds, steps: [{ centerHz: 748, deltaDb: 2 }, { centerHz: 346, deltaDb: -2 }] },
];

export function focusFor(key: string): SourceFocus | undefined {
  return SourceFocuses.find((f) => f.source.key === key);
}

// Band generation & frequency calculation
export function generateEqualizerBands(count: number, gainRangeDb: number = 12): EqBand[] {
  const bands = Math.min(25, Math.max(20, count));
  const lowBands = Math.min(12, Math.max(6, Math.round(bands * 0.4)));
  const crossover = 1000;

  const cutoffs: number[] = [];
  for (let index = 1; index <= lowBands; index++) {
    cutoffs.push((crossover * index) / lowBands);
  }
  const highBands = bands - lowBands;
  if (highBands > 0) {
    const ratio = Math.pow(20000.0 / crossover, 1.0 / highBands);
    for (let index = 1; index <= highBands; index++) {
      cutoffs.push(crossover * Math.pow(ratio, index));
    }
  }

  return cutoffs.map((cutoff, index) => ({
    id: index,
    frequencyHz: Math.min(20000, Math.max(20, cutoff)),
    gainDb: 0,
    q: 1.414,
    enabled: true,
  }));
}

export function bandCenterHz(bands: EqBand[], index: number): number {
  const cutoff = bands[index]?.frequencyHz ?? 0;
  const previous = index === 0 ? 0 : bands[index - 1]?.frequencyHz ?? 0;
  return previous <= 0 ? cutoff / 2 : Math.sqrt(previous * cutoff);
}

export function bandProbeHz(bands: EqBand[], index: number): number {
  const cutoff = bands[index]?.frequencyHz ?? 0;
  const previous = index === 0 ? 0 : bands[index - 1]?.frequencyHz ?? 0;
  const width = Math.max(cutoff * 0.1, cutoff - previous);
  return cutoff - width * 0.15;
}

export function resolveFocus(bands: EqBand[], focus: SourceFocus): Array<[number, number]> {
  if (bands.length === 0) return [];
  const centers = bands.map((_, i) => bandCenterHz(bands, i));
  const result: Array<[number, number]> = [];
  const seenIndices = new Set<number>();

  for (const step of focus.steps) {
    let bestIndex = 0;
    let minDistance = Math.abs(centers[0] - step.centerHz);
    for (let i = 1; i < centers.length; i++) {
      const distance = Math.abs(centers[i] - step.centerHz);
      if (distance < minDistance) {
        minDistance = distance;
        bestIndex = i;
      }
    }
    if (minDistance <= step.centerHz * 0.35 && !seenIndices.has(bestIndex)) {
      seenIndices.add(bestIndex);
      result.push([bestIndex, step.deltaDb]);
    }
  }
  return result;
}

// Formatting helpers
export function formatFrequency(hz: number): string {
  if (hz >= 1000) {
    const val = hz / 1000;
    return val >= 10 ? `${Math.round(val)}k` : `${val.toFixed(1).replace(/\.0$/, "")}k`;
  }
  return `${Math.round(hz)}`;
}

export function formatGain(gain: number): string {
  if (Math.abs(gain) < 0.05) return "0.0";
  return gain > 0 ? `+${gain.toFixed(1)}` : gain.toFixed(1);
}
