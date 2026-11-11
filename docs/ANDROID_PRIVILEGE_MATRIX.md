# 🛡️ MATRIZ DE CAPACIDADES Y PRIVILEGIOS EN ANDROID

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. NIVELES DE ACCESO DE AURALINE

1. **`STANDARD` (Sin Root / Sin Shizuku):**
   - Procesamiento por sesión (`DynamicsProcessing`), Captura PCM (`MediaProjection`), Motor PCM propio Rust + Oboe.
2. **`ADVANCED` (Shizuku Shell Active):**
   - Monitoreo avanzado de sesiones y políticas de audio, depuración ADB inalámbrica guiada.
3. **`ROOT_SYSTEM` (Magisk / KernelSU / APatch):**
   - Integración systemless a nivel de Audio Effects HAL mediante el módulo `AuraLine DSP System Module`.

---

## 2. MATRIZ DE VIABILIDAD TÉCNICA

| Funcionalidad | Sin privilegios (STANDARD) | Shizuku Shell (ADVANCED) | Root (ROOT_SYSTEM) | Dependencia Técnica |
|---|---|---|---|---|
| **Efectos por sesión** | **Sí** (apps compatibles) | **Sí** (con diagnóstico mejorado) | **Sí** | Framework `AudioEffect` / Broadcasts |
| **Captura PCM** | **Sí** (con `MediaProjection`) | **Sí** (monitoreo de UID) | **Sí** | API 29+ `AudioPlaybackCapture` |
| **Modificación AudioPolicy** | **No** (Solo lectura) | **Parcial** (Consultas Binder / Shell) | **Sí** | AudioPolicyManager / Service |
| **Integración de efecto global** | **Limitada** (Sesión 0 según OEM) | **Limitada** (No permite inyección DLL) | **Sí** | Audio Effects HAL (Legacy/HIDL/AIDL) |
| **Instalación de módulo systemless** | **No** | **No** (Requiere UID 0 real) | **Sí** | Magisk / KernelSU / APatch |
| **Motor DSP propio (Rust)** | **Sí** (Ruta propia Oboe/PCM) | **Sí** (Ruta propia Oboe/PCM) | **Sí** (Ruta nativa + HAL) | Crate Rust `auraline-core` + JNI |

---

## 3. DETECCIÓN DE CAPACIDADES

El `PrivilegeManager.kt` verifica cada capacidad mediante operaciones reales:

### 3.1. Root
- **Disponibilidad:** Búsqueda de `su`, `magisk`, `ksud`, `apatch` en paths conocidos.
- **Autorización:** Ejecución real de `su -c id` y verificación de `uid=0`.
- **Gestor detectado:** Magisk, KernelSU, APatch o Unknown.

### 3.2. Shizuku
- **API disponible:** `Class.forName("rikka.shizuku.Shizuku")`.
- **Servicio activo:** `Shizuku.pingBinder()`.
- **Autorizado:** `Shizuku.checkSelfPermission() == 0`.
- **UID efectivo:** `Shizuku.getUid()` (2000=Shell, 0=Root).

### 3.3. SELinux
- **Estado:** Lectura de `/sys/fs/selinux/enforce` (1=enforcing, 0=permissive).

### 3.4. Audio HAL
- **Tipo:** Determinado por versión Android (API 33+=AIDL, 29-32=HIDL, <29=Legacy).
- **Configuración:** Búsqueda de `audio_effects.xml` en múltiples paths.
