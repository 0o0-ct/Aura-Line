# 🔊 COMPATIBILIDAD CON AUDIO EFFECTS HAL

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. EVALUACIÓN DE ARQUITECTURAS HAL

- **Legacy HAL (API 28–29):** Soporte mediante `/vendor/etc/audio_effects.xml` y descriptor UUID `4c2b9a10-2384-11ee-be56-0242ac120002`.
- **HIDL HAL (API 29–32):** Pasarela Binder a efectos nativos.
- **AIDL HAL (API 33+ Android 13-16):** Estándar moderno de servicios AIDL aislados.

---

## 2. DETECCIÓN DE HAL EN `PrivilegeManager.kt`

La detección se basa en la versión de Android:

| API | HAL Type | Configuración |
|---|---|---|
| 33+ (Android 13-16) | AIDL Audio Effects HAL | Servicio AIDL compilado |
| 29-32 (Android 10-12) | HIDL Audio Effects HAL | Pasarela Binder HIDL |
| 28-28 (Android 9) | Legacy Audio Effects HAL | `audio_effects.xml` + `.so` |

---

## 3. REQUISITOS DE INTEGRACIÓN POR HAL

### 3.1. Legacy HAL
- Biblioteca nativa en `/vendor/lib64/soundfx/libauralinedsp.so`.
- Descriptor en `audio_effects.xml` con UUID único.
- Interfaz `create_effect` / `destroy_effect` / `command`.
- **Estado:** Estructura de módulo preparada, `.so` pendiente de compilación.

### 3.2. HIDL HAL
- Interfaz `IEffectsFactory` Binder.
- Pasarela entre HIDL y biblioteca nativa.
- Registro mediante `audio_effects.xml`.
- **Estado:** Pendiente de investigación por dispositivo.

### 3.3. AIDL HAL
- Servicio AIDL `IEffectsConfig`.
- Registro como proveedor de efectos.
- Compilación con AIDL toolchain.
- **Estado:** Pendiente de investigación. Requiere Android 13+.

---

## 4. COMPATIBILIDAD POR FABRICANTE

| Fabricante | OEM Audio | Conflicto Potencial |
|---|---|---|
| Samsung | SoundAlive | Sesión 0 bloqueada |
| Xiaomi | Mi Sound | Sesión 0 bloqueada |
| Sony | Xperiance | Efectos propietarios |
| Huawei/Honor | Histen | Efectos propietarios |
| Pixel | Stock Android | Compatible |
| OnePlus | Stock/ColorOS | Variable |

**Nota:** La compatibilidad real debe verificarse por dispositivo durante la Fase 7.

---

## 5. ESTADO ACTUAL

- **Módulo systemless:** Estructura preparada en `auraline-system-module/`.
- **`audio_effects.xml`:** Descriptor con UUID `4c2b9a10-2384-11ee-be56-0242ac120002`.
- **`libauralinedsp.so`:** Pendiente de compilación (Fase 5).
- **Instalación automática:** Desactivada. Requiere confirmación explícita del usuario.
