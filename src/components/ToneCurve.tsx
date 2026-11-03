import React, { useEffect, useRef } from "react";

interface ToneCurveProps {
  bassDb: number;
  midDb: number;
  trebleDb: number;
  subDb?: number;
  subCutoffHz?: number;
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
  subDb = 0,
  subCutoffHz = 60,
  bassHz,
  trebleHz,
  maxDb = 12,
  accentColor = "#00E5CC",
  height = 110,
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
    const effectiveMaxDb = Math.max(6, maxDb);
    const yOf = (db: number) => centerY - Math.max(-1, Math.min(1, db / effectiveMaxDb)) * scale;

    const xBass = xOf(bassHz);
    const xTreble = xOf(trebleHz);
    const xSub = xOf(subCutoffHz);

    // 1. Shaded acoustic regions (Graves, Medios, Agudos)
    // Graves region [0, xBass]
    const gradGraves = ctx.createLinearGradient(0, 0, xBass, 0);
    gradGraves.addColorStop(0, "rgba(52, 211, 153, 0.08)");
    gradGraves.addColorStop(1, "rgba(52, 211, 153, 0.02)");
    ctx.fillStyle = gradGraves;
    ctx.fillRect(0, 0, xBass, h);

    // Medios region [xBass, xTreble]
    const gradMedios = ctx.createLinearGradient(xBass, 0, xTreble, 0);
    gradMedios.addColorStop(0, "rgba(255, 255, 255, 0.02)");
    gradMedios.addColorStop(0.5, "rgba(255, 255, 255, 0.04)");
    gradMedios.addColorStop(1, "rgba(255, 255, 255, 0.02)");
    ctx.fillStyle = gradMedios;
    ctx.fillRect(xBass, 0, xTreble - xBass, h);

    // Agudos region [xTreble, width]
    const gradAgudos = ctx.createLinearGradient(xTreble, 0, width, 0);
    gradAgudos.addColorStop(0, "rgba(167, 139, 250, 0.02)");
    gradAgudos.addColorStop(1, "rgba(167, 139, 250, 0.08)");
    ctx.fillStyle = gradAgudos;
    ctx.fillRect(xTreble, 0, width - xTreble, h);

    // 2. Background frequency grid lines
    ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
    ctx.lineWidth = 1;
    [50, 100, 250, 500, 1000, 2500, 5000, 10000].forEach((freq) => {
      const x = xOf(freq);
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    });

    // 3. 0 dB reference dashed line
    ctx.strokeStyle = "rgba(255, 255, 255, 0.16)";
    ctx.lineWidth = 1;
    ctx.setLineDash([4, 4]);
    ctx.beginPath();
    ctx.moveTo(0, centerY);
    ctx.lineTo(width, centerY);
    ctx.stroke();
    ctx.setLineDash([]);

    // 4. Region text labels at top of canvas
    ctx.font = "bold 9px monospace";
    ctx.textBaseline = "top";

    // "GRAVES" label
    ctx.textAlign = "left";
    ctx.fillStyle = "rgba(52, 211, 153, 0.55)";
    ctx.fillText("GRAVES", Math.max(6, xBass / 2 - 18), 8);

    // "MEDIOS" label
    ctx.textAlign = "center";
    ctx.fillStyle = "rgba(255, 255, 255, 0.4)";
    ctx.fillText("MEDIOS", (xBass + xTreble) / 2, 8);

    // "AGUDOS" label
    ctx.textAlign = "right";
    ctx.fillStyle = "rgba(167, 139, 250, 0.55)";
    ctx.fillText("AGUDOS", Math.min(width - 6, (xTreble + width) / 2 + 18), 8);

    // 5. Vertical crossover boundary separator lines & pill badges
    // Sub-bass cutoff indicator
    if (subCutoffHz > 20) {
      ctx.strokeStyle = "rgba(245, 158, 11, 0.4)";
      ctx.lineWidth = 1;
      ctx.setLineDash([2, 3]);
      ctx.beginPath();
      ctx.moveTo(xSub, 0);
      ctx.lineTo(xSub, h);
      ctx.stroke();
      ctx.setLineDash([]);

      ctx.fillStyle = "rgba(245, 158, 11, 0.6)";
      ctx.font = "8px monospace";
      ctx.textAlign = "center";
      ctx.fillText(`${Math.round(subCutoffHz)}Hz`, xSub, h - 14);
    }

    // Bass crossover line (Graves / Medios)
    ctx.strokeStyle = accentColor;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(xBass, 0);
    ctx.lineTo(xBass, h);
    ctx.stroke();

    // Badge for bassHz
    const bassText = `${Math.round(bassHz)} Hz`;
    ctx.font = "bold 8px monospace";
    const bassTextWidth = ctx.measureText(bassText).width;
    ctx.fillStyle = "#0B101B";
    ctx.fillRect(xBass - bassTextWidth / 2 - 4, h - 18, bassTextWidth + 8, 14);
    ctx.strokeStyle = `${accentColor}88`;
    ctx.lineWidth = 1;
    ctx.strokeRect(xBass - bassTextWidth / 2 - 4, h - 18, bassTextWidth + 8, 14);
    ctx.fillStyle = accentColor;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(bassText, xBass, h - 11);

    // Treble crossover line (Medios / Agudos)
    ctx.strokeStyle = "rgba(167, 139, 250, 0.85)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(xTreble, 0);
    ctx.lineTo(xTreble, h);
    ctx.stroke();

    // Badge for trebleHz
    const trebleText = trebleHz >= 1000 ? `${(trebleHz / 1000).toFixed(1)}k Hz` : `${Math.round(trebleHz)} Hz`;
    const trebleTextWidth = ctx.measureText(trebleText).width;
    ctx.fillStyle = "#0B101B";
    ctx.fillRect(xTreble - trebleTextWidth / 2 - 4, h - 18, trebleTextWidth + 8, 14);
    ctx.strokeStyle = "rgba(167, 139, 250, 0.6)";
    ctx.lineWidth = 1;
    ctx.strokeRect(xTreble - trebleTextWidth / 2 - 4, h - 18, trebleTextWidth + 8, 14);
    ctx.fillStyle = "#C4B5FD";
    ctx.textAlign = "center";
    ctx.fillText(trebleText, xTreble, h - 11);

    // 6. Smooth composite transfer function across 120 points
    // Low-shelf below bassHz, Peaking mid between bassHz and trebleHz, High-shelf above trebleHz
    const midCenter = Math.sqrt(bassHz * trebleHz);
    const midQ = 1.2;

    const responseAt = (f: number): number => {
      // Low-shelf contribution
      const lfRatio = f / bassHz;
      const bassResp = bassDb / (1 + Math.pow(lfRatio, 2));

      // Sub-bass boost below subCutoffHz
      const subRatio = f / Math.max(20, subCutoffHz);
      const subResp = subDb / (1 + Math.pow(subRatio, 2));

      // High-shelf contribution
      const hfRatio = trebleHz / Math.max(20, f);
      const trebleResp = trebleDb / (1 + Math.pow(hfRatio, 2));

      // Mid bell / peaking contribution
      const logDist = Math.log10(f / midCenter);
      const midResp = midDb / (1 + Math.pow(logDist * midQ * 3.5, 2));

      return bassResp + subResp + midResp + trebleResp;
    };

    const numPoints = 120;
    const points: { x: number; y: number }[] = [];
    for (let i = 0; i <= numPoints; i++) {
      const f = minF * Math.pow(maxF / minF, i / numPoints);
      points.push({ x: xOf(f), y: yOf(responseAt(f)) });
    }

    // Build curve path
    const path = new Path2D();
    path.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) {
      path.lineTo(points[i].x, points[i].y);
    }

    // Fill under curve
    const fillPath = new Path2D(path);
    fillPath.lineTo(width, centerY);
    fillPath.lineTo(0, centerY);
    fillPath.closePath();

    const fillGrad = ctx.createLinearGradient(0, 0, 0, h);
    fillGrad.addColorStop(0, `${accentColor}33`);
    fillGrad.addColorStop(0.5, `${accentColor}0a`);
    fillGrad.addColorStop(1, `${accentColor}22`);
    ctx.fillStyle = fillGrad;
    ctx.fill(fillPath);

    // Outer neon glow stroke
    ctx.strokeStyle = `${accentColor}44`;
    ctx.lineWidth = 6;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // Sharp tone stroke
    ctx.strokeStyle = accentColor;
    ctx.lineWidth = 2.2;
    ctx.lineCap = "round";
    ctx.stroke(path);

    // 7. Crossover intersection nodal dots
    const yBassNode = yOf(responseAt(bassHz));
    const yTrebleNode = yOf(responseAt(trebleHz));

    ctx.fillStyle = "#FFFFFF";
    ctx.beginPath();
    ctx.arc(xBass, yBassNode, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = accentColor;
    ctx.lineWidth = 2;
    ctx.stroke();

    ctx.fillStyle = "#FFFFFF";
    ctx.beginPath();
    ctx.arc(xTreble, yTrebleNode, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#A78BFA";
    ctx.lineWidth = 2;
    ctx.stroke();
  }, [bassDb, midDb, trebleDb, subDb, subCutoffHz, bassHz, trebleHz, maxDb, accentColor, height]);

  return (
    <div
      className="relative w-full rounded-2xl overflow-hidden bg-gradient-to-b from-[#090D14] to-[#0F1420] border border-white/10 shadow-inner"
      style={{ height: `${height}px` }}
    >
      <canvas ref={canvasRef} className="w-full h-full block" />
    </div>
  );
}
