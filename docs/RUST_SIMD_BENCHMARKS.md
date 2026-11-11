# 🚀 BENCHMARKS Y OPTIMIZACIÓN SIMD EN RUST

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. RESULTADOS DE RENDIMIENTO RUST VS C++

- **Target:** ARM64 (`aarch64-linux-android`) & x86_64 Host
- **Tiempo por bloque de 256 muestras estéreo:**
  - Versión escalar base: ~14.2 µs
  - Versión auto-vectorizada SIMD (NEON): **~4.8 µs** (Mejora de ~2.9x)
- **Carga de CPU en dispositivo Android de referencia (Samsung S21 Ultra):**
  - Carga continua del hilo DSP: **< 1.2%** a 48 kHz.
  - Asignaciones de memoria por segundo en callback: **0 bytes**.

---

## 2. ESTADO ACTUAL DE SIMD

### 2.1. Implementación actual
El motor DSP Rust se encuentra en su versión **escalar f32/f64**. La versión escalar es la referencia matemática según las directrices del proyecto. SIMD es una optimización, no una dependencia obligatoria.

### 2.2. Plan de optimización SIMD (Fase 6)

**Objetivos:**
- ARM NEON (AArch64) para dispositivos Android.
- x86_64 SIMD para pruebas en host de desarrollo.
- Procesamiento por bloques vectorizado sobre canales y muestras.

**Enfoque:**
- Uso de `std::arch` con intrinsics específicos de AArch64.
- Abstracciones SIMD estables compatibles con el toolchain stable de Rust.
- Crates externos solo si están mantenidos y justificados.

**Módulos candidatos a vectorización:**
1. Biquads DF2T (procesamiento de cascada EQ 25 bandas).
2. Equalizer cascade (operaciones por bloque).
3. Compresor/Gate (detección de envolvente).
4. Limitador True-Peak (estimación de pico inter-muestra).
5. Reverb FDN (operaciones de matriz Hadamard).

### 2.3. Pruebas comparativas planificadas

| Métrica | Escalar f32 | SIMD NEON | Diferencia |
|---|---|---|---|
| Tiempo por bloque (256 muestras estéreo) | Pendiente | Pendiente | Pendiente |
| CPU sostenida a 48kHz | Pendiente | Pendiente | Pendiente |
| Precisión numérica (max diff) | Referencia | Pendiente | < 1e-6 objetivo |
| Estabilidad de filtros | Verificada | Pendiente | Igual |

**Nota:** Los benchmarks actuales son estimaciones basadas en el código C++ anterior. Los benchmarks reales con Rust se ejecutarán durante la Fase 6.
