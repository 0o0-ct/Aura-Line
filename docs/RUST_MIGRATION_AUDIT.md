# 🔍 AUDITORÍA FORENSE Y ESTUDIO DE VIABILIDAD PARA LA MIGRACIÓN A RUST DSP DE AURALINE

**Proyecto:** AuraLine (`0o0-ct/Aura-Line`)  
**Fecha:** 5 de noviembre de 2026  
**Versión del documento:** 1.0.0 (Fase 0 — Auditoría Forense)  
**Rama activa:** `main` (Commit `95c834d`)  
**Estado del repositorio local:** Limpio (`main...origin/main`), sin modificaciones locales no guardadas.

---

## 1. RESUMEN EJECUTIVO DE LA AUDITORÍA

El presente documento constituye el informe técnico oficial de la **Fase 0 (Auditoría Forense)** para la transformación progresiva del backend de **AuraLine** hacia un motor de procesamiento de señal digital (DSP) escrito en **Rust**, manteniendo intacta la interfaz existente (Jetpack Compose en Android y la variante Next.js/React).

### Objetivos de la auditoría:
1. Inspeccionar minuciosamente el estado actual del repositorio clonado localmente (`main`), sus módulos Gradle, código Kotlin, C++, contratos JNI, persistencia y arquitectura actual de audio.
2. Analizar de forma independiente el **Pull Request #5** (`origin/fix/native-engine-limiter-tablet-ui`), evaluando sus aportaciones en C++ y separando los cambios visuales no deseados.
3. Evaluar la viabilidad técnica real de los tres niveles de privilegios (**STANDARD**, **ADVANCED / SHIZUKU**, **ROOT_SYSTEM**) y de la integración en la arquitectura Audio HAL de Android (Legacy, HIDL, AIDL).
4. Diseñar la arquitectura objetivo desacoplada en Rust (`auraline-core` y `auraline-android`) y el plan de migración por fases.

---

## 2. ESTADO REAL DEL REPOSITORIO Y ARQUITECTURA ACTUAL

### 2.1. Inspección de Git e Historial
- **Rama seleccionada:** `main` (sincronizada con `origin/main`).
- **Commit actual:** `95c834d` (*Fix adaptive icon foreground padding so logo and text fit within safe zone without clipping*).
- **Últimos 5 commits:**
  - `95c834d` Fix adaptive icon foreground padding
  - `ea7bd02` Update app launcher icons and custom logo
  - `b17c77b` Fix battery optimization exemption button in Settings
  - `300ee8b` Optimize UI responsiveness and icon-only buttons
  - `9709e7c` Corrección de diseño y adaptabilidad en DiagnosticsScreen
- **Estado de ramas locales y remotas:** Existen ramas remotas como `origin/fix/native-engine-limiter-tablet-ui` (correspondiente al PR #5), `origin/qwen-dsp-fixes`, y `origin/feature/bandas-graves-instrumentos`.

### 2.2. Estructura de Módulos Gradle del Proyecto
El proyecto está estructurado en 5 sub-proyectos Android más una plataforma web secundaria:
- **`:app` (Android Application):**
  - Contiene las pantallas Jetpack Compose (`AuraLineApp.kt`, `EqualizerScreen.kt`, `ToneScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`).
  - Servicios de primer plano (`AudioForegroundService`, `CaptureAudioService`).
  - Receivers (`AudioEffectSessionReceiver`, `BootReceiver`, `MotorToggleReceiver`).
  - Integración JNI NDK (`NativeEngine`, `NativeEngineBridge`) y `CMakeLists.txt`.
- **`:core:domain` (Android Library):**
  - Define las entidades de dominio: `EqualizerState`, `EngineStatus`, `EqBand`, `ToneControls`, `CompressorParams`, `ReverbParams`, `StereoControls`, `GateParams`, `AutoPreamp`, `PresetExporterImporter`.
  - Pruebas unitarias de matemáticas de filtros (`DspMathTest.kt`, `PcmGainStagingTest.kt`).
- **`:core:audio` (Android Library):**
  - Motor del framework: `AuraLineEngine.kt` (propietario único de `DynamicsProcessing`, `Equalizer`, `EnvironmentalReverb`, `Virtualizer` y gestor de nodos por sesión `SessionNode`).
  - Analizador de espectro: `AuraLineSpectrum.kt` (`Visualizer` de Android).
- **`:core:data` (Android Library):**
  - Persistencia: `AudioRepositoryImpl.kt` (SharedPreferences + JSON para estados, presets, perfiles).
  - Diagnóstico acústico: `AudioCheckRunner.kt`.
- **`:core:ui` (Android Library):**
  - Sistema de diseño visual neumórfico/glassmorphism: `AuraTheme.kt`, `AuraComponents.kt`, `AuraKit.kt`.
- **Plataforma secundaria (Next.js / React / TypeScript):**
  - Archivos web en la raíz (`package.json`, `next.config.ts`, `app/page.tsx`, `src/lib/audioEngine.ts`).

### 2.3. Diagrama del Flujo de Audio Actual

```
                                  ┌────────────────────────────────────────────────────────┐
                                  │           Jetpack Compose UI (StateFlow)              │
                                  └───────────────────────────┬────────────────────────────┘
                                                              │
                                            Estado de Ecualizador (EqualizerState)
                                                              │
                              ┌───────────────────────────────┴───────────────────────────────┐
                              ▼                                                               ▼
        ┌───────────────────────────────────────────┐                   ┌───────────────────────────────────────────┐
        │   RUTA A / B: Framework Session           │                   │   RUTA C / D: Motor Nativo C++ / Oboe     │
        │   AuraLineEngine.kt                       │                   │   NativeEngineBridge + dsp_engine.cpp     │
        ├───────────────────────────────────────────┤                   ├───────────────────────────────────────────┤
        │ • AudioEffectSessionReceiver              │                   │ • Oboe Output Stream (LowLatency Shared)  │
        │   (Broadcasts OPEN/CLOSE SESSION)         │                   │ • SignalGenerator (Rosa/Blanco/Sweep)     │
        │ • DynamicsProcessing (PreEQ 25B + MBC +   │                   │ • Biquad DF2T + Crossovers LR4 (C++)      │
        │   Limiter True-Peak) en sesión app/0      │                   │ • FDN Reverb + M/S Widener (C++)          │
        │ • Virtualizer + EnvironmentalReverb       │                   │ • AudioPlaybackCaptureManager (API 29+)   │
        └─────────────────────┬─────────────────────┘                   └─────────────────────┬─────────────────────┘
                              │                                                               │
                              └───────────────────────────────┬───────────────────────────────┘
                                                              ▼
                                             ┌─────────────────────────────────┐
                                             │   Salida de Audio del Sistema   │
                                             │   (Altavoz / Bluetooth / USB)   │
                                             └─────────────────────────────────┘
```

### 2.4. Diagnóstico del Backend Nativo C++ Actual (`dsp_engine.cpp`)
El backend nativo C++ actual en `app/src/main/cpp/dsp_engine.cpp` implementa:
- **Biquads en Formato Transpuesto II (DF2T):** Procesamiento de muestras `float` con coeficientes almacenados en `double` para prevenir inestabilidad numérica. Suavizado exponencial de coeficientes (`smoothingFactor = 0.01`) para evitar *zipper noise*.
- **Crossover Linkwitz-Riley 4º Orden (LR4):** Cascada de dos filtros Butterworth de 2º orden ($Q = 0.7071$).
- **Generador Interno de Señal:** Ruido rosa (algoritmo de Paul Kellet), ruido blanco y barrido senoidal logarítmico (20 Hz a 20 kHz).
- **Limitador True-Peak Lookahead:** Buffer circular de 256 muestras con estimación de picos inter-muestra por gradiente.
- **Reverberación FDN Estéreo:** 4 líneas de retardo con longitudes mutuamente primas, matriz de mezcla de Hadamard $4 \times 4$ y amortiguación por filtro de un polo.
- **Ampliador Estéreo Mid/Side (M/S):** Protección de graves monofónicos por debajo de 120 Hz (*Bass Mono*).
- **Procesadores Dinámicos:** Puerta de ruido y compresor con detectores de envolvente.
- **Limitación actual:** Aunque `dsp_engine.cpp` es técnicamente robusto, está escrito en C++ imperativo monolítico (~1050 líneas), acoplado a llamadas JNI directas y sin separación entre el núcleo matemático y la capa de la plataforma Android.

---

## 3. AUDITORÍA FORENSE DEL PULL REQUEST #5 (`fix/native-engine-limiter-tablet-ui`)

El Pull Request #5 (ubicado en la rama remota `origin/fix/native-engine-limiter-tablet-ui`) fue analizado mediante inspección diff detallada contra la rama `main`.

### 3.1. Hallazgos del Análisis de PR #5

| Componente | Modificación en PR #5 | Evaluación para la Migración Rust |
|---|---|---|
| **`dsp_engine.cpp`** | Reestructuración masiva del archivo C++, agregando envolventes de dinamica, amplificación M/S y stubs para pruebas host. | **Reutilizar lógica matemática en Rust:** Las ecuaciones de biquad, FDN, LR4 y limitador True-Peak deben portarse al crate `auraline-core` en Rust. No se mantendrá C++. |
| **`tools/dsp-host-test/`** | Adición de un ejecutable de pruebas host C++ (`dsp_test.cpp`) con stubs de JNI y Android log. | **Sustituir por `cargo test`:** En Rust, las pruebas unitarias e integración se ejecutan nativamente en el host de compilación sin necesidad de stubs manuales. |
| **`EffectsScreen.kt`** | Reintroducción de la pantalla de efectos (380 líneas) en Compose, modificando la estructura de navegación. | **RECHAZAR / EXCLUIR:** La UI actual en `main` debe permanecer intacta. No se aplicarán cambios de UI de este PR. |
| **Documentación** | Eliminación accidental de archivos de documentación (`AUDIO_ENGINE.md`, `AUDIO_RESEARCH.md`, etc.). | **RECHAZAR:** Se mantendrán y actualizarán todos los documentos en `docs/`. |
| **Pruebas unitarias Kotlin** | Eliminación de `PcmGainStagingTest.kt`, `DspMathTest.kt` y `EngineGainPolicyTest.kt`. | **RECHAZAR:** Mantener todas las pruebas unitarias existentes. |

### 3.2. Conclusión sobre PR #5
El PR #5 no se fusionará. Sus componentes matemáticos de DSP servirán como referencia durante la implementación del crate `auraline-core` en Rust, mientras que sus alteraciones visuales, de documentación y eliminaciones de tests quedan totalmente descartadas.

---

## 4. ESTUDIO DE VIABILIDAD Y MATRIZ DE PRIVILEGIOS

Para cumplir con la arquitectura de privilegios escalables, se evaluaron los tres niveles de acceso en Android 9 a 16 (API 28–35+).

### 4.1. Análisis Detallado por Nivel

#### NIVEL 1 — STANDARD (`STANDARD`)
- **Funcionamiento:** Opera mediante la API pública de Android (`android.media.audiofx`).
- **Modos:**
  1. *Framework Session:* Intercepta `audioSessionId` de reproductores compatibles mediante `AudioEffectSessionReceiver`.
  2. *PCM Propio / Generador:* Oboe / AAudio procesa flujos generados o capturados por la app.
  3. *Captura PCM (AudioPlaybackCapture):* API 29+ usando `MediaProjection`.
- **Restricciones verificadas:**
  - En Samsung One UI y Xiaomi HyperOS, la sesión global 0 es bloqueada silenciosamente por el framework para dar prioridad a sus procesadores propietarios (SoundAlive / Mi Sound).
  - `AudioPlaybackCapture` requiere consentimiento modal del usuario y solo captura apps que permiten grabación (`allowAudioPlaybackCapture="true"`). Genera eco si se reproduce simultáneamente sin silenciar el origen.

#### NIVEL 2 — AVANZADO / SHIZUKU / ADB (`ADVANCED`)
- **Funcionamiento:** Utiliza la API oficial de Shizuku (`rikka.shizuku:api`) para interactuar con servicios del sistema bajo UID Shell (2000) o UID Root (0).
- **Investigación de comandos e interfaces Binder:**
  - `dumpsys media.audio_policy` y `dumpsys audio` permiten inspeccionar el estado de las rutas, dispositivos activos y sesiones de audio registradas.
  - Comandos ADB shell como `cmd audio` o llamadas Binder a `AudioPolicyService` permiten consultar configuraciones.
- **REGLA DE ORO / LIMITACIÓN DE SEGURIDAD CRÍTICA:**  
  **El UID Shell (2000) de Shizuku NO permite inyectar un DSP nativo personalizado en el mezclador maestro `AudioFlinger`, ni cargar una biblioteca `.so` dentro del proceso `audioserver`.**  
  Las políticas de SELinux en Android 11+ (`enforcing`) prohíben estrictamente que procesos con dominio `shell` escriban en la memoria de `audioserver` o modifiquen descriptores HAL.
- **Conclusión del Modo Avanzado:** Shizuku proporciona capacidades avanzadas de **diagnóstico, auditoría de sesiones y telemetría**, pero NO promete un bypass de procesamiento global si el sistema no lo autoriza.

#### NIVEL 3 — HARDWARE / ROOT / INTEGRACIÓN DEL SISTEMA (`ROOT_SYSTEM`)
- **Funcionamiento:** Integración a nivel de Audio Effects HAL mediante un módulo systemless (Magisk / KernelSU / APatch).
- **Investigación de la Arquitectura Audio HAL:**
  1. *Legacy Audio Effects HAL:* Utiliza `/vendor/lib64/soundfx/libauralinedsp.so` y `/vendor/etc/audio_effects.xml` (Android 9 a 11).
  2. *HIDL Audio Effects HAL:* Interfaces pasarela Binder `android.hardware.audio.effect@...` (Android 10 a 13).
  3. *AIDL Audio Effects HAL:* Estándar moderno de Android 13+ (API 33+), donde los efectos se registran como servicios AIDL compilados e inyectados en la canalización del framework.
- **Requisitos de Reversibilidad y Seguridad:**
  - El módulo `AuraLine DSP System Module` debe ser 100% systemless.
  - Debe incluir scripts de verificación de ABI, backup de la configuración previa (`audio_effects.xml`), detección de conflictos (ej. Viper4Android, JamesDSP) y script de desinstalación limpia.
  - Prohibido modificar particiones de solo lectura (`/system`, `/vendor`) físicamente.

### 4.2. Matriz de Viabilidad Técnica

| Funcionalidad | Sin privilegios (`STANDARD`) | Shizuku Shell (`ADVANCED`) | Root (`ROOT_SYSTEM`) | Dependencia Técnica |
|---|---|---|---|---|
| **Efectos por sesión** | **Sí** (apps compatibles) | **Sí** (con diagnóstico mejorado) | **Sí** | Framework `AudioEffect` / Broadcasts |
| **Captura PCM** | **Sí** (con `MediaProjection`) | **Sí** (monitoreo de UID) | **Sí** | API 29+ `AudioPlaybackCapture` |
| **Modificación AudioPolicy** | **No** (Solo lectura) | **Parcial** (Consultas Binder / Shell) | **Sí** | AudioPolicyManager / Service |
| **Integración de efecto global** | **Limitada** (Sesión 0 según OEM) | **Limitada** (No permite inyección DLL) | **Sí** | Audio Effects HAL (Legacy/HIDL/AIDL) |
| **Instalación de módulo systemless** | **No** | **No** (Requiere UID 0 real) | **Sí** | Magisk / KernelSU / APatch |
| **Motor DSP propio (Rust)** | **Sí** (Ruta propia Oboe/PCM) | **Sí** (Ruta propia Oboe/PCM) | **Sí** (Ruta nativa + HAL) | Crate Rust `auraline-core` + JNI |

---

## 5. ARQUITECTURA OBJETIVO PROPUESTA (RUST CORE)

Se propone una separación limpia en cuatro capas independientes:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│   CAPA E: INTERFAZ DE USUARIO (CONSERVADA)                                             │
│   Jetpack Compose UI (Android) / React Next.js (Web)                                   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ StateFlow / Telemetría
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│   CAPA D: ADAPTADORES DE PLATAFORMA KOTLIN                                            │
│   AudioRepositoryImpl ── AuraLineViewModel ── AudioForegroundService                   │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Configuración validada
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│   CAPA C: GESTOR CENTRAL DE CAPACIDADES (PrivilegeManager)                             │
│   PrivilegeManager.kt ── AudioCapabilityDetector.kt ── AudioBackendSelector.kt          │
│   (Determina el nivel: STANDARD | ADVANCED | ROOT_SYSTEM y selecciona el backend)      │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Handles FFI opacos
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│   CAPA B: INTEGRACIÓN NATIVA RUST-ANDROID (`auraline-android`)                         │
│   JNI Bridge (repr(C)) ── C FFI ── Oboe Audio Stream ── Telemetry Sampler               │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │ Muestras PCM / Bloques de audio
┌───────────────────────────────────────────▼────────────────────────────────────────────┐
│   CAPA A: NÚCLEO DSP RUST INDEPENDIENTE (`auraline-core`)                              │
│   Biquads DF2T (f64/f32) ── Crossovers LR4 ── FDN Reverb ── True-Peak Limiter (SIMD)   │
│   • Cero asignaciones en callback real-time   • Lock-free SPSC Queues                  │
│   • 100% independiente de Android / JNI       • Pruebas matemáticas unitarias integradas  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. BLOQUEOS TÉCNICOS Y FUNCIONALIDADES VIABLES

### 6.1. Bloqueos Técnicos Identificados
1. **Inyección Global vía Shizuku sin Root:** Es técnicamente imposible inyectar una biblioteca `.so` de audio en `audioserver` únicamente con permisos Shell de Shizuku debido a las restricciones de espacio de direcciones de memoria y SELinux. AuraLine lo comunicará transparentemente.
2. **Duplicación de audio en Captura PCM:** `AudioPlaybackCapture` duplica la señal sin silenciar la app emisora. Para evitar eco, AuraLine advertirá al usuario sobre el uso de este modo.

### 6.2. Funcionalidades Viables Confirmadas
1. **Motor Rust `auraline-core`:** 100% viable y portable a Android (JNI/NDK) y WebAssembly (`auraline-wasm`).
2. **Detección real de Shizuku:** Consulta de estado de servicio Binder, versión y permisos concedidos mediante la API oficial `rikka.shizuku`.
3. **Módulo Systemless Root:** Estructura modular independiente de Magisk/KernelSU para dispositivos con Root verificado e interfaz HAL compatible.

---

## 7. PLAN DE MIGRACIÓN ORDENADO Y PRIMERA IMPLEMENTACIÓN RUST RECOMENDADA

### Plan de Fases:
- **FASE 0 — Auditoría Forense y Estudio de Viabilidad** (COMPLETADO en este documento).
- **FASE 1 — Diseño del Workspace Rust y Crate `auraline-core`**
  - Creación del directorio `rust/` con workspace Cargo.
  - Implementación del crate `auraline-core` con biquads DF2T, sanitización de denormales y tests matemáticos.
- **FASE 2 — Portabilidad Matemática Completa en Rust**
  - Migración de LR4, FDN Reverb, Limitador True-Peak, M/S Widener y Generador multiseñal a Rust.
- **FASE 3 — Integración Nativa JNI / Android (`auraline-android`)**
  - Exposición de la API FFI C y conexión con `NativeEngineBridge`.
- **FASE 4 — PrivilegeManager y Adaptabilidad de Capacidades**
  - Implementación de `PrivilegeManager.kt` e integración oficial con Shizuku API.
- **FASE 5 — Módulo de Sistema Root / HAL (`ROOT_SYSTEM`)**
  - Diseño del artefacto de módulo systemless de audio.
- **FASE 6 — Optimización SIMD (NEON)**
- **FASE 7 — Pruebas de Hardware en Dispositivo Físico**
- **FASE 8 — Limpieza del Backend C++ Legacy**
- **FASE 9 — WebAssembly (`auraline-wasm`)**

### Primera Implementación Rust Recomendada (Siguiente Paso):
Comenzar la **FASE 1**:
1. Crear la estructura del workspace Rust en `rust/Cargo.toml`.
2. Crear el crate `rust/crates/auraline-core`.
3. Implementar el módulo `biquad` en Rust (Direct Form II Transposed con coeficientes `f64` y procesamiento `f32`, sanitización de denormales y suavizado exponencial).
4. Agregar pruebas matemáticas unitarias en Rust (`cargo test`) verificando la estabilidad y coincidencia con las ecuaciones RBJ.
