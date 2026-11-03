import React from "react";

interface SpectrumBarsProps {
  levels: number[];
  count?: number;
  accentColor?: string;
  height?: number;
}

export function SpectrumBars({
  levels,
  count = 25,
  accentColor = "#00E5CC",
  height = 56,
}: SpectrumBarsProps) {
  const displayLevels = Array.from({ length: count }, (_, i) => levels[i] || 0);

  return (
    <div
      className="relative w-full rounded-xl bg-[#080C14] border border-white/10 px-2 py-1.5 flex items-end justify-between gap-[3px] shadow-inner overflow-hidden"
      style={{ height: `${height}px` }}
    >
      {displayLevels.map((lvl, index) => {
        const heightPct = Math.min(100, Math.max(3, lvl * 100));
        const isPeak = lvl > 0.8;

        return (
          <div
            key={index}
            className="relative flex-1 h-full rounded-sm bg-white/[0.04] overflow-hidden flex flex-col justify-end"
          >
            {/* Meter level bar */}
            <div
              className="w-full rounded-sm transition-all duration-75"
              style={{
                height: `${heightPct}%`,
                background: `linear-gradient(to top, ${accentColor}44, ${accentColor})`,
                boxShadow: lvl > 0.3 ? `0 0 8px ${accentColor}88` : undefined,
              }}
            />

            {/* Peak LED dot */}
            {isPeak && (
              <div
                className="absolute top-0 left-0 right-0 h-1 rounded-sm bg-white shadow-[0_0_6px_#fff]"
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
