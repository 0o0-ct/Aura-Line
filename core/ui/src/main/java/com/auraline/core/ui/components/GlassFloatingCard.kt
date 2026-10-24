package com.auraline.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassFloatingCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 6.dp,
    borderColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val glassGlow = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .shadow(elevation, shape = shape, clip = false)
            .clip(shape)
            .background(surfaceColor)
            .background(glassGlow)
            .border(1.2.dp, borderColor, shape = shape)
            .padding(12.dp),
        content = content
    )
}

fun Modifier.glass(
    cornerRadius: Dp = 24.dp,
    alpha: Float = 0.08f
): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color.White.copy(alpha = alpha))
    .border(1.dp, Color.White.copy(alpha = alpha * 2), RoundedCornerShape(cornerRadius))
