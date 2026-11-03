"use client";

import React, { useState, useEffect } from "react";
import { AudioRepository } from "@/src/lib/audioRepository";
import { auraEngine } from "@/src/lib/audioEngine";
import { EngineStatus, EqualizerState, AppSettings } from "@/src/lib/types";
import { AuraCard, SectionTitle, StatusPill, SelectableChip } from "./AuraComponents";
import { Activity, Radio, Volume2, ShieldAlert } from "lucide-react";

interface DiagnosticScreenProps {
  repo: AudioRepository;
  state: EqualizerState;
  settings: AppSettings;
  engine: EngineStatus;
  accentColor: string;
}

export function DiagnosticScreen({
  repo,
  state,
  settings,
  engine,
  accentColor,
}: DiagnosticScreenProps) {
  const [generatorMode, setGeneratorMode] = useState<"off" | "noise" | "sweep" | "808">("off");
  const [level, setLevel] = useState(35);
  const [rmsL, setRmsL] = useState(-30);
  const [rmsR, setRmsR] = useState(-30);
  const [peakL, setPeakL] = useState(-18);
  const [peakR, setPeakR] = useState(-18);
  const [clipping, setClipping] = useState(false);

  useEffect(() => {
    const unsub = auraEngine.subscribeSpectrum((levels) => {
      // Approximate RMS & Peak from spectrum
      const maxVal = Math.max(...levels, 0);
      const avgVal = levels.reduce((a, b) => a + b, 0) / (levels.length || 1);
      const pDb = maxVal > 0.001 ? Math.round(20 * Math.log10(maxVal)) : -90;
      const rDb = avgVal > 0.001 ? Math.round(20 * Math.log10(avgVal)) : -90;
      setPeakL(pDb);
      setPeakR(pDb);
      setRmsL(rDb);
      setRmsR(rDb);
      setClipping(pDb >= -0.1);
    });
    return () => unsub();
  }, []);

  const handleSetGenerator = (mode: "off" | "noise" | "sweep" | "808") => {
    setGeneratorMode(mode);
    if (mode === "off") {
      auraEngine.stopDemoTrack();
    } else {
      auraEngine.stopDemoTrack();
      auraEngine.toggleDemoTrack(mode);
    }
  };

  const effectList = [
    { name: "1. PreEQ (25 Bandas)", available: true, init: true, ctrl: state.modules.equalizer && !state.bypass, run: state.motorEnabled && !state.bypass },
    { name: "2. Filtro Subsónico (LR4)", available: true, init: true, ctrl: true, run: state.motorEnabled },
    { name: "3. Tono y Crossovers LR4", available: true, init: true, ctrl: true, run: state.motorEnabled && !state.bypass },
    { name: "4. Realzador Graves (15 Instrumentos)", available: true, init: true, ctrl: true, run: state.motorEnabled && !state.bypass },
    { name: "5. Compresor Multibanda / MBC", available: true, init: true, ctrl: state.modules.compressor && !state.bypass, run: state.motorEnabled && !state.bypass },
    { name: "6. Puerta de Ruido y Expansor", available: true, init: true, ctrl: state.modules.gate && !state.bypass, run: state.motorEnabled && !state.bypass },
    { name: "7. Reverb DSP (FDN Estéreo)", available: true, init: true, ctrl: state.modules.reverb && state.effectsEnabled && !state.bypass, run: state.motorEnabled && state.effectsEnabled && !state.bypass },
    { name: "8. Ampliador Estéreo (Mid/Side)", available: true, init: true, ctrl: state.modules.width && !state.bypass, run: state.motorEnabled && !state.bypass },
    { name: "9. Limitador True-Peak", available: true, init: true, ctrl: state.modules.limiter && !state.bypass, run: state.motorEnabled && !state.bypass },
  ];

  return (
    <div className="flex flex-col gap-4 pb-12">
      {/* Estado General del Motor */}
      <AuraCard>
        <div className="flex items-center justify-between gap-3">
          <div>
            <SectionTitle
              text="Diagnóstico de Audio en Vivo"
              trailing={state.motorEnabled ? "MOTOR ENCENDIDO" : "APAGADO"}
            />
            <div className="text-xs text-white/90 font-medium mt-1">
              Modo: {generatorMode !== "off" ? `Generador Interno (${generatorMode.toUpperCase()})` : "AudioTrack / DynamicsProcessing"}
            </div>
            <div className="text-[11px] text-[#8E9BAE]">
              {engine.sampleRateHz || 48000} Hz · {engine.channels} canales · Buffer: 512 frames
            </div>
          </div>
          <StatusPill
            text={state.motorEnabled ? "ACTIVO" : "INACTIVO"}
            color={state.motorEnabled ? "#34D399" : "#8E9BAE"}
          />
        </div>
      </AuraCard>

      {/* Generador Interno A/B */}
      <AuraCard>
        <SectionTitle
          text="Probar Motor (Generador A/B)"
          trailing={state.bypass ? "BYPASS ACTIVO" : "PROCESANDO"}
        />
        <p className="text-[11px] text-[#8E9BAE] mt-1 mb-3">
          Genera señales de prueba puras para verificar de inmediato el comportamiento audible del ecualizador sin depender de reproductores externos.
        </p>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 mb-3">
          {[
            { id: "off", label: "Desactivado" },
            { id: "noise", label: "Ruido Rosa" },
            { id: "sweep", label: "Barrido (20Hz–20kHz)" },
            { id: "808", label: "Sub-bajo 808" },
          ].map((item) => (
            <SelectableChip
              key={item.id}
              text={item.label}
              selected={generatorMode === item.id}
              onClick={() => handleSetGenerator(item.id as "off" | "noise" | "sweep" | "808")}
              accentColor={accentColor}
            />
          ))}
        </div>

        {generatorMode !== "off" && (
          <div className="pt-2 border-t border-white/10 flex items-center justify-between gap-4">
            <div>
              <div className="text-xs font-semibold text-white">Comparación A/B</div>
              <div className="text-[10px] text-[#8E9BAE]">
                {state.bypass ? "Escuchando señal pura sin EQ" : "Escuchando señal procesada por la cadena DSP"}
              </div>
            </div>
            <button
              type="button"
              onClick={() => repo.setBypass(!state.bypass)}
              className="px-3 py-1.5 rounded-xl text-xs font-bold transition-all border"
              style={{
                backgroundColor: state.bypass ? "#FF525222" : `${accentColor}22`,
                borderColor: state.bypass ? "#FF5252" : accentColor,
                color: state.bypass ? "#FF5252" : accentColor,
              }}
            >
              {state.bypass ? "Bypass: SÍ (Original)" : "Bypass: NO (DSP)"}
            </button>
          </div>
        )}
      </AuraCard>

      {/* Medidores de Nivel */}
      <AuraCard>
        <div className="flex items-center justify-between mb-3">
          <SectionTitle text="Medidores de Nivel (dBFS)" trailing="L / R" />
          <StatusPill
            text={clipping ? "CLIPPING" : "SEGURO"}
            color={clipping ? "#FF5252" : "#34D399"}
          />
        </div>

        {/* Canal L */}
        <div className="flex items-center gap-3 text-xs font-mono mb-2">
          <span className="w-4 font-bold text-white">L</span>
          <div className="flex-1 h-3.5 bg-black/40 rounded border border-white/10 overflow-hidden relative">
            <div
              className="h-full transition-all duration-75 rounded"
              style={{
                width: `${Math.min(100, Math.max(0, ((peakL + 60) / 60) * 100))}%`,
                backgroundColor: peakL >= -0.5 ? "#FF5252" : peakL >= -3 ? "#FFB800" : accentColor,
              }}
            />
          </div>
          <span className="w-14 text-right text-[10px] text-[#8E9BAE]">
            {peakL > -80 ? `${peakL} dB` : "−∞"}
          </span>
        </div>

        {/* Canal R */}
        <div className="flex items-center gap-3 text-xs font-mono">
          <span className="w-4 font-bold text-white">R</span>
          <div className="flex-1 h-3.5 bg-black/40 rounded border border-white/10 overflow-hidden relative">
            <div
              className="h-full transition-all duration-75 rounded"
              style={{
                width: `${Math.min(100, Math.max(0, ((peakR + 60) / 60) * 100))}%`,
                backgroundColor: peakR >= -0.5 ? "#FF5252" : peakR >= -3 ? "#FFB800" : accentColor,
              }}
            />
          </div>
          <span className="w-14 text-right text-[10px] text-[#8E9BAE]">
            {peakR > -80 ? `${peakR} dB` : "−∞"}
          </span>
        </div>
      </AuraCard>

      {/* Estado por Etapa */}
      <AuraCard>
        <SectionTitle text="Estado Real por Efecto (4 Niveles)" trailing="Hardware/DSP" />
        <div className="flex flex-col gap-1.5 mt-2">
          {effectList.map((eff) => (
            <div key={eff.name} className="flex items-center justify-between text-xs py-1 border-b border-white/5 last:border-none">
              <span className="text-white/90 font-medium text-[11px] truncate max-w-[200px]">
                {eff.name}
              </span>
              <div className="flex items-center gap-1 text-[9px] font-bold">
                <span className={`px-1.5 py-0.5 rounded ${eff.available ? "bg-emerald-500/20 text-emerald-400" : "bg-white/5 text-white/30"}`}>Disp</span>
                <span className={`px-1.5 py-0.5 rounded ${eff.init ? "bg-emerald-500/20 text-emerald-400" : "bg-white/5 text-white/30"}`}>Init</span>
                <span className={`px-1.5 py-0.5 rounded ${eff.ctrl ? "bg-emerald-500/20 text-emerald-400" : "bg-white/5 text-white/30"}`}>Ctrl</span>
                <span className={`px-1.5 py-0.5 rounded ${eff.run ? "bg-emerald-500/20 text-emerald-400" : "bg-white/5 text-white/30"}`}>Run</span>
              </div>
            </div>
          ))}
        </div>
      </AuraCard>
    </div>
  );
}
