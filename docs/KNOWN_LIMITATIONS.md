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

1. **Compresor usa detección peak:** El compresor actual usa detección de envolvente peak en lugar de RMS. La versión RMS se implementará en Fase 2.
2. **Sin oversampling polifásico:** El limitador True-Peak usa estimación de gradiente para picos inter-muestra. El oversampling polifásico completo se implementará en Fase 2.
3. **Sin SIMD:** El motor es escalar f32/f64. La optimización SIMD NEON se implementará en Fase 6.
4. **Sin WASM:** El crate `auraline-wasm` no existe aún. Se implementará en Fase 9.

---

## 3. LIMITACIONES DE INTEGRACIÓN ANDROID

1. **Mutex global en FFI:** `auraline-android` usa `OnceLock<Mutex<>>` para el engine global. Esto es aceptable para JNI (llamadas desde hilo de control), pero no para el callback de audio.
2. **Sin Oboe integrado:** La integración con Oboe/AAudio se implementará en Fase 3.
3. **Sin conexión Kotlin-Rust:** El puente entre `NativeEngineBridge.kt` y el motor Rust se implementará en Fase 3.

---

## 4. LIMITACIONES DEL MÓDULO SYSTEMLESS

1. **`libauralinedsp.so` no compilado:** La biblioteca nativa HAL no existe aún. Se compilará en Fase 5.
2. **Solo Legacy HAL probado:** HIDL y AIDL requieren investigación por dispositivo.
3. **Sin pruebas en dispositivo:** La instalación se probará en Fase 7.
