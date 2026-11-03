import React from "react";
import {
  AuraCard,
  AuraSliderRow,
  SectionTitle,
  SelectableChip,
} from "./AuraComponents";
import { ToneCurve } from "./ToneCurve";
import { AudioRepository } from "@/src/lib/audioRepository";
import { AppSettings, EqualizerState } from "@/src/lib/types";
import { UsageProfiles, formatGain } from "@/src/lib/constants";
import { RotateCcw, Volume2, Headphones, Radio, Speaker, Tv } from "lucide-react";

interface ToneScreenProps {
  repo: AudioRepository;
  state: EqualizerState;
  settings: AppSettings;
  accentColor?: string;
}

export function ToneScreen({
  repo,
  state,
  settings,
  accentColor = "#00E5CC",
}: ToneScreenProps) {
  const tone = state.tone;
  const powered = state.motorEnabled;
  const boostMax = Math.min(24, Math.max(12, state.toneBoostMarginDb));
  const activeProfile = UsageProfiles.find((p) => p.key === settings.usageProfile) || UsageProfiles[0];

  const formatKilo = (hz: number) => {
    return hz % 1000 === 0 ? `${hz / 1000} kHz` : `${(hz / 1000).toFixed(1)} kHz`;
  };

  const balanceText = () => {
    if (state.stereo.balance > 1) return `D ${Math.round(state.stereo.balance)}%`;
    if (state.stereo.balance < -1) return `I ${Math.round(-state.stereo.balance)}%`;
    return "Centro";
  };

  const profileIcon = (key: string) => {
    switch (key) {
      case "mobile":
        return <Volume2 className="w-3.5 h-3.5" />;
      case "wired":
        return <Headphones className="w-3.5 h-3.5" />;
      case "bluetooth":
        return <Radio className="w-3.5 h-3.5" />;
      case "external":
        return <Speaker className="w-3.5 h-3.5" />;
      case "surround":
        return <Tv className="w-3.5 h-3.5" />;
      default:
        return <Volume2 className="w-3.5 h-3.5" />;
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* Device Usage Profile Selector Card */}
      <AuraCard>
        <SectionTitle
          text="Perfil de salida acústica"
          trailing={activeProfile.name}
          className="mb-1"
        />
        <p className="text-xs text-[#8E9BAE] mb-3">
          Configura automáticamente los cruces de frecuencia, límites de sub-bajo y márgenes según tu dispositivo de audio.
        </p>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
          {UsageProfiles.map((profile) => {
            const isSelected = settings.usageProfile === profile.key;
            return (
              <button
                key={profile.key}
                type="button"
                onClick={() => repo.applyUsageProfile(profile.key)}
                className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-tight transition-all duration-200 border shadow-sm select-none shrink-0 ${
                  isSelected
                    ? "border-white/30 text-[#07090E] shadow-md scale-[1.02]"
                    : "bg-white/[0.04] text-[#8E9BAE] border-white/[0.08] hover:bg-white/[0.08] hover:text-white"
                }`}
                style={
                  isSelected
                    ? { backgroundColor: accentColor, color: "#07090E" }
                    : undefined
                }
              >
                {profileIcon(profile.key)}
                <span>{profile.name}</span>
              </button>
            );
          })}
        </div>

        {activeProfile.description && (
          <div className="mt-2.5 p-2 rounded-xl bg-white/[0.03] border border-white/5 flex items-center justify-between text-xs">
            <span className="text-[#A0AFC2] text-[11px] leading-relaxed">
              {activeProfile.description}
            </span>
            <span
              className="text-[10px] font-mono font-semibold px-2 py-0.5 rounded bg-white/5 border border-white/10 shrink-0 ml-2"
              style={{ color: accentColor }}
            >
              Corte {activeProfile.subCutoffHz} Hz · Cruces {activeProfile.bassFrequencyHz ?? 100}/{activeProfile.trebleFrequencyHz ? formatKilo(activeProfile.trebleFrequencyHz) : "8k"}
            </span>
          </div>
        )}
      </AuraCard>

      {/* 4-Region Tone Shaping Card */}
      <AuraCard>
        <SectionTitle
          text="Curva espectral de tono"
          trailing={`Graves <${Math.round(tone.bassFrequencyHz)} Hz · Agudos >${formatKilo(tone.trebleFrequencyHz)}`}
        />

        <div className="mt-2 mb-3">
          <ToneCurve
            bassDb={tone.bassDb}
            midDb={tone.midDb}
            trebleDb={tone.trebleDb}
            subDb={tone.subDb}
            subCutoffHz={tone.subCutoffHz}
            bassHz={tone.bassFrequencyHz}
            trebleHz={tone.trebleFrequencyHz}
            maxDb={boostMax}
            accentColor={accentColor}
            height={115}
          />
        </div>

        <AuraSliderRow
          label="Sub-bajo · 0–100 Hz"
          valueText={`${formatGain(tone.subDb)} dB`}
          value={tone.subDb}
          valueRange={[-boostMax, boostMax]}
          onValueChange={(val) => repo.setSubBass(val)}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Filtro de corte del sub-bajo"
          valueText={`${Math.round(tone.subCutoffHz)} Hz`}
          value={tone.subCutoffHz}
          valueRange={[20, 100]}
          onValueChange={(val) => repo.setSubCutoff(val)}
          accentColor={accentColor}
        />

        <p className="text-[11px] text-[#8E9BAE] py-1">
          {activeProfile?.key === "mobile"
            ? "El altavoz del móvil no reproduce por debajo de ~150 Hz: aquí el refuerzo se centra en 70–100 Hz, lo más grave que de verdad saca tu teléfono. Con auriculares baja el corte a 30–50 Hz para sentir el sub-grave puro."
            : "Por debajo del corte actúa el refuerzo extra de sub-bajo. Auriculares: 30–50 Hz · altavoces con subwoofer: 40–80 Hz · barra o 5.1: 80 Hz (el subwoofer hace el resto)."}
        </p>

        <AuraSliderRow
          label="Graves"
          valueText={`${formatGain(tone.bassDb)} dB`}
          value={tone.bassDb}
          valueRange={[-boostMax, boostMax]}
          onValueChange={(val) => repo.setTone(val, tone.midDb, tone.trebleDb)}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Medios"
          valueText={`${formatGain(tone.midDb)} dB`}
          value={tone.midDb}
          valueRange={[-12, 12]}
          onValueChange={(val) => repo.setTone(tone.bassDb, val, tone.trebleDb)}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Agudos"
          valueText={`${formatGain(tone.trebleDb)} dB`}
          value={tone.trebleDb}
          valueRange={[-12, 12]}
          onValueChange={(val) => repo.setTone(tone.bassDb, tone.midDb, val)}
          accentColor={accentColor}
        />

        {!powered && (
          <p className="text-[11px] text-amber-400/90 mt-1">
            Motor inactivo: activa el motor en la barra superior para procesar la salida en vivo.
          </p>
        )}
      </AuraCard>

      {/* Crossover Frequencies Card */}
      <AuraCard>
        <SectionTitle
          text="Frecuencias de cruce (Crossovers)"
          trailing={`${Math.round(tone.bassFrequencyHz)} Hz | ${formatKilo(tone.trebleFrequencyHz)}`}
        />
        <p className="text-xs text-[#8E9BAE] mb-3">
          Define con precisión analógica los puntos de separación espectral entre Graves, Medios y Agudos. Al mover los deslizadores, la frontera se desplaza en tiempo real sobre la curva.
        </p>

        <AuraSliderRow
          label="Cruce Graves / Medios"
          valueText={`${Math.round(tone.bassFrequencyHz)} Hz`}
          value={tone.bassFrequencyHz}
          valueRange={[40, 400]}
          step={5}
          onValueChange={(val) => repo.setToneFrequencies(val, tone.trebleFrequencyHz)}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Cruce Medios / Agudos"
          valueText={formatKilo(tone.trebleFrequencyHz)}
          value={tone.trebleFrequencyHz}
          valueRange={[2000, 16000]}
          step={100}
          onValueChange={(val) => repo.setToneFrequencies(tone.bassFrequencyHz, val)}
          accentColor={accentColor}
        />

        {/* Quick Crossover presets */}
        <div className="flex items-center gap-2 mt-2 pt-2 border-t border-white/5 overflow-x-auto pb-1 scrollbar-none">
          <span className="text-[10px] text-[#8E9BAE] font-medium uppercase tracking-wider shrink-0 mr-1">
            Cruces rápidos:
          </span>
          <button
            type="button"
            onClick={() => repo.setToneFrequencies(80, 4000)}
            className="px-2.5 py-1 rounded-md text-[11px] font-mono bg-white/5 hover:bg-white/10 text-white/80 border border-white/10 whitespace-nowrap"
          >
            Sub 80 / 4k
          </button>
          <button
            type="button"
            onClick={() => repo.setToneFrequencies(120, 6000)}
            className="px-2.5 py-1 rounded-md text-[11px] font-mono bg-white/5 hover:bg-white/10 text-white/80 border border-white/10 whitespace-nowrap"
          >
            Estudio 120 / 6k
          </button>
          <button
            type="button"
            onClick={() => repo.setToneFrequencies(220, 8000)}
            className="px-2.5 py-1 rounded-md text-[11px] font-mono bg-white/5 hover:bg-white/10 text-white/80 border border-white/10 whitespace-nowrap"
          >
            Voz 220 / 8k
          </button>
        </div>

        <p className="text-[11px] text-[#8E9BAE] mt-2">
          Cuerpo de voces y caja en 200–400 Hz · presencia en 2,5–5 kHz · aire por encima de 10 kHz
        </p>
      </AuraCard>

      {/* Stereo Image & Volume Card */}
      <AuraCard>
        <SectionTitle text="Imagen estéreo y volumen" />
        <AuraSliderRow
          label="Balance"
          valueText={balanceText()}
          value={state.stereo.balance}
          valueRange={[-100, 100]}
          onValueChange={(val) => repo.setBalance(val)}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Anchura estéreo"
          valueText={`${Math.round((state.stereo.expansion - 1) * 100)}%`}
          value={state.stereo.expansion}
          valueRange={[1.0, 2.0]}
          step={0.05}
          onValueChange={(val) => repo.setExpansion(val)}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Volumen multimedia"
          valueText={`${Math.round(state.volume.percent)}%`}
          value={state.volume.percent}
          valueRange={[0, 100]}
          step={1}
          onValueChange={(val) => repo.setVolume(val)}
          accentColor={accentColor}
        />
      </AuraCard>

      {/* Reset button */}
      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => repo.resetControls()}
          className="px-4 py-2.5 rounded-full text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 flex items-center gap-1.5 transition-all active:scale-95"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          Restablecer controles
        </button>
      </div>
    </div>
  );
}
