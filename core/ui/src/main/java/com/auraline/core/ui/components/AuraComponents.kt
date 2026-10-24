package com.auraline.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Contenedor base: superficie elevada con borde sutil. */
@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = AuraTheme.colors
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.outline.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    val colors = AuraTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp
        )
        if (trailing != null) {
            Text(text = trailing, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(50)))
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

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
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (selected) accent.copy(alpha = 0.16f) else colors.surfaceElevated, shape)
            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else colors.outline, shape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) accent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Fader vertical con relleno desde el centro (0 dB), arrastre relativo y doble toque para volver a 0.
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
                    // Solo se arrastra desde el pulsador y solo si el gesto es vertical:
                    // así el scroll de la pantalla y el pager horizontal siguen funcionando.
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
                val trackWidth = 4.dp.toPx()
                val padding = 10.dp.toPx()
                val usable = size.height - padding * 2
                val centerX = size.width / 2f
                val zeroY = padding + usable * (1f - zeroFraction)
                val thumbY = padding + usable * (1f - normalized)

                drawLine(
                    color = colors.outline,
                    start = Offset(centerX, padding),
                    end = Offset(centerX, padding + usable),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = colors.textSecondary.copy(alpha = 0.35f),
                    start = Offset(centerX - 8.dp.toPx(), zeroY),
                    end = Offset(centerX + 8.dp.toPx(), zeroY),
                    strokeWidth = 1.dp.toPx()
                )

                val fillColor = if (highlighted) accent else accent.copy(alpha = 0.85f)
                drawLine(
                    color = fillColor,
                    start = Offset(centerX, zeroY),
                    end = Offset(centerX, thumbY),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )

                val thumbWidth = 20.dp.toPx()
                val thumbHeight = 9.dp.toPx()
                drawRoundRect(
                    color = colors.surfaceElevated,
                    topLeft = Offset(centerX - thumbWidth / 2f, thumbY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f)
                )
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(centerX - thumbWidth / 2f, thumbY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = if (highlighted) controlAccent() else colors.textPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = colors.textSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** Mando circular con arco de progreso. Usado para tono, balance, volumen y efectos. */
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

                drawArc(
                    color = colors.outline,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = colors.accent,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                drawCircle(
                    color = colors.surfaceElevated,
                    radius = radius - 10.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = colors.outline,
                    radius = radius - 10.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                val angleRad = Math.toRadians((startAngle + sweepAngle * animated).toDouble())
                val outer = radius - 14.dp.toPx()
                val inner = outer - 10.dp.toPx()
                drawLine(
                    color = colors.accent,
                    start = Offset(
                        (center.x + inner * kotlin.math.cos(angleRad)).toFloat(),
                        (center.y + inner * kotlin.math.sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        (center.x + outer * kotlin.math.cos(angleRad)).toFloat(),
                        (center.y + outer * kotlin.math.sin(angleRad)).toFloat()
                    ),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            Text(
                text = valueText,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1
            )
        }
    }
}

/** Etiqueta + valor + deslizador horizontal: no compite con el scroll vertical. */
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
    val accent = if (enabled) colors.accent else colors.textSecondary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textSecondary,
                fontSize = 13.sp
            )
            Text(text = valueText, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = colors.outline
            )
        )
    }
}

/** Curva de respuesta construida con las bandas reales, eje logarítmico. */
@Composable
fun ResponseCurve(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    gainRangeDb: Float = 12f,
    height: Dp = 96.dp
) {
    val colors = AuraTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.surfaceElevated.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .border(1.dp, colors.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        val accent = controlAccent()
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)) {
            if (bands.isEmpty()) return@Canvas
            val minF = 20f
            val maxF = 20000f
            val spanLog = log10(maxF / minF)
            val centerY = size.height / 2f

            listOf(100f, 1000f, 10000f).forEach { frequency ->
                val x = ((log10(frequency / minF)) / spanLog) * size.width
                drawLine(
                    color = colors.outline.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.3f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            // La respuesta de DynamicsProcessing es constante por banda (bins contiguos),
            // así que la curva honesta es escalonada, no suavizada.
            val edge = { index: Int ->
                val cutoff = bands[index].frequencyHz.coerceIn(minF, maxF)
                ((log10(cutoff / minF)) / spanLog) * size.width
            }
            val level = { index: Int ->
                centerY - (bands[index].gainDb / gainRangeDb).coerceIn(-1f, 1f) * (size.height / 2.1f)
            }

            val path = Path().apply {
                moveTo(0f, level(0))
                for (index in bands.indices) {
                    lineTo(edge(index), level(index))
                    if (index < bands.lastIndex) lineTo(edge(index), level(index + 1))
                }
            }

            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            drawPath(path = path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            bands.indices.forEach { index ->
                drawCircle(
                    color = accent,
                    radius = 1.6.dp.toPx(),
                    center = Offset(edge(index), level(index))
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

@Suppress("unused")
private fun logScale(value: Float, min: Float, max: Float): Float =
    log10(max(value, min) / min) / log10(max / min)

@Suppress("unused")
private fun expScale(fraction: Float, min: Float, max: Float): Float =
    min * (max / min).pow(fraction)
