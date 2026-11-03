import React, { useState } from "react";
import {
  AuraCard,
  AuraSliderRow,
  SectionTitle,
  SelectableChip,
  StatusPill,
} from "./AuraComponents";
import { BandFader } from "./BandFader";
import { ResponseCurve } from "./ResponseCurve";
import { SpectrumBars } from "./SpectrumBars";
import {
  FactoryPresets,
  FrequencyGuides,
  LowFrequencyRoles,
  LowFrequencySources,
  bandCenterHz,
  focusFor,
  formatFrequency,
  formatGain,
  generateEqualizerBands,
  ownerShortNameFor,
  resolveFocus,
} from "@/src/lib/constants";
import {
  AudioRepository,
} from "@/src/lib/audioRepository";
import {
  EngineStatus,
  EqualizerState,
  SourceCategory,
} from "@/src/lib/types";
import { ChevronDown, ChevronUp, BookOpen, X, Plus, Minus, Save, RotateCcw } from "lucide-react";
import { cn } from "@/src/lib/utils";

interface EqualizerScreenProps {
  repo: AudioRepository;
  state: EqualizerState;
  engine: EngineStatus;
  spectrum: number[];
  accentColor?: string;
}

const WindowSizes = [8, 12, 25];

export function EqualizerScreen({
  repo,
  state,
  engine,
  spectrum,
  accentColor = "#00E5CC",
}: EqualizerScreenProps) {
  const [windowSizeIndex, setWindowSizeIndex] = useState(0); // 0 = 8, 1 = 12, 2 = all
  const [pageIndex, setPageIndex] = useState(0);
  const [showGuide, setShowGuide] = useState(false);
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [pendingPresetName, setPendingPresetName] = useState("");
  const [expandedEngine, setExpandedEngine] = useState(false);

  const bands = state.bands.length ? state.bands : generateEqualizerBands(state.numBands);
  const range = Math.max(6, Math.min(36, state.gainRangeDb));
  const perWindow = WindowSizes[windowSizeIndex];

  // Chunk bands for pagination
  const chunks: typeof bands[] = [];
  if (windowSizeIndex === 2) {
    chunks.push(bands);
  } else {
    for (let i = 0; i < bands.length; i += perWindow) {
      chunks.push(bands.slice(i, i + perWindow));
    }
  }
  const currentChunk = chunks[pageIndex] || chunks[0] || bands;

  // Selected factory preset hint
  const selectedFactory = FactoryPresets.find((p) => p.name === state.presetName);
  const customPresets = repo.listPresets();

  // Find strongest band for feedback
  const strongest = [...bands].sort(
    (a, b) => Math.abs(b.gainDb) - Math.abs(a.gainDb)
  )[0];

  const categoryColor = (cat: SourceCategory) => {
    switch (cat) {
      case SourceCategory.PERCUSION:
        return "#FF9F43";
      case SourceCategory.ELECTRONIC:
        return "#2BD9C0";
      case SourceCategory.STRING:
        return "#7C9CFF";
      case SourceCategory.WIND:
        return "#B388FF";
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* Engine Status Card */}
      <AuraCard>
        <div className="flex items-center justify-between">
          <div>
            <h4 className="text-sm font-semibold text-white tracking-tight">Cadena de procesado</h4>
            <p className="text-xs text-[#8E9BAE] mt-0.5">
              {engine.running
                ? `${engine.activeStages}/${engine.stages.length} etapas activas · ${engine.sessions.length} sesión(es)`
                : "Inactiva: activa el motor para procesar la salida"}
            </p>
          </div>
          <StatusPill
            text={engine.running ? "EN VIVO" : "INACTIVA"}
            color={engine.running ? "#34D399" : "#8E9BAE"}
          />
        </div>

        {expandedEngine && (
          <div className="mt-3 pt-3 border-t border-white/10 space-y-2">
            {engine.stages.map((stage, idx) => (
              <div key={idx} className="flex items-center justify-between text-xs py-1">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      "w-2 h-2 rounded-full",
                      stage.active ? "bg-emerald-400 shadow-[0_0_6px_#34d399]" : "bg-white/20"
                    )}
                  />
                  <span className="text-white font-medium">{stage.name}</span>
                </div>
                <span className="text-[#8E9BAE] text-[11px]">{stage.detail}</span>
              </div>
            ))}
          </div>
        )}

        <button
          type="button"
          onClick={() => setExpandedEngine(!expandedEngine)}
          className="mt-2 text-xs font-semibold flex items-center gap-1 transition-colors"
          style={{ color: accentColor }}
        >
          {expandedEngine ? (
            <>
              Ocultar detalle <ChevronUp className="w-3.5 h-3.5" />
            </>
          ) : (
            <>
              Ver estado real del motor <ChevronDown className="w-3.5 h-3.5" />
            </>
          )}
        </button>
      </AuraCard>

      {/* Response Curve & Spectrum RTA */}
      <AuraCard>
        <div className="flex items-center justify-between mb-2">
          <SectionTitle
            text="Curva de respuesta"
            trailing={state.presetName}
            className="mb-0"
          />
          <button
            type="button"
            onClick={() => setShowGuide(true)}
            className="text-xs font-semibold px-2.5 py-1 rounded-full bg-white/5 hover:bg-white/10 transition-colors flex items-center gap-1.5"
            style={{ color: accentColor }}
          >
            <BookOpen className="w-3.5 h-3.5" />
            Guía
          </button>
        </div>

        <ResponseCurve
          bands={bands}
          gainRangeDb={range}
          accentColor={accentColor}
          height={110}
        />

        <div className="mt-3">
          <SpectrumBars
            levels={spectrum}
            count={bands.length}
            accentColor={accentColor}
            height={52}
          />
        </div>
      </AuraCard>

      {/* Presets Carousel */}
      <AuraCard>
        <SectionTitle text="Preajustes" trailing="según tablas de ecualización" />
        <div className="flex items-center gap-2 overflow-x-auto pb-1 mt-2 scrollbar-none">
          {FactoryPresets.map((preset) => (
            <SelectableChip
              key={preset.name}
              text={preset.name}
              selected={state.presetName === preset.name}
              onClick={() => {
                if (preset.name === "Plano") repo.resetAll();
                else repo.applyFactoryPreset(preset.name);
                setPageIndex(0);
              }}
              accentColor={accentColor}
            />
          ))}
        </div>

        {selectedFactory?.hint && (
          <p className="text-xs text-[#8E9BAE] mt-2 italic">{selectedFactory.hint}</p>
        )}

        {strongest && Math.abs(strongest.gainDb) >= 0.5 && (
          <p className="text-xs font-medium mt-1.5" style={{ color: accentColor }}>
            Aplicado: {formatGain(strongest.gainDb)} dB en{" "}
            {formatFrequency(bandCenterHz(bands, bands.indexOf(strongest)))} Hz
          </p>
        )}

        {!state.motorEnabled && (
          <p className="text-xs text-amber-400 mt-2">
            El motor está apagado: actívalo para escuchar el preajuste.
          </p>
        )}

        {customPresets.length > 0 && (
          <div className="mt-3 pt-3 border-t border-white/10">
            <span className="text-[11px] text-[#8E9BAE] uppercase tracking-wider font-semibold block mb-2">
              Mis Preajustes
            </span>
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {customPresets.map((name) => (
                <div key={name} className="flex items-center gap-1">
                  <SelectableChip
                    text={name}
                    selected={state.presetName === name}
                    onClick={() => repo.applyPreset(name)}
                    accentColor={accentColor}
                  />
                  <button
                    type="button"
                    onClick={() => repo.deletePreset(name)}
                    className="p-1 text-white/40 hover:text-red-400 text-xs"
                    title="Eliminar preajuste"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </AuraCard>

      {/* Bands Fader Section */}
      <AuraCard>
        <div className="flex items-center justify-between mb-3">
          <SectionTitle
            text="Bandas"
            trailing={`${state.numBands} · ±${Math.round(range)} dB`}
            className="mb-0"
          />
        </div>

        {/* Window and Range Selectors */}
        <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
          <div className="flex items-center gap-1.5">
            <SelectableChip
              text="Ventana 8"
              selected={windowSizeIndex === 0}
              onClick={() => {
                setWindowSizeIndex(0);
                setPageIndex(0);
              }}
              accentColor={accentColor}
            />
            <SelectableChip
              text="12"
              selected={windowSizeIndex === 1}
              onClick={() => {
                setWindowSizeIndex(1);
                setPageIndex(0);
              }}
              accentColor={accentColor}
            />
            <SelectableChip
              text="Todas"
              selected={windowSizeIndex === 2}
              onClick={() => {
                setWindowSizeIndex(2);
                setPageIndex(0);
              }}
              accentColor={accentColor}
            />
          </div>

          <div className="flex items-center gap-1.5">
            {[6, 12, 24, 30, 36].map((v) => (
              <SelectableChip
                key={v}
                text={`±${v}`}
                selected={Math.round(range) === v}
                onClick={() => repo.setGainRange(v)}
                accentColor={accentColor}
              />
            ))}
          </div>
        </div>

        {/* Faders Area */}
        <div className="w-full overflow-x-auto pb-2">
          <div className="flex items-center justify-around gap-1 min-w-full">
            {currentChunk.map((band) => {
              const index = bands.indexOf(band);
              const center = bandCenterHz(bands, index);
              return (
                <BandFader
                  key={band.id}
                  value={band.gainDb}
                  onValueChange={(val) => repo.setBandGain(band.id, val)}
                  valueRange={[-range, range]}
                  height={155}
                  label={formatFrequency(center)}
                  valueText={formatGain(band.gainDb)}
                  highlighted={band.gainDb !== 0}
                  subtitle={ownerShortNameFor(center)}
                  accentColor={accentColor}
                />
              );
            })}
          </div>
        </div>

        {/* Pagination indicator if chunked */}
        {windowSizeIndex !== 2 && chunks.length > 1 && (
          <div className="flex items-center justify-center gap-3 mt-3 pt-2 border-t border-white/5">
            <button
              type="button"
              disabled={pageIndex === 0}
              onClick={() => setPageIndex((p) => Math.max(0, p - 1))}
              className="text-xs font-semibold px-2 py-1 rounded bg-white/5 hover:bg-white/10 disabled:opacity-30 disabled:pointer-events-none text-white"
            >
              ← Anterior
            </button>
            <span className="text-xs text-[#8E9BAE] font-medium font-mono">
              {pageIndex + 1} / {chunks.length}
            </span>
            <button
              type="button"
              disabled={pageIndex >= chunks.length - 1}
              onClick={() => setPageIndex((p) => Math.min(chunks.length - 1, p + 1))}
              className="text-xs font-semibold px-2 py-1 rounded bg-white/5 hover:bg-white/10 disabled:opacity-30 disabled:pointer-events-none text-white"
            >
              Siguiente →
            </button>
          </div>
        )}

        <p className="text-[11px] text-[#8E9BAE] mt-3">
          Doble toque en un fader para volver a 0 dB
        </p>
      </AuraCard>

      {/* Dedicated Low Frequency Sources Card */}
      <AuraCard>
        <SectionTitle
          text="Fuentes graves"
          trailing={`${LowFrequencySources.length} instrumentos`}
        />
        <p className="text-xs text-[#8E9BAE] mb-3">
          Cada banda grave tiene un dueño. El botón + prepara esa fuente sobre tu curva
          actual, sin borrarla.
        </p>

        <div className="divide-y divide-white/5">
          {LowFrequencySources.map((source) => {
            const steps = focusFor(source.key)
              ? resolveFocus(bands, focusFor(source.key)!)
              : [];
            const primaryStep = steps[0];
            const primaryIndex = primaryStep ? primaryStep[0] : null;
            const role = LowFrequencyRoles.find((r) => r.owner.key === source.key);
            const shared = role ? role.companions.map((c) => c.name) : [];
            const gain = primaryIndex !== null ? bands[primaryIndex]?.gainDb || 0 : 0;
            const rangeScale = range / 12.0;
            const targetDelta = primaryStep ? primaryStep[1] * rangeScale : 0;
            const applied =
              primaryStep !== undefined &&
              Math.abs(gain) >= Math.abs(targetDelta) - 0.5 &&
              (Math.sign(gain) === Math.sign(targetDelta) || targetDelta === 0);

            return (
              <div
                key={source.key}
                className="flex items-center justify-between py-2.5 gap-2"
              >
                <div className="flex items-center gap-2.5 flex-1 min-w-0">
                  <span
                    className="w-2.5 h-2.5 rounded-full shrink-0"
                    style={{ backgroundColor: categoryColor(source.category) }}
                  />
                  <div className="min-w-0">
                    <div className="text-xs font-semibold text-white truncate">
                      {source.name}
                    </div>
                    <div className="text-[10px] text-[#8E9BAE] truncate">
                      {source.fundamentalHz[0]}–{source.fundamentalHz[1]} Hz
                      {shared.length > 0 && ` · con ${shared.join(", ")}`}
                    </div>
                  </div>
                </div>

                {primaryIndex !== null && (
                  <div className="flex items-center gap-2 text-right shrink-0">
                    <span
                      className="text-xs font-semibold font-mono"
                      style={{ color: accentColor }}
                    >
                      {formatFrequency(bandCenterHz(bands, primaryIndex))}
                    </span>
                    <span
                      className={cn(
                        "text-xs font-mono font-semibold w-12 text-right",
                        gain !== 0 ? "text-white" : "text-[#8E9BAE]"
                      )}
                    >
                      {formatGain(gain)} dB
                    </span>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => repo.focusLowSource(source.key)}
                  className={cn(
                    "w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs shrink-0 transition-transform active:scale-90 border",
                    applied
                      ? "bg-white/20 text-white border-white/40 shadow-sm"
                      : "bg-white/5 text-[#8E9BAE] border-white/10 hover:bg-white/10"
                  )}
                  style={applied ? { backgroundColor: accentColor, color: "#07090E" } : undefined}
                  title={applied ? "Retirar foco" : "Sumar foco a la curva"}
                >
                  {applied ? <Minus className="w-3.5 h-3.5" /> : <Plus className="w-3.5 h-3.5" />}
                </button>
              </div>
            );
          })}
        </div>
      </AuraCard>

      {/* Input Gain / Preamp & A/B Bypass */}
      <AuraCard>
        <div className="flex items-center justify-between">
          <SectionTitle
            text="Ganancia de entrada"
            trailing={`${formatGain(state.preampDb)} dB`}
            className="mb-0"
          />
          <SelectableChip
            text={state.bypass ? "A/B activo" : "A/B"}
            selected={state.bypass}
            onClick={() => repo.setBypass(!state.bypass)}
            accentColor={accentColor}
          />
        </div>

        <AuraSliderRow
          label="Nivel de preamplificación"
          valueText={`${formatGain(state.preampDb)} dB`}
          value={state.preampDb}
          valueRange={[-15, 15]}
          onValueChange={(val) => repo.setPreamp(val)}
          enabled={state.motorEnabled}
          accentColor={accentColor}
        />

        {state.bypass && (
          <p className="text-xs text-amber-400 mt-1">
            Estás escuchando la curva plana para comparar. Pulsa A/B para volver.
          </p>
        )}
      </AuraCard>

      {/* Bottom Action Buttons */}
      <div className="flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => repo.resetAll()}
          className="px-4 py-2.5 rounded-full text-xs font-semibold bg-white/5 hover:bg-white/10 text-white border border-white/10 flex items-center gap-1.5 transition-all active:scale-95"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          Restablecer
        </button>

        <button
          type="button"
          onClick={() => {
            setPendingPresetName(state.presetName || "Mi Curva");
            setShowSaveDialog(true);
          }}
          className="px-4 py-2.5 rounded-full text-xs font-semibold text-[#07090E] flex items-center gap-1.5 transition-all active:scale-95 shadow-md"
          style={{ backgroundColor: accentColor }}
        >
          <Save className="w-3.5 h-3.5" />
          Guardar preajuste
        </button>
      </div>

      {/* Save Preset Dialog */}
      {showSaveDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md">
          <div className="w-full max-w-sm rounded-3xl bg-[#161F2E] border border-white/15 p-6 shadow-2xl">
            <h3 className="text-base font-semibold text-white mb-2">Guardar preajuste</h3>
            <p className="text-xs text-[#8E9BAE] mb-4">
              Introduce un nombre para recordar la configuración actual de bandas y efectos.
            </p>
            <input
              type="text"
              value={pendingPresetName}
              onChange={(e) => setPendingPresetName(e.target.value)}
              placeholder="Nombre del preajuste"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#090D14] border border-white/15 text-white text-sm focus:outline-none focus:border-white/30 mb-5"
            />
            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowSaveDialog(false)}
                className="px-4 py-2 rounded-full text-xs font-semibold text-[#8E9BAE] hover:text-white"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={() => {
                  repo.savePreset(pendingPresetName);
                  setShowSaveDialog(false);
                }}
                className="px-4 py-2 rounded-full text-xs font-semibold text-[#07090E]"
                style={{ backgroundColor: accentColor }}
              >
                Guardar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Frequency Guide Dialog */}
      {showGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md">
          <div className="w-full max-w-lg max-h-[85vh] rounded-3xl bg-[#161F2E] border border-white/15 p-6 shadow-2xl flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
              <h3 className="text-base font-semibold text-white">Guía de frecuencias y mapa de graves</h3>
              <button
                type="button"
                onClick={() => setShowGuide(false)}
                className="p-1 text-[#8E9BAE] hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="overflow-y-auto space-y-5 pr-2">
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-2">
                  Mapa de graves por banda
                </h4>
                <div className="space-y-3">
                  {LowFrequencyRoles.map((role, idx) => (
                    <div key={idx} className="p-3 rounded-xl bg-white/[0.03] border border-white/5">
                      <div className="text-xs font-semibold" style={{ color: accentColor }}>
                        {role.centerWindow[0]}–{role.centerWindow[1]} Hz · {role.owner.name}
                      </div>
                      <p className="text-[11px] text-[#8E9BAE] mt-1">
                        {role.companions.length > 0 && (
                          <span className="text-white/80">
                            Comparten banda: {role.companions.map((c) => c.name).join(", ")}.{" "}
                          </span>
                        )}
                        {role.owner.tip}
                      </p>
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-white mb-2">
                  Guía de zonas del espectro
                </h4>
                <div className="space-y-2">
                  {FrequencyGuides.map((guide, idx) => (
                    <div key={idx} className="p-2.5 rounded-lg bg-white/[0.02]">
                      <span className="text-xs font-semibold block" style={{ color: accentColor }}>
                        {guide.label}
                      </span>
                      <span className="text-[11px] text-[#8E9BAE]">{guide.uses}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="pt-4 border-t border-white/10 mt-4 flex justify-end">
              <button
                type="button"
                onClick={() => setShowGuide(false)}
                className="px-5 py-2 rounded-full text-xs font-semibold text-[#07090E]"
                style={{ backgroundColor: accentColor }}
              >
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
