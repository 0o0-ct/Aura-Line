package com.auraline.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.domain.EqBand
import com.auraline.core.ui.theme.AuraTheme
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Cuando el motor está apagado los controles pierden el color de acento. */
val LocalPowered = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun controlAccent(): Color =
    if (LocalPowered.current) AuraTheme.colors.accent else AuraTheme.colors.textSecondary


/**
 * Modificadores para sombras Neumórficas personalizadas (2.5D)
 */
fun Modifier.neumorphicConvex(
    radius: Dp,
    highlightColor: Color,
    shadowColor: Color,
    elevation: Dp = 8.dp
) = this.drawBehind {
    val cornerRadius = radius.toPx()
    val blurRadius = elevation.toPx() * 1.5f // Blur más difuso como en el diseño de referencia
    val dx = (elevation).toPx()
    val dy = (elevation).toPx()

    val paintHighlight = Paint().apply {
        color = highlightColor
        asFrameworkPaint().apply {
            maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    val paintShadow = Paint().apply {
        color = shadowColor
        asFrameworkPaint().apply {
            maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    drawIntoCanvas { canvas ->
        // Sombra inferior derecha (Oscura)
        canvas.drawRoundRect(
            left = dx,
            top = dy,
            right = size.width + dx,
            bottom = size.height + dy,
            radiusX = cornerRadius,
            radiusY = cornerRadius,
            paint = paintShadow
        )
        // Brillo superior izquierdo (Claro)
        canvas.drawRoundRect(
            left = -dx,
            top = -dy,
            right = size.width - dx,
            bottom = size.height - dy,
            radiusX = cornerRadius,
            radiusY = cornerRadius,
            paint = paintHighlight
        )
    }
}

fun Modifier.neumorphicConcave(
    radius: Dp,
    shadowColor: Color,
    highlightColor: Color,
    elevation: Dp = 4.dp
) = this.drawBehind {
    val cornerRadius = radius.toPx()
    val blurRadius = elevation.toPx()
    val dx = (elevation / 1.5f).toPx()
    val dy = (elevation / 1.5f).toPx()

    val paintShadow = Paint().apply {
        color = shadowColor
        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
        strokeWidth = elevation.toPx() * 2 // Stroke más grueso para que se vea más natural al difuminar
        asFrameworkPaint().apply {
            maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    val paintHighlight = Paint().apply {
        color = highlightColor
        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
        strokeWidth = elevation.toPx() * 2
        asFrameworkPaint().apply {
            maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    drawIntoCanvas { canvas ->
        // Sombra interior superior izquierda (simulando que la luz viene de arriba a la izquierda y pega dentro)
        canvas.drawRoundRect(
            left = -dx,
            top = -dy,
            right = size.width + dx,
            bottom = size.height + dy,
            radiusX = cornerRadius,
            radiusY = cornerRadius,
            paint = paintShadow
        )
        // Brillo interior inferior derecho
        canvas.drawRoundRect(
            left = -dx,
            top = -dy,
            right = size.width + dx,
            bottom = size.height + dy,
            radiusX = cornerRadius,
            radiusY = cornerRadius,
            paint = paintHighlight
        )

        // Se requiere un clip o un clear en el borde interior si no estuviese recortado,
        // pero Compose aplica modifier background/clip despues de drawBehind
        // (por lo tanto drawBehind afecta al fondo)
    }
}

/** Permite que Ajustes desactive la vibración de toda la app. */
val LocalHapticsEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun hapticFeedback(): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val feedback = LocalHapticFeedback.current
    return {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Contenedor Glassmorphism inspirado en Apple HIG y OpenDesign:
 * Superficie translúcida con degradado sutil de cristal esmerilado y
 * borde especular con luz superior (specular rim light).
 */
@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 22.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = AuraTheme.colors
    val shape = RoundedCornerShape(shapeRadius)

    // Glassmorphism adaptado: más oscuro y con relieve convexo
    val glassGradient = Brush.verticalGradient(
        listOf(
            colors.glassSurfaceStart,
            colors.glassSurfaceEnd
        )
    )
    // Simular el corte del cristal (10% opacity white on top)
    val borderGradient = Brush.verticalGradient(
        listOf(
            Color(0x1AFFFFFF),
            Color.Transparent,
            Color(0x05FFFFFF)
        )
    )

    Column(
        modifier = modifier
            .neumorphicConvex(
                radius = shapeRadius,
                highlightColor = colors.neumorphicHighlight,
                shadowColor = colors.neumorphicShadow,
                elevation = 8.dp
            )
            .background(glassGradient, shape)
            .border(1.dp, borderGradient, shape)
            .padding(16.dp),
        content = content
    )
}

/**
 * Título de sección con tipografía Apple SF: mayúsculas compactas,
 * tracking expandido y soporte para badge / texto secundario con brillo.
 */
@Composable
fun SectionTitle(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = colors.textSecondary.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp
            )
        }
    }
}

/**
 * Píldora de estado estilo Dynamic Island / Apple Capsule:
 * Translucidez de vidrio, borde reflectante y punto de pulso con aura de neón.
 */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val pillShape = RoundedCornerShape(999.dp)
    val colors = AuraTheme.colors
    Row(
        modifier = modifier
            // Relieve hundido tipo Dynamic Island
            .neumorphicConcave(
                radius = 999.dp,
                shadowColor = colors.neumorphicCavity,
                highlightColor = Color(0x0AFFFFFF),
                elevation = 2.dp
            )
            .background(colors.neumorphicCavity, pillShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                // Efecto brillo/neón para el LED
                .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = color)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp
        )
    }
}

/**
 * Chip selector híbrido Neumorfismo + Glassmorphism:
 * En reposo: botón táctil extruido suave con doble sombra.
 * Seleccionado: cápsula de cristal translúcido con brillo de neón y borde especular Apple.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val haptic = hapticFeedback()
    val shape = RoundedCornerShape(999.dp)

    val backgroundBrush = if (selected) {
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.20f), Color.Transparent)
        )
    } else {
        Brush.verticalGradient(
            listOf(colors.glassSurfaceStart, colors.glassSurfaceEnd)
        )
    }

    val borderBrush = if (selected) {
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.1f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0x1AFFFFFF), Color.Transparent)
        )
    }

    // Inactivo = Convexo. Activo = Cóncavo + Brillo
    val interactionModifier = if (selected) {
        Modifier.neumorphicConcave(
            radius = 999.dp,
            shadowColor = colors.neumorphicCavity,
            highlightColor = accent.copy(alpha = 0.3f),
            elevation = 3.dp
        )
    } else {
        Modifier.neumorphicConvex(
            radius = 999.dp,
            highlightColor = colors.neumorphicHighlight,
            shadowColor = colors.neumorphicShadow,
            elevation = 3.dp
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(interactionModifier)
            .background(backgroundBrush, shape)
            .border(1.dp, borderBrush, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic()
                        onClick()
                    },
                    onLongPress = {
                        haptic()
                        onLongClick?.invoke()
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) accent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Fader de precisión para consolas de audio profesionales:
 * - Ranura cóncava neumórfica (ranura hundida con sombra interior).
 * - Columna líquida de neón con resplandor (bloom) desde 0 dB hasta el valor actual.
 * - Marca fresada a 0 dB.
 * - Botón/deslizador extruido táctil con degradado metálico, borde superior especular
 *   y estrías de agarre antideslizante con iluminación de neón.
 */
@Composable
fun BandFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    label: String = "",
    valueText: String = "",
    highlighted: Boolean = false,
    faderWidth: Dp = 34.dp,
    /** Nombre corto del instrumento o función que posee la banda. */
    subtitle: String = ""
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val haptic = hapticFeedback()
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)

    val span = (currentRange.endInclusive - currentRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - currentRange.start) / span).coerceIn(0f, 1f)
    val zeroFraction = ((0f - currentRange.start) / span).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(faderWidth)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(faderWidth)
                .height(height)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            haptic()
                            val paddingPx = 10.dp.toPx()
                            val usablePx = size.height - paddingPx * 2f
                            if (usablePx > 0f) {
                                val yPx = (offset.y - paddingPx).coerceIn(0f, usablePx)
                                val pct = 1f - (yPx / usablePx)
                                val span = currentRange.endInclusive - currentRange.start
                                val newValue = currentRange.start + (pct * span)
                                currentOnChange(newValue.coerceIn(currentRange.start, currentRange.endInclusive))
                            }
                        },
                        onDoubleTap = {
                            haptic()
                            currentOnChange(0f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { _ -> haptic() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val paddingPx = 10.dp.toPx()
                            val usablePx = size.height - paddingPx * 2f
                            if (usablePx > 0f) {
                                val deltaPct = -dragAmount.y / usablePx
                                val span = currentRange.endInclusive - currentRange.start
                                val newValue = (currentValue + deltaPct * span).coerceIn(currentRange.start, currentRange.endInclusive)
                                currentOnChange(newValue)
                            }
                        }
                    )
                }
        ) {

            // 1. Canal profundamente hundido (Inset shadow simulada por Modifier.neumorphicConcave)
            val cavityWidth = 12.dp
            Box(
                modifier = Modifier
                    .width(cavityWidth)
                    .fillMaxHeight()
                    .padding(vertical = 10.dp)
                    .neumorphicConcave(
                        radius = cavityWidth / 2,
                        shadowColor = colors.neumorphicCavity,
                        highlightColor = colors.neumorphicHighlight,
                        elevation = 3.dp
                    )
                    .background(colors.background, RoundedCornerShape(cavityWidth / 2))
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 10.dp.toPx()
                val usable = size.height - padding * 2
                val centerX = size.width / 2f
                val zeroY = padding + usable * (1f - zeroFraction)
                val thumbY = padding + usable * (1f - normalized)

                // Progreso Activo (Pintura Rosa/Accent) desde 0 dB hasta el thumb
                val fillColor = if (highlighted) accent else accent.copy(alpha = 0.9f)
                val topY = min(zeroY, thumbY)
                val bottomY = max(zeroY, thumbY)
                val barWidth = 6.dp.toPx()

                if (kotlin.math.abs(thumbY - zeroY) > 1.5f) {
                     drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(centerX - barWidth / 2f, topY),
                        size = Size(barWidth, bottomY - topY),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                // 2. Marca de 0 dB fresada sutil
                drawLine(
                    color = colors.textSecondary.copy(alpha = 0.3f),
                    start = Offset(centerX - 16.dp.toPx(), zeroY),
                    end = Offset(centerX + 16.dp.toPx(), zeroY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Thumb 3D rectangular (como en el mockup)
            val thumbYPadding = 10.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = thumbYPadding)
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (faderWidth - 28.dp) / 2,
                            y = (height - thumbYPadding * 2 - 16.dp) * (1f - normalized)
                        )
                        .size(width = 28.dp, height = 16.dp)
                        // Convexo
                        .neumorphicConvex(
                            radius = 6.dp,
                            highlightColor = colors.neumorphicHighlight,
                            shadowColor = colors.neumorphicShadow,
                            elevation = 4.dp
                        )
                        .background(colors.surfaceElevated, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val fillColor = if (highlighted) accent else colors.textSecondary.copy(alpha=0.5f)
                    // Pequeña franja / LED en el centro
                    Box(
                        modifier = Modifier
                            .size(width = 8.dp, height = 2.dp)
                            .background(fillColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = if (highlighted) controlAccent() else colors.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = if (highlighted) controlAccent() else colors.textSecondary.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = colors.textSecondary.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/**
 * Control rotativo neumórfico tipo consola de estudio (AuraKnob):
 * Bisel cóncavo exterior, arco de progreso iluminado y perilla central extruida.
 */
@Composable
fun AuraKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    diameter: Dp = 84.dp,
    label: String = "",
    valueText: String = ""
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val haptic = hapticFeedback()
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)

    val startAngle = 140f
    val sweepAngle = 260f
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val animated = animateFloatAsState(targetValue = normalized, label = "knob").value

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .neumorphicConvex(
                    radius = diameter / 2,
                    highlightColor = colors.neumorphicHighlight,
                    shadowColor = colors.neumorphicShadow,
                    elevation = 8.dp
                )
                .background(colors.surfaceElevated, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        haptic()
                        currentOnChange(((currentRange.start + currentRange.endInclusive) / 2f))
                    })
                }
                .pointerInput(Unit) {
                    var accumulated = 0f
                    detectDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            accumulated -= drag.y
                            val delta = (accumulated / 220f) * span
                            currentOnChange((currentValue + delta).coerceIn(currentRange.start, currentRange.endInclusive))
                            accumulated = 0f
                        }
                    )
                }
        ) {

            // Relieve intermedio (como en el mockup: dial de perilla extruida suave)
            Box(
                modifier = Modifier
                    .size(diameter - 28.dp)
                    .neumorphicConvex(
                        radius = (diameter - 28.dp) / 2,
                        highlightColor = colors.neumorphicHighlight,
                        shadowColor = colors.neumorphicShadow,
                        elevation = 4.dp
                    )
                    .background(colors.background, CircleShape)
            )

            // Centro cóncavo (hendidura central)
            Box(
                modifier = Modifier
                    .size(diameter - 56.dp)
                    .neumorphicConcave(
                        radius = (diameter - 56.dp) / 2,
                        shadowColor = colors.neumorphicCavity,
                        highlightColor = colors.neumorphicHighlight,
                        elevation = 3.dp
                    )
                    .background(colors.background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.width / 2f
                val markRadiusOuter = radius - 8.dp.toPx() // Alejar las lineas un poco del borde
                val markRadiusInner = radius - 14.dp.toPx()
                val numMarks = 36 // Más densidad de lineas pequeñas
                val angleStep = sweepAngle / (numMarks - 1)

                for (i in 0 until numMarks) {
                    val angleDeg = startAngle + i * angleStep
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val markFraction = i.toFloat() / (numMarks - 1)
                    val isActive = markFraction <= animated

                    val start = Offset(
                        (center.x + markRadiusInner * cos(angleRad)).toFloat(),
                        (center.y + markRadiusInner * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        (center.x + markRadiusOuter * cos(angleRad)).toFloat(),
                        (center.y + markRadiusOuter * sin(angleRad)).toFloat()
                    )

                    val markColor = if (isActive) {
                        accent.copy(alpha = 0.6f + (0.4f * markFraction))
                    } else {
                        // Inactivo: tono secundario de la paleta
                        colors.textSecondary.copy(alpha = 0.55f)
                    }

                    if (isActive) {
                         drawLine(
                            color = markColor.copy(alpha = 0.3f),
                            start = start,
                            end = end,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        color = markColor,
                        start = start,
                        end = end,
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1
            )
        }
    }
}

/**
 * Fila deslizadora táctil con estética Apple / Neumorphic:
 * Canal empotrado, cursor de cápsula con sombra física y brillo de luz superior.
 */
/**
 * Analizador de espectro en tiempo real con estética Apple / Glassmorphism:
 * 25 barras de ancho uniforme y esquinas redondeadas tipo cápsula, ranuras empotradas
 * de precisión (hardware meter wells), degradado luminoso cian-azul y resaltado de pico.
 */
@Composable
fun SpectrumBars(
    levels: FloatArray,
    edges: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
    height: Dp = 62.dp
) {
    val accent = controlAccent()
    val shape = RoundedCornerShape(16.dp)

    val colors = AuraTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Panel cóncavo (hundido)
            .neumorphicConcave(
                radius = 16.dp,
                shadowColor = colors.neumorphicCavity,
                highlightColor = Color(0x0AFFFFFF),
                elevation = 6.dp
            )
            .background(
                Brush.verticalGradient(
                    listOf(colors.neumorphicCavity, colors.background)
                ),
                shape
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = levels.size
            if (count == 0) return@Canvas

            val gap = 2.2.dp.toPx()
            val totalGaps = gap * (count - 1)
            val barWidth = ((size.width - totalGaps) / count).coerceAtLeast(1.5f)
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
            val minBarHeight = (barWidth * 0.85f).coerceAtMost(size.height * 0.2f)

            for (index in 0 until count) {
                val left = index * (barWidth + gap)
                val level = levels[index].coerceIn(0f, 1f)

                // 1. Ranura física empotrada de fondo (canal inactivo visible)
                drawRoundRect(
                    color = colors.outline.copy(alpha = 0.16f),
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )

                // 2. Barra activa con altura proporcional y degradado luminoso
                val barHeight = minBarHeight + level * (size.height - minBarHeight)
                val top = size.height - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.textPrimary, // Tono luminoso de la paleta
                            accent              // Acento de la paleta
                        ),
                        startY = top,
                        endY = size.height
                    ),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                    alpha = 0.35f + 0.65f * level
                )

                // 3. Resaltado de cúspide (LED glow en la cresta de la barra)
                if (level > 0.04f) {
                    val capHeight = (barWidth * 0.55f).coerceAtLeast(1.5.dp.toPx())
                    drawRoundRect(
                        color = colors.textPrimary.copy(alpha = (0.50f + 0.50f * level).coerceIn(0f, 1f)),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, capHeight),
                        cornerRadius = radius
                    )
                }
            }

            // Línea sutil de referencia base
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.12f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun AuraSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AuraTheme.colors
    val accent = if (enabled) controlAccent() else colors.textSecondary
    val haptic = hapticFeedback()
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)

    val span = (currentRange.endInclusive - currentRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - currentRange.start) / span).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val thumbRadiusPx = 11.dp.toPx()
                        val usable = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        val pos = (offset.x - thumbRadiusPx).coerceIn(0f, usable)
                        val newValue = currentRange.start + (pos / usable) * span
                        haptic()
                        currentOnChange(newValue.coerceIn(currentRange.start, currentRange.endInclusive))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val thumbRadiusPx = 11.dp.toPx()
                        val usable = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        val pos = (change.position.x - thumbRadiusPx).coerceIn(0f, usable)
                        val newValue = currentRange.start + (pos / usable) * span
                        currentOnChange(newValue.coerceIn(currentRange.start, currentRange.endInclusive))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // 1. Canal neumórfico cóncavo (ranura profunda)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .neumorphicConcave(
                        radius = 4.dp,
                        shadowColor = colors.neumorphicCavity,
                        highlightColor = Color(0x0AFFFFFF),
                        elevation = 2.dp
                    )
                    .background(colors.neumorphicCavity, RoundedCornerShape(4.dp))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.5f), accent)
                        )
                    )
            )

            // Layout custom para el thumb
            var widthPx by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            val currentDensity = androidx.compose.ui.platform.LocalDensity.current
            val density = currentDensity.density
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { widthPx = it.width.toFloat() }
            ) {
                val thumbWidth = 24.dp
                val thumbHeight = 20.dp
                val thumbRadiusPx = 11.dp
                // Aproximación del offset para Box
                val usableWidth = max(0f, widthPx - (thumbRadiusPx.value * 2 * density))

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(androidx.compose.ui.platform.LocalDensity.current) { (thumbRadiusPx.toPx() + (usableWidth * fraction) - (thumbWidth.toPx() / 2)).toDp() },
                            y = 8.dp
                        )
                        .size(width = thumbWidth, height = thumbHeight)
                        .neumorphicConvex(
                            radius = 10.dp,
                            highlightColor = colors.neumorphicHighlight,
                            shadowColor = colors.neumorphicShadow,
                            elevation = 3.dp
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.surfaceElevated, colors.background)
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(Color(0x26FFFFFF), Color(0x05FFFFFF))
                            ),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                     Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accent, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Pantalla de osciloscopio de estudio para la curva de respuesta:
 * Bisel cóncavo de cristal ahumado, retícula de rejilla de frecuencias,
 * resplandor de neón en la curva de ecualización y nodos iluminados.
 */
@Composable
fun ResponseCurve(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    gainRangeDb: Float = 12f,
    height: Dp = 105.dp
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .neumorphicConcave(
                radius = 18.dp,
                shadowColor = colors.neumorphicCavity,
                highlightColor = Color(0x0AFFFFFF),
                elevation = 6.dp
            )
            .background(
                Brush.verticalGradient(
                    listOf(colors.neumorphicCavity, colors.background)
                ),
                shape
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)) {
            if (bands.isEmpty()) return@Canvas
            val minF = 20f
            val maxF = 20000f
            val spanLog = log10(maxF / minF)
            val centerY = size.height / 2f

            // Retícula de frecuencias (100 Hz, 1 kHz, 10 kHz)
            listOf(100f, 1000f, 10000f).forEach { frequency ->
                val x = ((log10(frequency / minF)) / spanLog) * size.width
                drawLine(
                    color = colors.textSecondary.copy(alpha = 0.08f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Línea de referencia 0 dB
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.20f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            val steps = 90
            val path = Path()
            val points = ArrayList<Offset>(steps)

            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val freq = minF * Math.pow((maxF / minF).toDouble(), fraction.toDouble()).toFloat()
                var totalGainDb = 0f

                for (band in bands) {
                    if (kotlin.math.abs(band.gainDb) >= 0.05f) {
                        totalGainDb += calculateBiquadMagnitudeDb(freq, band.frequencyHz, band.gainDb, band.q)
                    }
                }

                val x = fraction * size.width
                val y = centerY - (totalGainDb / gainRangeDb).coerceIn(-1.2f, 1.2f) * (size.height / 2.2f)
                val pt = Offset(x, y)
                points.add(pt)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prev = points[i - 1]
                    val cx = (prev.x + x) / 2f
                    path.cubicTo(cx, prev.y, cx, y, x, y)
                }
            }

            // Relleno de área degradada bajo la curva
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.28f), Color.Transparent)
                )
            )

            // Resplandor de la curva (bloom pass)
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.35f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Línea nítida de la curva
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Nodos en cada banda
            bands.forEach { band ->
                val cutoff = band.frequencyHz.coerceIn(minF, maxF)
                val x = ((log10(cutoff / minF)) / spanLog) * size.width
                val y = centerY - (band.gainDb / gainRangeDb).coerceIn(-1f, 1f) * (size.height / 2.2f)
                drawCircle(
                    color = accent,
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * Respuesta en frecuencia de un Biquad Peaking Filter (fórmula RBJ cookbook en dB).
 */
fun calculateBiquadMagnitudeDb(f: Float, f0: Float, gainDb: Float, q: Float = 1.41f, fs: Float = 48000f): Float {
    if (kotlin.math.abs(gainDb) < 0.05f) return 0f
    val A = Math.pow(10.0, gainDb / 40.0)
    val w0 = 2.0 * Math.PI * f0 / fs
    val w = 2.0 * Math.PI * f / fs
    val alpha = Math.sin(w0) / (2.0 * q)
    val cosW0 = Math.cos(w0)

    val b0 = 1.0 + alpha * A
    val b1 = -2.0 * cosW0
    val b2 = 1.0 - alpha * A
    val a0 = 1.0 + alpha / A
    val a1 = -2.0 * cosW0
    val a2 = 1.0 - alpha / A

    val cosW = Math.cos(w)
    val cos2W = Math.cos(2.0 * w)
    val sinW = Math.sin(w)
    val sin2W = Math.sin(2.0 * w)

    val numReal = (b0 + b1 * cosW + b2 * cos2W) / a0
    val numImag = (-b1 * sinW - b2 * sin2W) / a0
    val denReal = (a0 + a1 * cosW + a2 * cos2W) / a0
    val denImag = (-a1 * sinW - a2 * sin2W) / a0

    val numMagSq = numReal * numReal + numImag * numImag
    val denMagSq = denReal * denReal + denImag * denImag

    if (denMagSq < 1e-12) return 0f
    val magSq = numMagSq / denMagSq
    return (10.0 * Math.log10(Math.max(magSq, 1e-12))).toFloat()
}

/**
 * Interruptor táctil estilo Apple iOS / Control Center con Neumorfismo y Cristal:
 * Cápsula translúcida con brillo reactivo y cursor esférico táctil extruido.
 */
@Composable
fun AuraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AuraTheme.colors
    val accent = if (enabled) colors.accent else colors.textSecondary
    val haptic = hapticFeedback()

    val trackWidth = 50.dp
    val trackHeight = 28.dp
    val thumbSize = 22.dp

    val targetOffset = if (checked) trackWidth - thumbSize - 3.dp else 3.dp
    val thumbOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = tween(durationMillis = 180),
        label = "switchThumb"
    )

    val trackBgColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "switchBg"
    )

    // En el diseño de referencia, los toggles son cóncavos/hundidos (como pistas).
    val interactionModifier = Modifier.neumorphicConcave(
        radius = trackHeight / 2,
        shadowColor = colors.neumorphicCavity,
        highlightColor = colors.neumorphicHighlight,
        elevation = 3.dp
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .then(interactionModifier)
            .background(colors.background, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) {
                haptic()
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // En el diseño, cuando esta ON, se ve un pequeño punto interior en el thumb.
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .neumorphicConvex(
                    radius = thumbSize / 2,
                    highlightColor = colors.neumorphicHighlight,
                    shadowColor = colors.neumorphicShadow,
                    elevation = 2.dp
                )
                .background(colors.surfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape)
                )
            }
        }
    }
}

fun formatFrequency(hz: Float): String = when {
    hz >= 1000f -> {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else String.format(java.util.Locale.US, "%.1fk", k)
    }
    else -> hz.toInt().toString()
}

fun formatGain(db: Float): String {
    val rounded = (db * 10).toInt() / 10f
    return if (rounded > 0f) "+$rounded" else "$rounded"
}
