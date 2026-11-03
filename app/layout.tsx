import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "AuraLine",
  description:
    "Global audio equalizer and sound shaping studio with Apple Neumorphism & Glassmorphism design, 20–25 parametric bands, 4-region tone shaping, DSP effects chain (multiband compressor, reverb, noise gate, limiter, stereo expander), acoustic engine check, and 30 monochrome palettes.",
  openGraph: {
    title: "AuraLine",
    description:
      "Global audio equalizer and sound shaping studio with Apple Neumorphism & Glassmorphism design, 20–25 parametric bands, 4-region tone shaping, DSP effects chain (multiband compressor, reverb, noise gate, limiter, stereo expander), acoustic engine check, and 30 monochrome palettes.",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="es" className="dark">
      <body className="antialiased min-h-screen bg-[#07090E] text-[#F5F6F8] font-sans selection:bg-cyan-500/30 selection:text-white">
        {children}
      </body>
    </html>
  );
}
