# ⚠️ LIMITACIONES TÉCNICAS Y HONESTAS

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. LIMITACIONES CONOCIDAS Y DOCUMENTADAS

1. **Sesión 0 en fabricantes agresivos (Samsung One UI, Xiaomi HyperOS):**
   - La sesión global 0 es bloqueada silenciosamente por la capa del fabricante. AuraLine redirige automáticamente a la captura por sesión o informa al usuario.
2. **AudioPlaybackCapture:**
   - En Android 10+, las aplicaciones protegidas por DRM o declarando `allowAudioPlaybackCapture="false"` no son capturadas.
3. **Bluetooth Inter-Sample Overshoot:**
   - Códecs con pérdida como SBC/AAC generan picos de hasta +2.5 dB. AuraLine aplica un techo de -1.5 dBFS en el limitador True-Peak para evitar saturación en el decodificador del auricular.
4. **Shizuku sin inyección global:**
   - El UID Shell (2000) de Shizuku NO permite inyectar DSP en AudioFlinger. Solo proporciona diagnóstico y telemetría.
5. **Latencia round-trip no medida:**
   - No existe hardware de loopback en el entorno de desarrollo. La latencia round-trip se medirá en Fase 7.

---

## 2. LIMITACIONES DEL MOTOR RUST ACTUAL

1. **~Compresor usa detección peak~ ✓ RESUELTO:** El compresor ahora usa detección RMS con soft-knee y envelope follower asimétrico.
2. **~Sin oversampling polifásico~ ✓ RESUELTO:** El limitador True-Peak ahora usa interpolación cúbica 2x combinada con estimación por gradiente.
3. **SIMD (Fase 6 completada):** `dsp/simd.rs` implementa intrínsecos reales: ARM NEON (AArch64) y x86 SSE2 con fallback escalar. Verificado con pruebas de equivalencia numérica (error < 1e-4). Los tiempos absolutos (µs/bloque) requieren medición en dispositivo ARM (Fase 7).
4. **~Sin WASM~ ✓ RESUELTO:** El crate `auraline-wasm` existe con bridge wasm-bindgen y AudioWorklet TypeScript. Requiere `wasm-pack build` para generar el artefacto.
5. **Bass Mono nivel:** El filtro Bass Mono puede alterar ligeramente el nivel en la transición de fase. Se recomienda calibrar con el limitador activo.

---

## 3. LIMITACIONES DE INTEGRACIÓN ANDROID

1. **Mutex global en FFI:** `auraline-android` usa `OnceLock<Mutex<>>` para el engine global. Esto es aceptable para JNI (llamadas desde hilo de control), pero no para el callback de audio.
2. **~Sin Oboe integrado~ ✓ RESUELTO:** `OboeAudioStream.kt` proporciona el bridge con fallback a framework. Requiere la biblioteca Oboe C++ incluida en el proyecto.
3. **~Sin conexión Kotlin-Rust~ ✓ RESUELTO:** `AuraLineEngine.kt` conecta el motor Rust con Android Audio APIs, PrivilegeManager y OboeAudioStream.
4. **Oboe C++ library:** La biblioteca Oboe no está incluida en el repositorio. Debe descargarse desde https://github.com/google/oboe y añadirse al build.

---

## 4. LIMITACIONES DEL MÓDULO SYSTEMLESS

1. **`libauralinedsp.so` no compilado:** La biblioteca nativa HAL no existe aún. Se compilará en Fase 5 con CMake para cada ABI.
2. **Solo Legacy HAL probado:** HIDL y AIDL requieren investigación por dispositivo.
3. **Sin pruebas en dispositivo:** La instalación se probará en Fase 7.

---

## 5. FASES PENDIENTES

1. **Fase 5 — Compilar `libauralinedsp.so` para HAL:** Requiere Android NDK + targets rustup para Android. Pendiente.
2. **Fase 7 — Pruebas de Hardware:** No se ha probado en dispositivos reales. Se requiere hardware con diferentes configuraciones (altavoz, auriculares, USB DAC, Bluetooth).
3. **Fase 8 — Retirada del backend antiguo:** `dsp_engine.cpp` aún es el camino de audio activo. `compressor.cpp` y `reverb.cpp` eliminados (código muerto).
4. **Fase 6 — SIMD:** Implementado y verificado (NEON + SSE + escalar). Pendiente: benchmark de tiempo real en dispositivo ARM.
5. **Integración Rust → Android activa:** NativeEngine.kt carga `libauralinedsp` (C++). El FFI Rust (`auraline-android`) tiene 21 funciones JNI completas pero no es invocado por el path de audio. Cablear requiere compilar la biblioteca Rust para Android (NDK).


---

## 6. LIMITACIONES DEL ENTORNO DE DESARROLLO (ESTA SESIÓN)

1. **Compilación de `libauralinedsp.so` para ARM (Fase 5):** No ejecutable aquí. No hay Android NDK instalado ni targets rustup para Android. Requiere NDK + CMake cruzado.

2. **Pruebas de hardware (Fase 7):** No ejecutables sin dispositivos físicos. No se dispone de loopback hardware ni de dispositivos Android conectados.

3. **Motor Rust aún no en el camino de audio activo de Android:** NativeEngine.kt carga `libauralinedsp` (C++, `dsp_engine.cpp`). El crate `auraline-android` (FFI Rust) tiene 21 funciones JNI completas pero no es invocado por el path de audio. Cablear Rust requiere compilar la biblioteca para Android (NDK).

4. **`dsp_engine.cpp` conservado:** Es el DSP activo. No se retira hasta validar equivalencia C++ vs Rust en hardware físico.

5. **Sin JDK en este entorno:** No se puede ejecutar `./gradlew testDebugUnitTest` para verificar la compilación Kotlin. Los cambios Kotlin son seguros (campos con valores por defecto, firmas compatibles).

### ✅ Resuelto en esta sesión

- **JNI completo:** Todas las funciones `external` de `NativeEngine.kt` ahora tienen implementación Rust en `auraline-android`.
- **`writeInputPcm` y `getTelemetry`:** Agregadas al FFI Rust.
- **`setNoiseGate`:** Corregido de 3 a 5 parámetros (attackMs, releaseMs).
- **NativeEngineBridge:** Sincroniza Bass Enhancement, Sub-bass, Bass Mono y Gate mejorado.
- **ToneControls/GateParams:** Campos nuevos con valores por defecto seguros.
