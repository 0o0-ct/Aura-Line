# AuraLine 2.0

Ecualizador de audio para Android. Procesa la salida del dispositivo con una cadena de
efectos real (`DynamicsProcessing`) y permite comprobar con el micrófono que el efecto
está actuando sobre la señal, no solo que se haya podido instanciar.

| | |
|---|---|
| Versión | 2.0.0 (versionCode 2) |
| minSdk / targetSdk | 28 / 35 |
| Lenguaje | Kotlin 2.0.20 · Jetpack Compose (Material 3) |
| APK release | ~3,4 MB con R8 habilitado |

Capturas en [`docs/capturas`](docs/capturas). APK firmado en [`dist/AuraLine-2.0.0.apk`](dist/AuraLine-2.0.0.apk).

## Módulos

```
:app          UI Compose, actividad, servicio en primer plano, receivers
:core:domain  Modelo (EqualizerState, EngineStatus, AppSettings) e interfaces
:core:audio   AuraLineEngine: propietario único de los AudioEffect
:core:data    Persistencia (SharedPreferences + JSON), presets, perfiles, autodiagnóstico
:core:ui      Sistema de diseño (tema, faders, knobs, curva de respuesta)
```

## Cómo procesa el audio

`AuraLineEngine` construye **una sola vez** la cadena sobre la mezcla de salida
(`AudioEffect` con sesión 0) y sólo actualiza parámetros en cada cambio:

- **Ecualizador paramétrico** de 20–25 bandas (`DynamicsProcessing` preEq).
- **Compresor multibanda** de 3 bandas (MBC) con umbral, ratio, ataque y liberación.
- **Limitador** de salida a −1 dBFS.
- **Graves/Agudos** como shelves dentro del propio EQ.
- **Anchura estéreo** (`Virtualizer`) y **reverb** (`EnvironmentalReverb`), solo si están activos.
- **Ecualizador de 5 bandas del dispositivo** únicamente como respaldo si `DynamicsProcessing` falla,
  para no duplicar la curva.

Los reproductores que lo anuncian (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`) se enganchan
por sesión: el motor crea un `Equalizer` independiente y lo refleja en el estado.

Todo el trabajo con efectos ocurre en un hilo dedicado y los cambios en ráfaga se agrupan,
porque cada llamada es una IPC síncrona con el servidor de audio.

## Verificación (prueba objetiva)

Ajustes → **Comprobar el motor** reproduce un tono de 1 kHz, lo captura con el micrófono
(`AudioRecord`) y compara el nivel con el motor apagado y con una atenuación de −20 dB en la
banda cercana a 1 kHz. Mide con Goertzel y muestra la diferencia en dB.

Resultado en un Samsung SM-G998U1 / Android 15 (sin root):

```
Referencia  -43,1 dBFS
Procesado   -59,5 dBFS
Δ           -16,5 dB   → VERIFICADO
```

## Integración con el dispositivo

- Edge-to-edge real (`enableEdgeToEdge` + insets), sin barras opacas.
- Permisos en tiempo de ejecución: `POST_NOTIFICATIONS` al abrir; `RECORD_AUDIO` sólo al diagnosticar.
- Servicio en primer plano `mediaPlayback` con notificación de control y acción funcional para apagar el motor.
- `BootReceiver` deshabilitado en el manifest y activado en caliente solo si el usuario habilita el inicio automático.
- Detección de salida (altavoz, cable, Bluetooth, USB) con `AudioDeviceCallback` y perfiles de curva por salida.
- R8 y reducción de recursos en release; APK de 3,4 MB.

## Compilar

El proyecto incluye su propio toolchain, así que no hace falta instalar nada:

```bash
./gradlew :app:assembleDebug     # APK de desarrollo
./gradlew :app:assembleRelease   # APK release (sin firmar)
```

`gradlew` usa el JDK 17 de `jdk/` (Kotlin 2.0.20 no entiende JDK 24+) y la distribución de
Gradle de `gradle/dist/`. Ambos están excluidos de git por tamaño; si clonas el repositorio
en otra máquina necesitas un JDK 17–23 y Gradle 8.10.

## Limitaciones conocidas

- El procesado de la mezcla global depende del dispositivo: en este Samsung los efectos se
  enganchan y procesan; en firmwares que lo restrinjan el motor cae al ecualizador de sesión
  y el estado lo refleja (`Cadena de procesado`).
- La interfaz está en español; los textos no están externalizados todavía.
- El APK de `dist/` está firmado con una clave de desarrollo, no de publicación.
