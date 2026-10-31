package com.auraline.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.AuraTheme
import kotlin.math.abs

enum class AuraDepth {
    Surface,
    Raised,
    Floating,
    Overlay
}

enum class AuraChipState {
    Default,
    Selected,
    Preview,
    Disabled
}

enum class AuraStatusState {
    Active,
    Neutral,
    Warning,
    Error
}

@Immutable
data class AuraStatus(
    val text: String,
    val state: AuraStatusState
)

@Composable
fun AuraSurface(
    modifier: Modifier = Modifier,
    depth: AuraDepth = AuraDepth.Surface,
    shapeRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AuraTheme.colors
    val shape = RoundedCornerShape(shapeRadius)
    val surface = when (depth) {
        AuraDepth.Surface -> colors.glassSurfaceEnd
        AuraDepth.Raised -> colors.surfaceElevated
        AuraDepth.Floating -> colors.glassSurfaceStart
        AuraDepth.Overlay -> colors.surfaceElevated.copy(alpha = 0.97f)
    }
    val border = when (depth) {
        AuraDepth.Surface -> colors.glassBorderBottom
        AuraDepth.Raised, AuraDepth.Floating, AuraDepth.Overlay -> colors.glassBorderTop
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(surface, shape)
            .border(1.dp, border, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun AuraChip(
    label: String,
    state: AuraChipState = AuraChipState.Default,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val selected = state == AuraChipState.Selected || state == AuraChipState.Preview
    val enabled = state != AuraChipState.Disabled
    val shape = RoundedCornerShape(999.dp)
    val container = if (selected) {
        colors.accent.copy(alpha = if (state == AuraChipState.Preview) 0.16f else 0.24f)
    } else {
        colors.surfaceElevated.copy(alpha = 0.68f)
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(container, shape)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.75f) else colors.outline.copy(alpha = 0.45f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                stateDescription = when (state) {
                    AuraChipState.Selected -> "Seleccionado"
                    AuraChipState.Preview -> "Previsualización"
                    AuraChipState.Disabled -> "No disponible"
                    AuraChipState.Default -> "No seleccionado"
                }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent else colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun AuraStatusPill(
    status: AuraStatus,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val statusColor = when (status.state) {
        AuraStatusState.Active -> colors.positive
        AuraStatusState.Neutral -> colors.textSecondary
        AuraStatusState.Warning -> colors.warning
        AuraStatusState.Error -> colors.danger
    }
    val shape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.neumorphicCavity, shape)
            .border(1.dp, statusColor.copy(alpha = 0.32f), shape)
            .semantics {
                contentDescription = "${status.text}: ${status.state.name}"
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(statusColor, CircleShape)
        )
        Text(
            text = status.text,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AuraBandFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Fader"
) {
    val colors = AuraTheme.colors
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    var trackHeight by remember { mutableFloatStateOf(1f) }
    val normalized = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .width(64.dp)
            .semantics {
                contentDescription = label
                stateDescription = "%.1f".format(value)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .height(180.dp)
                .width(48.dp)
                .onSizeChanged { trackHeight = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(value, valueRange) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val next = value - (dragAmount.y / trackHeight) * range
                            onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
                        },
                        onDragEnd = {
                            if (abs(value) < 0.05f) onValueChange(0f.coerceIn(valueRange))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(4.dp)) {
                val centerY = size.height * (1f - normalized)
                drawLine(
                    color = colors.neumorphicCavity,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = size.width
                )
                drawLine(
                    brush = Brush.verticalGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.45f))),
                    start = Offset(size.width / 2f, centerY),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = size.width
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (180.dp * (1f - normalized)).coerceIn(0.dp, 152.dp))
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(2.dp, colors.textPrimary.copy(alpha = 0.7f), CircleShape)
                    .clickable(onClick = onReset)
            )
        }
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
fun AuraKitPreviewTheme(content: @Composable () -> Unit) {
    val colors = AuraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(16.dp)
    ) {
        content()
    }
}
