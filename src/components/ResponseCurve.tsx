import React, { useEffect, useRef } from "react";
import { EqBand } from "@/src/lib/types";
import { bandCenterHz } from "@/src/lib/constants";

interface ResponseCurveProps {
  bands: EqBand[];
  gainRangeDb: number;
  accentColor?: string;
  height?: number;
}

export function ResponseCurve({
  bands,
  gainRangeDb,
  accentColor = "#00E5CC",
  height = 96,
}: ResponseCurveProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Handle high DPI
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
    const scale = h / 2.2;
    const yOf = (db: number) => centerY - Math.max(-1, Math.min(1, db / gainRangeDb)) * scale;

    // Background grid & logarithmic guide lines
    ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
    ctx.lineWidth = 1;
    [50, 100, 250, 500, 1000, 2500, 5000, 10000].forEach((freq) => {
      const x = xOf(freq);
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    });

    // dB Reference grid lines & labels (+range, 0 dB, -range)
    ctx.font = "9px monospace";
    ctx.textAlign = "right";
    ctx.textBaseline = "middle";

    // Top +range line
    const yTop = centerY - scale;
    ctx.strokeStyle = "rgba(255, 255, 255, 0.08)";
    ctx.lineWidth = 1;
    ctx.setLineDash([2, 4]);
    ctx.beginPath();
    ctx.moveTo(0, yTop);
    ctx.lineTo(width, yTop);
    ctx.stroke();

    ctx.fillStyle = "rgba(255, 255, 255, 0.35)";
    ctx.fillText(`+${Math.round(gainRangeDb)} dB`, width - 6, yTop + 7);

    // 0 dB dashed reference line
    ctx.strokeStyle = "rgba(255, 255, 255, 0.22)";
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(0, centerY);
    ctx.lineTo(width, centerY);
    ctx.stroke();
    ctx.fillText("0 dB", width - 6, centerY - 6);

    // Bottom -range line
    const yBottom = centerY + scale;
    ctx.strokeStyle = "rgba(255, 255, 255, 0.08)";
    ctx.lineWidth = 1;
    ctx.setLineDash([2, 4]);
    ctx.beginPath();
    ctx.moveTo(0, yBottom);
    ctx.lineTo(width, yBottom);
    ctx.stroke();
    ctx.fillText(`-${Math.round(gainRangeDb)} dB`, width - 6, yBottom - 7);
    ctx.setLineDash([]);

    if (bands.length === 0) return;

    // Generate curve points
    const points: { x: number; y: number }[] = [];
    points.push({ x: 0, y: yOf(bands[0].gainDb) });

    bands.forEach((band, idx) => {
      const center = bandCenterHz(bands, idx);
      points.push({ x: xOf(center), y: yOf(band.gainDb) });
    });

    points.push({ x: width, y: yOf(bands[bands.length - 1].gainDb) });

    // Smooth path using Catmull-Rom or Cardinal Spline
    const path = new Path2D();
    path.moveTo(points[0].x, points[0].y);

    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[i === 0 ? 0 : i - 1];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[i + 2 < points.length ? i + 2 : i + 1];

      const cp1x = p1.x + (p2.x - p0.x) / 6;
      const cp1y = p1.y + (p2.y - p0.y) / 6;
      const cp2x = p2.x - (p3.x - p1.x) / 6;
      const cp2y = p2.y - (p3.y - p1.y) / 6;

      path.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
    }

    // Fill area below curve
    const fillPath = new Path2D(path);
    fillPath.lineTo(width, centerY);
    fillPath.lineTo(0, centerY);
    fillPath.closePath();

    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, `${accentColor}33`);
    grad.addColorStop(0.5, `${accentColor}0a`);
    grad.addColorStop(1, `${accentColor}22`);
    ctx.fillStyle = grad;
    ctx.fill(fillPath);

    // Outer neon glow stroke
    ctx.strokeStyle = `${accentColor}55`;
    ctx.lineWidth = 6;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // Inner sharp stroke
    ctx.strokeStyle = accentColor;
    ctx.lineWidth = 2;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // Draw nodal points on active bands
    bands.forEach((band, idx) => {
      if (Math.abs(band.gainDb) > 0.4) {
        const center = bandCenterHz(bands, idx);
        const x = xOf(center);
        const y = yOf(band.gainDb);

        ctx.fillStyle = accentColor;
        ctx.beginPath();
        ctx.arc(x, y, 2.5, 0, Math.PI * 2);
        ctx.fill();
      }
    });
  }, [bands, gainRangeDb, accentColor, height]);

  return (
    <div
      className="relative w-full rounded-2xl overflow-hidden bg-gradient-to-b from-[#090D14] to-[#0F1420] border border-white/10 shadow-inner"
      style={{ height: `${height}px` }}
    >
      <canvas ref={canvasRef} className="w-full h-full block" />
    </div>
  );
}
