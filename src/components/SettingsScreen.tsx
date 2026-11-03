import React, { useState } from "react";
import {
  AuraCard,
  AuraSliderRow,
  AuraSwitch,
  SectionTitle,
  SelectableChip,
  StatusPill,
} from "./AuraComponents";
import { AudioRepository } from "@/src/lib/audioRepository";
import { AppSettings, AudioCheckResult, EngineStatus, EqualizerState } from "@/src/lib/types";
import { AuraPalettes, UsageProfiles, paletteFor } from "@/src/lib/constants";
import { Activity, Check, ShieldCheck } from "lucide-react";
import { cn } from "@/src/lib/utils";

interface SettingsScreenProps {
  repo: AudioRepository;
  state: EqualizerState;
  settings: AppSettings;
  engine: EngineStatus;
  accentColor?: string;
}

export function SettingsScreen({
  repo,
  state,
  settings,
  engine,
  accentColor = "#00E5CC",
}: SettingsScreenProps) {
  const [checking, setChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<AudioCheckResult | null>(null);

  const activeProfile = UsageProfiles.find((p) => p.key === settings.usageProfile);
  const activePalette = paletteFor(settings.theme.paletteKey);

  const handleRunCheck = async () => {
    if (checking) return;
    setChecking(true);
    try {
      const res = await repo.runAudioCheck();
      setCheckResult(res);
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* Usage Profile Card */}
      <AuraCard>
        <SectionTitle
          text="Perfil de uso"
          trailing={activeProfile?.name || "sin elegir"}
        />
        <div className="flex items-center gap-2 overflow-x-auto pb-1 mt-2 mb-2 scrollbar-none">
          {UsageProfiles.map((profile) => (
            <SelectableChip
              key={profile.key}
              text={profile.name}
              selected={settings.usageProfile === profile.key}
              onClick={() => repo.applyUsageProfile(profile.key)}
              accentColor={accentColor}
            />
          ))}
        </div>
        {activeProfile?.description && (
          <p className="text-xs text-[#8E9BAE] mt-2 italic">{activeProfile.description}</p>
        )}
      </AuraCard>

      {/* 30 Monochrome Palettes Selector Card */}
      <AuraCard>
        <SectionTitle text="Paleta" trailing={activePalette.label} />
        <div className="flex items-center gap-3 overflow-x-auto py-2 px-1 scrollbar-none">
          {AuraPalettes.map((pal) => {
            const isSelected = settings.theme.paletteKey === pal.key;
            return (
              <button
                key={pal.key}
                type="button"
                onClick={() => repo.setPalette(pal.key, pal.accent)}
                className={cn(
                  "relative w-11 h-11 rounded-full shrink-0 flex items-center justify-center transition-all duration-300",
                  "border shadow-md active:scale-95 cursor-pointer",
                  isSelected
                    ? "border-white ring-2 ring-white/30 scale-105"
                    : "border-white/20 hover:border-white/50 opacity-80 hover:opacity-100"
                )}
                style={{
                  background: `radial-gradient(circle, ${pal.accent} 0%, ${pal.colors[1] || pal.accent} 70%, ${pal.colors[2] || "#0A1F11"} 100%)`,
                  boxShadow: isSelected ? `0 0 16px ${pal.accent}88` : undefined,
                }}
                title={pal.label}
              >
                {isSelected && (
                  <span className="w-2.5 h-2.5 rounded-full bg-white shadow-sm" />
                )}
              </button>
            );
          })}
        </div>
      </AuraCard>

      {/* Band Resolution Card */}
      <AuraCard>
        <SectionTitle text="Ecualizador" trailing={`${settings.numBands} bandas`} />
        <AuraSliderRow
          label="Resolución de bandas"
          valueText={`${settings.numBands} bandas`}
          value={settings.numBands}
          valueRange={[20, 25]}
          step={1}
          onValueChange={(val) => repo.setNumBands(Math.round(val))}
          accentColor={accentColor}
        />
        <p className="text-[11px] text-[#8E9BAE] mt-1">
          Más bandas dan más control; el rango audible va de 20 Hz a 20 kHz.
        </p>
      </AuraCard>

      {/* System Behavior Card */}
      <AuraCard>
        <SectionTitle text="Comportamiento" />
        <div className="divide-y divide-white/5">
          <SettingSwitch
            title="Perfiles por salida"
            subtitle="Recuerda la curva para altavoz, cable o Bluetooth"
            checked={settings.profilesByOutput}
            onCheckedChange={(c) => repo.setProfilesByOutput(c)}
            accentColor={accentColor}
          />
          <SettingSwitch
            title="Respuesta háptica"
            subtitle="Vibración al mover faders y cambiar interruptores"
            checked={settings.hapticFeedback}
            onCheckedChange={(c) => repo.setHapticFeedback(c)}
            accentColor={accentColor}
          />
          <SettingSwitch
            title="Protección de nivel"
            subtitle="Compensa parte del refuerzo para que el códec no sature. Desactívala si prefieres volumen máximo"
            checked={state.autoPreamp}
            onCheckedChange={(c) => repo.setAutoPreamp(c)}
            accentColor={accentColor}
          />
          <SettingSwitch
            title="Iniciar al encender"
            subtitle="Activa el ecualizador automáticamente tras iniciar la app"
            checked={settings.autoStart}
            onCheckedChange={(c) => repo.setAutoStart(c)}
            accentColor={accentColor}
          />
        </div>
      </AuraCard>

      {/* Battery / Background Mode Card */}
      <AuraCard>
        <SectionTitle text="Segundo plano" />
        <p className="text-xs text-[#8E9BAE] my-2 leading-relaxed">
          Los navegadores modernos y sistemas móviles suspenden pestañas inactivas. El
          motor Web Audio de AuraLine mantiene su gráfica activa para procesar audio en
          primer y segundo plano.
        </p>
        <div className="flex items-center justify-between mt-3">
          <StatusPill text="Batería protegida" color="#34D399" />
          <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
            <ShieldCheck className="w-3.5 h-3.5" /> En reposo seguro
          </span>
        </div>
      </AuraCard>

      {/* Acoustic Engine Check Card */}
      <AuraCard>
        <SectionTitle text="Comprobación del motor" trailing="prueba acústica" />
        <p className="text-xs text-[#8E9BAE] my-1 leading-relaxed">
          Mide si el ecualizador modifica realmente el espectro de sonido. Emite dos
          tonos simultáneos (485 Hz y 1771 Hz) y comprueba si al subir la banda del tono
          agudo este se vuelve más dominante respecto al grave.
        </p>
        <p className="text-[11px] text-[#8E9BAE] mb-3">
          Salida por altavoz del dispositivo o auriculares, volumen al 50% o más.
        </p>

        <button
          type="button"
          onClick={handleRunCheck}
          disabled={checking}
          className="w-full sm:w-auto px-6 py-2.5 rounded-full font-bold text-xs flex items-center justify-center gap-2 transition-all active:scale-95 disabled:opacity-50 text-[#07090E]"
          style={{ backgroundColor: accentColor }}
        >
          <Activity className="w-4 h-4 animate-spin-slow" />
          {checking ? "Midiendo selectividad…" : "Comprobar el motor"}
        </button>

        <div className="flex items-center justify-between pt-3 mt-3 border-t border-white/5">
          <div>
            <div className="text-xs font-semibold text-white">
              Comprobar al encender el motor
            </div>
            <div className="text-[10px] text-[#8E9BAE]">
              Opcional: verifica el motor automáticamente cada vez que lo activas
            </div>
          </div>
          <AuraSwitch
            checked={settings.autoCheck}
            onCheckedChange={(c) => repo.setAutoCheck(c)}
            accentColor={accentColor}
          />
        </div>

        {checkResult && (
          <div className="mt-4 p-3.5 rounded-2xl bg-white/[0.04] border border-white/10 space-y-1.5">
            <div className="flex items-center gap-2">
              <StatusPill
                text={checkResult.passed ? "VERIFICADO" : "SIN CAMBIO"}
                color={checkResult.passed ? "#34D399" : "#FBBF24"}
              />
              <span className="text-xs font-semibold font-mono text-white">
                Δ {checkResult.deltaDb.toFixed(1)} dB
              </span>
            </div>
            <p className="text-xs text-[#8E9BAE] leading-snug">{checkResult.message}</p>
            <p className="text-[11px] text-white/50 font-mono">
              Referencia {checkResult.referenceDb.toFixed(1)} dBFS · procesado{" "}
              {checkResult.processedDb.toFixed(1)} dBFS
            </p>
          </div>
        )}
      </AuraCard>

      {/* Version Footer */}
      <div className="text-center text-[11px] text-[#8E9BAE]/60 py-2">
        AuraLine 2.4.0 · {engine.sampleRateHz} Hz ·{" "}
        {engine.globalScope ? "procesado global activo" : "sin cadena activa"}
      </div>
    </div>
  );
}

function SettingSwitch({
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
