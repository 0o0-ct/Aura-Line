import React, { useEffect, useRef } from "react";

interface ToneCurveProps {
  bassDb: number;
  midDb: number;
  trebleDb: number;
  bassHz: number;
  trebleHz: number;
  maxDb?: number;
  accentColor?: string;
  height?: number;
}

export function ToneCurve({
  bassDb,
  midDb,
  trebleDb,
  bassHz,
  trebleHz,
  maxDb = 12,
  accentColor = "#00E5CC",
  height = 90,
}: ToneCurveProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const h = rect.height;
    ctx.clearRect(0, 0, width, h);

    const minF = 20;
    const maxF = 20000;
    const span = Math.log10(maxF / minF);
    const xOf = (freq: number) => {
      const clamped = Math.max(minF, Math.min(maxF, freq));
      return (Math.log10(clamped / minF) / span) * width;
    };

    const centerY = h / 2;
    const scale = h / 2.3;
    const yOf = (db: number) => centerY - Math.max(-1, Math.min(1, db / maxDb)) * scale;

    // 0 dB dashed center line
    ctx.strokeStyle = "rgba(255, 255, 255, 0.16)";
    ctx.lineWidth = 1;
    ctx.setLineDash([5, 5]);
    ctx.beginPath();
    ctx.moveTo(0, centerY);
    ctx.lineTo(width, centerY);
    ctx.stroke();
    ctx.setLineDash([]);

    // Crossover frequency vertical lines
    const xBass = xOf(bassHz);
    const xTreble = xOf(trebleHz);

    ctx.strokeStyle = "rgba(255, 255, 255, 0.12)";
    ctx.lineWidth = 1;

    ctx.beginPath();
    ctx.moveTo(xBass, 0);
    ctx.lineTo(xBass, h);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(xTreble, 0);
    ctx.lineTo(xTreble, h);
    ctx.stroke();

    // Stepped tone curve with smooth transitions
    const path = new Path2D();
    const yBass = yOf(bassDb);
    const yMid = yOf(midDb);
    const yTreble = yOf(trebleDb);

    path.moveTo(0, yBass);
    path.lineTo(xBass - 8, yBass);
    path.bezierCurveTo(xBass, yBass, xBass, yMid, xBass + 8, yMid);
    path.lineTo(xTreble - 8, yMid);
    path.bezierCurveTo(xTreble, yMid, xTreble, yTreble, xTreble + 8, yTreble);
    path.lineTo(width, yTreble);

    // Outer neon glow
    ctx.strokeStyle = `${accentColor}44`;
    ctx.lineWidth = 6;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // Sharp tone stroke
    ctx.strokeStyle = accentColor;
    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // Crossover node circles
    ctx.fillStyle = accentColor;
    ctx.beginPath();
    ctx.arc(xBass, yBass, 3, 0, Math.PI * 2);
    ctx.arc(xTreble, yTreble, 3, 0, Math.PI * 2);
    ctx.fill();
  }, [bassDb, midDb, trebleDb, bassHz, trebleHz, maxDb, accentColor, height]);

  return (
    <div
      className="relative w-full rounded-2xl overflow-hidden bg-gradient-to-b from-[#090D14] to-[#0F1420] border border-white/10 shadow-inner"
      style={{ height: `${height}px` }}
    >
      <canvas ref={canvasRef} className="w-full h-full block" />
    </div>
  );
}
