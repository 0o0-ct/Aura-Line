import React, { useRef } from "react";
import { cn } from "@/src/lib/utils";

interface BandFaderProps {
  value: number;
  onValueChange: (val: number) => void;
  valueRange: [number, number];
  label: string;
  valueText: string;
  subtitle?: string;
  highlighted?: boolean;
  accentColor?: string;
  height?: number;
  faderWidth?: number;
}

export function BandFader({
  value,
  onValueChange,
  valueRange,
  label,
  valueText,
  subtitle,
  highlighted,
  accentColor = "#00E5CC",
  height = 160,
  faderWidth,
}: BandFaderProps) {
  const [min, max] = valueRange;
  const isDragging = useRef(false);
  const trackRef = useRef<HTMLDivElement>(null);

  // 0 dB is at 50%
  // Normalized position: 0 (bottom = min) to 1 (top = max)
  const norm = (value - min) / (max - min);
  const topPercent = (1 - norm) * 100;

  const handlePointerDown = (e: React.PointerEvent) => {
    isDragging.current = true;
    (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
    updateFromPointer(e.clientY);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging.current) return;
    updateFromPointer(e.clientY);
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    isDragging.current = false;
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {}
  };

  const updateFromPointer = (clientY: number) => {
    if (!trackRef.current) return;
    const rect = trackRef.current.getBoundingClientRect();
    const ratio = (rect.bottom - clientY) / rect.height;
    const clampedRatio = Math.max(0, Math.min(1, ratio));
    const newVal = min + clampedRatio * (max - min);
    // Snap to 0.5 dB
    const snapped = Math.round(newVal * 2) / 2;
    onValueChange(snapped);
  };

  const handleDoubleClick = () => {
    onValueChange(0);
  };

  return (
    <div
      className="flex flex-col items-center justify-between select-none py-1 group"
      style={{ width: faderWidth ? `${faderWidth}px` : "auto", minWidth: "32px" }}
      onDoubleClick={handleDoubleClick}
      title="Doble toque para restablecer a 0 dB"
    >
      {/* Gain readout */}
      <span
        className={cn(
          "text-[10px] font-mono font-semibold transition-colors duration-200 mb-1 leading-none text-center",
          highlighted ? "text-white" : "text-[#7B8B9E] group-hover:text-white/80"
        )}
        style={highlighted ? { color: accentColor } : undefined}
      >
        {valueText}
      </span>

      {/* Fader well trough */}
      <div
        ref={trackRef}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        className="relative w-7 rounded-full bg-[#080C14] border border-white/10 shadow-[inset_0_2px_8px_rgba(0,0,0,0.8)] cursor-pointer flex justify-center touch-none overflow-visible"
        style={{ height: `${height}px` }}
      >
        {/* Center 0 dB marker */}
        <div className="absolute top-1/2 left-0 right-0 h-px bg-white/20 pointer-events-none" />

        {/* Level bar fill from center */}
        {value !== 0 && (
          <div
            className="absolute left-1.5 right-1.5 rounded-full pointer-events-none transition-all duration-75"
            style={{
              top: value > 0 ? `${topPercent}%` : "50%",
              bottom: value < 0 ? `${(norm) * 100}%` : "50%",
              backgroundColor: `${accentColor}33`,
              borderLeft: `2px solid ${accentColor}`,
              borderRight: `2px solid ${accentColor}`,
            }}
          />
        )}

        {/* Tactile Capsule Extruded Thumb */}
        <div
          className="absolute -left-1 -right-1 h-6 rounded-lg shadow-[0_4px_10px_rgba(0,0,0,0.8),inset_0_1px_1px_rgba(255,255,255,0.4)] border transition-transform pointer-events-none transform -translate-y-1/2 flex items-center justify-center"
          style={{
            top: `${topPercent}%`,
            backgroundColor: highlighted ? "#1F2E42" : "#141D2B",
            borderColor: highlighted ? accentColor : "rgba(255,255,255,0.2)",
            boxShadow: highlighted ? `0 0 12px ${accentColor}66` : undefined,
          }}
        >
          {/* Thumb grip line */}
          <div
            className="w-4 h-0.5 rounded-full"
            style={{ backgroundColor: highlighted ? accentColor : "#8E9BAE" }}
          />
        </div>
      </div>

      {/* Frequency label */}
      <span className="text-[10px] font-semibold text-white/90 mt-2 tracking-tight text-center">
        {label}
      </span>

      {/* Instrument Owner Subtitle badge */}
      {subtitle ? (
        <span
          className="text-[8px] font-bold uppercase tracking-wider px-1 py-0.5 mt-0.5 rounded text-center truncate max-w-full"
          style={{
            color: accentColor,
            backgroundColor: `${accentColor}18`,
          }}
        >
          {subtitle}
        </span>
      ) : (
        <span className="h-3 text-[8px] opacity-0">_</span>
      )}
    </div>
  );
}
