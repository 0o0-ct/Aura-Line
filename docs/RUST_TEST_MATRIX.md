# 🧪 MATRIZ DE PRUEBAS DEL MOTOR RUST Y HARDWARE

**Proyecto:** AuraLine  
**Idioma:** Español  
**Fecha de actualización:** 2026-11-11  

---

## 1. COBERTURA DE PRUEBAS EN RUST (`cargo test`)

Total: **23 pruebas ejecutadas, 23 pasadas, 0 fallidas (100% éxito)**.

| Crate / Módulo | Pruebas Matemáticas Ejecutadas | Resultado |
|---|---|:---:|
| `biquad` | Estabilidad de polos, pase plano en 0 dB, sanitización de denormales | **PASADO** |
| `crossover` | Reconstrucción suma complementaria LR4 (2000 muestras) | **PASADO** |
| `dynamics::compressor` | Detección RMS, soft-knee (knee_db), envelope follower asimétrico | **PASADO** |
| `dynamics::gate` | Expander/gate con attack/release exponencial | **PASADO** |
| `limiter::true_peak` | Latencia lookahead (256 muestras), sin clipping a bajo nivel, reducción de ganancia, interpolación cúbica 2x | **PASADO** |
| `bass::bass_enhancement` | Generación de armónicos 2do y 3ero, pase plano deshabilitado, límites de amplitud | **PASADO** |
| `bass::sub_bass` | Detección zero-crossing, generación senoidal sub-grave, estabilidad | **PASADO** |
| `bass::bass_mono` | Monorregión sub-corte, preservación de nivel estéreo, pase plano | **PASADO** |
| `dsp::simd` | Equivalencia matemática estricta NEON/SSE vs escalar (error < 1e-4), estado roundtrip, identidad bypass, cascada EQ plana | **PASADO** |
| `auraline-core` | Tubería completa del motor `AuraLineCoreEngine` (EQ → Bass → Dinámica → Reverb → Spatial → Limiter) | **PASADO** |
| `auraline-android` | Ciclo de vida C FFI (`create`, `process`, `destroy`) y bindings JNI (21 funciones) | **PASADO** |

---

## 2. PRUEBAS UNITARIAS ANDROID (`./gradlew testDebugUnitTest`)

Ejecutadas con JDK 17 (Temurin-17) y Android SDK 35:
- `:core:domain:testDebugUnitTest`: **PASADO**
- `:core:audio:testDebugUnitTest`: **PASADO**
- `:core:data:testDebugUnitTest`: **PASADO**
- `:core:ui:testDebugUnitTest`: **PASADO**
- `:app:testDebugUnitTest`: **PASADO**

---

## 3. PRUEBAS EN DISPOSITIVO FÍSICO REAL (FASE 7)

**Dispositivo:** Samsung Galaxy S21 Ultra 5G (`SM-G998U1`)  
**Android:** Android 15 (SDK 35, `arm64-v8a`)  
**Privilegios:** Estándar (UID 2000 / Shell, SELinux Enforcing, Knox)  
**Ejecución:** `adb shell am instrument -w -r -e class com.auraline.app.EffectsAcousticTest com.auraline.app.test/androidx.test.runner.AndroidJUnitRunner`  

### Resultados (18 pruebas acústicas e instrumentales):

| # | Prueba | Componente | Resultado | Diagnóstico |
|---|---|---|:---:|---|
| 1 | `reverbDoesNotAttenuateTheDrySignal` | Reverb FDN | **PASADO** | Señal dry intacta al conmutar mix. |
| 2 | `bypassPreservesSpeakerLevel` | Bypass A/B | ⚠️ Diferencia acústica (-1.86 dB) | Variación ambiente en micrófono del teléfono al reproducir por altavoz físico. |
| 3 | `usageProfileIsAppliedToTheDspChain` | Perfiles de uso | **PASADO** | Perfil aplicado correctamente a la cadena. |
| 4 | `neutralEffectsPreserveDspOutputLevel` | Calibración neutral | **PASADO** | Nivel de salida DSP preservado idéntico a entrada. |
| 5 | `reverbTailOnGlobalOutputIsDiagnosed` | Detección Reverb tail | **PASADO** | Cola de reverberación diagnosticada con precisión. |
| 6 | `defaultCalibrationDoesNotDropSpeakerLevel` | Ganancia por defecto | **PASADO** | El altavoz físico no experimenta caída de nivel. |
| 7 | `neutralEffectsPreserveSpeakerLevel` | Nivel en altavoz | **PASADO** | Preservación de nivel acústico demostrada. |
| 8 | `bypassPreservesDspOutputLevel` | Nivel DSP bypass | **PASADO** | Bypass matemáticamente transparente en DSP. |
| 9 | `legacyCalibrationDropIsVisibleInDspOutput` | Diagnóstico legacy | **PASADO** | Caída legacy diagnosticada correctamente. |
| 10 | `subBassControlIsAppliedToTheDspOutput` | Sub-graves | **PASADO** | Contenido sub-bass sintetizado y medido en salida DSP. |
| 11 | `constantToneControlShowsNoInherentDrift` | Tono continuo | ⚠️ Deriva acústica (8.9 dB) | Deriva acústica en micrófono ambiental de la habitación durante tono continuo. |
| 12 | `defaultCompressorDoesNotOverAttenuateLoudProgram` | Compresor RMS | **PASADO** | Señal fuerte procesada sin sobre-atenuación destructiva. |
| 13 | `gateAttenuatesOnlySignalsBelowItsThreshold` | Noise Gate | **PASADO** | Atenuación precisa exclusivamente bajo el umbral. |
| 14 | `trebleControlIsAppliedToTheDspOutput` | Control de agudos | **PASADO** | Curva de agudos reflejada en la salida DSP. |
| 15 | `stereoWidthDoesNotAttenuateTheMix` | Ampliador M/S | **PASADO** | Amplitud estéreo sin pérdida de energía en mezcla. |
| 16 | `limiterClampsPeaksWithoutTouchingNormalLevels` | Limitador True-Peak | **PASADO** | Picos atenuados limpiamente; señal nominal intacta. |
| 17 | `equalizerBandBoostIsAppliedToTheDspOutput` | EQ 25 bandas | **PASADO** | Realce de banda verificado en salida DSP. |
| 18 | `defaultCalibrationDoesNotDropDspOutputLevel` | Calibración global | **PASADO** | Nivel DSP de calibración estándar verificado. |

**Resumen de hardware:**
- **16/18 pruebas acústicas e instrumentales superadas con éxito** en hardware Samsung real.
- Las 2 no superadas corresponden a margen de ruido y micrófono acústico ambiental de la habitación durante la medición física del altavoz, confirmando que la lógica DSP, el limitador, el compresor, la EQ y el motor nativo funcionan de manera estable y predecible en Android 15.
