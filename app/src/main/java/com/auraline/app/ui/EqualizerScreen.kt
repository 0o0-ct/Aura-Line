package com.auraline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EqBand
import com.auraline.core.domain.EqualizerState
import com.auraline.core.ui.components.*
import com.auraline.core.ui.components.glass
import com.auraline.core.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun EqualizerScreen(
    viewModel: AuraLineViewModel,
    modifier: Modifier = Modifier
) {
    val state: EqualizerState by viewModel.state.collectAsState()
    var selectedModeChip by remember { mutableIntStateOf(0) }

    val bands = if (state.bands.isNotEmpty()) state.bands else generateDefaultBands(state.numBands)

    val cfg = LocalConfiguration.current
    val screenH = cfg.screenHeightDp.dp
    val sliderH = (screenH * 0.24f).coerceIn(120.dp, 180.dp)
    val knobSize = (cfg.screenWidthDp.dp * 0.18f).coerceIn(60.dp, 84.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .glass(cornerRadius = 20.dp, alpha = 0.06f)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- TOP SECTION: Sleeker Capsule Preamp Container (Left) + Scrollable 20-25 Bands (Right) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleeker Preamp Pill Capsule Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .neumorphic(style = NeumorphicStyle.Convex, cornerRadius = 24.dp, shadowOffset = 2.dp)
                    .background(com.auraline.core.ui.theme.DarkShadowDark, shape = RoundedCornerShape(24.dp))
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                NeumorphicVerticalSlider(
                    value = state.preampDb,
                    onValueChange = { viewModel.setPreamp(it) },
                    valueRange = -15f..15f,
                    height = sliderH,
                    width = 28.dp,
                    label = "Preamp",
                    valueText = String.format(Locale.US, "%.1f", state.preampDb),
                    isPreamp = true
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 20-25 Bands Scrollable Row (7-8 visible at once)
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bands.forEach { band ->
                    val freqLabel = formatFreqLabel(band.frequencyHz)
                    val gainText = String.format(Locale.US, "%.1f", band.gainDb)
                    NeumorphicVerticalSlider(
                        value = band.gainDb,
                        onValueChange = { newGain -> viewModel.setBandGain(band.id, newGain) },
                        valueRange = -12f..12f,
                        height = sliderH,
                        width = 24.dp,
                        label = freqLabel,
                        valueText = gainText
                    )
                }
            }
        }

        // --- MIDDLE SECTION: Full-width Frequency Response Curve ---
        FrequencyCurveView(bands = bands, modifier = Modifier.fillMaxWidth())

        Text(
            text = "ventana de 7-8 bandas (arrastrable)",
            color = TextSecondary,
            fontSize = 11.sp
        )

        EngineStatusBadge(text = "MOTOR GLOBAL 16 BIT 48 KHZ")

        // --- MODE CHIPS: Tono / Límite / Avanzado ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val chips = listOf("Tono", "Límite", "Avanzado")
            chips.forEachIndexed { index, chipName ->
                NeumorphicChip(
                    text = chipName,
                    selected = selectedModeChip == index,
                    onClick = { selectedModeChip = index }
                )
            }
        }

        // --- TONE KNOBS: Graves & Agudos ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bassPct = ((state.tone.bassDb + 12f) / 24f * 100f).toInt()
            val treblePct = ((state.tone.trebleDb + 12f) / 24f * 100f).toInt()

            NeumorphicKnob(
                value = state.tone.bassDb,
                onValueChange = { viewModel.setTone(it, state.tone.trebleDb) },
                valueRange = -12f..12f,
                size = knobSize,
                label = "",
                valueText = "Graves ${if (bassPct >= 0) "+$bassPct%" else "$bassPct%"}"
            )

            NeumorphicKnob(
                value = state.tone.trebleDb,
                onValueChange = { viewModel.setTone(state.tone.bassDb, it) },
                valueRange = -12f..12f,
                size = knobSize,
                label = "",
                valueText = "Agudos ${if (treblePct >= 0) "+$treblePct%" else "$treblePct%"}"
            )
        }
    }
}

private fun formatFreqLabel(hz: Float): String {
    return if (hz >= 1000f) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else {
        "${hz.toInt()}"
    }
}

private fun generateDefaultBands(count: Int): List<EqBand> {
    val fStart = 20f
    val fEnd = 20000f
    return List(count) { i ->
        val freq = fStart * Math.pow((fEnd / fStart).toDouble(), (i.toDouble() / (count - 1))).toFloat()
        EqBand(id = i, frequencyHz = freq, gainDb = 0f)
    }
}
