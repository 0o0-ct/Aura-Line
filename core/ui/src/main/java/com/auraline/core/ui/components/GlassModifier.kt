package com.auraline.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auraline.core.ui.theme.CyanNeon

fun Modifier.glass(
    cornerRadius: Dp = 16.dp,
    alpha: Float = 0.12f,
    borderColor: Color = Color.White.copy(alpha = 0.18f),
    tint: Color = Color.White
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    tint.copy(alpha = alpha),
                    tint.copy(alpha = alpha * 0.5f)
                )
            ),
            shape = shape
        )
        .border(BorderStroke(1.dp, borderColor), shape)
}

fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    alpha: Float = 0.10f
): Modifier = glass(
    cornerRadius = cornerRadius,
    alpha = alpha,
    borderColor = Color.White.copy(alpha = 0.14f)
)
