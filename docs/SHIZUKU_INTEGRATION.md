# ⚡ INTEGRACIÓN Y LÍMITES DE SHIZUKU / ADB SHELL

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. CAPACIDADES Y RESTRICCIONES REALES DE SHIZUKU (UID 2000 SHELL)

### Capacidades Verificadas:
- Monitoreo del estado de servicios de audio mediante `dumpsys audio` y `dumpsys media.audio_policy`.
- Diagnóstico de sesiones de audio activas y detección de paquetes reproductores emitiendo audio.

### Límites Infranqueables de Seguridad:
- **No permite inyectar bibliotecas nativas `.so` dentro de `audioserver`.**
- Las políticas SELinux en Android 11+ prohíben que procesos Shell modifiquen directamente el mezclador de hardware de AudioFlinger.

---

## 2. DETECCIÓN IMPLEMENTADA EN `PrivilegeManager.kt`

La detección de Shizuku se realiza mediante reflexión para evitar dependencias de compilación:

```kotlin
val clazz = Class.forName("rikka.shizuku.Shizuku")
```

### 2.1. Condiciones diferenciadas

| Condición | Método de verificación | Resultado |
|---|---|---|
| Shizuku instalado | `Class.forName("rikka.shizuku.Shizuku")` | API disponible |
| Shizuku iniciado | `Shizuku.pingBinder()` | Binder responsive |
| Shizuku autorizado | `Shizuku.checkSelfPermission() == 0` | Permiso concedido |
| UID Shell (2000) | `Shizuku.getUid() == 2000` | AID_SHELL |
| UID Root (0) | `Shizuku.getUid() == 0` | AID_ROOT |

### 2.2. Estado implementado

El `PrivilegeManager.kt` reporta:
- `shizukuAvailable`: Binder responsive.
- `shizukuAuthorized`: Permiso concedido a AuraLine.
- `shizukuUid`: UID efectivo del servicio.
- `isShellUid`: UID == 2000.

---

## 3. ADB WIRELESS

AuraLine puede guiar al usuario para configurar Shizuku mediante Depuración inalámbrica, respetando:

- No activar silenciosamente las opciones de desarrollador.
- No emparejar ADB sin intervención del usuario.
- No leer libremente los códigos de emparejamiento.
- Seguir los procedimientos oficiales de Shizuku.

Referencia: https://shizuku.rikka.app/guide/setup.html

---

## 4. OPERACIONES DE AUDIO CON SHIZUKU

### 4.1. Viables
- `dumpsys audio` — Inspección de estado de audio.
- `dumpsys media.audio_policy` — Rutas y dispositivos activos.
- Monitoreo de sesiones de audio registradas.
- Telemetría avanzada del sistema.

### 4.2. No viables
- Inyección de DSP en AudioFlinger.
- Carga de `.so` en audioserver.
- Modificación de AudioPolicyManager sin Root.
- Inserción de efectos globales.

**Conclusión:** Shizuku proporciona capacidades avanzadas de diagnóstico y telemetría, pero NO promete procesamiento global de audio.
