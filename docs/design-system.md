# AuraKit: especificación visual de AuraLine

## Estado

Propuesta de Fase 1. Esta especificación precede a la implementación de
componentes y requiere confirmación visual antes de iniciar la Fase 2.

## Dirección de producto

AuraLine debe sentirse como un instrumento: oscuro, silencioso y preciso. La
interfaz reserva el contraste y el color para aquello que cambia el sonido:
motor, selección, curva y valor que se está editando. El resto crea contexto,
no compite con él.

No se busca reproducir Apple Music ni copiar un sistema externo. Se toman como
referencias conceptuales la jerarquía, el cuidado espacial y el cristal
contenido; los componentes, iconos y tokens serán propios y compatibles con
Compose.

## AuraKit

AuraKit vivirá en `:core:ui` y será stateless. Los componentes recibirán valores
y callbacks; no conocerán ViewModel, repositorio, `AuraLineEngine` ni
persistencia.

### Capas de profundidad

| Nivel | Uso | Tratamiento |
|---|---|---|
| L0 Canvas | Fondo | 2–3 glows suaves derivados del acento, sin imágenes |
| L1 Surface | Cards y grupos | Gradiente corto, borde fino, sin blur real |
| L2 Raised | Fader, chip seleccionado, controles activos | Superficie tonal, borde de foco, sombra mínima |
| L3 Floating | Top bar, dock, rail, popovers | Cristal reservado; blur solo cuando haya contenido detrás |
| L4 Overlay | Dialog, sheet, scrim | Superficie opaca/translúcida controlada, foco modal |

Regla: no aplicar glass a todo. Una card sobre el fondo procedural usa glass
barato; el blur de fondo real queda reservado para superficies flotantes.

### Capacidades por API

- API 33+: backdrop blur/efecto especular únicamente tras medir coste y
  compatibilidad.
- API 31–32: `RenderEffect` solo en superficies flotantes.
- API 28–30, ahorro de batería o reducción de transparencia: gradiente + borde
  de luz, sin blur.
- Nunca aplicar blur al espectro animado ni al contenido que se está
  arrastrando.

## Tokens

Los tokens se expondrán desde un objeto estable de `:core:ui`; los valores se
derivarán de `AuraColors` sin cambiar la semántica de `AuraPalettes`.

### Color

| Token | Uso |
|---|---|
| `background` | Canvas L0 |
| `surface1`, `surface2`, `surface3` | L1, L2 y estados elevados |
| `borderSubtle`, `borderStrong` | Separación y foco |
| `textPrimary`, `textSecondary`, `textTertiary` | Jerarquía |
| `accent`, `accentContainer`, `onAccent` | Estado activo, selección y curva |
| `positive`, `warning`, `danger` | Estado semántico independiente de la paleta |
| `grid`, `curveStroke`, `curveFill` | Visualización de audio |
| `spectrumGradient` | Barras, con contraste controlado |

Disciplina 60/30/10: superficies neutras, estructura secundaria contenida y
un solo acento por pantalla. Los estados semánticos nunca se derivan del
acento para evitar que una paleta roja parezca un error.

`onAccent` se calculará para contraste contra el acento. Si el accent original
no ofrece contraste suficiente, solo la UI usará una variante legible; el valor
persistido de `AppTheme` no se altera.

### Tipografía y números

| Rol | Tamaño base | Uso |
|---|---:|---|
| `display` | 28 sp | Lectura principal del instrumento |
| `title` | 18 sp | Títulos de grupo |
| `body` | 14 sp | Descripciones y filas |
| `label` | 12 sp | Etiquetas y estados |
| `monoNum` | 14 sp | Hz, dB, sample rate y valores de ajuste |

El secundario no baja de 12 sp y el cuerpo no baja de 14 sp. Los valores
numéricos usarán cifras tabulares cuando la fuente disponible lo permita.
Ningún dato crítico dependerá de truncado; la especificación se probará con
fontScale 1.0, 1.5 y 2.0.

### Geometría

- Espaciado: escala de 4 dp, con 8 dp como unidad frecuente.
- Radios: 8, 12, 16, 24 y pill.
- Área táctil mínima: 48 dp.
- Fader: pista de 4 dp y thumb visual de 28 dp dentro de un target de 48 dp.
- Whitespace deliberado: máximo dos niveles de énfasis por grupo.

### Motion y hápticos

- `snappy`: cambios de selección y dock, 120–240 ms.
- `gentle`: aparición de contenido y cambios de estado, 240–400 ms.
- `bouncy`: thumb de toggle, solo si no distrae del valor.
- Con reducción de animaciones: fades cortos, sin desplazamiento espacial.
- Tick al cruzar 0 dB o un detent.
- Confirmación al aplicar una paleta o cambiar un toggle.
- Se respeta `LocalHapticsEnabled` y el comportamiento del sistema.

## Inventario de componentes

Todos serán previewables y tendrán estados default, pressed, focused,
disabled y error cuando aplique.

| Actual | AuraKit propuesto | Criterio |
|---|---|---|
| `AuraCard` | `AuraSurface` | Separa L1/L2/L3 y limita sombras |
| `AuraSliderRow` | `AuraParameterRow` | Etiqueta, valor tabular, semántica y foco |
| `BandFader` | `AuraBandFader` | Bipolar, detent, reset, modo fino y TalkBack |
| `ResponseCurve` | `AuraResponseCurve` | Grid logarítmico, curva y zonas de guía |
| `SpectrumBars` | `AuraSpectrum` | Dibujo desacoplado, peak-hold y decay |
| `SelectableChip` | `AuraChip` | Default, selected, preview y disabled |
| `StatusPill` | `AuraStatusPill` | Punto vivo sin depender solo del color |
| `AuraSwitch` | `AuraToggle` | Estado y transición propios |
| Dock/rail actual | `AuraNavigation` | Indicador deslizante y tamaños adaptativos |
| Top bar actual | `AuraTopBar` | Tab, motor y spec strip |

### Especificación de instrumentos

**AuraBandFader**

- Relleno bipolar desde 0 dB.
- Doble toque restablece la banda.
- Pulsación larga activa modo fino.
- Bubble de valor solo durante interacción.
- Semántica: progreso, estado descriptivo y acciones de ±0,5 dB.
- El callback entrega el valor; no ejecuta audio en el hilo de composición.

**AuraResponseCurve**

- X logarítmico de 20 Hz a 20 kHz.
- Y usa el rango real del dominio.
- Grid sutil con octavas y relleno degradado bajo la curva.
- Guía de fuentes graves como zonas anotadas, no como ruido decorativo.

**AuraSpectrum**

- Consume el mismo `StateFlow<FloatArray>` existente.
- Lectura en fase de dibujo o estado derivado estable.
- Sin asignaciones por frame en `DrawScope`.
- Se pausa cuando la pantalla no es visible o el motor está inactivo.
- No se añade blur ni glow por barra.

## Preview conceptual en Compose

La Fase 2 convertirá estos bocetos en previews reales y una galería de estados.

```kotlin
@Preview
@Composable
private fun AuraShellPreview() {
    AuraKitPreviewTheme {
        AuraSurface(level = AuraDepth.Floating) {
            AuraTopBar(
                title = "Ecualizador",
                status = AuraStatus.Active("Motor activo"),
                specStrip = "Bluetooth · 48 kHz · 2 canales"
            )
            AuraNavigation(selected = AuraDestination.Equalizer)
        }
    }
}
```

```kotlin
@Preview
@Composable
private fun AuraInstrumentPreview() {
    AuraKitPreviewTheme {
        AuraSurface(level = AuraDepth.Surface) {
            AuraResponseCurve(/* curva estática de preview */)
            AuraBandFader(
                value = 3.5f,
                valueRange = -12f..12f,
                onValueChange = {},
                onReset = {}
            )
        }
    }
}
```

```kotlin
@Preview
@Composable
private fun AuraChipPreview() {
    AuraKitPreviewTheme {
        Row {
            AuraChip(label = "Voz", state = AuraChipState.Default, onClick = {})
            AuraChip(label = "Plano", state = AuraChipState.Selected, onClick = {})
            AuraStatusPill(text = "Motor activo", state = AuraStatusState.Active)
        }
    }
}
```

Estos snippets son dirección visual, no APIs aprobadas. La implementación debe
integrarse con los nombres y patrones ya existentes, evitando casts o estados
duplicados.

## Arquitectura de pantallas

### Shell

- Top bar compacta: título de tab, estado del motor y acciones mínimas.
- Dock en Compact; rail/sidebar flotante en Medium y Expanded.
- Indicador de navegación deslizante, safe areas y edge-to-edge.
- Consola en dos niveles: estado/preset como primario; routing, frecuencia,
  canales y perfil como spec strip.

### Ecualizador

1. Consola del motor.
2. Hero de curva + espectro.
3. Preajustes.
4. Edición de bandas: inspector seleccionado en teléfono; banco completo en
   tablet/landscape.
5. Preamp.
6. Guía de graves colapsable.
7. Salud del motor.

### Tono

Channel strip: sub-bajo, graves, medios y agudos por grupos, mini-curva viva y
lecturas numéricas.

### Efectos

Rack vertical: módulos, LED de estado/bypass, orden visible y parámetros
expandibles.

### Ajustes

Inset grouped por dominio: Apariencia, audio, perfiles y acerca de. El selector
de paletas es una sección de Apariencia; no cambia ninguna ruta de persistencia.

Todas las pantallas deberán diseñar estados de motor inactivo, permiso
denegado/error, vacío, carga y sesión perdida.

## Selector de paletas

El backend permanece intacto. La UI usa siempre
`viewModel.setPalette(palette.key, palette.accent.value.toInt())`.

### Curaduría inicial basada en paletas existentes

| Key real | Nombre mostrado | Justificación |
|---|---|---|
| `p11` | Grafito Titanio | Neutral premium, buen fondo oscuro |
| `p10` | Azul Hielo | Frío, legible y técnico |
| `p20` | Azul Eléctrico | Energía de instrumento y curva |
| `p9` | Púrpura Real | Contraste dramático sin ser estridente |
| `p13` | Verde Neón | Señal, energía y lectura de estado |
| `p15` | Ámbar Dorado | Calidez de hardware y control |
| `p25` | Ciber Turquesa | Identidad AuraLine y alta claridad |
| `p4` | Esmeralda Menta | Alternativa calmada y diferenciada |

Estas keys fueron leídas de `AuraPalettes`; no se modifica su semántica. La
implementación debe añadir una prueba que valide que toda key curada existe.

### Flujo

1. Tocar una tarjeta solo selecciona preview local.
2. Miniatura viva con shell, curva, chip y pill bajo un
   `CompositionLocalProvider` acotado.
3. `Aplicar` queda habilitado solo si la preview difiere de la actual.
4. `Cancelar` descarta la preview.
5. Si la actual no está entre las destacadas, aparece como “Actual”.
6. “Explorar todas” abre sheet/pantalla con 30 opciones agrupadas por matiz
   calculado desde los colores existentes.

El backend no recibe colores nuevos ni se inventa una función de colores propios.

## Calidad y verificación

### Rendimiento

- Objetivo p95 de frame ≤ 12 ms en gama media.
- Cero jank visible durante arrastre de fader.
- Medir con herramientas existentes antes de introducir infraestructura nueva.
- Keys estables y `@Immutable` donde ya sea válido.

### Accesibilidad

- Contraste AA: 4.5:1 para texto y 3:1 para componentes grandes/bordes.
- Semántica real en faders, chips y toggles.
- Foco lógico y targets ≥48 dp.
- Color nunca es el único indicador.
- Validar fontScale 2.0 y reducción de movimiento.

### Pruebas planificadas

- Unit tests de keys curadas y derivación de tokens.
- Contraste de las 30 paletas.
- Screenshot previews de componentes y tres paletas en fontScale 1.0/1.5.
- UI tests de tabs y flujo preview → aplicar/cancelar.

## Decisiones y límites

- Compose Custom, no JUCE.
- Sin nuevas dependencias por ahora.
- Sin cambios en audio, DSP, repositorios, dominio o persistencia.
- Sin nuevos permisos ni estado persistente.
- Los textos nuevos deben migrar a recursos antes de implementarse.
- AGSL y blur real son opt-in por medición, no requisito decorativo.
