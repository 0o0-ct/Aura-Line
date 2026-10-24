# AuraLine 2.0

Ecualizador de audio para Android. Procesa la salida del dispositivo con una cadena de
efectos real (`DynamicsProcessing`) y permite comprobar con el micrófono que el efecto
está actuando sobre la señal, no solo que se haya podido instanciar.

| | |
|---|---|
| Versión | 2.1.0 (versionCode 3) |
| minSdk / targetSdk | 28 / 35 |
| Lenguaje | Kotlin 2.0.20 · Jetpack Compose (Material 3) |
| APK release | ~3,4 MB con R8 habilitado |

Capturas en [`docs/capturas`](docs/capturas). APK firmado en [`dist/AuraLine-2.1.0.apk`](dist/AuraLine-2.1.0.apk).

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


## Rejilla de bandas (por qué 25 bandas de verdad)

`DynamicsProcessing` no filtra de forma continua: reparte la señal en bins de FFT
(`binStop = round(cutoff * blockSize / sampleRate)`, ≈47 Hz con bloque de 1024 a 48 kHz) y
**descarta en silencio toda banda cuyo rango quede vacío**. Con una rejilla logarítmica de 20 Hz
a 20 kHz, 11 bandas compartían bin y 4 no hacían absolutamente nada.

La rejilla actual es lineal hasta 1 kHz (pasos de 100 Hz, dos bins por banda) y logarítmica por
encima. Resultado verificado en el dispositivo: **0 bandas muertas y un mínimo de 2 bins por
banda** para 20 y para 25 bandas. La UI etiqueta cada fader con el **centro** de su banda, no con
el corte.

Cambiar el número de bandas reconstruye la cadena: el recuento de bandas de un
`DynamicsProcessing` no se puede modificar en caliente.

## Verificación (prueba objetiva)

Ajustes → **Comprobar el motor** reproduce un tono de 1 kHz, lo captura con el micrófono
(`AudioRecord`) y compara el nivel con el motor apagado y con una atenuación de −20 dB en la
banda cercana a 1 kHz. Mide con Goertzel y muestra la diferencia en dB.

Y **Comprobar selectividad** emite dos tonos a la vez (447 Hz y 1647 Hz), sube sólo la banda de
1647 Hz y compara el cociente entre ambas frecuencias, lo que cancela el AGC del micrófono.

Resultados en un Samsung SM-G998U1 / Android 15 (sin root, salida por altavoz):

```
Atenuación:  referencia -43,1 dBFS · procesado -59,5 dBFS · Δ -16,5 dB   → VERIFICADO
Selectividad: 447 Hz -48,9 → -49,3 dBFS (−0,4 dB)
              1647 Hz -32,5 → -21,4 dBFS (+11,1 dB)                    → +11,5 dB VERIFICADO
```

La comprobación exige que la salida sea el altavoz: con Bluetooth o auriculares el micrófono no
captura el tono y la app lo avisa en lugar de dar un resultado falso.

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
