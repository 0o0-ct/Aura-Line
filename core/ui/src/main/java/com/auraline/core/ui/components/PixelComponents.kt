package com.auraline.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.LocalMonoPalette
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PixelVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    width: Dp = 32.dp,
    label: String = "",
    valueText: String = "",
    isPreamp: Boolean = false
) {
    val palette = LocalMonoPalette.current
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)

    val primaryColor = if (isPreamp) palette.primary else MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(width)
                .height(height)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val yPx = change.position.y.coerceIn(0f, size.height.toFloat())
                        val pct = 1f - (yPx / size.height.toFloat())
                        val newValue = currentRange.start + (pct * (currentRange.endInclusive - currentRange.start))
                        currentOnChange(newValue)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidthPx = 6.dp.toPx()
                val topPadding = 12.dp.toPx()
                val bottomPadding = 12.dp.toPx()
                val usableHeight = size.height - topPadding - bottomPadding

                // Track Line Background
                drawLine(
                    color = palette.surface.copy(alpha = 0.8f),
                    start = Offset(size.width / 2f, topPadding),
                    end = Offset(size.width / 2f, size.height - bottomPadding),
                    strokeWidth = trackWidthPx,
                    cap = StrokeCap.Round
                )

                // Active Primary Fill Line
                val thumbCenterY = size.height - bottomPadding - (normalizedValue * usableHeight)
                drawLine(
                    color = primaryColor,
                    start = Offset(size.width / 2f, size.height - bottomPadding),
                    end = Offset(size.width / 2f, thumbCenterY),
                    strokeWidth = trackWidthPx,
                    cap = StrokeCap.Round
                )

                // Fader Capsule Thumb (Pixel Pill Shape)
                val thumbWidthPx = (size.width - 6.dp.toPx()).coerceIn(16.dp.toPx(), 22.dp.toPx())
                val thumbHeightPx = 32.dp.toPx()
                val thumbTop = (thumbCenterY - thumbHeightPx / 2f).coerceIn(0f, size.height - thumbHeightPx)
                val thumbLeft = (size.width - thumbWidthPx) / 2f

                // Thumb Body
                drawRoundRect(
                    color = palette.surface,
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Thumb border
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.8f),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // White rotation tick marker
                val tickLineY = thumbTop + (thumbHeightPx / 2f)
                drawLine(
                    color = Color.White,
                    start = Offset(thumbLeft + 5.dp.toPx(), tickLineY),
                    end = Offset(thumbLeft + thumbWidthPx - 5.dp.toPx(), tickLineY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun PixelKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    modifier: Modifier = Modifier,
    size: Dp = 90.dp,
    label: String = "",
    valueText: String = ""
) {
    val palette = LocalMonoPalette.current
    val startAngle = 135f
    val sweepAngle = 270f

    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    val currentAngle = startAngle + (normalizedValue * sweepAngle)

    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragStartValue = currentValue
                            dragAccumulator = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator -= dragAmount.y
                            val changeRange = currentRange.endInclusive - currentRange.start
                            val delta = (dragAccumulator / 220f) * changeRange
                            val newValue = (dragStartValue + delta).coerceIn(currentRange.start, currentRange.endInclusive)
                            currentOnChange(newValue)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPx = Offset(this.size.width / 2f, this.size.height / 2f)
                val outerRadius = (this.size.width / 2f) - 8.dp.toPx()
                val knobRadius = outerRadius - 8.dp.toPx()

                // Background track arc
                drawArc(
                    color = palette.surface.copy(alpha = 0.8f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - outerRadius, centerPx.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Primary progress arc
                drawArc(
                    color = palette.primary,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * normalizedValue,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - outerRadius, centerPx.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Knob Body Circle
                drawCircle(
                    color = palette.surface,
                    radius = knobRadius,
                    center = centerPx
                )

                // Border Outline
                drawCircle(
                    color = palette.primary.copy(alpha = 0.4f),
                    radius = knobRadius,
                    center = centerPx,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // White rotation tick marker
                val tickRad = Math.toRadians(currentAngle.toDouble())
                val tickStart = Offset(
                    (centerPx.x + (knobRadius - 14.dp.toPx()) * cos(tickRad)).toFloat(),
                    (centerPx.y + (knobRadius - 14.dp.toPx()) * sin(tickRad)).toFloat()
                )
                val tickEnd = Offset(
                    (centerPx.x + (knobRadius - 4.dp.toPx()) * cos(tickRad)).toFloat(),
                    (centerPx.y + (knobRadius - 4.dp.toPx()) * sin(tickRad)).toFloat()
                )

                drawLine(
                    color = Color.White,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PixelChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonoPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "chipScale")

    val bg = if (selected) palette.primary else palette.surface
    val textColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, palette.primary.copy(alpha = if (selected) 1f else 0.3f), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
