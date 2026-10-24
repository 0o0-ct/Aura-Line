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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.domain.EqBand
import com.auraline.core.ui.theme.AuraTheme
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Cuando el motor está apagado los controles pierden el color de acento. */
val LocalPowered = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun controlAccent(): Color =
    if (LocalPowered.current) AuraTheme.colors.accent else AuraTheme.colors.textSecondary

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
    val glassGradient = Brush.verticalGradient(
        listOf(
            colors.glassSurfaceStart,
            colors.glassSurfaceEnd
        )
    )
    val borderGradient = Brush.verticalGradient(
        listOf(
            colors.glassBorderTop,
            Color(0x18FFFFFF),
            colors.glassBorderBottom
        )
    )

    Column(
        modifier = modifier
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
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), pillShape)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.12f))
                ),
                pillShape
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
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
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val haptic = hapticFeedback()
    val shape = RoundedCornerShape(999.dp)

    val backgroundBrush = if (selected) {
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.12f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF161E2C), Color(0xFF101520))
        )
    }

    val borderBrush = if (selected) {
        Brush.verticalGradient(
            listOf(Color(0x99FFFFFF), accent.copy(alpha = 0.8f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0x28FFFFFF), Color(0x08FFFFFF))
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(backgroundBrush, shape)
            .border(1.dp, borderBrush, shape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) colors.textPrimary else colors.textSecondary,
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
    faderWidth: Dp = 34.dp
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
                    detectTapGestures(onDoubleTap = {
                        haptic()
                        currentOnChange(0f)
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val paddingPx = 10.dp.toPx()
                        val usablePx = size.height - paddingPx * 2f
                        if (usablePx <= 0f) return@awaitEachGesture

                        val current = currentValue.coerceIn(currentRange.start, currentRange.endInclusive)
                        val fraction = ((current - currentRange.start) / span).coerceIn(0f, 1f)
                        val thumbY = paddingPx + usablePx * (1f - fraction)
                        if (kotlin.math.abs(down.position.y - thumbY) > 44.dp.toPx()) {
                            return@awaitEachGesture
                        }

                        val startX = down.position.x
                        val startY = down.position.y
                        var vertical = false
                        while (!vertical) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@awaitEachGesture
                            if (!change.pressed) return@awaitEachGesture
                            val dx = kotlin.math.abs(change.position.x - startX)
                            val dy = kotlin.math.abs(change.position.y - startY)
                            val slop = viewConfiguration.touchSlop
                            if (dy > slop || dx > slop) {
                                if (dy <= dx) return@awaitEachGesture
                                change.consume()
                                vertical = true
                            }
                        }

                        var previousY = startY
                        drag(down.id) { change ->
                            change.consume()
                            val deltaPx = previousY - change.position.y
                            previousY = change.position.y
                            val delta = (deltaPx / usablePx) * span
                            currentOnChange(
                                (currentValue + delta).coerceIn(currentRange.start, currentRange.endInclusive)
                            )
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 10.dp.toPx()
                val usable = size.height - padding * 2
                val centerX = size.width / 2f
                val zeroY = padding + usable * (1f - zeroFraction)
                val thumbY = padding + usable * (1f - normalized)

                // 1. Ranura neumórfica hundida (cavidad oscura + borde reflectante sutil)
                val cavityWidth = 6.dp.toPx()
                drawRoundRect(
                    color = colors.neumorphicCavity,
                    topLeft = Offset(centerX - cavityWidth / 2f, padding),
                    size = Size(cavityWidth, usable),
                    cornerRadius = CornerRadius(cavityWidth / 2f, cavityWidth / 2f)
                )
                drawRoundRect(
                    color = Color(0x2BFFFFFF),
                    topLeft = Offset(centerX - cavityWidth / 2f, padding),
                    size = Size(cavityWidth, usable),
                    cornerRadius = CornerRadius(cavityWidth / 2f, cavityWidth / 2f),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 2. Marca de 0 dB fresada
                drawLine(
                    color = Color(0x66FFFFFF),
                    start = Offset(centerX - 8.dp.toPx(), zeroY),
                    end = Offset(centerX + 8.dp.toPx(), zeroY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 3. Columna de neón activa desde 0 dB hasta el cursor
                val fillColor = if (highlighted) accent else accent.copy(alpha = 0.9f)
                if (kotlin.math.abs(thumbY - zeroY) > 1.5f) {
                    // Resplandor difuso exterior (bloom)
                    drawLine(
                        color = fillColor.copy(alpha = 0.3f),
                        start = Offset(centerX, zeroY),
                        end = Offset(centerX, thumbY),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Haz central nítido
                    drawLine(
                        color = fillColor,
                        start = Offset(centerX, zeroY),
                        end = Offset(centerX, thumbY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // 4. Cursor táctil extruido (Neumorphic Knob)
                val thumbW = 24.dp.toPx()
                val thumbH = 12.dp.toPx()
                val thumbLeft = centerX - thumbW / 2f
                val thumbTop = thumbY - thumbH / 2f
                val thumbRadius = 6.dp.toPx()

                // Sombra proyectada del cursor
                drawRoundRect(
                    color = Color(0x99000000),
                    topLeft = Offset(thumbLeft, thumbTop + 2.dp.toPx()),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbRadius, thumbRadius)
                )

                // Superficie táctil extruida (degradado metálico / grafito)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2E394A), Color(0xFF141924)),
                        startY = thumbTop,
                        endY = thumbTop + thumbH
                    ),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbRadius, thumbRadius)
                )

                // Resalte especular en el borde superior (Apple glass highlight)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF)),
                        startY = thumbTop,
                        endY = thumbTop + thumbH
                    ),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbRadius, thumbRadius),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Ranura central iluminada con el acento del motor
                val gripW = 10.dp.toPx()
                drawLine(
                    color = fillColor,
                    start = Offset(centerX - gripW / 2f, thumbY),
                    end = Offset(centerX + gripW / 2f, thumbY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val radius = (min(this.size.width, this.size.height) / 2f) - stroke
                val topLeft = Offset((this.size.width - radius * 2f) / 2f, (this.size.height - radius * 2f) / 2f)
                val arcSize = Size(radius * 2f, radius * 2f)

                // Canal cóncavo del arco
                drawArc(
                    color = colors.neumorphicCavity,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Arco de fondo con borde sutil
                drawArc(
                    color = Color(0x22FFFFFF),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Arco activo con resplandor neón
                drawArc(
                    color = accent.copy(alpha = 0.35f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + 4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Perilla central extruida (Neumorphic dial)
                val knobRadius = radius - 10.dp.toPx()
                // Sombra exterior
                drawCircle(
                    color = Color(0x99000000),
                    radius = knobRadius,
                    center = center + Offset(0f, 2.dp.toPx())
                )
                // Cuerpo metálico
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2E3848), Color(0xFF141924)),
                        center = center - Offset(knobRadius * 0.3f, knobRadius * 0.3f),
                        radius = knobRadius * 1.4f
                    ),
                    radius = knobRadius,
                    center = center
                )
                // Borde especular de luz
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x80FFFFFF), Color(0x1AFFFFFF)),
                        startY = center.y - knobRadius,
                        endY = center.y + knobRadius
                    ),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Indicador de aguja en el dial
                val angleRad = Math.toRadians((startAngle + sweepAngle * animated).toDouble())
                val outer = knobRadius - 4.dp.toPx()
                val inner = outer - 10.dp.toPx()
                drawLine(
                    color = accent,
                    start = Offset(
                        (center.x + inner * kotlin.math.cos(angleRad)).toFloat(),
                        (center.y + inner * kotlin.math.sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        (center.x + outer * kotlin.math.cos(angleRad)).toFloat(),
                        (center.y + outer * kotlin.math.sin(angleRad)).toFloat()
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            Text(
                text = valueText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF090D15), Color(0xFF101725))
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x35FFFFFF), Color(0x0CFFFFFF))
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
                    color = Color(0x0EFFFFFF),
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
                            Color(0xFF68D8FF), // Cian eléctrico Apple
                            accent             // Azul real de AuraLine
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
                        color = Color(0xFFF2F9FF).copy(alpha = (0.50f + 0.50f * level).coerceIn(0f, 1f)),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, capHeight),
                        cornerRadius = radius
                    )
                }
            }

            // Línea sutil de referencia base
            drawLine(
                color = Color(0x1AFFFFFF),
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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackHeight = 7.dp.toPx()
                val centerY = size.height / 2f
                val thumbRadiusPx = 11.dp.toPx()
                val usableWidth = size.width - thumbRadiusPx * 2f
                val thumbX = thumbRadiusPx + usableWidth * fraction

                // 1. Canal neumórfico cóncavo (ranura profunda)
                drawRoundRect(
                    color = colors.neumorphicCavity,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
                drawRoundRect(
                    color = Color(0x24FFFFFF),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // 2. Progreso activo con degradado neón
                if (thumbX > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.7f), accent)
                        ),
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(thumbX, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                }

                // 3. Botón deslizador de cápsula física táctil
                val knobWidth = 24.dp.toPx()
                val knobHeight = 20.dp.toPx()
                val knobLeft = thumbX - knobWidth / 2f
                val knobTop = centerY - knobHeight / 2f
                val knobRadius = 10.dp.toPx()

                // Sombra proyectada
                drawRoundRect(
                    color = Color(0x80000000),
                    topLeft = Offset(knobLeft, knobTop + 2.dp.toPx()),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(knobRadius, knobRadius)
                )

                // Cuerpo extruido
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2E3848), Color(0xFF141924)),
                        startY = knobTop,
                        endY = knobTop + knobHeight
                    ),
                    topLeft = Offset(knobLeft, knobTop),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(knobRadius, knobRadius)
                )

                // Borde especular Apple Glass
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF)),
                        startY = knobTop,
                        endY = knobTop + knobHeight
                    ),
                    topLeft = Offset(knobLeft, knobTop),
                    size = Size(knobWidth, knobHeight),
                    cornerRadius = CornerRadius(knobRadius, knobRadius),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Punto central iluminado
                drawCircle(
                    color = accent,
                    radius = 2.5.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
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
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF090D14), Color(0xFF0F1420))
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x38FFFFFF), Color(0x0AFFFFFF))
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
                    color = Color(0x14FFFFFF),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Línea de referencia 0 dB
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            val edge = { index: Int ->
                val cutoff = bands[index].frequencyHz.coerceIn(minF, maxF)
                ((log10(cutoff / minF)) / spanLog) * size.width
            }
            val level = { index: Int ->
                centerY - (bands[index].gainDb / gainRangeDb).coerceIn(-1f, 1f) * (size.height / 2.2f)
            }

            val path = Path().apply {
                moveTo(0f, level(0))
                for (index in bands.indices) {
                    lineTo(edge(index), level(index))
                    if (index < bands.lastIndex) lineTo(edge(index), level(index + 1))
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
            bands.indices.forEach { index ->
                drawCircle(
                    color = accent,
                    radius = 2.dp.toPx(),
                    center = Offset(edge(index), level(index))
                )
            }
        }
    }
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
        targetValue = if (checked) accent else Color(0xFF141A25),
        animationSpec = tween(durationMillis = 180),
        label = "switchBg"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(trackBgColor)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        if (checked) Color(0x66FFFFFF) else Color(0x28FFFFFF),
                        Color(0x0AFFFFFF)
                    )
                ),
                RoundedCornerShape(999.dp)
            )
            .clickable(enabled = enabled) {
                haptic()
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFD6DFEC))
                    ),
                    CircleShape
                )
                .border(
                    0.5.dp,
                    Color(0x40FFFFFF),
                    CircleShape
                )
        )
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
