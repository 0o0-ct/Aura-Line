package com.auraline.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.LightShadowDark

enum class NeumorphicStyle {
    Convex, // Elevated
    Concave // Inset / Pressed
}

fun Modifier.neumorphic(
    lightShadowColor: Color = LightShadowDark,
    darkShadowColor: Color = DarkShadowDark,
    shadowOffset: Dp = 4.dp,
    blurRadius: Dp = 8.dp,
    cornerRadius: Dp = 16.dp,
    style: NeumorphicStyle = NeumorphicStyle.Convex
): Modifier = this.drawBehind {
    val offsetPx = shadowOffset.toPx()
    val blurPx = blurRadius.toPx()
    val radiusPx = cornerRadius.toPx()

    if (style == NeumorphicStyle.Convex) {
        // Draw Light Shadow (Top-Left)
        drawIntoCanvas { canvas ->
            val p = Paint().apply {
                color = lightShadowColor
                this.style = PaintingStyle.Fill
                asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                    blurPx,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRoundRect(
                left = -offsetPx,
                top = -offsetPx,
                right = size.width - offsetPx,
                bottom = size.height - offsetPx,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = p
            )
        }

        // Draw Dark Shadow (Bottom-Right)
        drawIntoCanvas { canvas ->
            val p = Paint().apply {
                color = darkShadowColor
                this.style = PaintingStyle.Fill
                asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                    blurPx,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRoundRect(
                left = offsetPx,
                top = offsetPx,
                right = size.width + offsetPx,
                bottom = size.height + offsetPx,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = p
            )
        }
    } else {
        // Concave / Inset shadow inside bounds
        drawIntoCanvas { canvas ->
            val darkP = Paint().apply {
                color = darkShadowColor
                this.style = PaintingStyle.Fill
                asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                    blurPx,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = darkP
            )

            val lightP = Paint().apply {
                color = lightShadowColor
                this.style = PaintingStyle.Fill
                asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                    blurPx / 2,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            canvas.drawRoundRect(
                left = offsetPx,
                top = offsetPx,
                right = size.width - offsetPx,
                bottom = size.height - offsetPx,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = lightP
            )
        }
    }
}
