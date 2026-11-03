# INVESTIGACIÓN TÉCNICA DE AUDIO EN ANDROID (AOSP / HAL / ESPACIO DE USUARIO)
**Proyecto Aura-Line — Documentación de Arquitectura e Integración de Audio**

---

## 1. Sesión de Audio Global (Session 0) vs. Sesiones por Aplicación en Android 9 a 16

### 1.1. Contexto Histórico y Deprecación de Session 0
En las primeras versiones de Android (API 9 a 27), instanciar un efecto de audio con `audioSessionId = 0` (`AudioEffect.EXTRA_AUDIO_SESSION = 0` o `GLOBAL_SESSION`) vinculaba el efecto a la mezcla global del bus multimedia (`MIXER`).
A partir de **Android 9.0 (Pie, API 28)** y consolidado en **Android 10+ (API 29 a 16)**:
- Google deprecó y restringió formalmente el uso de la sesión global `0` para aplicaciones de terceros no privilegiadas.
- **Razón de seguridad y privacidad**: Una app de terceros con acceso a la sesión 0 podría interceptar o degradar el audio de llamadas, notificaciones del sistema o flujos protegidos por DRM.
- **Comportamiento en fabricantes (OEMs)**:
  - **AOSP estándar**: `DynamicsProcessing(0)` puede instanciarse sin lanzar excepción inmediata, pero el motor `AudioFlinger` no garantiza que el grafo de efectos procese los streams de aplicaciones de terceros que especifican sus propios atributos de audio (`AudioAttributes.USAGE_MEDIA`).
  - **Samsung One UI**: El framework `SoundAlive` intercepta las sesiones y a menudo desconecta o ignora silenciosamente los efectos en sesión `0` para no interferir con Dolby Atmos.
  - **Google Pixel**: Aplica políticas estrictas de SELinux y enrutamiento en `AudioFlinger`, ignorando efectos auxiliares e insertos globales salvo que provengan del paquete `com.android.musicfx` registrado en el sistema.
  - **Xiaomi (MIUI / HyperOS)**: El daemon de audio desvía el flujo multimedia a través de algoritmos propietarios (`Mi Sound`), desechando cadenas insertas en sesión 0.

### 1.2. El Protocolo Oficial: Broadcasts de Control de Sesión
El mecanismo oficial estándar en Android para que ecualizadores de terceros procesen la salida de reproductores es el intercambio de intents dirigidos a `AudioEffectSessionReceiver`:
- `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` (`"android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION"`):
  - Contiene `AudioEffect.EXTRA_AUDIO_SESSION`: el identificador entero único asignado por `AudioTrack` o `MediaPlayer`.
  - Contiene `AudioEffect.EXTRA_PACKAGE_NAME`: el paquete del reproductor (ej. `com.spotify.music`).
- `AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` (`"android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION"`):
  - Notifica que el reproductor ha detenido la reproducción o destruido su pipeline de audio.
- **Limitación crítica**: No todas las aplicaciones anuncian su sesión. Reproductores como YouTube oficial o navegadores web Chrome/Brave no emiten este broadcast por motivos de derechos de autor y políticas internas.

---

## 2. Fallo de Efectos Auxiliares (`EnvironmentalReverb`, `PresetReverb`) en Streams Externos

### 2.1. Efectos Insertos (*Insert Effects*) vs. Efectos Auxiliares (*Auxiliary Effects*)
- **Efectos Insertos** (ej. `Equalizer`, `DynamicsProcessing`, `Virtualizer`, `BassBoost`):
  - Se colocan directamente en serie dentro de la cadena de procesamiento de la sesión. Toda muestra que viaja por la sesión atraviesa el efecto.
- **Efectos Auxiliares** (ej. `EnvironmentalReverb`, `PresetReverb`):
  - Operan en un bus de envío/retorno paralelo.
  - Para que una señal pase por un efecto auxiliar, la pista de audio origen debe invocar explícitamente:
    ```java
    audioTrack.attachAuxEffect(reverb.getId());
    audioTrack.setAuxEffectSendLevel(1.0f);
    ```
- **Por qué fallan sobre audio ajeno**: Ninguna aplicación externa (Spotify, YouTube, reproductores locales) invoca `attachAuxEffect` con el ID de la reverberación creada por una app externa en sesión 0. Por ende, la señal entrante al procesador de reverberación es matemáticamente cero.
- **Solución en Aura-Line**: Implementación de una red de reverberación nativa C++ basada en FDN / filtros peine y paso-todo que procesa el flujo PCM directamente en serie (*insert mode*), garantizando que el efecto sea siempre audible.

---

## 3. Compatibilidad de `Virtualizer`, `BassBoost` y `DynamicsProcessing` por Ruta

| Efecto | Altavoz Integrado | Auriculares Cable (3.5mm/USB) | Bluetooth A2DP | Bluetooth LE Audio | Observaciones AOSP |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DynamicsProcessing** | Soportado (API 28+) | Soportado (API 28+) | Soportado (API 28+) | Soportado (API 31+) | Es el procesador más completo; incluye PreEQ, MBC, PostEQ y Limiter. Requiere gestión dinámica de sample rate. |
| **Virtualizer** | Desactivado por SO | Soportado | Soportado | Soportado | La mayoría de HALs desactivan la espacialización si la salida es altavoz mono/estéreo integrado para evitar cancelaciones de fase. |
| **BassBoost** | Atenuado por HAL | Soportado | Soportado (Riesgo clipping) | Soportado | En Bluetooth produce saturación agresiva si no hay compensación de headroom. |
| **Equalizer (5 bandas)**| Soportado | Soportado | Soportado | Soportado | Rango limitado (típicamente $\pm 15$ dB en pasos de 100 mB). |

---

## 4. Detección de Rutas que Evitan el Mezclador (*Audio Offload*, USB Directo)

### 4.1. Audio Offload (Hardware Offload / DSP Tunneling)
- En dispositivos modernos con procesadores Qualcomm Snapdragon o MediaTek, el framework puede transferir archivos MP3/AAC comprimidos directamente al DSP de bajo consumo del módem/SoC sin decodificar en CPU ni pasar por `AudioFlinger`.
- **Impacto**: Cualquier efecto de audio basado en software (`AudioEffect` o captura PCM) queda completamente puenteado (*bypassed*).
- **Mitigación**: `AuraLineEngine` monitoriza si el flag de sesión pierde control o si el reproductor opera en modo directo, alertando al usuario en el Panel de Diagnóstico para desactivar "Audio Offload" o "Aceleración por Hardware" en la app emisora si está disponible.

### 4.2. Bluetooth A2DP / LE Audio y Codificación Pre-Códec
- Las muestras que salen de `AudioFlinger` hacia Bluetooth se codifican en SBC, AAC, aptX o LDAC.
- Los códecs con pérdidas aplican transformadas de coseno discretas modificadas (MDCT) y bancos de filtros polifase. Señales que rozan 0 dBFS sufren un incremento de amplitud de pico entre muestras (*Inter-Sample Peak Overshoot*) de hasta $+2.5$ dB.
- Si no se aplica un techo de seguridad de al menos $-1.5$ dBFS antes de la salida, el decodificador Bluetooth del auricular generará distorsión armónica digital severa.

---

## 5. Captura PCM mediante `MediaProjection` + `AudioPlaybackCaptureConfiguration`

### 5.1. Marco Legal y Técnico (Android 10+, API 29+)
- Permite que una aplicación capture el audio reproducido por otras aplicaciones utilizando:
  ```kotlin
  val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
      .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
      .addMatchingUsage(AudioAttributes.USAGE_GAME)
      .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
      .build()
  val record = AudioRecord.Builder()
      .setAudioPlaybackCaptureConfig(config)
      ...
  ```
- **Condiciones y restricciones impuestas por AOSP**:
  1. Requiere consentimiento explícito del usuario mediante el diálogo modal del sistema de `MediaProjection`.
  2. Las apps que deseen ser capturadas deben tener `targetSdkVersion >= 29` o permitirlo explícitamente en su manifiesto con `android:allowAudioPlaybackCapture="true"`.
  3. Apps con audio protegido por DRM (Widevine L1 en Netflix, algunas pistas protegidas de Spotify) no son capturadas.
  4. **Duplicación de señal**: El sistema entrega una copia del flujo de audio a la app de captura sin silenciar automáticamente la salida de la app emisora. Si Aura-Line reproduce la señal procesada a través de Oboe/AudioTrack simultáneamente, el usuario escuchará la pista dos veces con retardo (efecto eco/comb filter). Por ello, Aura-Line incluye advertencia en UI y generador interno para verificación aislada.

---

## 6. Integración a Nivel de Sistema Operativo (AOSP / HAL / Vendor / Root)

Para que un motor de ecualización opere como procesador maestro del sistema sin las restricciones de espacio de usuario, se requeriría:
1. **Librería compartida de efectos (`libauraline_effects.so`)**:
   - Implementar la interfaz `audio_effect_library_t` definida en `<hardware/audio_effect.h>`.
   - Ubicarse en `/vendor/lib64/soundfx/` o `/system/lib64/soundfx/`.
2. **Registro en el descriptor de efectos (`audio_effects.xml`)**:
   - Registrar la UUID del efecto dentro del bloque `<effects>` y mapearla a la ruta `output_session` o `global` en `/vendor/etc/audio_effects.xml`.
3. **Módulo Magisk / APatch**:
   - Inyectar el XML modificado mediante overlay sin modificar particiones de solo lectura (Systemless).
- **Postura en Aura-Line**: Mantenerse 100% libre de root y seguro para cualquier usuario estándar mediante la arquitectura híbrida: Sesiones `DynamicsProcessing` automáticas para reproductores compatibles, Generador de audio de prueba de alta fidelidad, y modo opcional `AudioPlaybackCapture`.
