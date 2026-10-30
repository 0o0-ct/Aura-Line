# 🎛️ AuraLine - Global Audio Equalizer for Android

**AuraLine** es una aplicación de ecualización y moldeado de sonido de alta fidelidad para Android (API 28+ / Android 9.0+) que permite controlar de forma profunda y en tiempo real todo el audio saliente del dispositivo desde una sola interfaz táctil moderna con diseño **Apple Neumorphism & Glassmorphism**.

Procesa la salida global del dispositivo con una cadena de efectos real (`DynamicsProcessing`) y permite comprobar con el micrófono que el efecto está actuando sobre la señal acústica, no solo que se haya podido instanciar.

| Especificación | Detalle |
|---|---|
| **Versión** | 2.4.0 (versionCode 6) |
| **minSdk / targetSdk** | 28 / 35 (Android 9.0 – 15) |
| **Lenguaje** | Kotlin 2.0.20 · Jetpack Compose |
| **Diseño** | Apple Neumorphism + Glassmorphism UI (30 paletas) |
| **APK release** | ~3,4 MB con R8 optimizado |

Capturas en [`docs/capturas`](docs/capturas). APKs compilados en [`dist/`](dist/).

---

## ✨ Características Principales

### 🎚️ 1. Ventana de Ecualizador
- **Preamplificador Máster (Preamp)**: Control de ganancia de entrada encapsulado en cápsula flotante con ganancia precisa.
- **Ecualizador Gráfico de 20 a 25 Bandas**:
  - Filtros independientes con cortes realizables para evitar bandas inactivas en `DynamicsProcessing`.
  - Deslizadores fader verticales táctiles con manejador en cápsula extruida e indicación de nivel con código de color.
- **Gráfica de Respuesta en Frecuencia**: Panel cóncavo en tiempo real con trazado de curva en cristal oscuro.
- **Analizador de Espectro en Tiempo Real (RTA)**: 25 barras de ancho uniforme con ranuras físicas de canal (*meter wells*), degradado luminoso Apple neón y picos LED reactivos.
- **Preajustes de Fábrica e Instrumento**: Plano, Voz, Bombo, Bajo eléctrico, Guitarras, Teclado, Batería, Metales, Platillos, más curvas Harman y JBL.
- **Selector de Ventana y Rango**: Vista de 8, 12 o todas las bandas; rangos conmutables de ±6 dB, ±12 dB y ±24 dB.

### 🎛️ 2. Ventana de Tono
- **Moldeado de 4 Regiones**: Sub-bajo (0–100 Hz), Graves, Medios y Agudos con deslizadores empotrados.
- **Frecuencias de Cruce Ajustables**: Personalización milimétrica de las frecuencias de corte entre bandas de tono.
- **Panel de Curva de Tono**: Previsualización instantánea en tiempo real de la respuesta de ecualización de tono.

### 🔘 3. Ventana de Efectos
- **Cadena de 6 Módulos con Bypass**: Conmutadores individuales para Ecualizador, Puerta/Expansor, Compresor multibanda, Limitador de salida, Reverb acústica y Virtualizador estéreo.
- **Puerta de Ruido / Expansor**: Umbral y ratio ajustables para silenciar siseos de fondo.
- **Compresor Multibanda**: Umbral de compresión y recuperación transparente.
- **Reverberación de Entorno**: 8 salas acústicas calibradas (Auditorio, Estudio, Gran Salón, Escenario, etc.).

### ⚙️ 4. Ajustes y Personalización
- **Perfiles de Uso Automáticos**: Altavoz del móvil, auriculares con cable, auriculares Bluetooth, altavoces externos y barras de sonido/5.1.
- **30 Paletas de Color Monocromáticas**: Selector táctil con carrusel horizontal de discos neumórficos y brillo ambiental.
- **Diagnóstico Acústico Objetivo**: Emisión de tonos duales (485 Hz y 1771 Hz) y medición por micrófono para verificar la ganancia real del DSP (+10 dB selectividad).
- **Protección de Batería y Auto-arranque**: Configuración del servicio en primer plano y exención de optimización de batería en One UI / AOSP.

### 🎻 5. Fuentes Graves Dedicadas
- **Cada banda grave tiene un instrumento dueño**: sub-bajo, bombo, bajo eléctrico, contrabajo, bajo fretless, guitarrón, tuba y sousafón, clarinete bajo y fagot, Minimoog y caja de ritmos 808.
- **Nombre del instrumento bajo el fader**: las diez bandas de graves (50 → 948 Hz) llevan el dueño de su rango, para saber qué estás moviendo.
- **Foco de un toque por fuente**: quince recetas (sub-bajo, 808, bombo, toms, tímpanos, taiko, synth, bajo, contrabajo,…) que se **suman** a tu curva actual sin borrarla. El botón alterna: un toque prepara la fuente, otro la retira.
- **Mapa de graves completo** dentro de la *Guía de frecuencias*, con los instrumentos que comparten banda y qué hacer con cada uno.

---

## 🏛️ Módulos del Proyecto

```
:app          UI Jetpack Compose, actividades, servicio en primer plano, receivers
:core:domain  Modelo de dominio (EqualizerState, EngineStatus, AppSettings) y contratos
:core:audio   AuraLineEngine: motor de audio, propietario único de DynamicsProcessing y Visualizer
:core:data    Persistencia (SharedPreferences + JSON), presets, perfiles, diagnóstico
:core:ui      Sistema de diseño (AuraTheme, faders, perillas neumórficas, barras de espectro, curvas)
```

---

## 🛠️ Arquitectura Técnica y Motor DSP

- **Motor Principal (`DynamicsProcessing`)**:
  - Utiliza la API nativa de Android para ecualización paramétrica estéreo de bloque completo en la etapa `PreEQ` con asignación atómica por canal.
- **Captura de Sesiones Globales (`AudioEffectSessionReceiver`)**:
  - Intercepta eventos `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` emitidos por reproductores (Spotify, YouTube, Poweramp, etc.) y acopla el procesamiento dinámico al `audioSessionId` o a la mezcla global (sesión 0).
- **Analizador de Espectro (`AuraLineSpectrum`)**:
  - `Visualizer` en tiempo real con normalización por trama contra el pico y caída balística suave (decay 0.82), emitiendo una barra por cada una de las 25 bandas del ecualizador.
- **Servicio en Primer Plano (`AudioForegroundService`)**:
  - Servicio `mediaPlayback` con notificación persistente y controles rápidos para garantizar que el sistema no interrumpa el motor.

---

## 🚀 Tecnologías Utilizadas

- **Lenguaje**: Kotlin 100% (2.0.20)
- **UI Framework**: Jetpack Compose con renderizado directo en Canvas (`drawRoundRect`, `drawPath`, degradados verticales).
- **Estilo Visual**: Apple Neumorphism & Glassmorphism UI.
- **Arquitectura**: Clean Architecture modularizada.
- **Concurrencia**: Kotlin Coroutines & StateFlow reactivo.

---

## 💻 Compilación e Instalación

### Requisitos
- JDK 17 – 23
- Android SDK (API 28 mínimo, API 35 destino)

### Pasos
```bash
# Clonar el repositorio
git clone git@github.com:0o0-ct/Aura-Line.git

# Abrir el directorio del proyecto
cd Aura-Line

# Compilar e instalar APK de desarrollo en dispositivo conectado
./gradlew :app:installDebug

# Compilar APK de lanzamiento optimizado
./gradlew :app:assembleRelease
```
