# AURALINE DESIGN SYSTEM v2.5+ — ESPECIFICACIÓN DE INGENIERÍA UI/UX

## 1. Visión y Filosofía de Diseño
AuraLine combina la elegancia y sutileza del diseño **Apple (iOS/macOS Human Interface Guidelines + Logic Pro)** con la expresividad y consistencia de **Material Design 3 Expressive**, integrando una estética híbrida de **Neumorfismo Táctil 2.5D/3D + Liquid/Frosted Glass**.

### Principios Fundamentales:
1. **Claridad, Respeto y Profundidad (Clarity, Deference & Depth):** La interfaz nunca compite con las curvas acústicas ni el analizador de espectro (RTA). El cristal esmerilado y el relieve neumórfico se reservan para los contenedores de control, navegación y diales físicos.
2. **Jerarquía Espacial Estricta:** Rejilla base de `8.dp`, padding de contenedores de `16.dp` a `20.dp`, y separación entre tarjetas de `12.dp` a `16.dp`.
3. **Agencia y Feedback Háptico:** Todo control interactivo garantiza un área de toque mínima de `48.dp x 48.dp` y respuesta visual inmediata en el mismo frame (*optimistic UI*), con retroalimentación háptica coordinada por `LocalHapticsEnabled`.
4. **Eficiencia Extrema de Batería y GPU:** Cero asignaciones de memoria en scopes de dibujo (`drawWithCache`), limitación del RTA a 30–45 FPS con apagado automático en reposo.

---

## 2. Tokens del Sistema (`AuraTheme.kt`)

### 2.1. Color y Monocromatismo
- **Fondo Base OLED:** `#07090E` (confort visual, contraste infinito y mínimo consumo en pantallas AMOLED).
- **30 Paletas Monocromáticas:** Disponibles a través de `AuraPalettes` (de `p1` a `p30`), cada una con gradación tonal calibrada por luminancia (`calcLuminance`).
- **Tokens Semánticos Invariables:**
  - `positive`: `#34D399` (verde esmeralda, estado del motor y bypass).
  - `warning`: `#FBBF24` (ámbar cálido, advertencias y saturación).
  - `danger`: `#F87171` (coral/rojo, recortes o errores de audio).
  - `textPrimary`: Tono de máxima luminancia con tinte suave de la paleta.
  - `textSecondary`: Tono medio con opacidad del 80% para subtítulos y unidades.

### 2.2. Tokens de Cristal Líquido (Liquid Glass)
- `glassSurfaceStart`: Superficie superior translúcida (`alpha = 0.90f`).
- `glassSurfaceEnd`: Superficie inferior que se funde con el fondo (`alpha = 0.85f`).
- `glassBorderTop`: Borde especular superior de `1.dp` (`alpha = 0.32f`) que simula la refracción de luz incidente cenital.
- `glassBorderBottom`: Borde inferior sutil (`alpha = 0.06f`).

### 2.3. Tokens Neumórficos 2.5D / 3D
- `neumorphicHighlight`: Resplandor superior izquierdo derivado del acento con luz blanca (`alpha = 0.24f`).
- `neumorphicShadow`: Sombra física inferior derecha entintada con el tono más oscuro de la paleta (`alpha = 0.88f`), evitando negros planos desconectados.
- `neumorphicCavity`: Hendidura cóncava para canales de faders, bahías de RTA y fondos de osciloscopio (`alpha = 0.70f`).

### 2.4. Tipografía y Microtipografía
- **Titulares:** `headlineMedium` (24 sp, `letterSpacing = -0.5 sp`), `titleLarge` (19 sp, `letterSpacing = -0.3 sp`).
- **Valores y Unidades:** `titleMedium` (15 sp, SemiBold), `bodySmall` (12 sp) y `labelSmall` (11 sp, SemiBold, `letterSpacing = 1.0 sp`) para unidades de estudio (`dB`, `Hz`, `ms`, `kHz`).

---

## 3. Catálogo de Componentes de Estudio (`AuraComponents.kt` y `AuraKit.kt`)

### 3.1. `BandFader` (Fader Táctil de 25 Bandas)
- **Canal Cóncavo:** Ranura profunda de `12.dp` dibujada con `neumorphicConcave`.
- **Relleno Dinámico:** Franja de acento que parte desde la marca de 0 dB hasta la posición actual del cursor.
- **Cursor Físico 3D:** Cápsula extruida de `28.dp x 16.dp` con hendidura LED central y relieve `neumorphicConvex`.
- **Área Táctil:** Ancho total de `48.dp` a `64.dp`, permitiendo arrastre vertical continuo o salto directo por toque en la pista. Doble toque restablece la banda a 0 dB con confirmación háptica.
- **Subtítulo de Instrumento:** En frecuencias graves (< 1 kHz), indica el instrumento dueño de la banda (`SUB`, `BOMBO`, `BAJO`, `CONTRA`, etc.).

### 3.2. `ResponseCurve` (Curva de Respuesta en Frecuencia)
- **Caja de Cristal Ahumado:** Hendidura cóncava estilo pantalla de osciloscopio de laboratorio.
- **Retícula Logarítmica:** Escala logarítmica de 20 Hz a 20 kHz con marcas de referencia en octavas y línea central de 0 dB punteada.
- **Cálculo Biquad de Precisión:** Evaluación continua de la magnitud de filtros RBJ peaking con renderizado en curva Bézier cúbica y sombreado degradado vertical (*bloom glow* + línea nítida).
- **Rendimiento:** Implementado con `Modifier.drawWithCache`, garantizando 0 asignaciones y 0 cálculos trigonométricos por frame durante el scroll o la reproducción.

### 3.3. `SpectrumBars` (Analizador RTA de 25 Canales)
- **Bahías Físicas (Meter Wells):** 25 ranuras redondeadas empotradas en chasis oscuro.
- **Gradiente Lumínico:** Barras con degradado vertical desde el acento hasta el blanco del texto, con LED de cúspide (*peak hold LED*) en niveles superiores a 0.04.
- **Cero Asignaciones en Bucle:** Brocha `Brush.verticalGradient` y radio `CornerRadius` precalculados una sola vez fuera del bucle de 25 barras.
- **Control de Ciclo de Vida:** Sincronizado con `AuraLineSpectrum` mediante `DisposableEffect(state.motorEnabled)` para pausar la captura de FFT en reposo o al salir de la pantalla.

### 3.4. `AuraKnob` (Dial Giratorio de Precisión)
- Dial de control concéntrico con marcadores de escala angular (36 pasos) y puntero LED de alta precisión con sombra proyectada.

### 3.5. `FloatingDock` y `SideRail` (Navegación Adaptativa)
- **Móviles (< 600 dp):** Dock flotante en forma de cápsula semitranslúcida de `68.dp` de altura con navegación inferior desacoplada del fondo, respetando los insets del sistema.
- **Tablets y Plegables (≥ 600 dp):** Riel lateral vertical de `112.dp` de ancho a la izquierda de la pantalla, con un ancho máximo de contenido de `960.dp` para evitar estiramientos antiestéticos en pantallas grandes.

---

## 4. Reglas y Garantías de Rendimiento Gráfico (Verificables en Código)

| Aspecto | Implementación Anterior (Riesgosa) | Implementación Optimizada v2.5+ |
|---|---|---|
| **Sombras Neumórficas** | `Paint()` y `BlurMaskFilter` recreados en cada invocación de `drawBehind`. | `Modifier.drawWithCache`: objetos de pintura y filtros de desenfoque cacheados. |
| **Curva de Respuesta** | `Path()`, `points ArrayList`, `Brush` y biquads calculados en cada frame. | `Modifier.drawWithCache`: la curva solo se recalcula si cambian las ganancias o el tamaño. |
| **RTA (Spectrum)** | 25 `Brush.verticalGradient` y 25 `CornerRadius` asignados en cada frame (1500 obj/seg). | 1 sola brocha y radio precalculados por frame; 0 asignaciones en el bucle interior. |
| **Fondo Ambiental** | `Canvas` con `Brush.radialGradient` asignado en cada frame de dibujo. | `Modifier.drawWithCache` almacena el gradiente radial ambiental hasta que cambie el estado. |
| **Ciclo de Vida RTA** | `Visualizer` activo continuamente consumiendo batería en background. | `DisposableEffect(motorEnabled)` apaga el capturador de FFT si el motor se apaga o la pantalla se abandona. |

---

## 5. Checklist de Accesibilidad (WCAG 2.1 AA)

- [x] **Áreas de Toque (Touch Targets):** Todos los botones, chips, interruptores y faders superan el mínimo de `48.dp x 48.dp`.
- [x] **Relación de Contraste:** 
  - Textos principales sobre fondo OLED: $\ge 12:1$ (supera el estándar AA de $4.5:1$).
  - Textos secundarios e indicadores: $\ge 4.8:1$.
  - Controles activos destacados con acento lumínico: $\ge 3:1$ contra superficies adyacentes.
- [x] **Semántica y Lectores de Pantalla:**
  - `AuraBandFader`: expone `contentDescription = label` y `stateDescription = "X.X dB"`.
  - `AuraSwitch`: expone rol `Switch` y estado `checked`.
  - `AuraChip`: expone rol `Button` y descripción de selección.
- [x] **Independencia del Color:** Ningún estado crítico depende únicamente del color (los interruptores usan posición física + píldora textual; los estados usan etiquetas "Activo", "Bypass", etc.).

---

## 6. Guía de Verificación Visual y Auditoría en Hardware Físico

Al instalar el APK (`./gradlew assembleDebug`) en un teléfono o tablet Android real:

1. **Prueba de Fluidez de Faders:**
   - Deslizar rápidamente múltiples bandas en la pantalla de Ecualizador.
   - Verificar que no existan tirones ni micro-pausas (comprobar en Ajustes de Desarrollador de Android: *GPU Rendering Profile / Perfil de representación de GPU* — las barras deben mantenerse debajo de la línea verde de 16 ms / 60 FPS).
2. **Prueba del Dock Flotante:**
   - Hacer scroll vertical hasta el final en Ecualizador, Tono y Efectos.
   - Verificar que el último elemento sea visible y no quede oculto detrás del Dock gracias al padding inferior de `120.dp`.
3. **Prueba de Modo Tableta / Plegable:**
   - Rotar el dispositivo a orientación horizontal o abrir un dispositivo plegable.
   - Confirmar que la navegación cambia automáticamente al riel lateral (`SideRail`) y que el contenido principal se centra elegantemente con un ancho máximo de `960.dp`.
4. **Prueba del Selector de Paletas en Ajustes:**
   - Recorrer el carrusel de las 30 paletas monocromáticas.
   - Verificar que los discos reflejen el relieve tridimensional neumórfico y que toda la aplicación cambie de tonalidad de inmediato.
5. **Prueba del Modo de Ahorro de Batería:**
   - Apagar el interruptor maestro del motor en la cabecera.
   - Confirmar que el fondo ambiental reduce su resplandor, el RTA se apaga y el procesador no consume ciclos en reposo.
