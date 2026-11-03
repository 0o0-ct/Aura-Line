import React from "react";
import { cn } from "@/src/lib/utils";

interface AuraCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  className?: string;
}

export function AuraCard({ children, className, ...props }: AuraCardProps) {
  return (
    <div
      className={cn(
        "relative rounded-3xl p-5 md:p-6",
        "bg-gradient-to-b from-[#161F2E]/75 to-[#0E1420]/85",
        "backdrop-blur-xl border border-white/10 shadow-[0_16px_32px_rgba(0,0,0,0.5)]",
        "before:absolute before:inset-x-0 before:top-0 before:h-px before:bg-gradient-to-r before:from-transparent before:via-white/20 before:to-transparent",
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}

interface AuraSwitchProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  enabled?: boolean;
  accentColor?: string;
}

export function AuraSwitch({
  checked,
  onCheckedChange,
  enabled = true,
  accentColor = "#00E5CC",
}: AuraSwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={!enabled}
      onClick={() => enabled && onCheckedChange(!checked)}
      className={cn(
        "relative inline-flex h-8 w-14 shrink-0 cursor-pointer rounded-full p-1 transition-all duration-300 ease-out focus:outline-none",
        "border shadow-inner",
        checked
          ? "border-white/25 shadow-[0_0_16px_var(--glow)]"
          : "bg-[#090D14] border-white/10 shadow-[inset_0_2px_4px_rgba(0,0,0,0.6)]",
        !enabled && "opacity-40 cursor-not-allowed"
      )}
      style={
        checked
          ? ({
              backgroundColor: accentColor,
              "--glow": `${accentColor}66`,
            } as React.CSSProperties)
          : undefined
      }
    >
      <span
        className={cn(
          "pointer-events-none block h-6 w-6 rounded-full transition-all duration-300 ease-out transform shadow-md",
          checked
            ? "translate-x-6 bg-white shadow-[0_2px_6px_rgba(0,0,0,0.4)]"
            : "translate-x-0 bg-[#7B8B9E] shadow-[0_2px_4px_rgba(0,0,0,0.3)]"
        )}
      />
    </button>
  );
}

interface StatusPillProps {
  text: string;
  color?: string;
  className?: string;
}

export function StatusPill({ text, color = "#34D399", className }: StatusPillProps) {
  return (
    <div
      className={cn(
        "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold tracking-wide",
        "bg-white/[0.06] border border-white/10 backdrop-blur-md shadow-sm",
        className
      )}
    >
      <span
        className="w-2 h-2 rounded-full animate-pulse shadow-sm"
        style={{ backgroundColor: color, boxShadow: `0 0 8px ${color}` }}
      />
      <span style={{ color }}>{text}</span>
    </div>
  );
}

interface SelectableChipProps {
  text: string;
  selected: boolean;
  onClick: () => void;
  accentColor?: string;
  className?: string;
}

export function SelectableChip({
  text,
  selected,
  onClick,
  accentColor = "#00E5CC",
  className,
}: SelectableChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "relative px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-tight transition-all duration-200 select-none",
        "border shadow-sm active:scale-95 whitespace-nowrap",
        selected
          ? "border-white/30 text-[#07090E] shadow-[0_2px_10px_var(--chip-glow)]"
          : "bg-white/[0.04] text-[#8E9BAE] border-white/[0.08] hover:bg-white/[0.08] hover:text-white",
        className
      )}
      style={
        selected
          ? ({
              backgroundColor: accentColor,
              "--chip-glow": `${accentColor}55`,
            } as React.CSSProperties)
          : undefined
      }
    >
      {text}
    </button>
  );
}

interface SectionTitleProps {
  text: string;
  trailing?: string;
  modifierClassName?: string;
  className?: string;
}

export function SectionTitle({ text, trailing, className }: SectionTitleProps) {
  return (
    <div className={cn("flex items-baseline justify-between gap-2 mb-2", className)}>
      <h3 className="text-base font-semibold text-white tracking-tight">{text}</h3>
      {trailing && (
        <span className="text-xs text-[#8E9BAE] font-medium tracking-normal truncate max-w-[200px]">
          {trailing}
        </span>
      )}
    </div>
  );
}

interface AuraSliderRowProps {
  label: string;
  valueText: string;
  value: number;
  valueRange: [number, number];
  onValueChange: (val: number) => void;
  step?: number;
  enabled?: boolean;
  accentColor?: string;
}

export function AuraSliderRow({
  label,
  valueText,
  value,
  valueRange,
  onValueChange,
  step = 0.5,
  enabled = true,
  accentColor = "#00E5CC",
}: AuraSliderRowProps) {
  const [min, max] = valueRange;
  const pct = Math.min(100, Math.max(0, ((value - min) / (max - min)) * 100));

  return (
    <div className={cn("py-2.5", !enabled && "opacity-45 pointer-events-none")}>
      <div className="flex items-center justify-between text-xs mb-1.5">
        <span className="text-[#C5D0DE] font-medium">{label}</span>
        <span className="font-semibold px-2 py-0.5 rounded-md bg-white/[0.06] border border-white/10 text-white font-mono">
          {valueText}
        </span>
      </div>
      <div className="relative flex items-center h-5">
        <div className="w-full h-2 rounded-full bg-[#080C14] border border-white/10 relative overflow-hidden shadow-inner">
          <div
            className="h-full rounded-full transition-all duration-75"
            style={{
              width: `${pct}%`,
              backgroundColor: accentColor,
              boxShadow: `0 0 10px ${accentColor}88`,
            }}
          />
        </div>
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onValueChange(parseFloat(e.target.value))}
          disabled={!enabled}
          className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        />
      </div>
    </div>
  );
}
