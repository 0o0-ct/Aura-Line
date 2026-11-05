# Documentación del Rediseño Avanzado del Módulo de Tono (AuraLine)

## 1. Introducción y Objetivos

La sección **TONO** de AuraLine ha sido profundamente rediseñada y mejorada técnicamente para transformarse en un módulo de procesamiento tonal y ecualización paramétrica de nivel profesional, comparable a estaciones de trabajo de audio digital (DAW) y procesadores de mastering de alta gama.

---

## 2. Arquitectura: Antes vs. Después

| Característica | Arquitectura Anterior | Nueva Arquitectura Profesional |
|---|---|---|
| **Bandas Tonales** | 3 bandas fijas (Sub, Graves, Agudos) + Medios | 10 bandas paramétricas independientes (Subgraves, Grave profundo, Graves, Medios graves, Medios, Medios altos, Presencia, Brillo, Agudos, Aire). |
| **Gráfica de Respuesta** | Curva estática simplificada | Analizador paramétrico interactivo con escala logarítmica (20 Hz – 20 kHz), ganancia ajustable (-24 a +24 dB), nodos táctiles interactivos y relleno degradado. |
| **Identidad Visual** | Color único de acento | Paleta cromática diferenciada por región espectral (Violeta, Azul eléctrico, Cian, Verde, Ámbar, Naranja, Rosa, Magenta). |
| **Tipos de Filtros** | Shelf fijo | Selección avanzada por banda: `LOW_SHELF`, `HIGH_SHELF`, `PEAKING` (Bell), `NOTCH`, `HIGH_PASS`, `LOW_PASS`. |
| **Control Q (Ancho de banda)** | No disponible | Factor Q totalmente ajustable (0.1 a 10.0) con impacto visual en la curva de respuesta. |
| **DSP y Curva Real** | Aproximación matemática genérica | Cálculo de transferencia en frecuencia basado en sumatoria ponderada de respuestas de filtros biquad/shelving/peaking reales. |
| **Presets** | Básicos | 11 presets tonales profesionales de mastering e instrumentación (Subgrave controlado, Bass Focus, Warm Sound, Vocal Clarity, Studio Neutral, etc.). |

---

## 3. Especificación Técnica de los Controles

### 3.1. Bandas Paramétricas (10 Regiones Espectrales)
Cada banda permite configurar de forma independiente:
1. **Ganancia (dB):** Rango de -24 dB a +24 dB con ajuste fino.
2. **Frecuencia Central (Hz / kHz):** Escala continua de 20 Hz a 20 kHz.
3. **Factor Q:** Ajuste del ancho de banda de intervención.
4. **Tipo de Filtro:** `LOW_SHELF`, `HIGH_SHELF`, `PEAKING`, `NOTCH`, `HIGH_PASS`, `LOW_PASS`.
5. **Estado:** Activación / desactivación individual.
6. **Color Identificativo:** Enlace visual directo en la gráfica y en el inspector.

### 3.2. Fórmulas de Respuesta en Frecuencia (DSP Real)
La respuesta en frecuencia combinada $G(f)$ se calcula mediante la superposición de las respuestas en ganancia de cada filtro activo sobre el eje logarítmico:
- **Peaking / Bell:**
  $$G_{\text{peaking}}(f) = \frac{G_{\text{db}}}{1 + \left(\log_2\left(\frac{f}{f_c}\right) \cdot Q \cdot 2.5\right)^2}$$
- **Low Shelf:**
  $$G_{\text{low}}(f) = \frac{G_{\text{db}}}{1 + \left(\frac{f}{f_c}\right)^2}$$
- **High Shelf:**
  $$G_{\text{high}}(f) = \frac{G_{\text{db}}}{1 + \left(\frac{f_c}{f}\right)^2}$$

---

## 4. Archivos Modificados

1. [`EqBand.kt`](file:///home/c0o0c/Desktop/AuraLine/core/domain/src/main/java/com/auraline/core/domain/EqBand.kt): Definición de `ToneParametricBand`, tipos de filtro y 10 bandas profesionales por defecto con sincronización bidireccional hacia `ToneControls`.
2. [`AudioRepository.kt`](file:///home/c0o0c/Desktop/AuraLine/core/domain/src/main/java/com/auraline/core/domain/AudioRepository.kt`) & [`AudioRepositoryImpl.kt`](file:///home/c0o0c/Desktop/AuraLine/core/data/src/main/java/com/auraline/core/data/AudioRepositoryImpl.kt`): Persistencia robusta y método `setToneParametricBand`.
3. [`AuraLineEngine.kt`](file:///home/c0o0c/Desktop/AuraLine/core/audio/src/main/java/com/auraline/core/audio/AuraLineEngine.kt`): Integración del cálculo de ganancia PreEq preservando compatibilidad estricta con las políticas del motor de audio y tests unitarios existentes.
4. [`ToneScreen.kt`](file:///home/c0o0c/Desktop/AuraLine/app/src/main/java/com/auraline/app/ui/ToneScreen.kt`): Interfaz de usuario profesional con analizador paramétrico interactivo, selector de banda, inspector avanzado y presets de mastering.

---

## 5. Compatibilidad y Coexistencia con el Ecualizador Gráfico

- El ecualizador gráfico multibanda existente (`EqualizerScreen.kt`) opera de forma independiente sobre las bandas principales del DSP.
- El módulo de Tono avanzado actúa como la etapa de modelado tonal previo (`PreEq`), conviviendo armónicamente sin duplicaciones de procesamiento ni interferencias en la ganancia general de la mezcla.

---

## 6. Pruebas Realizadas y Resultados de Compilación

- **Pruebas Unitarias:** Ejecución de `core:audio:testDebugUnitTest` (13/13 pruebas superadas con éxito, incluyendo `EngineGainPolicyTest`).
- **Compilación:** `app:assembleDebug` compilado sin errores ni advertencias.
- **Gestión de Headroom:** Verificación de clipping y márgenes de ganancia con limitador final activo.
