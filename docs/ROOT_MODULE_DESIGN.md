# 📦 DISEÑO DEL MÓDULO SYSTEMLESS DE ROOT

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. ESTRUCTURA Y REVERSIBILIDAD DEL MÓDULO MAGISK / KERNELSU

El artefacto `auraline-system-module/` implementa:
- Instalación systemless limpia sin modificar la partición física `/system` ni `/vendor`.
- Verificación automática de ABI (AArch64 / ARMv7) y versión de SDK.
- Script de copia de seguridad `backup_config.sh` y restauración `restore_config.sh`.
- Desinstalador totalmente limpio `uninstall.sh`.

---

## 2. ARCHIVOS DEL MÓDULO

| Archivo | Propósito |
|---|---|
| `module.prop` | Metadatos del módulo (id, nombre, versión, autor). |
| `customize.sh` | Script principal de instalación: detección ABI, HAL, backup, instalación, verificación. |
| `service.sh` | Servicio post-boot: verificación de audioserver. |
| `post-fs-data.sh` | Montaje systemless de overlays. |
| `uninstall.sh` | Restauración de configuración original. |
| `common/detect_audio_hal.sh` | Detección de tipo HAL por versión Android. |
| `common/backup_config.sh` | Copia de seguridad de `audio_effects.xml`. |
| `common/install_effect.sh` | Inyección del descriptor de efecto AuraLine. |
| `common/restore_config.sh` | Restauración desde backup. |
| `common/verify_installation.sh` | Verificación de instalación exitosa. |
| `config/audio_effects.xml` | Descriptor de efecto con UUID `4c2b9a10-2384-11ee-be56-0242ac120002`. |

---

## 3. FLUJO DE INSTALACIÓN

1. **Detección de ABI:** Verifica `arm64-v8a` o `armeabi-v7a`. Aborta si no es compatible.
2. **Detección de HAL:** Determina Legacy, HIDL o AIDL según versión Android.
3. **Backup:** Copia `audio_effects.xml` original a `$MODPATH/backup/`.
4. **Instalación:** Copia el descriptor de efecto a `$MODPATH/system/vendor/etc/`.
5. **Verificación:** Confirma la presencia del archivo instalado.

---

## 4. SEGURIDAD Y REVERSIBILIDAD

- **Systemless:** No modifica particiones físicas `/system` ni `/vendor`.
- **Backup automático:** Antes de cualquier modificación.
- **Desinstalación limpia:** Restaura la configuración original.
- **Sin SELinux global:** No desactiva SELinux ni instala políticas permisivas.
- **Confirmación del usuario:** La instalación requiere confirmación explícita.
- **Detección de conflictos:** Verifica la presencia de otros módulos de audio (Viper4Android, JamesDSP).

---

## 5. PENDIENTE (Fase 5)

- Compilación de `libauralinedsp.so` para arm64-v8a.
- Implementación de la interfaz de efectos HAL.
- Pruebas de instalación en dispositivos con Root.
- Verificación de compatibilidad por fabricante.
