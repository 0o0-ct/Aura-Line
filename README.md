# 🎛️ AuraLine (NeoEQ) - Global Audio Equalizer for Android

**AuraLine** es una aplicación de ecualización y moldeado de sonido de alta fidelidad para Android (API 28+ / Android 9.0+) que permite controlar de forma profunda y en tiempo real todo el audio saliente del dispositivo desde una sola interfaz táctil moderna en **estilo neumórfico oscuro (Dark Neumorphism UI)**.

---

## ✨ Características Principales

### 🎚️ 1. Ventana de Ecualizador
- **Preamplificador Máster (Preamp)**: Control de ganancia de entrada encapsulado en cápula flotante para amplificación de volumen de salida (+15 dB / -15 dB).
- **Ecualizador Gráfico de 20 a 25 Bandas**:
  - Filtros totalmente independientes desde **20 Hz hasta 20 kHz** con distribución logarítmica.
  - Deslizadores fader verticales con manejador táctil en cápsula e indicación de nivel con código de color.
- **Gráfica de Respuesta en Frecuencia**: Panel cóncavo en tiempo real con trazado spline en cian neón brillante.
- **Controles de Tono Independientes**: Diales dedicados para **Graves** (Low-Shelf) y **Agudos** (High-Shelf) de hasta ±18 dB.

### 🎛️ 2. Ventana de Controles
- **Balance Estéreo**: Atenuación suave e independiente para canal izquierdo/derecho.
- **Expansión Estéreo**: Ensanchamiento de imagen sonora.
- **Botón Mono y Restablecer**: Conmutador mono y reinicio instantáneo de parámetros.
- **Dial Máster de Volumen**: Perilla central de gran formato sincronizada con el volumen del sistema.

### 🔘 3. Ventana de Efectos
- **Sub-pestañas**: *Reverb*, *Compresor* y *Otros*.
- **Rejilla de 6 Parámetros de Reverberación**: *Amortiguación, Filtro, Fundido, Preretardo, Mezcla de preretardo y Tamaño*.
- **Preajustes de Entornos Acústicos**: *Auditorio, Eco, Escenario, Estadio, Estudio, Gran salón, Ligera reverberación y Sala pequeña*.
- **Dial de Mezclado (Dry/Wet)**: Control principal de mezcla de efectos.

---

## 🛠️ Arquitectura Técnica y Motor DSP

- **Motor Principal (`DynamicsProcessing`)**:
  - Utiliza la API nativa `android.media.audiofx.DynamicsProcessing` para ecualización paramétrica estéreo directa en la etapa `PreEQ` con asignación atómica por canal.
- **Captura de Sesiones Globales (`AudioEffectSessionReceiver`)**:
  - Intercepta eventos `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` emitidos por reproductores externos (Spotify, YouTube, Poweramp, etc.) y acopla el procesamiento dinámico al `audioSessionId`.
- **Procesamiento de Hardware Secundario**:
  - Efectos independientes de `BassBoost` (Graves) y `Virtualizer` (Agudos).
- **Servicio en Primer Plano (`AudioForegroundService`)**:
  - Garantiza que el motor permanezca activo en segundo plano con una notificación persistente de control.

---

## 🚀 Tecnologías Utilizadas

- **Lenguaje**: Kotlin 100%
- **UI Framework**: Jetpack Compose con componentes neumórficos propios y dibujo optimizado en Canvas (`drawWithCache`).
- **Arquitectura**: Clean Architecture modularizada (`:app`, `:core:ui`, `:core:domain`, `:core:data`, `:core:audio`).
- **Gestión de Estado**: ViewModel, StateFlow y Corrutinas de Kotlin.
- **Persistencia**: SharedPreferences y DataStore Preferences.

---

## 💻 Compilación e Instalación

### Requisitos
- Android Studio Ladybug / 2024.2+
- SDK Mínimo: **API 28 (Android 9.0)**
- SDK Objetivo: **API 35 (Android 15)**

### Pasos
```bash
# Clonar el repositorio
git clone https://github.com/0o0-ct/Aura-Line.git

# Abrir el directorio del proyecto
cd Aura-Line

# Compilar e instalar la APK de depuración
./gradlew app:assembleDebug
