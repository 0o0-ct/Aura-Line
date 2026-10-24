package com.auraline.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.SurfaceDark
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary

@Composable
fun NeumorphicChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = if (selected) NeumorphicStyle.Concave else NeumorphicStyle.Convex
    val borderColor = if (selected) CyanNeon else Color.Transparent
    val textColor = if (selected) CyanNeon else TextSecondary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "chipScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .neumorphic(style = style, cornerRadius = 20.dp, shadowOffset = 3.dp)
            .background(SurfaceDark, shape = RoundedCornerShape(20.dp))
            .border(1.5.dp, borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun NeumorphicButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val borderColor = if (active) CyanNeon else Color.Transparent
    val textColor = if (active) CyanNeon else TextPrimary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .neumorphic(cornerRadius = 24.dp, shadowOffset = 4.dp)
            .background(if (active) CyanNeon.copy(alpha = 0.18f) else SurfaceDark, shape = RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NeumorphicTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .neumorphic(cornerRadius = 28.dp, shadowOffset = 4.dp)
            .background(DarkShadowDark, shape = RoundedCornerShape(28.dp))
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icons = listOf("🎚️", "🎛️", "🔘")
            val labels = listOf("Ecualizador", "Controles", "Efectos")

            labels.forEachIndexed { index, _ ->
                val isSelected = selectedTab == index
                val style = if (isSelected) NeumorphicStyle.Convex else NeumorphicStyle.Concave
                val bg = if (isSelected) SurfaceDark else Color.Transparent

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .padding(horizontal = 4.dp)
                        .neumorphic(style = style, cornerRadius = 22.dp, shadowOffset = 2.dp)
                        .background(bg, shape = RoundedCornerShape(22.dp))
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) CyanNeon.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { onTabSelected(index) }
                ) {
                    Text(
                        text = icons[index],
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
