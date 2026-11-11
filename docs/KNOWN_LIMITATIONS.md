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
3. **SIMD NEON:** El módulo `dsp/simd.rs` existe con estructura y benchmark, pero los intrinsics NEON son pseudocódigo. La implementación real requiere compilación AArch64 nativa.
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

1. **Fase 7 — Pruebas de Hardware:** No se ha probado en dispositivos reales. Se requiere hardware con diferentes configuraciones (altavoz, auriculares, USB DAC, Bluetooth).
2. **Fase 8 — Retirada del backend antiguo:** El DSP C++ (`dsp_engine.cpp`) y cálculos Kotlin duplicados aún existen. No se han eliminado.
3. **Fase 6 — SIMD NEON real:** Los intrinsics NEON son pseudocódigo. Se requiere implementación con `std::arch::aarch64`.
