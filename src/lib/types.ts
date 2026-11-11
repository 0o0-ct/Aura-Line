// AuraLine domain types and data models

export interface EqBand {
  id: number;
  frequencyHz: number;
  gainDb: number;
  q: number;
  enabled: boolean;
}

export interface ToneControls {
  subDb: number;
  bassDb: number;
  trebleDb: number;
  midDb: number;
  bassFrequencyHz: number;
  trebleFrequencyHz: number;
  subCutoffHz: number;
}

export interface EqFilters {
  hpfEnabled: boolean;
  hpfCutoffHz: number;
  lpfEnabled: boolean;
  lpfCutoffHz: number;
  lowShelfHz: number;
  lowShelfGainDb: number;
  highShelfHz: number;
  highShelfGainDb: number;
}

export interface StereoControls {
  balance: number;      // -100..100
  expansion: number;    // 1.0..2.0
  mono: boolean;
  swapChannels: boolean;
  invertPhase: boolean;
}

export interface VolumeControls {
  percent: number;      // 0..100
  linkedToSystem: boolean;
}

export interface ReverbParams {
  damping: number;
  filter: number;
  decay: number;
  preDelay: number;
  predelayMix: number;
  size: number;
  mix: number;
}

export interface CompressorParams {
  threshold: number;
  ratio: number;
  attackMs: number;
  releaseMs: number;
  makeupGain: number;
  limiterCeiling: number;
}

export interface GateParams {
  thresholdDb: number;
  ratio: number;
}

export interface ModuleState {
  equalizer: boolean;
  gate: boolean;
  compressor: boolean;
  limiter: boolean;
  reverb: boolean;
  width: boolean;
}

export interface EqualizerState {
  numBands: number;
  gainRangeDb: number;
  preampDb: number;
  bands: EqBand[];
  filters: EqFilters;
  tone: ToneControls;
  stereo: StereoControls;
  volume: VolumeControls;
  reverb: ReverbParams;
  compressor: CompressorParams;
  gate: GateParams;
  modules: ModuleState;
  motorEnabled: boolean;
  effectsEnabled: boolean;
  bypass: boolean;
  autoPreamp: boolean;
  selectedRoute: number;
  presetName: string;
  toneBoostMarginDb: number;
}

export interface AppTheme {
  paletteKey: string;
  accentColor: string;
  isDark: boolean;
  followSystem: boolean;
  highContrast: boolean;
}

export interface AppSettings {
  numBands: number;
  gainRangeDb: number;
  motorMode: string;
  autoStart: boolean;
  hapticFeedback: boolean;
  showValues: boolean;
  spectrumAnalyzer: string;
  profilesByOutput: boolean;
  excludedApps: string[];
  autoCheck: boolean;
  usageProfile: string;
  language: string;
  theme: AppTheme;
}

export interface EffectStage {
  name: string;
  active: boolean;
  detail: string;
}

export interface AudioSession {
  id: number;
  owner: string;
  global: boolean;
}

export enum AudioRoute {
  SPEAKER = "SPEAKER",
  WIRED_HEADSET = "WIRED_HEADSET",
  BLUETOOTH = "BLUETOOTH",
  USB = "USB",
  UNKNOWN = "UNKNOWN",
}

export const AudioRouteLabels: Record<AudioRoute, string> = {
  [AudioRoute.SPEAKER]: "Altavoz",
  [AudioRoute.WIRED_HEADSET]: "Auriculares con cable",
  [AudioRoute.BLUETOOTH]: "Bluetooth",
  [AudioRoute.USB]: "USB",
  [AudioRoute.UNKNOWN]: "Salida del sistema",
};

export interface EngineStatus {
  running: boolean;
  globalScope: boolean;
  route: AudioRoute;
  sampleRateHz: number;
  outputName: string | null;
  channels: number;
  stages: EffectStage[];
  sessions: AudioSession[];
  lastError: string | null;
  activeStages: number;
}

export interface AudioCheckResult {
  referenceDb: number;
  processedDb: number;
  deltaDb: number;
  passed: boolean;
  message: string;
}

export interface FactoryPreset {
  name: string;
  hint: string;
  gains: Record<number, number>;
}

export interface FrequencyGuide {
  label: string;
  uses: string;
}

export interface ReverbPreset {
  name: string;
  params: ReverbParams;
}

export interface UsageProfile {
  key: string;
  name: string;
  description: string;
  gainRangeDb: number;
  levelProtection: boolean;
  effectsEnabled: boolean;
  basePreset: string;
  toneBoostMarginDb: number;
  subCutoffHz: number;
  bassFrequencyHz?: number;
  trebleFrequencyHz?: number;
  defaultTone?: Partial<ToneControls>;
  modules: ModuleState;
}

export enum SourceCategory {
  PERCUSION = "Percusión",
  ELECTRONIC = "Electrónicos",
  STRING = "Cuerdas",
  WIND = "Vientos",
}

export interface LowSource {
  key: string;
  name: string;
  shortName: string;
  category: SourceCategory;
  fundamentalHz: [number, number];
  detail: string;
  tip: string;
}

export interface BandRole {
  centerWindow: [number, number];
  owner: LowSource;
  companions: LowSource[];
}

export interface FocusStep {
  centerHz: number;
  deltaDb: number;
}

export interface SourceFocus {
  source: LowSource;
  steps: FocusStep[];
}

export interface AuraPalette {
  key: string;
  label: string;
  colors: string[];
  accent: string;
}
