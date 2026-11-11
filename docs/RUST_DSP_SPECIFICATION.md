# 🎛️ ESPECIFICACIÓN TÉCNICA DEL DSP RUST

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. CADENA DE PROCESAMIENTO PCM EN `auraline-core`

1. **Generador de Señal Interno:** Ruido Rosa (Paul Kellet), Blanco o Barrido Senoidal (20Hz-20kHz).
2. **Ganancia Preamp (-36 dB a +36 dB):** Mapeo lineal suave.
3. **Filtro Subsónico Pasa-Altos (25 Hz):** Butterworth de 2º orden.
4. **Ecualizador de 25 Bandas:** Filtros Biquad DF2T independientes con centros logarítmicos ISO.
5. **Realzador de Fuentes Graves:** 15 recetas aditivas con impulso fundamental, cuerpo, punch y corte de opacidad.
6. **Suite de Dinámica:** Puerta de Ruido + Compresor con codo y recuperación progresiva.
7. **Procesamiento Espacial:** Ampliador Estéreo M/S con *Bass Mono* (<120 Hz) + Reverberación FDN Estéreo.
8. **Limitador True-Peak de Salida:** Lookahead de 256 muestras con techo predeterminado a -1.5 dBFS.
9. **Colector de Telemetría:** Medición de RMS L/R, Peak L/R y detección de picos en tiempo real.

---

## 2. ESPECIFICACIÓN DE FILTROS BIQUAD

**Topología:** Direct Form II Transposed (DF2T).
**Precisión de coeficientes:** f64.
**Procesamiento de muestras:** f32.
**Suavizado exponencial:** `smoothing_factor = 0.01` (~10ms).

### 2.1. Tipos de filtro implementados

| Tipo | Función | Ecuaciones | Parámetros |
|---|---|---|---|
| Peaking EQ | `peaking_eq()` | RBJ Cookbook | freq, gain_db, Q |
| Low Shelf | `low_shelf()` | RBJ Cookbook | freq, gain_db, Q |
| High Shelf | `high_shelf()` | RBJ Cookbook | freq, gain_db, Q |
| Low Pass | `low_pass()` | RBJ Cookbook | cutoff, Q |
| High Pass | `high_pass()` | RBJ Cookbook | cutoff, Q |
| Notch | `notch()` | RBJ Cookbook | freq, Q |
| Band Pass | `band_pass()` | RBJ Cookbook (constant skew) | freq, Q |

### 2.2. Validación de parámetros

- Frecuencia: `clamp(10.0, sample_rate * 0.49)` — protección contra Nyquist.
- Q: `clamp(0.1, 20.0)` — protección contra inestabilidad.
- Gain: `clamp(-36.0, 36.0)` — protección contra overflow.

---

## 3. CROSSOVER LINKWITZ-RILEY LR4

**Orden:** 4º (24 dB/octava).
**Implementación:** Cascada de dos Butterworth 2º orden (Q = 0.7071).
**Propiedad:** Suma complementaria plana en magnitud.
**Fase:** 0° de diferencia relativa en el punto de cruce.

---

## 4. REVERBERACIÓN FDN ESTÉREO

**Líneas de retardo:** 4 con longitudes primas escaladas por sample rate.
**Matriz de mezcla:** Hadamard 4x4 normalizada (factor 0.5).
**Amortiguación:** Filtro one-pole lowpass configurable.
**Controles:** room_size (0-0.95), damping (0-0.9), mix (0-1).
**Estabilidad:** Energía acotada por matriz unitaria.

---

## 5. LIMITADOR TRUE-PEAK

**Lookahead:** 256 muestras (~5.33ms a 48kHz).
**Buffer:** Circular preasignado `[f32; 512]` (estéreo).
**Estimación de pico inter-muestra:** Gradiente con factor 0.25.
**Ataque:** ~1ms exponencial.
**Release:** ~80ms exponencial.
**Ceiling:** -1.5 dBFS predeterminado, configurable -20 a 0 dB.

---

## 6. DINÁMICA

### 6.1. Compresor
- **Detección:** Peak envelope.
- **Parámetros:** threshold_db, ratio, attack_ms, release_ms, makeup_db.
- **Ataque/Release:** Exponencial basado en tiempo real.

### 6.2. Noise Gate
- **Detección:** Peak L/R.
- **Parámetros:** threshold_db, ratio, attack_ms, release_ms.
- **Comportamiento:** Expander por debajo del threshold, gate completo en silencio.
- **Transiciones:** Suaves con attack/release para evitar clicks.

---

## 7. ESTÉREO M/S

**Matriz:** Mid = (L+R)/2, Side = (L-R)/2.
**Bass Mono:** HPF a 120 Hz aplica widening solo a frecuencias superiores.
**Rango de width:** 0.0 (mono) a 2.0 (ultra-wide).

---

## 8. GENERADOR DE SEÑAL

| Modo | Algoritmo | Descripción |
|---|---|---|
| Pink Noise | Paul Kellet 7-stage | Ruido rosa con respuesta plana por octava |
| White Noise | LCG (Linear Congruential) | Ruido blanco uniforme |
| Sine Sweep | Logarítmico | 20Hz→20kHz en 6 segundos |

**Nivel seguro:** -9 dBFS (0.35 lineal) predeterminado.
