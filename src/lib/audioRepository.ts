import {
  AppSettings,
  AudioCheckResult,
  CompressorParams,
  EngineStatus,
  EqualizerState,
  GateParams,
  ModuleState,
  ReverbParams,
  ToneControls,
} from "./types";
import {
  FactoryPresets,
  ReverbPresets,
  UsageProfiles,
  bandCenterHz,
  focusFor,
  generateEqualizerBands,
  resolveFocus,
  toneBoostMarginFor,
  usageProfileByKey,
} from "./constants";
import { auraEngine } from "./audioEngine";

export function clampPreampDb(db: number): number {
  return Math.min(15, Math.max(-15, db));
}

export function clampBandGainDb(db: number, rangeDb: number): number {
  return Math.min(rangeDb, Math.max(-rangeDb, db));
}

export function clampToneDb(db: number, marginDb: number): number {
  return Math.min(marginDb, Math.max(-marginDb, db));
}

export function clampBassCrossHz(hz: number): number {
  return Math.min(400, Math.max(40, hz));
}

export function clampTrebleCrossHz(hz: number): number {
  return Math.min(16000, Math.max(2000, hz));
}

export function clampSubCutoffHz(hz: number): number {
  return Math.min(100, Math.max(20, hz));
}

export const DEFAULT_STATE: EqualizerState = {
  numBands: 25,
  gainRangeDb: 12,
  preampDb: 0,
  bands: generateEqualizerBands(25),
  tone: {
    subDb: 0,
    bassDb: 0,
    trebleDb: 0,
    midDb: 0,
    bassFrequencyHz: 100,
    trebleFrequencyHz: 8000,
    subCutoffHz: 60,
  },
  stereo: {
    balance: 0,
    expansion: 1.0,
    mono: false,
    swapChannels: false,
    invertPhase: false,
  },
  volume: {
    percent: 100,
    linkedToSystem: true,
  },
  reverb: {
    damping: 0.5,
    filter: 0.5,
    decay: 0.5,
    preDelay: 0.0,
    predelayMix: 0.5,
    size: 0.5,
    mix: 0.25,
  },
  compressor: {
    threshold: -28,
    ratio: 4.0,
    attackMs: 10,
    releaseMs: 200,
    makeupGain: 3,
    limiterCeiling: -1,
  },
  gate: {
    thresholdDb: -60,
    ratio: 2.0,
  },
  modules: {
    equalizer: true,
    gate: true,
    compressor: true,
    limiter: true,
    reverb: true,
    width: true,
  },
  motorEnabled: false,
  effectsEnabled: false,
  bypass: false,
  autoPreamp: true,
  selectedRoute: 0,
  presetName: "Plano",
  toneBoostMarginDb: 12,
};

export const DEFAULT_SETTINGS: AppSettings = {
  numBands: 25,
  gainRangeDb: 12,
  motorMode: "automatic",
  autoStart: false,
  hapticFeedback: true,
  showValues: true,
  spectrumAnalyzer: "off",
  profilesByOutput: true,
  excludedApps: [],
  autoCheck: false,
  usageProfile: "mobile",
  language: "es",
  theme: {
    paletteKey: "p1",
    accentColor: "#A7EBF2",
    isDark: true,
    followSystem: true,
    highContrast: false,
  },
};

export class AudioRepository {
  private state: EqualizerState;
  private settings: AppSettings;
  private customPresets: Record<string, EqualizerState> = {};
  private listeners: Set<() => void> = new Set();

  constructor() {
    this.state = DEFAULT_STATE;
    this.settings = DEFAULT_SETTINGS;
    // Note: loadFromStorage is explicitly called in client useEffect to avoid SSR hydration mismatch
  }

  public loadFromStorage(): void {
    if (typeof window === "undefined") return;
    try {
      const storedSettings = localStorage.getItem("auraline_settings");
      if (storedSettings) {
        this.settings = { ...DEFAULT_SETTINGS, ...JSON.parse(storedSettings) };
      }
      const storedState = localStorage.getItem("auraline_state");
      if (storedState) {
        const parsed = JSON.parse(storedState);
        this.state = {
          ...DEFAULT_STATE,
          ...parsed,
          bands: parsed.bands?.length ? parsed.bands : generateEqualizerBands(this.settings.numBands),
        };
      }
      const storedPresets = localStorage.getItem("auraline_presets");
      if (storedPresets) {
        this.customPresets = JSON.parse(storedPresets);
      }
    } catch (e) {
      console.error("Failed to load auraline storage:", e);
    }
  }

  private persist(): void {
    if (typeof window === "undefined") return;
    try {
      localStorage.setItem("auraline_state", JSON.stringify(this.state));
      localStorage.setItem("auraline_settings", JSON.stringify(this.settings));
      localStorage.setItem("auraline_presets", JSON.stringify(this.customPresets));
    } catch {}
  }

  private notify(): void {
    this.persist();
    auraEngine.setState(this.state);
    this.listeners.forEach((fn) => fn());
  }

  public subscribe(callback: () => void): () => void {
    this.listeners.add(callback);
    return () => {
      this.listeners.delete(callback);
    };
  }

  public getState(): EqualizerState {
    return this.state;
  }

  public getSettings(): AppSettings {
    return this.settings;
  }

  public getEngineStatus(): EngineStatus {
    return auraEngine.getStatus();
  }

  public toggleMotor(enabled: boolean): void {
    this.state = { ...this.state, motorEnabled: enabled };
    this.notify();
  }

  public setEffectsEnabled(enabled: boolean): void {
    this.state = { ...this.state, effectsEnabled: enabled };
    this.notify();
  }

  public setPreamp(db: number): void {
    this.state = { ...this.state, preampDb: clampPreampDb(db) };
    this.notify();
  }

  public setBandGain(bandId: number, gainDb: number): void {
    const range = this.state.gainRangeDb;
    const clampedGain = clampBandGainDb(gainDb, range);
    const bands = this.state.bands.map((b) =>
      b.id === bandId ? { ...b, gainDb: clampedGain } : b
    );
    this.state = { ...this.state, bands, presetName: "Personalizado" };
    this.notify();
  }

  public setSubBass(db: number): void {
    const margin = this.state.toneBoostMarginDb;
    this.state = {
      ...this.state,
      tone: { ...this.state.tone, subDb: clampToneDb(db, margin) },
    };
    this.notify();
  }

  public setSubCutoff(hz: number): void {
    this.state = {
      ...this.state,
      tone: { ...this.state.tone, subCutoffHz: clampSubCutoffHz(hz) },
    };
    this.notify();
  }

  public setTone(bassDb: number, midDb: number, trebleDb: number): void {
    const margin = this.state.toneBoostMarginDb;
    this.state = {
      ...this.state,
      tone: {
        ...this.state.tone,
        bassDb: clampToneDb(bassDb, margin),
        midDb: clampToneDb(midDb, 12),
        trebleDb: clampToneDb(trebleDb, 12),
      },
    };
    this.notify();
  }

  public setToneFrequencies(bassHz: number, trebleHz: number): void {
    const safeBass = clampBassCrossHz(bassHz);
    const safeTreble = Math.max(safeBass + 800, clampTrebleCrossHz(trebleHz));
    this.state = {
      ...this.state,
      tone: {
        ...this.state.tone,
        bassFrequencyHz: safeBass,
        trebleFrequencyHz: safeTreble,
      },
    };
    this.notify();
  }

  public setBypass(enabled: boolean): void {
    this.state = { ...this.state, bypass: enabled };
    this.notify();
  }

  public setGainRange(db: number): void {
    const range = Math.min(36, Math.max(6, db));
    this.settings = { ...this.settings, gainRangeDb: range };
    const previousRange = Math.min(36, Math.max(6, this.state.gainRangeDb || 12));
    const scale = range / previousRange;

    // Solo se escalan las bandas levantadas o recortadas; las bandas en 0 dB permanecen en 0 dB.
    const adjustedBands = this.state.bands.map((b) => {
      if (Math.abs(b.gainDb) < 0.05) {
        return { ...b, gainDb: 0 };
      }
      const scaled = b.gainDb * scale;
      return {
        ...b,
        gainDb: Math.round(clampBandGainDb(scaled, range) * 10) / 10,
      };
    });

    this.state = { ...this.state, gainRangeDb: range, bands: adjustedBands };
    this.notify();
  }

  public applyFactoryPreset(name: string): boolean {
    const preset = FactoryPresets.find((p) => p.name === name);
    if (!preset) return false;

    const bands = this.state.bands.length
      ? this.state.bands
      : generateEqualizerBands(this.state.numBands);
    const centers = bands.map((_, i) => bandCenterHz(bands, i));
    const range = Math.min(36, Math.max(6, this.state.gainRangeDb || 12));

    if (preset.name === "Plano" || Object.keys(preset.gains).length === 0) {
      const flattened = bands.map((b) => ({ ...b, gainDb: 0 }));
      this.state = { ...this.state, bands: flattened, presetName: preset.name };
      this.notify();
      return true;
    }

    const newGains = new Float32Array(bands.length);
    // Las tablas de preajustes están calibradas sobre una referencia base de 12 dB.
    // Al escalar de forma proporcional al rango de dB seleccionado (range / 12),
    // a 6 dB las bandas se elevan un poco (0.5x), a 12 dB suben más (1.0x), y a 24, 30 o 36 dB
    // suben en la misma proporción sin topar el techo del fader ni saturar la UI.
    const scaleFactor = range / 12.0;

    Object.entries(preset.gains).forEach(([freqStr, gainVal]) => {
      const freq = parseFloat(freqStr);
      let bestIndex = 0;
      let minDiff = Math.abs(centers[0] - freq);
      for (let i = 1; i < centers.length; i++) {
        const diff = Math.abs(centers[i] - freq);
        if (diff < minDiff) {
          minDiff = diff;
          bestIndex = i;
        }
      }
      // Solo las bandas de la receta reciben ganancia; las demás permanecen en 0
      const scaled = Math.round(clampBandGainDb(gainVal * scaleFactor, range) * 10) / 10;
      newGains[bestIndex] = clampBandGainDb(newGains[bestIndex] + scaled, range);
    });

    const tuned = bands.map((b, i) => ({ ...b, gainDb: newGains[i] }));
    this.state = { ...this.state, bands: tuned, presetName: preset.name };
    this.notify();
    return true;
  }

  public focusLowSource(key: string, scale: number = 1.0): boolean {
    const focus = focusFor(key);
    if (!focus) return false;

    const bands = this.state.bands.length
      ? this.state.bands
      : generateEqualizerBands(this.state.numBands);
    const steps = resolveFocus(bands, focus);
    if (!steps.length) return false;

    const range = Math.min(36, Math.max(6, this.state.gainRangeDb || 12));
    const rangeScale = range / 12.0;
    const effectiveScale = scale * rangeScale;

    const gains = bands.map((b) => b.gainDb);
    const primary = steps[0];
    const targetDelta = primary[1] * effectiveScale;
    const alreadyApplied =
      Math.abs(gains[primary[0]]) >= Math.abs(targetDelta) - 0.5 &&
      (Math.sign(gains[primary[0]]) === Math.sign(targetDelta) || targetDelta === 0);
    const direction = alreadyApplied ? -1 : 1;

    steps.forEach(([index, delta]) => {
      const applyDelta = Math.round(delta * effectiveScale * direction * 10) / 10;
      gains[index] = clampBandGainDb(gains[index] + applyDelta, range);
    });

    const tuned = bands.map((b, i) => ({ ...b, gainDb: gains[i] }));
    this.state = { ...this.state, bands: tuned, presetName: "Personalizado" };
    this.notify();
    return true;
  }

  public setBalance(balance: number): void {
    this.state = {
      ...this.state,
      stereo: { ...this.state.stereo, balance: Math.min(100, Math.max(-100, balance)) },
    };
    this.notify();
  }

  public setExpansion(expansion: number): void {
    this.state = {
      ...this.state,
      stereo: { ...this.state.stereo, expansion: Math.min(2.0, Math.max(1.0, expansion)) },
    };
    this.notify();
  }

  public setVolume(percent: number): void {
    this.state = {
      ...this.state,
      volume: { ...this.state.volume, percent: Math.min(100, Math.max(0, percent)) },
    };
    this.notify();
  }

  public setReverb(params: ReverbParams): void {
    this.state = {
      ...this.state,
      reverb: {
        ...params,
        size: Math.min(1, Math.max(0, params.size)),
        decay: Math.min(1, Math.max(0, params.decay)),
        damping: Math.min(1, Math.max(0, params.damping)),
        mix: Math.min(1, Math.max(0, params.mix)),
      },
    };
    this.notify();
  }

  public applyReverbPreset(name: string): void {
    const preset = ReverbPresets.find((p) => p.name === name);
    if (!preset) return;
    this.state = {
      ...this.state,
      reverb: preset.params,
      effectsEnabled: true,
    };
    this.notify();
  }

  public setCompressor(params: CompressorParams): void {
    this.state = {
      ...this.state,
      compressor: {
        ...params,
        threshold: Math.min(0, Math.max(-60, params.threshold)),
        ratio: Math.min(20, Math.max(1, params.ratio)),
        attackMs: Math.min(100, Math.max(1, params.attackMs)),
        releaseMs: Math.min(500, Math.max(10, params.releaseMs)),
        makeupGain: Math.min(24, Math.max(0, params.makeupGain)),
      },
    };
    this.notify();
  }

  public setGate(params: GateParams): void {
    this.state = {
      ...this.state,
      gate: {
        ...params,
        thresholdDb: Math.min(-20, Math.max(-90, params.thresholdDb)),
        ratio: Math.min(10, Math.max(1, params.ratio)),
      },
    };
    this.notify();
  }

  public setModules(modules: ModuleState): void {
    this.state = { ...this.state, modules };
    this.notify();
  }

  public applyUsageProfile(key: string): void {
    const profile = usageProfileByKey(key);
    this.settings = { ...this.settings, usageProfile: key, gainRangeDb: profile.gainRangeDb };

    const current = this.state;
    const clamped = current.bands.map((b) => ({
      ...b,
      gainDb: clampBandGainDb(b.gainDb, profile.gainRangeDb),
    }));

    const oldBassMax = current.toneBoostMarginDb;
    const bassScale = oldBassMax > 0 ? profile.toneBoostMarginDb / oldBassMax : 1.0;
    const newTone: ToneControls = {
      ...current.tone,
      subCutoffHz: profile.subCutoffHz,
      bassFrequencyHz: profile.bassFrequencyHz ?? current.tone.bassFrequencyHz,
      trebleFrequencyHz: profile.trebleFrequencyHz ?? current.tone.trebleFrequencyHz,
      bassDb: profile.defaultTone?.bassDb ?? clampToneDb(current.tone.bassDb * bassScale, profile.toneBoostMarginDb),
      subDb: profile.defaultTone?.subDb ?? clampToneDb(current.tone.subDb * bassScale, profile.toneBoostMarginDb),
      midDb: profile.defaultTone?.midDb ?? current.tone.midDb,
      trebleDb: profile.defaultTone?.trebleDb ?? current.tone.trebleDb,
    };

    this.state = {
      ...current,
      gainRangeDb: profile.gainRangeDb,
      toneBoostMarginDb: profile.toneBoostMarginDb,
      autoPreamp: profile.levelProtection,
      effectsEnabled: profile.effectsEnabled,
      modules: profile.modules,
      bands: clamped,
      tone: newTone,
      bypass: false,
    };

    if (profile.basePreset !== "Plano") {
      this.applyFactoryPreset(profile.basePreset);
    } else {
      this.notify();
    }
  }

  public resetControls(): void {
    this.state = {
      ...this.state,
      tone: {
        subDb: 0,
        bassDb: 0,
        trebleDb: 0,
        midDb: 0,
        bassFrequencyHz: 100,
        trebleFrequencyHz: 8000,
        subCutoffHz: 60,
      },
      stereo: {
        balance: 0,
        expansion: 1.0,
        mono: false,
        swapChannels: false,
        invertPhase: false,
      },
    };
    this.notify();
  }

  public resetAll(): void {
    this.state = {
      ...this.state,
      bands: generateEqualizerBands(this.state.numBands),
      tone: {
        subDb: 0,
        bassDb: 0,
        trebleDb: 0,
        midDb: 0,
        bassFrequencyHz: this.state.tone.bassFrequencyHz,
        trebleFrequencyHz: this.state.tone.trebleFrequencyHz,
        subCutoffHz: this.state.tone.subCutoffHz,
      },
      preampDb: 0,
      bypass: false,
      presetName: "Plano",
    };
    this.notify();
  }

  public setNumBands(count: number): void {
    const num = Math.min(25, Math.max(20, count));
    this.settings = { ...this.settings, numBands: num };
    this.state = {
      ...this.state,
      numBands: num,
      bands: generateEqualizerBands(num, this.state.gainRangeDb),
    };
    this.notify();
  }

  public setAutoPreamp(enabled: boolean): void {
    this.state = { ...this.state, autoPreamp: enabled };
    this.notify();
  }

  public setHapticFeedback(enabled: boolean): void {
    this.settings = { ...this.settings, hapticFeedback: enabled };
    this.notify();
  }

  public setAutoStart(enabled: boolean): void {
    this.settings = { ...this.settings, autoStart: enabled };
    this.notify();
  }

  public setProfilesByOutput(enabled: boolean): void {
    this.settings = { ...this.settings, profilesByOutput: enabled };
    this.notify();
  }

  public setAutoCheck(enabled: boolean): void {
    this.settings = { ...this.settings, autoCheck: enabled };
    this.notify();
  }

  public setPalette(key: string, accentColor: string): void {
    this.settings = {
      ...this.settings,
      theme: { ...this.settings.theme, paletteKey: key, accentColor },
    };
    this.notify();
  }

  public savePreset(name: string): void {
    const clean = name.trim() || "Preajuste";
    this.customPresets[clean] = { ...this.state, presetName: clean };
    this.state = { ...this.state, presetName: clean };
    this.notify();
  }

  public applyPreset(name: string): boolean {
    const target = this.customPresets[name];
    if (!target) return false;
    this.state = { ...target, presetName: name };
    this.notify();
    return true;
  }

  public deletePreset(name: string): void {
    delete this.customPresets[name];
    this.notify();
  }

  public listPresets(): string[] {
    return Object.keys(this.customPresets).sort();
  }

  public async runAudioCheck(): Promise<AudioCheckResult> {
    return auraEngine.runAudioCheck();
  }
}

export const audioRepo = new AudioRepository();
