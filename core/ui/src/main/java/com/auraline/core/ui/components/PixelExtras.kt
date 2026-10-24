package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.LocalMonoPalette
import kotlin.math.log10

/** Tarjeta del tema Pixel con contenido en columna. */
@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassFloatingCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun PixelSectionTitle(
    text: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonoPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = palette.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp
        )
        if (trailing != null) {
            Text(text = trailing, color = palette.primary, fontSize = 12.sp)
        }
    }
}

/** Etiqueta + valor + deslizador horizontal. */
@Composable
fun PixelSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalMonoPalette.current
    val accent = if (enabled) palette.primary else palette.secondary.copy(alpha = 0.5f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else palette.secondary,
                fontSize = 13.sp
            )
            Text(text = valueText, color = accent, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = palette.tertiary.copy(alpha = 0.4f)
            )
        )
    }
}

/** Título + descripción + interruptor. */
@Composable
fun PixelSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMonoPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(text = subtitle, color = palette.secondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = palette.primary,
                uncheckedTrackColor = palette.surface,
                uncheckedBorderColor = palette.secondary
            )
        )
    }
}

/** Barras del analizador, alineadas con la curva (eje logarítmico de 20 Hz a 20 kHz). */
@Composable
fun PixelSpectrumBars(
    levels: FloatArray,
    edges: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp
) {
    val palette = LocalMonoPalette.current
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (levels.isEmpty() || edges.isEmpty()) return@Canvas
        val minF = 20f
        val maxF = 20000f
        val span = log10(maxF / minF)
        fun xOf(hz: Float): Float =
            ((log10(hz.coerceIn(minF, maxF) / minF)) / span) * size.width

        val gap = 1.5.dp.toPx()
        for (index in levels.indices) {
            val left = xOf(if (index == 0) minF else edges[index - 1])
            val right = xOf(edges[index])
            val width = (right - left - gap).coerceAtLeast(1f)
            val level = levels[index].coerceIn(0f, 1f)
            val barHeight = (level * size.height).coerceAtLeast(1.5.dp.toPx())
            drawRoundRect(
                color = palette.primary.copy(alpha = 0.2f + 0.8f * level),
                topLeft = Offset(left, size.height - barHeight),
                size = Size(width, barHeight),
                cornerRadius = CornerRadius(width / 3f, width / 3f)
            )
        }
    }
}
