# REGISTRO DE DECISIONES TÉCNICAS Y LIMITACIONES DEL SO (DECISIONS.md)
**Proyecto Aura-Line — Decisiones de Ingeniería y Arquitectura**

---

## 1. Decisiones Técnicas y Justificación

### D-01: Arquitectura Híbrida Desacoplada (DynamicsProcessing + Motor C++ Oboe)
- **Problema**: Depender únicamente de `android.media.audiofx` falla en apps que no anuncian sesiones o en fabricantes con políticas restrictivas en sesión 0. Depender únicamente de Oboe/AAudio sin root no puede interceptar globalmente el audio del sistema sin el diálogo de `MediaProjection`.
- **Decisión**: Implementar una arquitectura híbrida donde `DynamicsProcessing` atiende las sesiones de reproductores compatibles (Spotify, Poweramp, reproductores locales), mientras que el motor nativo C++/Oboe provee el generador interno de pruebas auditivas (ruido rosa, blanco, barrido) y el receptor de captura PCM `AudioPlaybackCapture`.
- **Impacto**: El usuario puede verificar el sonido y los efectos inmediatamente con el generador de pruebas en cualquier dispositivo y auriculares, al tiempo que disfruta del ecualizador automático cuando reproduce música en apps compatibles.

### D-02: Generador Interno de Audio para Pruebas A/B
- **Problema**: El usuario no tenía forma de aislar si una falta de efecto provenía de la configuración del DSP o de que Spotify/YouTube no estaban enviando audio a la app.
- **Decisión**: Incorporar un generador de señales interno (Ruido Rosa, Ruido Blanco, Barrido Senoidal 20Hz-20kHz) en el motor C++ que reproduce directamente hacia la salida activa con botón conmutador instantáneo **A/B Bypass**.
- **Impacto**: Prueba inmediata e inequívoca de toda la cadena de filtros, compresor, limitador y espacialización sin depender de software externo.

### D-03: Techo del Limitador en -1.5 dBFS para Rutas Bluetooth
- **Problema**: Los códecs Bluetooth (SBC, AAC, LDAC) sufren *inter-sample clipping* cuando los picos alcanzan 0 dBFS, degradando la calidad con aspereza y distorsión.
- **Decisión**: Fijar automáticamente el techo del limitador en **$-1.5$ dBFS** en rutas Bluetooth y **$-1.0$ dBFS** en cable/USB, combinándolo con un tiempo de ataque rápido de 1.0 ms.
- **Impacto**: Eliminación total de la saturación y picos inter-muestra sin sacrificar el volumen percibido.

### D-04: Crossovers Linkwitz-Riley de 4.º Orden (LR4) en Compresor Multibanda
- **Problema**: Crossovers simples de 1.º o 2.º orden producen distorsión de fase o picos de $+3$ dB en los puntos de corte al sumar las bandas.
- **Decisión**: Implementar filtros Linkwitz-Riley de 4.º orden (24 dB/octava) en cascada en C++ y su proyección en las frecuencias de cruce del MBC en `DynamicsProcessing`.
- **Impacto**: Recombinación con respuesta de amplitud perfectamente plana y fase coherente.

### D-05: Mapeo de Presets Profesionales en JSON Versionados
- **Problema**: Los presets hardcodeados en código Kotlin dificultan auditorías acústicas y expansiones.
- **Decisión**: Crear archivos `assets/presets/*.json` con esquemas estrictos de 25 bandas y metadatos técnicos, cargados de forma asíncrona y testeados mediante tests unitarios automatizados.
- **Impacto**: Alta extensibilidad, portabilidad y validación formal de cada curva.

---

## 2. Limitaciones Intrínsecas del Sistema Operativo Android (Sin Root)

1. **Sesión 0 restringida por Fabricantes**: En Android 10+ (API 29+), `DynamicsProcessing` con ID de sesión 0 puede no tener efecto sobre la mezcla del sistema en ciertos dispositivos (Samsung One UI con Dolby Atmos, Xiaomi HyperOS, Google Pixel) a menos que la app reproductora envíe el broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`.
2. **AudioPlaybackCapture y Duplicación de Audio**: `AudioPlaybackCapture` de Android copia el flujo de audio pero no silencia la app origen a nivel de sistema. Se advierte claramente en la interfaz cuando se active esta modalidad experimental.
3. **Audio Offload en Reproductores**: Si un reproductor utiliza decodificación por hardware directo (Offload), el stream no atraviesa el mezclador de Android. Se instruye al usuario a desactivar "Audio Offload" o "Aceleración por Hardware" en la app emisora.
