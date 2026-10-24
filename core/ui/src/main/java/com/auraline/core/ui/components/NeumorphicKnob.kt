package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.LightShadowDark
import com.auraline.core.ui.theme.SurfaceDark
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NeumorphicKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    label: String = "",
    valueText: String = "",
    accentColor: Color = CyanNeon
) {
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
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
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
                            val delta = (dragAccumulator / 250f) * changeRange
                            val newValue = (dragStartValue + delta).coerceIn(currentRange.start, currentRange.endInclusive)
                            currentOnChange(newValue)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerPx = Offset(this.size.width / 2f, this.size.height / 2f)
                val outerRadius = (this.size.width / 2f) - 12.dp.toPx()
                val knobRadius = outerRadius - 10.dp.toPx()

                // Background track arc
                drawArc(
                    color = DarkShadowDark,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - outerRadius, centerPx.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Cyan progress arc
                drawArc(
                    color = accentColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * normalizedValue,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - outerRadius, centerPx.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Knob Body (Surface Circle)
                drawCircle(
                    color = SurfaceDark,
                    radius = knobRadius,
                    center = centerPx
                )

                // Outer border outline for 3D feel
                drawCircle(
                    color = LightShadowDark.copy(alpha = 0.5f),
                    radius = knobRadius,
                    center = centerPx,
                    style = Stroke(width = 2.dp.toPx())
                )

                // White rotation tick marker on knob surface
                val tickRad = Math.toRadians(currentAngle.toDouble())
                val tickStart = Offset(
                    (centerPx.x + (knobRadius - 18.dp.toPx()) * cos(tickRad)).toFloat(),
                    (centerPx.y + (knobRadius - 18.dp.toPx()) * sin(tickRad)).toFloat()
                )
                val tickEnd = Offset(
                    (centerPx.x + (knobRadius - 6.dp.toPx()) * cos(tickRad)).toFloat(),
                    (centerPx.y + (knobRadius - 6.dp.toPx()) * sin(tickRad)).toFloat()
                )

                drawLine(
                    color = Color.White,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
