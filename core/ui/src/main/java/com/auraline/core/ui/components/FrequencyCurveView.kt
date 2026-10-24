package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.TextSecondary
import com.auraline.core.domain.EqBand

@Composable
fun FrequencyCurveView(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    gainRangeDb: Float = 12f
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .neumorphic(style = NeumorphicStyle.Concave, cornerRadius = 12.dp)
            .background(DarkShadowDark, shape = RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (bands.isEmpty()) return@Canvas

            val widthPx = size.width
            val heightPx = size.height
            val centerY = heightPx / 2f

            // Zero dB centerline
            drawLine(
                color = TextSecondary.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(widthPx, centerY),
                strokeWidth = 1.dp.toPx()
            )

            // Calculate points
            val points = bands.mapIndexed { index, band ->
                val x = (index.toFloat() / (bands.size - 1).coerceAtLeast(1)) * widthPx
                val normalizedGain = (band.gainDb / gainRangeDb).coerceIn(-1f, 1f)
                val y = centerY - (normalizedGain * (heightPx / 2.2f))
                Offset(x, y)
            }

            // Draw smoothed curve using Bezier
            val curvePath = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val controlX1 = (p1.x + p2.x) / 2f
                        val controlY1 = p1.y
                        val controlX2 = (p1.x + p2.x) / 2f
                        val controlY2 = p2.y
                        cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                    }
                }
            }

            // Fill gradient under curve
            val fillPath = Path().apply {
                addPath(curvePath)
                lineTo(widthPx, heightPx)
                lineTo(0f, heightPx)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(CyanNeon.copy(alpha = 0.35f), Color.Transparent)
                )
            )

            // Neon glowing line
            drawPath(
                path = curvePath,
                color = CyanNeon,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}

@Composable
fun EngineStatusBadge(
    text: String = "MOTOR GLOBAL 16 BIT 48 KHZ",
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(vertical = 4.dp)
            .neumorphic(cornerRadius = 20.dp, blurRadius = 4.dp, shadowOffset = 2.dp)
            .background(DarkShadowDark.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = CyanNeon,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
    }
}
