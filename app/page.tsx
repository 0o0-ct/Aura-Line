"use client";

import React, { useEffect, useState } from "react";
import { audioRepo } from "@/src/lib/audioRepository";
import { auraEngine } from "@/src/lib/audioEngine";
import { paletteFor } from "@/src/lib/constants";
import { AudioRouteLabels, EngineStatus, EqualizerState, AppSettings } from "@/src/lib/types";
import { AuraSwitch, StatusPill } from "@/src/components/AuraComponents";
import { AudioPlayerBar } from "@/src/components/AudioPlayerBar";
import { EqualizerScreen } from "@/src/components/EqualizerScreen";
import { ToneScreen } from "@/src/components/ToneScreen";
import { EffectsScreen } from "@/src/components/EffectsScreen";
import { DiagnosticScreen } from "@/src/components/DiagnosticScreen";
import { SettingsScreen } from "@/src/components/SettingsScreen";
import { Sliders, SlidersHorizontal, Sparkles, Activity, Settings } from "lucide-react";
import { cn } from "@/src/lib/utils";

const tabs = [
  { label: "Ecualizador", icon: Sliders },
  { label: "Tono", icon: SlidersHorizontal },
  { label: "Efectos", icon: Sparkles },
  { label: "Diagnóstico", icon: Activity },
  { label: "Ajustes", icon: Settings },
];

export default function HomePage() {
  const [state, setState] = useState<EqualizerState>(audioRepo.getState());
  const [settings, setSettings] = useState<AppSettings>(audioRepo.getSettings());
  const [engine, setEngine] = useState<EngineStatus>(audioRepo.getEngineStatus());
  const [spectrum, setSpectrum] = useState<number[]>(new Array(25).fill(0));
  const [activeTab, setActiveTab] = useState(0);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    audioRepo.loadFromStorage();
    auraEngine.initialize();

    const unsubRepo = audioRepo.subscribe(() => {
      setState(audioRepo.getState());
      setSettings(audioRepo.getSettings());
    });

    const unsubStatus = auraEngine.subscribeStatus((newStatus) => {
      setEngine(newStatus);
    });

    const unsubSpectrum = auraEngine.subscribeSpectrum((levels) => {
      setSpectrum(levels);
    });

    return () => {
      unsubRepo();
      unsubStatus();
      unsubSpectrum();
    };
  }, []);

  const activePalette = paletteFor(settings.theme.paletteKey);
  const accent = state.motorEnabled ? activePalette.accent : "#8E9BAE";

  const handleToggleMotor = (enabled: boolean) => {
    audioRepo.toggleMotor(enabled);
    showToast(enabled ? "Motor de audio encendido" : "Motor de audio apagado");
  };

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage((cur) => (cur === msg ? null : cur));
    }, 2800);
  };

  return (
    <main className="relative min-h-screen bg-[#07090E] text-[#F5F6F8] flex flex-col items-center">
      {/* Dynamic Ambient Neon Blooms (Apple ambient lighting) */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
        <div
          className="absolute -top-[10%] -right-[15%] w-[600px] h-[600px] rounded-full blur-[120px] transition-all duration-700"
          style={{
            backgroundColor: accent,
            opacity: state.motorEnabled ? 0.12 : 0.03,
          }}
        />
        <div
          className="absolute -bottom-[15%] -left-[10%] w-[550px] h-[550px] rounded-full blur-[140px] transition-all duration-700"
          style={{
            backgroundColor: "#1E2B44",
            opacity: 0.25,
          }}
        />
      </div>

      <div className="relative z-10 w-full max-w-2xl px-4 pt-5 pb-8 flex flex-col flex-1">
        {/* Top Header Bar */}
        <header className="flex items-center justify-between gap-4 mb-4 pb-3 border-b border-white/10 backdrop-blur-md">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
              AuraLine
              <span className="text-[10px] font-semibold tracking-wider uppercase px-2 py-0.5 rounded-full bg-white/10 text-white/80">
                DSP Studio
              </span>
            </h1>
            <div className="flex items-center gap-2 mt-1.5 flex-wrap">
              <StatusPill
                text={state.motorEnabled ? "Motor activo" : "Motor apagado"}
                color={state.motorEnabled ? "#34D399" : "#8E9BAE"}
              />
              <span className="text-[11px] text-[#8E9BAE] font-medium truncate">
                {AudioRouteLabels[engine.route] || "Altavoz"}
                {engine.outputName && ` · ${engine.outputName}`}
                {engine.sampleRateHz > 0 && ` · ${engine.sampleRateHz} Hz`}
                {` · ${engine.channels} canales`}
              </span>
            </div>
          </div>

          {/* Apple Neumorphic Tactile Power Switch */}
          <AuraSwitch
            checked={state.motorEnabled}
            onCheckedChange={handleToggleMotor}
            accentColor={activePalette.accent}
          />
        </header>

        {/* Real-time Audition & Audio Test Bar */}
        <div className="mb-4">
          <AudioPlayerBar
            motorEnabled={state.motorEnabled}
            accentColor={activePalette.accent}
            onTurnOnMotor={() => handleToggleMotor(true)}
          />
        </div>

        {/* Main Tab Screen Content */}
        <div className="flex-1">
          {activeTab === 0 && (
            <EqualizerScreen
              repo={audioRepo}
              state={state}
              engine={engine}
              spectrum={spectrum}
              accentColor={activePalette.accent}
            />
          )}
          {activeTab === 1 && (
            <ToneScreen
              repo={audioRepo}
              state={state}
              settings={settings}
              accentColor={activePalette.accent}
            />
          )}
          {activeTab === 2 && (
            <EffectsScreen
              repo={audioRepo}
              state={state}
              engine={engine}
              accentColor={activePalette.accent}
            />
          )}
          {activeTab === 3 && (
            <DiagnosticScreen
              repo={audioRepo}
              state={state}
              settings={settings}
              engine={engine}
              accentColor={activePalette.accent}
            />
          )}
          {activeTab === 4 && (
            <SettingsScreen
              repo={audioRepo}
              state={state}
              settings={settings}
              engine={engine}
              accentColor={activePalette.accent}
            />
          )}
        </div>
      </div>

      {/* Floating Frosted Glass Dock (macOS / iOS Dock Style) */}
      <nav
        aria-label="Navegación principal"
        className="fixed bottom-4 inset-x-0 mx-auto w-[92%] max-w-md z-40"
      >
        <div
          className="h-16 rounded-[28px] px-2 py-1.5 flex items-center justify-around"
          style={{
            background: "linear-gradient(180deg, rgba(22, 30, 44, 0.88) 0%, rgba(15, 21, 32, 0.95) 100%)",
            backdropFilter: "blur(24px)",
            WebkitBackdropFilter: "blur(24px)",
            border: "1px solid rgba(255, 255, 255, 0.16)",
            boxShadow: "0 16px 36px -4px rgba(0, 0, 0, 0.7), inset 0 1px 1px rgba(255, 255, 255, 0.25)",
          }}
        >
          {tabs.map((tab, idx) => {
            const Icon = tab.icon;
            const isSelected = activeTab === idx;
            return (
              <button
                key={tab.label}
                type="button"
                onClick={() => setActiveTab(idx)}
                className={cn(
                  "flex-1 h-12 rounded-2xl flex flex-col items-center justify-center transition-all duration-200 select-none",
                  isSelected
                    ? "border shadow-sm"
                    : "text-[#8E9BAE] hover:text-white border-transparent"
                )}
                style={
                  isSelected
                    ? {
                        backgroundColor: `${activePalette.accent}18`,
                        borderColor: `${activePalette.accent}55`,
                        color: activePalette.accent,
                      }
                    : undefined
                }
              >
                <Icon className="w-5 h-5 mb-0.5" />
                <span className="text-[10px] font-semibold tracking-tight">{tab.label}</span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Floating Toast Notification */}
      {toastMessage && (
        <div className="fixed top-6 inset-x-0 mx-auto w-fit z-50 px-4 py-2 rounded-full bg-[#161F2E]/95 border border-white/20 text-xs font-semibold text-white shadow-2xl backdrop-blur-md animate-fade-in flex items-center gap-2">
          <span
            className="w-2 h-2 rounded-full"
            style={{ backgroundColor: activePalette.accent }}
          />
          {toastMessage}
        </div>
      )}
    </main>
  );
}
