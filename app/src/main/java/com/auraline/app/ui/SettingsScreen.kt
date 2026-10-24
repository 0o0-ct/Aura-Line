package com.auraline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.AppSettings
import com.auraline.core.ui.components.NeumorphicStyle
import com.auraline.core.ui.components.neumorphic
import com.auraline.core.ui.components.glass
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: AuraLineViewModel,
    modifier: Modifier = Modifier
) {
    val settings: AppSettings by viewModel.settings.collectAsState()
    var selectedPalette by remember { mutableIntStateOf(0) }

    // Sync selected palette with settings
    LaunchedEffect(settings.theme.paletteKey) {
        val keys = listOf("aura", "sunset", "forest", "midnight", "coral", "ocean")
        selectedPalette = keys.indexOf(settings.theme.paletteKey).coerceAtLeast(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .glass(cornerRadius = 20.dp, alpha = 0.06f)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Ajustes",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Paleta de colores", color = TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        val paletteColors = listOf(
            Color(0xFF00E5CC), Color(0xFFFF7043), Color(0xFF00E5A3),
            Color(0xFFB388FF), Color(0xFFFF6B6B), Color(0xFF4D96FF)
        )
        val paletteKeys = listOf("aura", "sunset", "forest", "midnight", "coral", "ocean")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            paletteColors.forEachIndexed { i, color ->
                val isSelected = selectedPalette == i
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .neumorphic(
                            style = if (isSelected) NeumorphicStyle.Concave else NeumorphicStyle.Convex,
                            cornerRadius = 22.dp
                        )
                        .background(color, shape = RoundedCornerShape(22.dp))
                        .clickable {
                            selectedPalette = i
                            viewModel.setThemePalette(paletteKeys[i], color.value.toInt())
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Número de bandas: ${settings.numBands}", color = TextSecondary, fontSize = 14.sp)
        Slider(
            value = settings.numBands.toFloat(),
            onValueChange = { viewModel.setNumBands(it.toInt()) },
            valueRange = 20f..25f,
            steps = 4,
            colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
        )

        Spacer(modifier = Modifier.height(24.dp))

        var autoStart by remember(settings.autoStart) { mutableStateOf(settings.autoStart) }
        var haptic by remember(settings.hapticFeedback) { mutableStateOf(settings.hapticFeedback) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Inicio automático", color = TextPrimary, fontSize = 15.sp)
            Switch(
                checked = autoStart,
                onCheckedChange = {
                    autoStart = it
                    viewModel.setAutoStart(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Retroalimentación háptica", color = TextPrimary, fontSize = 15.sp)
            Switch(
                checked = haptic,
                onCheckedChange = {
                    haptic = it
                    viewModel.setHapticFeedback(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon)
            )
        }
    }
}
