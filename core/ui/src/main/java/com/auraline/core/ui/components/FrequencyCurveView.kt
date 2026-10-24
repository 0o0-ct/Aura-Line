package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.domain.EqBand
import com.auraline.core.ui.theme.LocalMonoPalette

@Composable
fun FrequencyCurveView(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    gainRangeDb: Float = 12f
) {
    val palette = LocalMonoPalette.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(75.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface.copy(alpha = 0.9f))
            .padding(vertical = 6.dp, horizontal = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (bands.isEmpty()) return@Canvas

            val widthPx = size.width
            val heightPx = size.height
            val centerY = heightPx / 2f

            // Zero dB centerline
            drawLine(
                color = palette.primary.copy(alpha = 0.2f),
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
                    colors = listOf(palette.primary.copy(alpha = 0.35f), Color.Transparent)
                )
            )

            // Primary glowing line
            drawPath(
                path = curvePath,
                color = palette.primary,
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
    val palette = LocalMonoPalette.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface.copy(alpha = 0.8f))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = palette.primary,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
    }
}
