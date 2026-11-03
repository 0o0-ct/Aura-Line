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
import { RotateCcw } from "lucide-react";

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
  const activeProfile = UsageProfiles.find((p) => p.key === settings.usageProfile);

  const formatKilo = (hz: number) => {
    return hz % 1000 === 0 ? `${hz / 1000} kHz` : `${(hz / 1000).toFixed(1)} kHz`;
  };

  const balanceText = () => {
    if (state.stereo.balance > 1) return `D ${Math.round(state.stereo.balance)}%`;
    if (state.stereo.balance < -1) return `I ${Math.round(-state.stereo.balance)}%`;
    return "Centro";
  };

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* 4-Region Tone Shaping Card */}
      <AuraCard>
        <SectionTitle
          text="Tono"
          trailing={`graves ${Math.round(tone.bassFrequencyHz)} · agudos ${formatKilo(tone.trebleFrequencyHz)}`}
        />

        <div className="mt-2 mb-3">
          <ToneCurve
            bassDb={tone.bassDb}
            midDb={tone.midDb}
            trebleDb={tone.trebleDb}
            bassHz={tone.bassFrequencyHz}
            trebleHz={tone.trebleFrequencyHz}
            maxDb={boostMax}
            accentColor={accentColor}
            height={92}
          />
        </div>

        <AuraSliderRow
          label="Sub-bajo · 0–100 Hz"
          valueText={`${formatGain(tone.subDb)} dB`}
          value={tone.subDb}
          valueRange={[-boostMax, boostMax]}
          onValueChange={(val) => repo.setSubBass(val)}
          enabled={powered}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Corte del sub-bajo"
          valueText={`${Math.round(tone.subCutoffHz)} Hz`}
          value={tone.subCutoffHz}
          valueRange={[20, 100]}
          onValueChange={(val) => repo.setSubCutoff(val)}
          enabled={powered}
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
          enabled={powered}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Medios"
          valueText={`${formatGain(tone.midDb)} dB`}
          value={tone.midDb}
          valueRange={[-12, 12]}
          onValueChange={(val) => repo.setTone(tone.bassDb, val, tone.trebleDb)}
          enabled={powered}
          accentColor={accentColor}
        />

        <AuraSliderRow
          label="Agudos"
          valueText={`${formatGain(tone.trebleDb)} dB`}
          value={tone.trebleDb}
          valueRange={[-12, 12]}
          onValueChange={(val) => repo.setTone(tone.bassDb, tone.midDb, val)}
          enabled={powered}
          accentColor={accentColor}
        />

        <p className="text-[11px] text-[#8E9BAE] mt-2">
          Graves hasta {Math.round(tone.bassFrequencyHz)} Hz · medios hasta{" "}
          {formatKilo(tone.trebleFrequencyHz)}
        </p>
      </AuraCard>

      {/* Crossover Frequencies Card */}
      <AuraCard>
        <SectionTitle text="Frecuencias de cruce" />
        <AuraSliderRow
          label="Graves / medios"
          valueText={`${Math.round(tone.bassFrequencyHz)} Hz`}
          value={tone.bassFrequencyHz}
          valueRange={[40, 400]}
          onValueChange={(val) => repo.setToneFrequencies(val, tone.trebleFrequencyHz)}
          enabled={powered}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Medios / agudos"
          valueText={formatKilo(tone.trebleFrequencyHz)}
          value={tone.trebleFrequencyHz}
          valueRange={[2000, 16000]}
          onValueChange={(val) => repo.setToneFrequencies(tone.bassFrequencyHz, val)}
          enabled={powered}
          accentColor={accentColor}
        />
        <p className="text-[11px] text-[#8E9BAE] mt-1">
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
          enabled={powered}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Anchura estéreo"
          valueText={`${Math.round((state.stereo.expansion - 1) * 100)}%`}
          value={state.stereo.expansion}
          valueRange={[1.0, 2.0]}
          step={0.05}
          onValueChange={(val) => repo.setExpansion(val)}
          enabled={powered}
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
