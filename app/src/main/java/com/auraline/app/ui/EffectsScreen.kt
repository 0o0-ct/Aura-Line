package com.auraline.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.AuraSwitch
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.domain.ReverbPresets
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale

@Composable
fun EffectsScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val reverb = state.reverb
    val compressor = state.compressor

    val effectsOn = state.effectsEnabled && state.motorEnabled
    val reverbStage = engine.stages.firstOrNull { it.name.startsWith("Reverb") }
    val compressorStage = engine.stages.firstOrNull { it.name.startsWith("Compresor") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Cadena de módulos", trailing = "orden real del motor")
            Spacer(modifier = Modifier.height(6.dp))
            ModuleRow("Ecualizador", "bandas + tono", state.modules.equalizer) {
                viewModel.setModules(state.modules.copy(equalizer = it))
            }
            ModuleRow("Puerta / Expansor", "limpia el ruido de fondo", state.modules.gate) {
                viewModel.setModules(state.modules.copy(gate = it))
            }
            ModuleRow("Compresor multibanda", "3 bandas", state.modules.compressor) {
                viewModel.setModules(state.modules.copy(compressor = it))
            }
            ModuleRow("Limitador", "techo a −2 dBFS", state.modules.limiter) {
                viewModel.setModules(state.modules.copy(limiter = it))
            }
            ModuleRow("Reverb", "sala y cola", state.modules.reverb) {
                viewModel.setModules(state.modules.copy(reverb = it))
            }
            ModuleRow("Anchura estéreo", "Virtualizer", state.modules.width) {
                viewModel.setModules(state.modules.copy(width = it))
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Puerta / Expansor", trailing = if (state.modules.gate) "activa" else "desactivada")
            Spacer(modifier = Modifier.height(4.dp))
            AuraSliderRow(
                label = "Umbral",
                valueText = "${state.gate.thresholdDb.toInt()} dB",
                value = state.gate.thresholdDb,
                valueRange = -80f..-20f,
                onValueChange = { viewModel.setGate(state.gate.copy(thresholdDb = it)) },
                enabled = effectsOn && state.modules.gate
            )
            AuraSliderRow(
                label = "Expansión",
                valueText = String.format(Locale.US, "%.1f:1", state.gate.ratio),
                value = state.gate.ratio,
                valueRange = 1f..10f,
                onValueChange = { viewModel.setGate(state.gate.copy(ratio = it)) },
                enabled = effectsOn && state.modules.gate
            )
            Text(
                text = "Por debajo del umbral la señal se atenúa: quita el siseo entre canciones y en pasajes silenciosos.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

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
                        text = when {
                            !state.motorEnabled -> "El motor está apagado"
                            state.effectsEnabled -> "Compresor y reverb activos"
                            else -> "Compresor y reverb desactivados"
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                AuraSwitch(
                    checked = state.effectsEnabled,
                    onCheckedChange = { viewModel.setEffectsEnabled(it) },
                    enabled = state.motorEnabled
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (compressorStage?.active == true) "Compresor activo" else "Compresor inactivo",
                    color = if (compressorStage?.active == true) colors.positive else colors.textSecondary
                )
                StatusPill(
                    text = if (reverbStage?.active == true) "Reverb activa" else "Reverb inactiva",
                    color = if (reverbStage?.active == true) colors.positive else colors.textSecondary
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Compresor multibanda", trailing = "3 bandas")
            Spacer(modifier = Modifier.height(4.dp))
            AuraSliderRow(
                label = "Umbral",
                valueText = "${compressor.threshold.toInt()} dB",
                value = compressor.threshold,
                valueRange = -60f..0f,
                onValueChange = { viewModel.setCompressor(compressor.copy(threshold = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Proporción",
                valueText = String.format(Locale.US, "%.1f:1", compressor.ratio),
                value = compressor.ratio,
                valueRange = 1f..20f,
                onValueChange = { viewModel.setCompressor(compressor.copy(ratio = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Ataque",
                valueText = "${compressor.attackMs.toInt()} ms",
                value = compressor.attackMs,
                valueRange = 1f..100f,
                onValueChange = { viewModel.setCompressor(compressor.copy(attackMs = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Liberación",
                valueText = "${compressor.releaseMs.toInt()} ms",
                value = compressor.releaseMs,
                valueRange = 10f..500f,
                onValueChange = { viewModel.setCompressor(compressor.copy(releaseMs = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Ganancia de compensación",
                valueText = "${compressor.makeupGain.toInt()} dB",
                value = compressor.makeupGain,
                valueRange = 0f..24f,
                onValueChange = { viewModel.setCompressor(compressor.copy(makeupGain = it)) },
                enabled = effectsOn
            )
            Text(
                text = "Umbral más bajo = comprime antes. Los valores por defecto (−28 dB, 4:1 y +6 dB de compensación) ya se notan en música normal.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Reverb", trailing = "elige una sala")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReverbPresets.forEach { preset ->
                    SelectableChip(
                        text = preset.name,
                        selected = state.reverb == preset.params && state.effectsEnabled,
                        onClick = { viewModel.applyReverbPreset(preset.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            AuraSliderRow(
                label = "Tamaño",
                valueText = percent(reverb.size),
                value = reverb.size,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(size = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Decaimiento",
                valueText = percent(reverb.decay),
                value = reverb.decay,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(decay = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Amortiguación",
                valueText = percent(reverb.damping),
                value = reverb.damping,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(damping = it)) },
                enabled = effectsOn
            )
            AuraSliderRow(
                label = "Mezcla",
                valueText = percent(reverb.mix),
                value = reverb.mix,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(mix = it)) },
                enabled = effectsOn
            )
            if (reverbStage?.active == false) {
                Text(
                    text = "Este dispositivo no expone la reverb del sistema; el compresor sigue funcionando.",
                    color = colors.warning,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                viewModel.setReverb(
                    com.auraline.core.domain.ReverbParams(
                        damping = 0.4f,
                        filter = 0.5f,
                        decay = 0.45f,
                        preDelay = 0.02f,
                        predelayMix = 0.3f,
                        size = 0.5f,
                        mix = 0.3f
                    )
                )
                viewModel.setCompressor(
                    com.auraline.core.domain.CompressorParams(
                        threshold = -28f,
                        ratio = 4f,
                        attackMs = 10f,
                        releaseMs = 200f,
                        makeupGain = 6f
                    )
                )
            }) {
                Text("Ajustes recomendados", color = colors.accent)
            }
            Text(
                text = "El limitador de salida se mantiene a −1 dBFS.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ModuleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        AuraSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
