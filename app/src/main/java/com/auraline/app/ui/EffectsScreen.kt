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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.ReverbParams
import com.auraline.core.domain.ReverbPresets
import com.auraline.core.ui.components.PixelCard
import com.auraline.core.ui.components.PixelChip
import com.auraline.core.ui.components.PixelSectionTitle
import com.auraline.core.ui.components.PixelSliderRow
import com.auraline.core.ui.components.PixelSwitchRow
import com.auraline.core.ui.theme.LocalMonoPalette
import java.util.Locale

@Composable
fun EffectsScreen(viewModel: AuraLineViewModel) {
    val palette = LocalMonoPalette.current
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val reverb = state.reverb
    val compressor = state.compressor
    val gate = state.gate
    val effectsOn = state.effectsEnabled && state.motorEnabled

    val reverbStage = engine.stages.firstOrNull { it.name.startsWith("Reverb") }
    val compressorStage = engine.stages.firstOrNull { it.name.startsWith("Compresor") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PixelCard {
            PixelSwitchRow(
                title = "Procesado de efectos",
                subtitle = when {
                    !state.motorEnabled -> "El motor está apagado"
                    state.effectsEnabled -> "Compresor y reverb activos"
                    else -> "Compresor y reverb desactivados"
                },
                checked = state.effectsEnabled,
                onCheckedChange = { viewModel.setEffectsEnabled(it) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (compressorStage?.active == true) "Compresor activo" else "Compresor inactivo",
                    color = if (compressorStage?.active == true) palette.primary else palette.secondary,
                    fontSize = 11.sp
                )
                Text(text = "·", color = palette.secondary, fontSize = 11.sp)
                Text(
                    text = if (reverbStage?.active == true) "Reverb activa" else "Reverb inactiva",
                    color = if (reverbStage?.active == true) palette.primary else palette.secondary,
                    fontSize = 11.sp
                )
            }
        }

        PixelCard {
            PixelSectionTitle(text = "Cadena de módulos", trailing = "orden real del motor")
            Spacer(Modifier.height(6.dp))
            ModuleSwitch("Ecualizador", "bandas + tono", state.modules.equalizer) {
                viewModel.setModules(state.modules.copy(equalizer = it))
            }
            ModuleSwitch("Puerta / Expansor", "limpia el ruido de fondo", state.modules.gate) {
                viewModel.setModules(state.modules.copy(gate = it))
            }
            ModuleSwitch("Compresor multibanda", "3 bandas", state.modules.compressor) {
                viewModel.setModules(state.modules.copy(compressor = it))
            }
            ModuleSwitch("Limitador", "techo a −2 dBFS", state.modules.limiter) {
                viewModel.setModules(state.modules.copy(limiter = it))
            }
            ModuleSwitch("Reverb", "sala y cola", state.modules.reverb) {
                viewModel.setModules(state.modules.copy(reverb = it))
            }
            ModuleSwitch("Anchura estéreo", "Virtualizer", state.modules.width) {
                viewModel.setModules(state.modules.copy(width = it))
            }
        }

        PixelCard {
            PixelSectionTitle(
                text = "Compresor multibanda",
                trailing = "3 bandas"
            )
            PixelSliderRow(
                label = "Umbral",
                valueText = "${compressor.threshold.toInt()} dB",
                value = compressor.threshold,
                valueRange = -60f..0f,
                onValueChange = { viewModel.setCompressor(compressor.copy(threshold = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Proporción",
                valueText = String.format(Locale.US, "%.1f:1", compressor.ratio),
                value = compressor.ratio,
                valueRange = 1f..20f,
                onValueChange = { viewModel.setCompressor(compressor.copy(ratio = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Ataque",
                valueText = "${compressor.attackMs.toInt()} ms",
                value = compressor.attackMs,
                valueRange = 1f..100f,
                onValueChange = { viewModel.setCompressor(compressor.copy(attackMs = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Liberación",
                valueText = "${compressor.releaseMs.toInt()} ms",
                value = compressor.releaseMs,
                valueRange = 10f..500f,
                onValueChange = { viewModel.setCompressor(compressor.copy(releaseMs = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Ganancia de compensación",
                valueText = "${compressor.makeupGain.toInt()} dB",
                value = compressor.makeupGain,
                valueRange = 0f..24f,
                onValueChange = { viewModel.setCompressor(compressor.copy(makeupGain = it)) },
                enabled = effectsOn
            )
        }

        PixelCard {
            PixelSectionTitle(
                text = "Puerta / Expansor",
                trailing = if (state.modules.gate) "activa" else "desactivada"
            )
            PixelSliderRow(
                label = "Umbral",
                valueText = "${gate.thresholdDb.toInt()} dB",
                value = gate.thresholdDb,
                valueRange = -80f..-20f,
                onValueChange = { viewModel.setGate(gate.copy(thresholdDb = it)) },
                enabled = effectsOn && state.modules.gate
            )
            PixelSliderRow(
                label = "Expansión",
                valueText = String.format(Locale.US, "%.1f:1", gate.ratio),
                value = gate.ratio,
                valueRange = 1f..10f,
                onValueChange = { viewModel.setGate(gate.copy(ratio = it)) },
                enabled = effectsOn && state.modules.gate
            )
            Text(
                text = "Por debajo del umbral la señal se atenúa: quita el siseo entre canciones.",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }

        PixelCard {
            PixelSectionTitle(text = "Reverb", trailing = "elige una sala")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReverbPresets.forEach { preset ->
                    PixelChip(
                        text = preset.name,
                        selected = state.reverb == preset.params && state.effectsEnabled,
                        onClick = { viewModel.applyReverbPreset(preset.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PixelSliderRow(
                label = "Tamaño",
                valueText = percent(reverb.size),
                value = reverb.size,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(size = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Decaimiento",
                valueText = percent(reverb.decay),
                value = reverb.decay,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(decay = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Amortiguación",
                valueText = percent(reverb.damping),
                value = reverb.damping,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(damping = it)) },
                enabled = effectsOn
            )
            PixelSliderRow(
                label = "Mezcla",
                valueText = percent(reverb.mix),
                value = reverb.mix,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(mix = it)) },
                enabled = effectsOn
            )
            if (reverbStage?.active == false) {
                Text(
                    text = "Este dispositivo no expone la reverb del sistema.",
                    color = palette.secondary,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                viewModel.setReverb(
                    ReverbParams(damping = 0.4f, filter = 0.5f, decay = 0.45f, preDelay = 0.02f, predelayMix = 0.3f, size = 0.5f, mix = 0.3f)
                )
                viewModel.setCompressor(
                    CompressorParams(threshold = -28f, ratio = 4f, attackMs = 10f, releaseMs = 200f, makeupGain = 3f)
                )
                viewModel.setEffectsEnabled(true)
            }) {
                Text("Ajustes recomendados", color = palette.primary)
            }
            Text(
                text = "Limitador a −2 dBFS",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ModuleSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PixelSwitchRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
