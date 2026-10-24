package com.auraline.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraKnob
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale

@Composable
fun EffectsScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val reverb = state.reverb
    val compressor = state.compressor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Procesado de efectos",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Compresor multibanda y reverb sobre la salida",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.effectsEnabled,
                    onCheckedChange = { viewModel.setEffectsEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedTrackColor = colors.surfaceElevated,
                        uncheckedBorderColor = colors.outline
                    )
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Reverb")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AuraKnob(
                    value = reverb.size,
                    onValueChange = { viewModel.setReverb(reverb.copy(size = it)) },
                    valueRange = 0f..1f,
                    diameter = 86.dp,
                    label = "Tamaño",
                    valueText = percent(reverb.size)
                )
                AuraKnob(
                    value = reverb.decay,
                    onValueChange = { viewModel.setReverb(reverb.copy(decay = it)) },
                    valueRange = 0f..1f,
                    diameter = 86.dp,
                    label = "Decaimiento",
                    valueText = percent(reverb.decay)
                )
                AuraKnob(
                    value = reverb.damping,
                    onValueChange = { viewModel.setReverb(reverb.copy(damping = it)) },
                    valueRange = 0f..1f,
                    diameter = 86.dp,
                    label = "Amortiguación",
                    valueText = percent(reverb.damping)
                )
                AuraKnob(
                    value = reverb.mix,
                    onValueChange = { viewModel.setReverb(reverb.copy(mix = it)) },
                    valueRange = 0f..1f,
                    diameter = 86.dp,
                    label = "Mezcla",
                    valueText = percent(reverb.mix)
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Compresor multibanda", trailing = "3 bandas")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AuraKnob(
                    value = compressor.threshold,
                    onValueChange = { viewModel.setCompressor(compressor.copy(threshold = it)) },
                    valueRange = -60f..0f,
                    diameter = 86.dp,
                    label = "Umbral",
                    valueText = "${compressor.threshold.toInt()} dB"
                )
                AuraKnob(
                    value = compressor.ratio,
                    onValueChange = { viewModel.setCompressor(compressor.copy(ratio = it)) },
                    valueRange = 1f..20f,
                    diameter = 86.dp,
                    label = "Ratio",
                    valueText = String.format(Locale.US, "%.1f:1", compressor.ratio)
                )
                AuraKnob(
                    value = compressor.attackMs,
                    onValueChange = { viewModel.setCompressor(compressor.copy(attackMs = it)) },
                    valueRange = 1f..100f,
                    diameter = 86.dp,
                    label = "Ataque",
                    valueText = "${compressor.attackMs.toInt()} ms"
                )
                AuraKnob(
                    value = compressor.releaseMs,
                    onValueChange = { viewModel.setCompressor(compressor.copy(releaseMs = it)) },
                    valueRange = 10f..500f,
                    diameter = 86.dp,
                    label = "Liberación",
                    valueText = "${compressor.releaseMs.toInt()} ms"
                )
                AuraKnob(
                    value = compressor.makeupGain,
                    onValueChange = { viewModel.setCompressor(compressor.copy(makeupGain = it)) },
                    valueRange = 0f..24f,
                    diameter = 86.dp,
                    label = "Makeup",
                    valueText = "${compressor.makeupGain.toInt()} dB"
                )
            }
        }

        Text(
            text = "El limitador de salida se mantiene a −1 dBFS para evitar saturación.",
            color = colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
