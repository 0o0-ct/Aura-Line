import React from "react";
import {
  AuraCard,
  AuraSliderRow,
  AuraSwitch,
  SectionTitle,
  SelectableChip,
  StatusPill,
} from "./AuraComponents";
import { AudioRepository } from "@/src/lib/audioRepository";
import { EngineStatus, EqualizerState } from "@/src/lib/types";
import { ReverbPresets } from "@/src/lib/constants";
import { Sparkles } from "lucide-react";

interface EffectsScreenProps {
  repo: AudioRepository;
  state: EqualizerState;
  engine: EngineStatus;
  accentColor?: string;
}

export function EffectsScreen({
  repo,
  state,
  engine,
  accentColor = "#00E5CC",
}: EffectsScreenProps) {
  const reverb = state.reverb;
  const compressor = state.compressor;
  const effectsOn = state.effectsEnabled && state.motorEnabled;

  const compressorStage = engine.stages.find((s) => s.name.startsWith("Compresor"));
  const reverbStage = engine.stages.find((s) => s.name.startsWith("Reverb"));

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* 6-Module Chain Card */}
      <AuraCard>
        <SectionTitle text="Cadena de módulos" trailing="orden real del motor" />
        <div className="divide-y divide-white/5 mt-1">
          <ModuleRow
            title="Ecualizador"
            subtitle="bandas + tono"
            checked={state.modules.equalizer}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, equalizer: checked })
            }
            accentColor={accentColor}
          />
          <ModuleRow
            title="Puerta / Expansor"
            subtitle="limpia el ruido de fondo"
            checked={state.modules.gate}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, gate: checked })
            }
            accentColor={accentColor}
          />
          <ModuleRow
            title="Compresor multibanda"
            subtitle="3 bandas"
            checked={state.modules.compressor}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, compressor: checked })
            }
            accentColor={accentColor}
          />
          <ModuleRow
            title="Limitador"
            subtitle="techo a −1 dBFS"
            checked={state.modules.limiter}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, limiter: checked })
            }
            accentColor={accentColor}
          />
          <ModuleRow
            title="Reverb"
            subtitle="sala y cola"
            checked={state.modules.reverb}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, reverb: checked })
            }
            accentColor={accentColor}
          />
          <ModuleRow
            title="Anchura estéreo"
            subtitle="Virtualizer"
            checked={state.modules.width}
            onCheckedChange={(checked) =>
              repo.setModules({ ...state.modules, width: checked })
            }
            accentColor={accentColor}
          />
        </div>
      </AuraCard>

      {/* Noise Gate / Expander Card */}
      <AuraCard>
        <SectionTitle
          text="Puerta / Expansor"
          trailing={state.modules.gate ? "activa" : "desactivada"}
        />
        <AuraSliderRow
          label="Umbral"
          valueText={`${Math.round(state.gate.thresholdDb)} dB`}
          value={state.gate.thresholdDb}
          valueRange={[-80, -20]}
          onValueChange={(val) => repo.setGate({ ...state.gate, thresholdDb: val })}
          enabled={effectsOn && state.modules.gate}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Expansión"
          valueText={`${state.gate.ratio.toFixed(1)}:1`}
          value={state.gate.ratio}
          valueRange={[1.0, 10.0]}
          step={0.1}
          onValueChange={(val) => repo.setGate({ ...state.gate, ratio: val })}
          enabled={effectsOn && state.modules.gate}
          accentColor={accentColor}
        />
        <p className="text-[11px] text-[#8E9BAE] mt-1">
          Por debajo del umbral la señal se atenúa: quita el siseo entre canciones y en pasajes silenciosos.
        </p>
      </AuraCard>

      {/* Master Effects Activation & Status */}
      <AuraCard>
        <div className="flex items-center justify-between mb-3">
          <div>
            <h4 className="text-sm font-semibold text-white tracking-tight">Procesado de efectos</h4>
            <p className="text-xs text-[#8E9BAE] mt-0.5">
              {!state.motorEnabled
                ? "El motor está apagado"
                : state.effectsEnabled
                ? "Compresor y reverb activos"
                : "Compresor y reverb desactivados"}
            </p>
          </div>
          <AuraSwitch
            checked={state.effectsEnabled}
            onCheckedChange={(checked) => repo.setEffectsEnabled(checked)}
            enabled={state.motorEnabled}
            accentColor={accentColor}
          />
        </div>

        <div className="flex items-center gap-2">
          <StatusPill
            text={compressorStage?.active ? "Compresor activo" : "Compresor inactivo"}
            color={compressorStage?.active ? "#34D399" : "#8E9BAE"}
          />
          <StatusPill
            text={reverbStage?.active ? "Reverb activa" : "Reverb inactiva"}
            color={reverbStage?.active ? "#34D399" : "#8E9BAE"}
          />
        </div>
      </AuraCard>

      {/* Multiband Compressor Card */}
      <AuraCard>
        <SectionTitle text="Compresor multibanda" trailing="3 bandas" />
        <AuraSliderRow
          label="Umbral"
          valueText={`${Math.round(compressor.threshold)} dB`}
          value={compressor.threshold}
          valueRange={[-60, 0]}
          onValueChange={(val) => repo.setCompressor({ ...compressor, threshold: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Proporción"
          valueText={`${compressor.ratio.toFixed(1)}:1`}
          value={compressor.ratio}
          valueRange={[1.0, 20.0]}
          step={0.5}
          onValueChange={(val) => repo.setCompressor({ ...compressor, ratio: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Ataque"
          valueText={`${Math.round(compressor.attackMs)} ms`}
          value={compressor.attackMs}
          valueRange={[1, 100]}
          step={1}
          onValueChange={(val) => repo.setCompressor({ ...compressor, attackMs: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Liberación"
          valueText={`${Math.round(compressor.releaseMs)} ms`}
          value={compressor.releaseMs}
          valueRange={[10, 500]}
          step={5}
          onValueChange={(val) => repo.setCompressor({ ...compressor, releaseMs: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Ganancia de compensación"
          valueText={`${Math.round(compressor.makeupGain)} dB`}
          value={compressor.makeupGain}
          valueRange={[0, 24]}
          step={0.5}
          onValueChange={(val) => repo.setCompressor({ ...compressor, makeupGain: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <p className="text-[11px] text-[#8E9BAE] mt-1">
          Umbral más bajo = comprime antes. Los valores por defecto (−28 dB, 4:1 y +6 dB de compensación) ya se notan en música normal.
        </p>
      </AuraCard>

      {/* Reverb Card */}
      <AuraCard>
        <SectionTitle text="Reverb" trailing="elige una sala" />
        <div className="flex items-center gap-2 overflow-x-auto pb-1 mt-2 mb-3 scrollbar-none">
          {ReverbPresets.map((preset) => (
            <SelectableChip
              key={preset.name}
              text={preset.name}
              selected={
                state.effectsEnabled &&
                JSON.stringify(state.reverb) === JSON.stringify(preset.params)
              }
              onClick={() => repo.applyReverbPreset(preset.name)}
              accentColor={accentColor}
            />
          ))}
        </div>

        <AuraSliderRow
          label="Tamaño"
          valueText={`${Math.round(reverb.size * 100)}%`}
          value={reverb.size}
          valueRange={[0, 1]}
          step={0.05}
          onValueChange={(val) => repo.setReverb({ ...reverb, size: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Decaimiento"
          valueText={`${Math.round(reverb.decay * 100)}%`}
          value={reverb.decay}
          valueRange={[0, 1]}
          step={0.05}
          onValueChange={(val) => repo.setReverb({ ...reverb, decay: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Amortiguación"
          valueText={`${Math.round(reverb.damping * 100)}%`}
          value={reverb.damping}
          valueRange={[0, 1]}
          step={0.05}
          onValueChange={(val) => repo.setReverb({ ...reverb, damping: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
        <AuraSliderRow
          label="Mezcla"
          valueText={`${Math.round(reverb.mix * 100)}%`}
          value={reverb.mix}
          valueRange={[0, 1]}
          step={0.05}
          onValueChange={(val) => repo.setReverb({ ...reverb, mix: val })}
          enabled={effectsOn}
          accentColor={accentColor}
        />
      </AuraCard>

      {/* Bottom info & Recommended settings */}
      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => {
            repo.setReverb({
              damping: 0.4,
              filter: 0.5,
              decay: 0.45,
              preDelay: 0.02,
              predelayMix: 0.3,
              size: 0.5,
              mix: 0.3,
            });
            repo.setCompressor({
              threshold: -28,
              ratio: 4.0,
              attackMs: 10,
              releaseMs: 200,
              makeupGain: 6,
              limiterCeiling: -1,
            });
            repo.setEffectsEnabled(true);
          }}
          className="text-xs font-semibold px-4 py-2.5 rounded-full bg-white/5 hover:bg-white/10 text-white border border-white/10 flex items-center gap-1.5 transition-all active:scale-95"
          style={{ color: accentColor }}
        >
          <Sparkles className="w-3.5 h-3.5" />
          Ajustes recomendados
        </button>

        <span className="text-[11px] text-[#8E9BAE]">
          El limitador de salida se mantiene a −1 dBFS
        </span>
      </div>
    </div>
  );
}

function ModuleRow({
  title,
  subtitle,
  checked,
  onCheckedChange,
  accentColor,
}: {
  title: string;
  subtitle: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  accentColor?: string;
}) {
  return (
    <div className="flex items-center justify-between py-2.5">
      <div>
        <div className="text-xs font-semibold text-white">{title}</div>
        <div className="text-[10px] text-[#8E9BAE]">{subtitle}</div>
      </div>
      <AuraSwitch
        checked={checked}
        onCheckedChange={onCheckedChange}
        accentColor={accentColor}
      />
    </div>
  );
}
