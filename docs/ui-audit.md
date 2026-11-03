# Auditoría UI de AuraLine

## Alcance

Fase 0 del rediseño premium. Esta auditoría es solo de lectura: no modifica el
motor DSP, los contratos de dominio, la persistencia ni el origen de los datos
en tiempo real.

## Estado verificado

| Área | Estado actual |
|---|---|
| Host UI | Kotlin + Jetpack Compose + Material 3 |
| Min/target SDK | API 28 / 35 |
| Módulos | `:app`, `:core:ui`, `:core:audio`, `:core:data`, `:core:domain` |
| Navegación | Ecualizador, Tono, Efectos y Ajustes |
| Tema | Tema oscuro propio con `AuraPalettes` y `AuraColors` |
| Audio real | `AuraLineEngine` con `DynamicsProcessing` y efectos Android |
| Motor nativo | `NativeEngine` para pruebas/bloques en memoria; no es la ruta global de audio |

La estructura declarada en el README coincide con el código revisado. La
excepción importante también está correctamente declarada: el motor C++ no
procesa la mezcla global; la cadena audible continúa en
`core/audio/AuraLineEngine.kt`.

## Arquitectura de estado y audio

### Estado de UI

`AuraLineViewModel` expone `StateFlow` para:

- `EqualizerState`
- `AppSettings`
- `EngineStatus`
- lista de preajustes
- diagnóstico acústico y su estado de ejecución

Las pantallas recogen estos flujos con `collectAsState()` y escriben mediante
métodos del ViewModel que delegan en `AudioRepository`. La persistencia está en
`AudioRepositoryImpl`, usando `SharedPreferences`; la restauración ocurre al
crear el repositorio y el estado se aplica después a `AuraLineEngine`.

### Cadena de audio

1. `AudioRepositoryImpl` carga el estado persistido.
2. `AuraLineEngine.initialize()` crea la infraestructura de audio.
3. `AuraLineEngine.setState()` aplica el estado completo a la cadena real.
4. `NativeEngine` recibe una copia del estado solo para su ruta experimental
   de procesamiento en memoria.
5. La UI nunca escribe directamente en el motor ni ejecuta trabajo de audio.

No se propone cambiar esta arquitectura.

### Espectro

`AuraLineSpectrum` usa `Visualizer(0)` en `core/audio` para leer la FFT de la
mezcla de salida. Es una ruta de solo lectura y requiere el permiso de
micrófono por la API de Android. El listener calcula barras normalizadas,
aplica caída `DECAY = 0.82` y publica un `StateFlow<FloatArray>` conflado por
la propia asignación del flujo.

- Inicio/parada: `AuraLineEngine.setState()` según `motorEnabled`.
- Liberación: `AuraLineEngine.release()` llama a `AuraLineSpectrum.stop()`.
- Alineación: `AuraLineEngine` actualiza los cortes con
  `AuraLineSpectrum.setBandEdges()`.
- Presentación: `EqualizerScreen` recoge `levels` y los entrega a
  `SpectrumBars`.

El origen y el ciclo de vida son adecuados para el rediseño. La presentación
debe seguir siendo desacoplada y no debe introducir lecturas directas del hilo
de audio.

## Hallazgos de UX y visuales

### Jerarquía y composición

- `AuraLineApp` ya tiene fondo ambiental, top bar, dock flotante en teléfono y
  riel lateral en pantallas de al menos 600 dp.
- Las cuatro pantallas repiten una columna vertical de `AuraCard`, con
  espaciado uniforme y el mismo peso visual para diagnóstico, controles y
  contenido principal.
- En Ecualizador, la curva y el espectro son el contenido principal, pero
  comparten tratamiento de tarjeta con preajustes y guía.
- Tono y Efectos concentran muchos controles dentro de tarjetas largas; falta
  una lectura rápida del estado y una agrupación visual por prioridad.

### Profundidad y glass

- `AuraCard` usa gradientes y borde translúcido, pero no existe una escala
  explícita L0–L4 ni recetas diferenciadas para superficie, control activo,
  dock y overlay.
- Los modificadores neumórficos y algunos componentes todavía usan sombras
  rasterizadas; su uso debe limitarse en superficies repetidas para proteger
  el rendimiento.
- El fondo ambiental actual es estático durante la composición y solo varía
  con el estado del motor.

### Paleta y selección

- `AuraPalettes` conserva las 30 paletas requeridas y cada paleta tiene entre
  3 y 8 tonos normalizados.
- La selección visible ya fue reducida a una curaduría de seis paletas con
  acceso opcional a las 30 y diálogo de preview en `SettingsScreen`.
- El preview actual muestra la tira de tonos, pero no simula suficientemente
  la superficie real de la app ni ofrece agrupación por intención (por
  ejemplo, referencia, energía o calma).
- El cambio de paleta mantiene la ruta existente
  `SettingsScreen → ViewModel.setPalette() → AudioRepository.setThemePalette()`.

### Controles y Material 3

- La app usa Material 3 como dependencia y algunos controles nativos, aunque
  el sistema visual principal ya es propio.
- `BandFader`, `AuraSliderRow`, `ResponseCurve`, `SpectrumBars`,
  `SelectableChip` y `StatusPill` son buenos puntos de extensión para AuraKit.
- Los chips y sliders no tienen todavía una semántica visual común de estados
  reposo, foco, desactivado y motor apagado.
- Los textos de la UI están mayormente hardcodeados en Kotlin; no existe
  `res/values/strings.xml` en el árbol revisado. Esto contradice el requisito
  de hacer los textos nuevos i18n-ready y debe resolverse antes de añadir
  copy nuevo.

### Accesibilidad

- Hay `contentDescription` en algunos iconos de diagnóstico y navegación.
- No se encontró una capa sistemática de `semantics`, descripciones de valores
  para faders, orden de foco o acciones equivalentes para TalkBack.
- Los faders y controles táctiles deben conservar un área mínima cercana a
  48 dp aunque su tratamiento visual sea compacto.
- Los colores derivados de paleta necesitan verificación de contraste para
  texto, estados y bordes, especialmente con paletas claras.

### Rendimiento y recomposición

- `AuraLineSpectrum.levels` actualiza una matriz de barras con frecuencia
  elevada; `EqualizerScreen` recoge el flujo en el nivel de pantalla. El
  rediseño debe aislar el componente del espectro para evitar recomponer
  tarjetas y controles estáticos.
- Hay listas y scrolls horizontales dentro de columnas verticales. Deben
  conservarse, pero el inventario de componentes debe evitar anidamientos
  innecesarios y sombras costosas.
- No se observan dependencias UI nuevas necesarias para el objetivo.

## Discrepancias contra el objetivo

1. Falta un design system explícito de profundidad, tipografía, motion y
   estados de interacción.
2. La jerarquía entre instrumento principal, controles secundarios y diagnóstico
   es débil por la repetición de `AuraCard`.
3. El glass actual es principalmente un gradiente; no hay recetas por nivel ni
   límites de blur/overdraw documentados.
4. La accesibilidad de faders, chips y valores dinámicos no está normalizada.
5. La UI no está preparada para strings nuevos en recursos.
6. La selección de paleta necesita una guía más orientada a intención sin
   eliminar las 30 opciones existentes.
7. El contenido en tiempo real debe quedar aislado en componentes con
   recomposición acotada.

## Decisión de tecnología

### Elegida: `COMPOSE_CUSTOM`

Evidencia:

- El host y todas las pantallas ya están implementados en Compose.
- El proyecto ya cuenta con Canvas, DrawScope, gradientes y componentes
  dibujados a medida.
- El motor de audio y los servicios no necesitan cambiar para una mejora
  visual.
- JUCE introduciría una segunda jerarquía de UI, JNI adicional, riesgo de
  accesibilidad y una dependencia con implicaciones de licencia sin beneficio
  demostrado para el objetivo Android actual.
- No se requiere Flutter, Web, Qt ni otra capa multiplataforma.

La implementación deberá ampliar `:core:ui` con un AuraKit stateless,
mantener Material 3 como infraestructura disponible pero no como identidad
visual dominante, y evitar dependencias nuevas salvo evidencia de necesidad.
AGSL/`RenderEffect` solo se incorporarán si una medición en dispositivo
demuestra un beneficio claro y se mantiene el mínimo SDK mediante guards.

## Límites para las siguientes fases

- No modificar `app/src/main/cpp`, `NativeEngine`, `core/audio`,
  `core/domain` ni `core/data`.
- No añadir permisos, analítica, red ni estado persistente nuevo sin aprobación.
- No cambiar firmas públicas del ViewModel o pantallas.
- No tocar el origen, frecuencia ni ciclo de vida del espectro.
- Verificar cada fase con `assembleDebug`, lint y tests disponibles.
- Crear recursos de strings para todo texto nuevo antes de incorporarlo a UI.

## Propuesta de fases posteriores

1. Design system: tokens, profundidad, glass, motion, haptics, estados y mapa
   de componentes actuales a AuraKit.
2. Fundación en `:core:ui`, incluyendo previews de componentes.
3. Shell adaptativo: fondo, top bar, dock/rail y transiciones.
4. Pantallas por separado: Ecualizador, Tono, Efectos y Ajustes.
5. Selector de paletas con preview guiado.
6. Pulido de accesibilidad, rendimiento, dispositivos y documentación.

Esta auditoría no autoriza todavía la implementación de la Fase 1.
