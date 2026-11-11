# 🦀 ARQUITECTURA DEL MOTOR RUST DSP DE AURALINE

**Proyecto:** AuraLine  
**Versión del Motor:** Rust 1.0.0 (`auraline-core` & `auraline-android`)  
**Idioma:** Español  

---

## 1. VISIÓN GENERAL DE LA ARQUITECTURA

El motor DSP de AuraLine ha sido completamente migrado a **Rust**, garantizando seguridad de memoria, ausencia de carreras de datos, rendimiento determinista en tiempo real y aceleración SIMD.

El workspace de Rust consta de dos crates principales:
1. **`auraline-core`:** Biblioteca de procesamiento de señal digital en Rust puro, independiente de la plataforma Android, JNI o SO.
2. **`auraline-android`:** Capa de adaptación NDK que expone funciones C FFI y bindings JNI para la integración con `NativeEngineBridge` y `Oboe` en Android.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI / AuraLineViewModel                          │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ StateFlow
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                  PrivilegeManager.kt / AudioCapabilityDetector.kt                      │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ C FFI / JNI
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                  auraline-android (C FFI / JNI / Oboe Bridge)                          │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ PCM Buffers
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│                  auraline-core (Pure Rust Real-Time Safe DSP)                          │
│   • Preamp & Subsonic HPF 25Hz           • 25-Band Biquad EQ Cascade (DF2T)           │
│   • Low Instrument Enhancer (15 Recetas)  • Dynamics Suite (Gate + Compressor)         │
│   • Spatial (M/S Widener + FDN Reverb)    • Lookahead True-Peak Limiter (-1.5 dBFS)    │
│   • Internal Signal Generator             • Telemetry Collector (RMS/Peak/Clipping)    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. COMPONENTES DEL MOTOR

### 2.1. `auraline-core`
- **Filtros Biquad (DF2T):** Procesamiento Direct Form II Transposed con coeficientes calculados en `f64` y muestras PCM en `f32`. Incluye suavizado exponencial de coeficientes y sanitización de denormales.
- **Crossover LR4:** Filtros divisores Linkwitz-Riley de 4º orden (24 dB/octava) formados por cascada de filtros Butterworth.
- **Reverberación FDN:** Red Feedback Delay Network con 4 líneas de retardo de longitudes primas y matriz de Hadamard $4 \times 4$.
- **Limitador True-Peak:** Lookahead de 256 muestras con estimación de picos inter-muestra mediante gradiente.
- **Ampliador Estéreo M/S:** Matriz Mid/Side con protección de graves monofónicos por debajo de 120 Hz (*Bass Mono*).

---

## 3. GARANTÍAS TIEMPO REAL
- **Cero asignaciones en el callback de audio:** Todos los buffers son preasignados durante la inicialización.
- **Sincronización Lock-Free:** Uso de variables atómicas (`AtomicBool`, `AtomicF64`, `AtomicU32`, `AtomicU64`) para la actualización de parámetros desde hilos de control sin bloquear el hilo de audio.
- **Cero JNI en el callback:** Las llamadas a Kotlin/Java se realizan exclusivamente desde hilos de control.
- **Cero logs en el callback:** La telemetría se actualiza mediante atómicos sin I/O.

---

## 4. ESTRUCTURA DE CRATES

### 4.1. `auraline-core` (Núcleo DSP)

**Ubicación:** `rust/crates/auraline-core/`

**Dependencias de tiempo de ejecución:** Ninguna (Rust puro).
**Dependencias de prueba:** `approx = "0.5"` (comparación numérica).

**Módulos:**

| Módulo | Archivo | Descripción |
|---|---|---|
| `filters::biquad` | `src/filters/biquad.rs` | DF2T con coeficientes f64, suavizado exponencial, sanitización de denormales. Tipos: Peaking EQ, Low/High Pass, Low/High Shelf, Notch, Band Pass. |
| `filters::crossover` | `src/filters/crossover.rs` | Linkwitz-Riley 4º orden (LR4) mediante cascada de Butterworth 2º orden (Q=0.7071). |
| `equalizer` | `src/equalizer/mod.rs` | Cascada de 25 bandas gráficas/paramétricas con SmoothedBiquad. |
| `dynamics::compressor` | `src/dynamics/compressor.rs` | Compresor wideband con detección de envolvente, attack/release, ratio, knee, makeup gain. |
| `dynamics::gate` | `src/dynamics/gate.rs` | Noise gate/expander con threshold, ratio, attack y release configurables. |
| `limiter::true_peak` | `src/limiter/true_peak.rs` | Lookahead de 256 muestras (~5.33ms a 48kHz) con estimación de picos inter-muestra por gradiente. |
| `reverb::fdn` | `src/reverb/fdn.rs` | Feedback Delay Network con 4 líneas de retardo, matriz Hadamard 4x4, amortiguación one-pole. |
| `stereo::widener` | `src/stereo/widener.rs` | Mid/Side con protección Bass Mono (<120 Hz) mediante HPF. |
| `analyzer::signal_generator` | `src/analyzer/signal_generator.rs` | Ruido rosa (Paul Kellet), ruido blanco, barrido senoidal logarítmico 20Hz-20kHz. |
| `presets` | `src/presets/mod.rs` | Low Instrument Enhancer con perfiles acústicos (fundamental, attack, mud cut, armónicos). |
| `telemetry` | `src/telemetry/mod.rs` | Colector de RMS, peak, dBFS, clipping y bloques procesados mediante atómicos. |
| `engine` | `src/engine.rs` | Pipeline maestro: Preamp → Subsonic HPF → EQ → Enhancer → Dynamics → Spatial → Limiter → Telemetry. |

### 4.2. `auraline-android` (Capa NDK)

**Ubicación:** `rust/crates/auraline-android/`

**Dependencias:** `auraline-core`.

**Funciones C FFI:**
- `auraline_engine_create(sample_rate)` — Crea una instancia del motor.
- `auraline_engine_destroy(engine)` — Destruye una instancia.
- `auraline_engine_process(engine, buffer, length)` — Procesa un bloque PCM.
- `auraline_engine_set_bypass(engine, bypass)` — Activa/desactiva bypass.
- `auraline_engine_set_preamp_gain(engine, gain_db)` — Establece ganancia de preamp.

**Bindings JNI:**
- `Java_com_auraline_app_audio_NativeEngine_init`
- `Java_com_auraline_app_audio_NativeEngine_setBypass`
- `Java_com_auraline_app_audio_NativeEngine_setGeneratorMode`
- `Java_com_auraline_app_audio_NativeEngine_setGeneratorLevel`
- `Java_com_auraline_app_audio_NativeEngine_setPreampGain`
- `Java_com_auraline_app_audio_NativeEngine_setEqBand`
- `Java_com_auraline_app_audio_NativeEngine_setSubsonic`
- `Java_com_auraline_app_audio_NativeEngine_setInstrumentEnhancer`
- `Java_com_auraline_app_audio_NativeEngine_setCompressor`
- `Java_com_auraline_app_audio_NativeEngine_setNoiseGate`
- `Java_com_auraline_app_audio_NativeEngine_setReverb`
- `Java_com_auraline_app_audio_NativeEngine_setStereoWidener`
- `Java_com_auraline_app_audio_NativeEngine_setLimiter`
- `Java_com_auraline_app_audio_NativeEngine_setBluetoothRoute`

---

## 5. MODELO DE PARÁMETROS

El motor utiliza un modelo de parámetros basado en atómicos para permitir actualizaciones desde el hilo de control sin bloquear el hilo de audio:

- `AtomicBool` para estados binarios (enabled, bypass, subsonic).
- `AtomicF64` (wrapper sobre `AtomicU64` con conversión de bits IEEE 754) para parámetros de punto flotante.
- Suavizado exponencial de coeficientes biquad (`smoothing_factor = 0.01`) para evitar zipper noise.
- Los parámetros se leen con `Ordering::Relaxed` en el callback (suficiente para audio).

---

## 6. PRUEBAS

El motor incluye pruebas unitarias matemáticas:

| Prueba | Módulo | Verifica |
|---|---|---|
| `test_biquad_flat_pass` | biquad | Un peaking EQ con 0 dB pasa la señal sin alterar. |
| `test_denormal_sanitization` | biquad | Los denormales se convierten a cero. |
| `test_lr4_complementary_sum` | crossover | La suma de salidas LR4 reconstruye la señal original. |
| `test_limiter_latency` | true_peak | La latencia lookahead es 256 muestras (~5.33ms a 48kHz). |
| `test_limiter_no_clipping_at_low_level` | true_peak | Señal baja no activa clipping. |
| `test_limiter_gain_reduction` | true_peak | La ganancia se reduce por debajo del ceiling. |
| `test_core_engine_pipeline` | lib.rs | El pipeline completo procesa un bloque sin errores. |
| `test_c_ffi_lifecycle` | auraline-android | El ciclo de vida C FFI (create/process/destroy) funciona. |

Ejecutar: `cargo test` desde `rust/`.
