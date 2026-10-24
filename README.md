# AuraLine 2.0

Ecualizador de audio para Android. Procesa la salida del dispositivo con una cadena de
efectos real (`DynamicsProcessing`) y permite comprobar con el micrófono que el efecto
está actuando sobre la señal, no solo que se haya podido instanciar.

| | |
|---|---|
| Versión | 2.2.0 (versionCode 4) |
| minSdk / targetSdk | 28 / 35 |
| Lenguaje | Kotlin 2.0.20 · Jetpack Compose (Material 3) |
| APK release | ~3,4 MB con R8 habilitado |

Capturas en [`docs/capturas`](docs/capturas). APK firmado en [`dist/AuraLine-2.2.0.apk`](dist/AuraLine-2.2.0.apk).

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

La rejilla actual es lineal hasta 1 kHz (pasos de 100 Hz) y logarítmica por encima. El bloque
real del efecto es de 960 muestras (50 Hz por bin), así que los cortes se cuantizan a múltiplos
de 50 Hz: el borde efectivo de una banda puede desplazarse medio bin respecto a su corte
nominal, y por eso la verificación sondea **dentro** de la banda y no en su centro geométrico. Resultado verificado en el dispositivo: **0 bandas muertas y un mínimo de 2 bins por
banda** para 20 y para 25 bandas. La UI etiqueta cada fader con el **centro** de su banda, no con
el corte.

Cambiar el número de bandas reconstruye la cadena: el recuento de bandas de un
`DynamicsProcessing` no se puede modificar en caliente.


## Preajustes, ventana de bandas y tono

La app está construida sobre documentación real de ecualización
(tablas de electrofante.com, apuntes de Cetear y el manual del PreSonus DEQ624):

- **Ventana de bandas**: 8, 12 o todas, con paginación horizontal. A 360 dp de ancho, 25 faders
  en una sola fila eran inmanejables.
- **Rango ±6 / ±12 / ±24 dB**, como el interruptor Range del DEQ624.
- **A/B**: compara la curva con la señal plana sin apagar el motor.
- **Preajustes de fábrica** por instrumento (voz, bombo, bajo, guitarras, piano, platillos,
  metales, batería), derivados de las tablas de frecuencias por instrumento.
- **Guía de frecuencias** dentro de la app: qué hace cada zona del espectro.
- **Tono de tres regiones** (graves / medios / agudos) con frecuencias de cruce ajustables,
  en lugar de dos shelves fijos, con vista previa de la curva.

No se implementan HPF/LPF ni el modo HI-Q del DEQ624: `DynamicsProcessing` no expone filtros
paso-alto/paso-bajo ni control de Q por banda.

## Verificación (prueba objetiva)

Ajustes → **Comprobar el motor** emite **dos tonos simultáneos** (≈485 Hz y ≈1771 Hz), sube
sólo la banda del tono agudo y compara el cociente entre ambas frecuencias antes y después.
El cociente cancela el procesado espectral del micrófono, que en este teléfono compensa los
cortes y hace inservibles las medidas absolutas.

Resultado en un Samsung SM-G998U1 / Android 15 (sin root, salida por altavoz):

```
485 Hz:  -44,2 → -47,5 dBFS
1771 Hz: -27,8 → -20,5 dBFS
Selectividad: +10,5 dB   → VERIFICADO
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
