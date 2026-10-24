package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.SurfaceDark
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary

val BrightGreen = Color(0xFF39FF14) // Lime Green as in reference image

@Composable
fun NeumorphicVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    width: Dp = 34.dp,
    label: String = "",
    valueText: String = "",
    isPreamp: Boolean = false
) {
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    val currentRange by rememberUpdatedState(valueRange)
    val currentOnChange by rememberUpdatedState(onValueChange)

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
                val trackWidthPx = 4.dp.toPx()
                val trackLeft = (size.width - trackWidthPx) / 2f
                val topPadding = 12.dp.toPx()
                val bottomPadding = 12.dp.toPx()
                val usableHeight = size.height - topPadding - bottomPadding

                // Top & Bottom limit ticks (-)
                val tickWidth = 8.dp.toPx()
                drawLine(
                    color = Color.DarkGray,
                    start = Offset((size.width - tickWidth) / 2f, topPadding),
                    end = Offset((size.width + tickWidth) / 2f, topPadding),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = Color.DarkGray,
                    start = Offset((size.width - tickWidth) / 2f, size.height - bottomPadding),
                    end = Offset((size.width + tickWidth) / 2f, size.height - bottomPadding),
                    strokeWidth = 2.dp.toPx()
                )

                // Track Line Background
                drawLine(
                    color = Color(0xFF2C323B),
                    start = Offset(size.width / 2f, topPadding),
                    end = Offset(size.width / 2f, size.height - bottomPadding),
                    strokeWidth = trackWidthPx,
                    cap = StrokeCap.Round
                )

                // Active Green Fill Line from bottom to thumb position
                val thumbCenterY = size.height - bottomPadding - (normalizedValue * usableHeight)
                drawLine(
                    color = BrightGreen,
                    start = Offset(size.width / 2f, size.height - bottomPadding),
                    end = Offset(size.width / 2f, thumbCenterY),
                    strokeWidth = trackWidthPx,
                    cap = StrokeCap.Round
                )

                // Fader Capsule Thumb (Thinner and sleeker)
                val thumbWidthPx = (size.width - 6.dp.toPx()).coerceIn(16.dp.toPx(), 20.dp.toPx())
                val thumbHeightPx = 30.dp.toPx()
                val thumbTop = (thumbCenterY - thumbHeightPx / 2f).coerceIn(0f, size.height - thumbHeightPx)
                val thumbLeft = (size.width - thumbWidthPx) / 2f

                // Thumb Shadow
                drawRoundRect(
                    color = DarkShadowDark,
                    topLeft = Offset(thumbLeft + 2.dp.toPx(), thumbTop + 2.dp.toPx()),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Thumb Body
                drawRoundRect(
                    color = Color(0xFF2D333C),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Outer border of thumb
                drawRoundRect(
                    color = Color(0xFF424B57),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // Center White Tick line on Thumb
                val tickLineY = thumbTop + (thumbHeightPx / 2f)
                drawLine(
                    color = Color.White,
                    start = Offset(thumbLeft + 6.dp.toPx(), tickLineY),
                    end = Offset(thumbLeft + thumbWidthPx - 6.dp.toPx(), tickLineY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (valueText.isNotEmpty()) {
            Text(
                text = valueText,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
