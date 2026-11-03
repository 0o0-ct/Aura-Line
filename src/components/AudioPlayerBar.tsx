import React, { useEffect, useState } from "react";
import { auraEngine } from "@/src/lib/audioEngine";
import { Play, Square, Music, Activity, Volume2, Sparkles } from "lucide-react";
import { cn } from "@/src/lib/utils";

interface AudioPlayerBarProps {
  motorEnabled: boolean;
  accentColor?: string;
  onTurnOnMotor?: () => void;
}

export function AudioPlayerBar({
  motorEnabled,
  accentColor = "#00E5CC",
  onTurnOnMotor,
}: AudioPlayerBarProps) {
  const [isPlaying, setIsPlaying] = useState(false);
  const [trackType, setTrackType] = useState<"music" | "808" | "sweep" | "noise">("music");

  useEffect(() => {
    setIsPlaying(auraEngine.isDemoActive());
  }, []);

  const handleToggle = (type: "music" | "808" | "sweep" | "noise") => {
    if (!motorEnabled && onTurnOnMotor) {
      onTurnOnMotor();
    }
    setTrackType(type);
    const active = auraEngine.toggleDemoTrack(type);
    setIsPlaying(active);
  };

  const handleStop = () => {
    auraEngine.stopDemoTrack();
    setIsPlaying(false);
  };

  return (
    <div className="w-full rounded-2xl bg-[#121926]/90 border border-white/10 p-3 backdrop-blur-md shadow-lg flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2.5">
        <div
          className={cn(
            "w-9 h-9 rounded-xl flex items-center justify-center transition-all",
            isPlaying ? "bg-white/10 text-white" : "bg-white/5 text-[#8E9BAE]"
          )}
          style={isPlaying ? { color: accentColor, boxShadow: `0 0 12px ${accentColor}44` } : undefined}
        >
          {isPlaying ? <Activity className="w-5 h-5 animate-pulse" /> : <Volume2 className="w-5 h-5" />}
        </div>
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold text-white tracking-tight">Audio de prueba en vivo</span>
            {isPlaying && (
              <span className="px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider bg-emerald-500/20 text-emerald-400">
                Sonando
              </span>
            )}
          </div>
          <span className="text-[11px] text-[#8E9BAE]">
            {motorEnabled
              ? "Escucha en tiempo real cómo cambia el sonido con tu ecualización"
              : "Enciende el motor arriba para procesar el audio"}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
        <button
          type="button"
          onClick={() => handleToggle("music")}
          className={cn(
            "px-2.5 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all",
            isPlaying && trackType === "music"
              ? "bg-white/20 text-white border border-white/30"
              : "bg-white/5 text-[#C5D0DE] hover:bg-white/10"
          )}
          style={
            isPlaying && trackType === "music"
              ? { borderColor: accentColor, color: accentColor }
              : undefined
          }
        >
          <Music className="w-3.5 h-3.5" />
          Ritmo funk
        </button>

        <button
          type="button"
          onClick={() => handleToggle("808")}
          className={cn(
            "px-2.5 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all",
            isPlaying && trackType === "808"
              ? "bg-white/20 text-white border border-white/30"
              : "bg-white/5 text-[#C5D0DE] hover:bg-white/10"
          )}
          style={
            isPlaying && trackType === "808"
              ? { borderColor: accentColor, color: accentColor }
              : undefined
          }
        >
          <Sparkles className="w-3.5 h-3.5" />
          Trap 808
        </button>

        <button
          type="button"
          onClick={() => handleToggle("sweep")}
          className={cn(
            "px-2.5 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all",
            isPlaying && trackType === "sweep"
              ? "bg-white/20 text-white border border-white/30"
              : "bg-white/5 text-[#C5D0DE] hover:bg-white/10"
          )}
          style={
            isPlaying && trackType === "sweep"
              ? { borderColor: accentColor, color: accentColor }
              : undefined
          }
        >
          Barrido 20-18k
        </button>

        <button
          type="button"
          onClick={() => handleToggle("noise")}
          className={cn(
            "px-2.5 py-1.5 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all",
            isPlaying && trackType === "noise"
              ? "bg-white/20 text-white border border-white/30"
              : "bg-white/5 text-[#C5D0DE] hover:bg-white/10"
          )}
          style={
            isPlaying && trackType === "noise"
              ? { borderColor: accentColor, color: accentColor }
              : undefined
          }
        >
          Ruido rosa
        </button>

        {isPlaying && (
          <button
            type="button"
            onClick={handleStop}
            className="p-1.5 rounded-lg text-xs font-medium bg-red-500/20 text-red-300 hover:bg-red-500/30 transition-colors"
            title="Detener audio"
          >
            <Square className="w-3.5 h-3.5 fill-current" />
          </button>
        )}
      </div>
    </div>
  );
}
