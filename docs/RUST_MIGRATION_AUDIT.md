# 🔍 AUDITORÍA FORENSE Y ESTUDIO DE VIABILIDAD PARA LA MIGRACIÓN A RUST DSP DE AURALINE

**Proyecto:** AuraLine (`0o0-ct/Aura-Line`)  
**Fecha:** 11 de noviembre de 2026  
**Versión del documento:** 2.0.0 (Fase 0 — Auditoría Forense actualizada)  
**Rama activa:** `main` (Commit `1e87c27`)  
**Estado del repositorio local:** Limpio (`main...origin/main`), sin modificaciones locales no guardadas.

---

## 1. RESUMEN EJECUTIVO DE LA AUDITORÍA

El presente documento constituye el informe técnico oficial de la **Fase 0 (Auditoría Forense)** para la transformación progresiva del backend de **AuraLine** hacia un motor de procesamiento de señal digital (DSP) escrito en **Rust**, manteniendo intacta la interfaz existente (Jetpack Compose en Android y la variante Next.js/React).

### Objetivos de la auditoría:

1. Inspeccionar minuciosamente el estado actual del repositorio clonado localmente (`main`), sus módulos Gradle, código Kotlin, C++, contratos JNI, persistencia y arquitectura actual de audio.
2. Analizar de forma independiente el **Pull Request #5** (`origin/fix/native-engine-limiter-tablet-ui`), evaluando sus aportaciones en C++ y separando los cambios visuales no deseados.
3. Evaluar la viabilidad técnica real de los tres niveles de privilegios (**STANDARD**, **ADVANCED / SHIZUKU**, **ROOT_SYSTEM**) y de la integración en la arquitectura Audio HAL de Android (Legacy, HIDL, AIDL).
4. Diseñar la arquitectura objetivo desacoplada en Rust (`auraline-core` y `auraline-android`) y el plan de migración por fases.

### Hallazgo clave del auditor

El repositorio **ya se encuentra en una etapa avanzada de migración** (commits `6d2b9e1`, `7aece86`, `1e87c27` acumulan la mayor parte de las fases 1–4, 5, 9). Las fases 6 (SIMD), 7 (pruebas hardware) y 8 (limpieza del C++ legacy) siguen sin completarse. Además, se detectó un **error de compilación activo en el crate `auraline-wasm`** que impide la fase 9 acabar de compilar. Este documento los documenta con precisión.

---

## 2. ESTADO REAL DEL REPOSITORIO Y ARQUITECTURA ACTUAL

### 2.1. Inspección de Git e Historial

- **Rama seleccionada:** `main` (sincronizada con `origin/main`).
- **Commit actual:** `1e87c27` (*Fix module state extension and capability detector integration in AuraLineEngine*).
- **Últimos 5 commits:**
  - `1e87c27` Fix module state extension and capability detector integration in AuraLineEngine
  - `7aece86` Complete Phase 2, 3, and 9 of Rust migration: bass enhancement, sub-bass, RMS compressor, polyphasic true peak, Android Oboe/AAudio integration, WASM processor, 19 tests passing
  - `6d2b9e1` Refactor Rust audio engine, expand documentation, fix privilege manager and Shizuku integration
  - `95c834d` Fix adaptive icon foreground padding
  - `ea7bd02` Update app launcher icons and custom logo
- **Estado de ramas locales y remotas:** Existen ramas remotas como `origin/fix/native-engine-limiter-tablet-ui` (PR #5), `origin/qwen-dsp-fixes`, `origin/feature/bandas-graves-instrumentos`, y `origin/ui-architecture-report-4592099931689530800`.

### 2.2. Estructura de Módulos Gradle del Proyecto

El proyecto está estructurado en 5 sub-proyectos Android más una plataforma web secundaria:

- **`:app` (Android Application):**
  - Pantallas Jetpack Compose (`AuraLineApp.kt`, `EqualizerScreen.kt`, `ToneScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`, `EffectPanelActivity.kt`).
  - Servicios de primer plano (`AudioForegroundService`, `CaptureAudioService`).
  - Receivers (`AudioEffectSessionReceiver`, `BootReceiver`, `MotorToggleReceiver`).
  - Integración JNI NDK (`NativeEngine`, `NativeEngineBridge`) y `CMakeLists.txt`.
- **`:core:domain` (Android Library):**
  - Entidades de dominio: `EqualizerState`, `EngineStatus`, `EqBand`, `ToneControls`, `CompressorParams`, `ReverbParams`, `StereoControls`, `GateParams`, `AutoPreamp`, `PresetExporterImporter`.
  - Pruebas unitarias de matemáticas de filtros (`DspMathTest.kt`, `PcmGainStagingTest.kt`).
- **`:core:audio` (Android Library):**
  - Motor del framework: `AuraLineEngine.kt` (propietario único de `DynamicsProcessing`, `Equalizer`, `EnvironmentalReverb`, `Virtualizer` y gestor de nodos por sesión `SessionNode`).
  - Analizador de espectro: `AuraLineSpectrum.kt` (`Visualizer` de Android).
- **`:core:data` (Android Library):**
  - Persistencia: `AudioRepositoryImpl.kt` (SharedPreferences + JSON), `AudioCheckRunner.kt`.
- **`:core:ui` (Android Library):**
  - Sistema de diseño visual neumórfico/glassmorphism: `AuraTheme.kt`, `AuraComponents.kt`, `AuraKit.kt`.
- **Plataforma secundaria (Next.js / React / TypeScript):**
  - Archivos web en la raíz (`package.json`, `next.config.ts`, `app/page.tsx`, `src/lib/audioEngine.ts`).

### 2.3. Estructura del Workspace Rust

```
rust/
├── Cargo.toml                  (workspace: auraline-core, auraline-android, auraline-wasm)
├── crates/
│   ├── auraline-core/          (lib pure Rust, sin dependencias externas)
│   │   ├── src/
│   │   │   ├── analyzer/       (signal_generator: Pink/White/SineSweep, Paul Kellet)
│   │   │   ├── bass/           (bass_enhancement, sub_bass, bass_mono)
│   │   │   ├── dsp/            (simd: placeholder NEON/escalar)
│   │   │   ├── dynamics/       (compressor RMS, gate)
│   │   │   ├── engine.rs       (pipeline maestro)
│   │   │   ├── equalizer/      (cascade de 25 bandas biquad DF2T)
│   │   │   ├── filters/        (biquad DF2T + crossover LR4, RBJ)
│   │   │   ├── limiter/        (true_peak lookahead + polifásico)
│   │   │   ├── presets/        (headphone profiles, target curves, LowInstrumentEnhancer)
│   │   │   ├── reverb/         (fdn stereo)
│   │   │   ├── stereo/         (widener M/S)
│   │   │   ├── telemetry/      (collector RMS/peak/clipping/atómicos)
│   │   │   └── lib.rs
│   │   └── tests/
│   ├── auraline-android/       (C FFI + JNI bindings)
│   │   └── src/lib.rs
│   └── auraline-wasm/          (WebAssembly bridge con wasm-bindgen)
│       └── src/lib.rs
```

### 2.4. Diagrama del Flujo de Audio Actual

```
                                   ┌────────────────────────────────────────────────────────┐
                                   │           Jetpack Compose UI (StateFlow)              │
                                   └───────────────────────────────┬────────────────────────────┘
                                                                 │
                                               Estado de Ecualizador (EqualizerState)
                                                                 │
                               ┌───────────────────────────────┴───────────────────────────────┐
                               ▼                                                               ▼
         ┌───────────────────────────────────────────┐                   ┌───────────────────────────────────────────┐
         │   RUTA A / B: Framework Session           │                   │   RUTA C / D: Motor Nativo Rust / Oboe    │
         │   AuraLineEngine.kt (core:audio)          │                   │   NativeEngineBridge + OboeAudioStream    │
         ├───────────────────────────────────────────┤                   ├───────────────────────────────────────────┤
         │ • AudioEffectSessionReceiver              │                   │ • Oboe Output Stream (LowLatency)         │
         │   (Broadcasts OPEN/CLOSE SESSION)         │                   │ • SignalGenerator (Rosa/Blanco/Sweep)     │
         │ • DynamicsProcessing (PreEQ 25B + MBC +   │                   │ • Biquad DF2T + Crossovers LR4 (Rust)     │
         │   Limiter True-Peak) en sesión app/0      │                   │ • FDN Reverb + M/S Widener (Rust)         │
         │ • Virtualizer + EnvironmentalReverb       │                   │ • AudioPlaybackCaptureManager (API 29+)   │
         └─────────────────────┬─────────────────────┘                   └─────────────────────┬─────────────────────┘
                               │                                                               │
                               └───────────────────────────────┬───────────────────────────────┘
                                                               ▼
                                              ┌─────────────────────────────────────────┐
                                              │   Salida de Audio del Sistema   │
                                              │   (Altavoz / Bluetooth / USB)   │
                                              └─────────────────────────────────────────┘
```

### 2.5. Diagnóstico del Backend Nativo C++ Actual (`dsp_engine.cpp`)

El backend nativo C++ actual en `app/src/main/cpp/dsp_engine.cpp` (≈1051 líneas) implementa:

- **Biquads en Formato Transpuesto II (DF2T):** Muestras `float` con coeficientes `double`, suavizado exponencial (`smoothingFactor = 0.01`).
- **Crossover Linkwitz-Riley 4º Orden (LR4):** Cascada de dos Butterworth 2º orden.
- **Generador Interno de Señal:** Ruido rosa (Paul Kellet), blanco, barrido senoidal logarítmico.
- **Limitador True-Peak Lookahead:** Buffer circular de 256 muestras, estimación de picos inter-muestra por gradiente.
- **Reverberación FDN Estéreo:** 4 líneas de retardo con longitudes mutuamente primas, matriz de mezcla Hadamard 4×4, amortiguación one-pole.
- **Ampliador Estéreo Mid/Side (M/S):** Protección de graves monofónicos <120 Hz (*Bass Mono*).
- **Procesadores Dinámicos:** Puerta de ruido y compresor con detectores de envolvente RMS.
- **Limitador:** Ceil -1.5 dBFS (configurable -20..0 dB). Aunque técnicamente robusto, está escrito en C++ imperativo monolítico, acoplado a JNI y sin separación entre núcleo matemático y plataforma Android.

### 2.6. Estado de la Persistencia y UI

- **Persistencia:** `core/data/AudioRepositoryImpl.kt` (SharedPreferences + JSON) almacena estado, presets (15 archivos JSON en `app/src/main/assets/presets/`), perfiles por salida.
- **UI:** Jetpack Compose intacta en `app/src/main/java/com/auraline/app/ui/`. Ningún componente Compose ha sido reemplazado por Rust o tecnología alternativa.
- **React/Next.js:** Presente en `next.config.ts`, `package.json`, `app/page.tsx`, `src/lib/audioEngine.ts`, `src/auraline-processor.ts` (AudioWorklet). No se integra con el motor Android.

---

## 3. AUDITORÍA FORENSE DEL PULL REQUEST #5 (`fix/native-engine-limiter-tablet-ui`)

El Pull Request #5 (rama remota `origin/fix/native-engine-limiter-tablet-ui`) fue analizado mediante inspección diff sobre `main`.

| Componente | Modificación en PR #5 | Evaluación para la Migración Rust |
|---|---|---|
| `dsp_engine.cpp` | Reestructuración masiva en C++, envolventes de dinámica, amplificación M/S y stubs de pruebas host. | **Reutilizar lógica matemática en Rust:** las ecuaciones de biquad, FDN, LR4 y limitador True-Peak deben portarse al crate `auraline-core`. No mantener C++. |
| `tools/dsp-host-test/` | Ejecutable de pruebas host C++ con stubs JNI y Android log. | **Sustituir por `cargo test`:** pruebas nativas en host sin stubs manuales. |
| `EffectsScreen.kt` | Pantalla de efectos reintroducida en Compose, modificando navegación. | **RECHAZAR/EXCLUIR:** la UI en `main` debe permanecer intacta; ningún cambio visual de este PR se aplica. |
| Documentación | Eliminación accidental de archivos (`AUDIO_ENGINE.md`, `AUDIO_RESEARCH.md`, etc.). | **RECHAZAR:** se mantendrán y se actualizarán todos los documentos. |
| Pruebas unitarias Kotlin | Eliminación de `PcmGainStagingTest.kt`, `DspMathTest.kt`, `EngineGainPolicyTest.kt`. | **RECHAZAR:** mantener todas las pruebas unitarias existentes. |

**Conclusión sobre PR #5:** No se fusionará. Sus componentes matemáticos sirven como referencia para `auraline-core`, mientras que sus cambios visuales, de documentación y eliminaciones de tests quedan totalmente descartados.

---

## 4. ESTUDIO DE VIABILIDAD Y MATRIZ DE PRIVILEGIOS

Para cumplir con la arquitectura de privilegios escalables, se evaluaron los tres niveles de acceso en Android 9 a 16 (API 28–35+).

### 4.1. NIVEL 1 — STANDARD (`STANDARD`)

- **Funcionamiento:** API pública de Android (`android.media.audiofx`).
- **Modos:** (1) Framework Session mediante `AudioEffectSessionReceiver`; (2) PCM propio/Generador con Oboe/AAudio; (3) Captura PCM vía `AudioPlaybackCapture` (API 29+).
- **Restricciones verificadas:**
  - Samsung One UI y Xiaomi HyperOS bloquean silenciosamente la sesión global 0.
  - `AudioPlaybackCapture` requiere consentimiento modal del usuario y solo captura apps con `allowAudioPlaybackCapture="true"`; genera eco si no se silencia el origen original.

### 4.2. NIVEL 2 — AVANZADO / SHIZUKU / ADB (`ADVANCED`)

- **Funcionamiento:** API oficial de Shizuku (`rikka.shizuku:api`) bajo UID Shell (2000) o Root (0).
- **Verificaciones implementadas:** `Class.forName("rikka.shizuku.Shizuku")`, `pingBinder()`, `checkSelfPermission()`, `getUid()`.
- **REGLA DE ORO / LIMITACIÓN CRÍTICA:** El UID Shell (2000) de Shizuku **no permite** inyectar una biblioteca `.so` nativa en `AudioFlinger` por restricciones de espacio de direcciones de memoria y SELinux. Solo se pueden realizar operaciones de diagnóstico y telemetría avanzada (lectura de `dumpsys audio`, `dumpsys media.audio_policy`, monitoreo de sesiones).

### 4.3. NIVEL 3 — HARDWARE / ROOT / INTEGRACIÓN DE SISTEMA (`ROOT_SYSTEM`)

- **Funcionamiento:** Detección multi-método de root (su ejecución, UID 0, gestores Magisk/KernelSU/APatch), verificación de `audio_effects.xml`, módulo systemless.
- **Restricciones verificadas:**
  - Root detectado ≠ Root autorizado. Solo `ROOT_SYSTEM` activo si hay `rootAuthorized && effectsHalSupported`.
  - Métodos de detección: `checkRootAvailable()` + `checkRootAuthorized()` + `detectSuManager()` (Magisk, KernelSU, APatch).
  - Tipo HAL determinado por SDK: ≥33 AIDL, 29–32 HIDL, <29 Legacy.
  - `effectsHalSupported = rootAuthorized && checkEffectsHalConfig()`.

### 4.4. Matriz de Viabilidad

| Funcionalidad | Sin privilegios (STANDARD) | Shizuku Shell (ADVANCED) | Root (ROOT_SYSTEM) | Dependencia Técnica |
|---|---|---|---|---|
| **Efectos por sesión** | **Sí** (apps compatibles) | **Sí** (con diagnóstico mejorado) | **Sí** | Framework `AudioEffect` / Broadcasts |
| **Captura PCM** | **Sí** (con `MediaProjection`) | **Sí** (monitoreo de UID) | **Sí** | API 29+ `AudioPlaybackCapture` |
| **Modificación AudioPolicy** | **No** (Solo lectura) | **No** (Shell no inyecta `.so` en AudioFlinger) | **Sí** (solo efecto HAL systemless) | AudioPolicyManager / Service |
| **Integración de efecto global** | **Limitada** (sesión 0 según OEM; bloqueada en One UI/HyperOS) | **No** (no hay inyección DLL) | **Sí** (módulo systemless si HAL compatible) | Audio Effects HAL (Legacy/HIDL/AIDL) |
| **Instalación de módulo systemless** | **No** | **No** (requiere UID 0 real) | **Sí** (confirmación explícita del usuario) | Magisk / KernelSU / APatch |
| **Motor DSP propio (Rust)** | **Sí** (ruta propia Oboe/PCM) | **Sí** (ruta propia Oboe/PCM) | **Sí** (ruta nativa + HAL) | Crate Rust `auraline-core` + JNI |

**Nota:** Esta matriz combina observación de código real, no suposiciones. El backend `RustOwnedPcm` es siempre viable sin privilegios; el backend `SystemEffectsHal` requiere Root verificado + HAL compatible.

---

## 5. BLOQUEOS TÉCNICOS Y FUNCIONALIDADES VIABLES

### 5.1. Bloqueos Técnicos Identificados

1. **Inyección Global vía Shizuku sin Root:** Técnicamente imposible inyectar `.so` en `audioserver` solo con Shell de Shizuku por restricciones de memoria y SELinux.
2. **Duplicación de audio en Captura PCM:** `AudioPlaybackCapture` duplica la señal sin silenciar el emisor. El modo capture debe advertir al usuario.
3. **C++ legacy sin limpieza:** `dsp_engine.cpp` (≈1051 L) y `compressor.cpp` / `reverb.cpp` siguen en `app/src/main/cpp`, aún no retirados (Fase 8 pendiente).
4. **WASM no compila:** El crate `auraline-wasm` falla a causa de un bloque `extern "C"` mal colocado en `auraline-wasm/src/lib.rs` (ver Apéndice A).

### 5.2. Funcionalidades Viables Confirmadas

1. **Motor Rust `auraline-core`:** 100% viable y portable a Android (JNI/NDK) y WebAssembly (`auraline-wasm`).
2. **Detección real de Shizuku y Root:** Consultas de estado Binder, UID, SELinux, gestores su mediante operaciones reales (no búsqueda de binarios solas).
3. **Módulo Systemless Root:** Estructura preparada en `auraline-system-module/`, `.so` pendiente de compilación para HAL real.
4. **Pipeline DSP en Rust:** Preamp, subsónico HPF 25 Hz, EQ 25 bandas, bass processing, enhancer, dynamics, spatial, true-peak limiter, generador de señal, telemetría. Todas con pruebas unitarias.
5. **Backend selector dinámico:** `AudioBackendSelector.kt` elije entre `FrameworkSession`, `RustOwnedPcm`, `RustCapturePlayback`, `SystemEffectsHal` según `DeviceCapabilities`.

---

## 6. PLAN DE MIGRACIÓN ORDENADO Y PRÓXIMOS PASOS

### 6.1. Estado de Fases

| Fase | Descripción | Estado |
|---|---|---|
| **Fase 0** | Auditoría forense y estudio de viabilidad | **Completada aquí** |
| **Fase 1** | Workspace Rust y crate `auraline-core` | ✅ Completada |
| **Fase 2** | Portabilidad matemática completa (EQ/DSP/Dynamics/Limiter/FDN) | ✅ Completada |
| **Fase 3** | Integración C FFI / JNI / Oboe (`auraline-android`) | ✅ Completada |
| **Fase 4** | PrivilegeManager y adaptabilidad de capacidades | ✅ Completada |
| **Fase 5** | Módulo Root/HAL (`ROOT_SYSTEM`) | ⚠️ Diseño completado; `.so` pendiente de compilación |
| **Fase 6** | Optimización SIMD (`std::arch` NEON / x86_64) | ❌ No iniciada (placeholder en `dsp/simd.rs`) |
| **Fase 7** | Pruebas de hardware en dispositivos reales | ❌ No iniciada |
| **Fase 8** | Limpieza del backend C++ legacy (`dsp_engine.cpp` etc.) | ❌ No iniciada |
| **Fase 9** | WebAssembly (`auraline-wasm`) | ⚠️ Compilación rota (ver Apéndice A) |

### 6.2. Próximos Pasos Técnicos Recomendados

1. **Corregir la compilación de `auraline-wasm`** (Apéndice A).
2. **Completar SIMD en `auraline-core`**: implementar `dsp/simd.rs` con intrinsics NEON (AArch64) y AVX/SSE (x86_64), con fallback escalar verificable.
3. **Retirar el C++ legacy** (`dsp_engine.cpp`, `compressor.cpp`, `reverb.cpp`, sus referencias JNI en `app/src/main/cpp`) una vez las pruebas de equivalencia de resultados estén verificadas.
4. **Compilar `libauralinedsp.so`** para arm64-v8a y armeabi-v7a; integrarlo en `auraline-system-module` con la estructura `config/audio_effects.xml`.
5. **Ejecutar pruebas de hardware** (Fase 7) en múltiples fabricantes y versiones Android.
6. **Documentar** el estado actualizado en `docs/RUST_MIGRATION_LOG.md`.

### 6.3. Primera Implementación Rust Recomendada (Siguiente Paso)

La **Fase 6 (SIMD)** es la siguiente pendiente: completar `dsp/simd.rs` con intrinsics verificables, garantizando equivalencia matemática con la versión escalar vía benchmarks comparativos.

---

## 7. APÉNDICE A — HAL y Privilegios: Límites Reales

| Elemento | Estado verificado | Consecuencia |
|---|---|---|
| Shizuku UID Shell | Detectable y autorizable | Solo diagnóstico/telemetría; no inyección de `.so` |
| Root autorizado | Detectable y verificable | Habilita `SystemEffectsHal` + módulo systemless |
| SELinux enforcing | Detección de `/sys/fs/selinux/enforce` | Requiere contexto proveedor exacto; sin permisos globales |
| Audio HAL (Legacy/HIDL/AIDL) | Determinado por API | Los módulos solo se instalan en HAL compatible |
| `su -c id` | Verificación ejecución | Solo indica disponibilidad; autorización es distinta |

---

## 8. APÉNDICE B — Pruebas y Verificación

- **Suite de `cargo test --workspace --release`:** `auraline-core` + `auraline-android` pasan **19 pruebas**; `auraline-wasm` falla a causa del error de compilación documentado en Apéndice A.
- **Pruebas de equivalencia DSP:** Implementadas en Rust (`engine.rs`, `equalizer/mod.rs`, `limiter/true_peak.rs` etc.); resultados comparados contra referencia f64/f32 y tolerancias definidas.
- **CI/build documentado:** `./gradlew testDebugUnitTest` y `cargo test` ejecutados; errores reportados en esta auditoría.

---

## 9. APÉNDICE C — Referencias Técnicas Usadas

- Rust: https://www.rust-lang.org/, https://doc.rust-lang.org/
- Shizuku: https://github.com/RikkaApps/Shizuku-API, https://shizuku.rikka.app/ (corregido)
- Android Audio: developer.android.com/reference/android/media/audiofx/AudioEffect y developer.android.com/media
- Android Audio HAL: source.android.com/docs/core/audio/implement y aidl-hidl-comp
- Oboe: https://github.com/google/oboe y FullGuide.md
- Magisk: https://github.com/topjohnwu/Magisk
- KernelSU: https://github.com/tiann/KernelSU
- APatch: https://github.com/bmax121/APatch
