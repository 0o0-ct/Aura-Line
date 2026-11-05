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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.auraline.app.audio.NativeEngineBridge
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.ReverbPresets
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraDepth
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.AuraSurface
import com.auraline.core.ui.components.AuraSwitch
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale

/**
 * Rack profesional de procesamiento de efectos de estudio para AuraLine:
 * - Detección de compatibilidad de audio y ruta activa del dispositivo.
 * - Cadena modular con bypass individual por módulo (EQ, Puerta, Compresor, Limitador, Reverb, Anchura).
 * - Puerta de ruido / Expansor con umbral y ratio configurables.
 * - Compresor multibanda con telemetría de reducción de ganancia y envolvente en tiempo real.
 * - Limitador True-Peak Lookahead con protección contra clipping y techo en dBFS.
 * - Reverb estéreo FDN con presets acústicos y parámetros de sala.
 * - Control de imagen estéreo Mid/Side con protección mono de subgraves (<120 Hz).
 */
@Composable
fun EffectsScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val telemetry by NativeEngineBridge.telemetry.collectAsState()
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
            .padding(top = 148.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Tarjeta de compatibilidad y ruta de audio del sistema
        AuraSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = AuraDepth.Raised
        ) {
            SectionTitle(text = "Compatibilidad y ruta del dispositivo")
            Spacer(modifier = Modifier.height(8.dp))
            if (!state.motorEnabled) {
                Text(
                    text = "Enciende el motor de audio en la barra superior para activar el procesamiento de efectos.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ruta de salida activa", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${engine.route.label} ${engine.outputName?.let { "· $it" } ?: ""}",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    StatusPill(
                        text = if (engine.running) "En línea" else "Inactivo",
                        color = if (engine.running) colors.positive else colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compresor multibanda DSP", color = colors.textPrimary, fontSize = 13.sp)
                    StatusPill(
                        text = when {
                            compressorStage?.detail == "requiere DynamicsProcessing" -> "No disponible"
                            compressorStage?.active == true -> "Activo"
                            else -> "Disponible"
                        },
                        color = when {
                            compressorStage?.detail == "requiere DynamicsProcessing" -> colors.warning
                            compressorStage?.active == true -> colors.positive
                            else -> colors.accent
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reverberación de sala", color = colors.textPrimary, fontSize = 13.sp)
                    StatusPill(
                        text = when {
                            engine.lastError?.contains("Reverb", ignoreCase = true) == true -> "No disponible"
                            reverbStage?.active == true -> "Activa"
                            else -> "Disponible"
                        },
                        color = when {
                            engine.lastError?.contains("Reverb", ignoreCase = true) == true -> colors.warning
                            reverbStage?.active == true -> colors.positive
                            else -> colors.accent
                        }
                    )
                }

                if (telemetry.clippingCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Advertencia de sobreimpulso: ${telemetry.clippingCount} picos superaron el techo. El limitador True-Peak ha protegido la salida.",
                        color = colors.warning,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Interruptor maestro de la suite de efectos
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rack de Efectos de Estudio",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            !state.motorEnabled -> "El motor de audio está apagado"
                            state.effectsEnabled -> "Cadena de dinámica, espacial y reverberación activa"
                            else -> "Efectos en bypass (sólo ecualización básica)"
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

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = if (compressorStage?.active == true) "Dinámica activa" else "Compresor en reposo",
                    color = if (compressorStage?.active == true) colors.positive else colors.textSecondary
                )
                StatusPill(
                    text = if (reverbStage?.active == true) "Reverb activa" else "Reverb en reposo",
                    color = if (reverbStage?.active == true) colors.positive else colors.textSecondary
                )
            }
        }

        // 3. Cadena de módulos (orden real en la señal)
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Cadena de módulos", trailing = "orden serie del DSP")
            Spacer(modifier = Modifier.height(6.dp))
            ModuleRow("Ecualizador", "25 bandas + tono + preamplificador", state.modules.equalizer) {
                viewModel.setModules(state.modules.copy(equalizer = it))
            }
            ModuleRow("Puerta / Expansor", "Elimina siseo y ruido entre canciones", state.modules.gate) {
                viewModel.setModules(state.modules.copy(gate = it))
            }
            ModuleRow("Compresor multibanda", "Control dinámico y densidad sonora", state.modules.compressor) {
                viewModel.setModules(state.modules.copy(compressor = it))
            }
            ModuleRow("Limitador True-Peak", "Protección estricta contra clipping y saturación", state.modules.limiter) {
                viewModel.setModules(state.modules.copy(limiter = it))
            }
            ModuleRow("Reverb estéreo", "Sensación espacial de sala y profundidad", state.modules.reverb) {
                viewModel.setModules(state.modules.copy(reverb = it))
            }
            ModuleRow("Anchura estéreo", "Expansión Mid/Side con graves mono centrados", state.modules.width) {
                viewModel.setModules(state.modules.copy(width = it))
            }
        }

        // 4. Puerta de ruido / Expansor
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Puerta / Expansor", trailing = if (state.modules.gate) "activo" else "en bypass")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Umbral de apertura",
                valueText = "${state.gate.thresholdDb.toInt()} dB",
                value = state.gate.thresholdDb,
                valueRange = -80f..-20f,
                onValueChange = { viewModel.setGate(state.gate.copy(thresholdDb = it)) },
                enabled = effectsOn && state.modules.gate
            )
            AuraSliderRow(
                label = "Proporción de expansión",
                valueText = String.format(Locale.US, "%.1f:1", state.gate.ratio),
                value = state.gate.ratio,
                valueRange = 1f..10f,
                onValueChange = { viewModel.setGate(state.gate.copy(ratio = it)) },
                enabled = effectsOn && state.modules.gate
            )
            Text(
                text = "Atenúa progresivamente la señal cuando desciende del umbral, silenciando el piso de ruido digital y siseos de compresión Bluetooth.",
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 5. Compresor Multibanda
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Compresor de dinámica", trailing = "3 bandas de cruce LR4")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Umbral (Threshold)",
                valueText = "${compressor.threshold.toInt()} dB",
                value = compressor.threshold,
                valueRange = -60f..0f,
                onValueChange = { viewModel.setCompressor(compressor.copy(threshold = it)) },
                enabled = effectsOn && state.modules.compressor
            )
            AuraSliderRow(
                label = "Proporción (Ratio)",
                valueText = String.format(Locale.US, "%.1f:1", compressor.ratio),
                value = compressor.ratio,
                valueRange = 1f..20f,
                onValueChange = { viewModel.setCompressor(compressor.copy(ratio = it)) },
                enabled = effectsOn && state.modules.compressor
            )
            AuraSliderRow(
                label = "Tiempo de ataque (Attack)",
                valueText = "${compressor.attackMs.toInt()} ms",
                value = compressor.attackMs,
                valueRange = 1f..100f,
                onValueChange = { viewModel.setCompressor(compressor.copy(attackMs = it)) },
                enabled = effectsOn && state.modules.compressor
            )
            AuraSliderRow(
                label = "Tiempo de liberación (Release)",
                valueText = "${compressor.releaseMs.toInt()} ms",
                value = compressor.releaseMs,
                valueRange = 10f..500f,
                onValueChange = { viewModel.setCompressor(compressor.copy(releaseMs = it)) },
                enabled = effectsOn && state.modules.compressor
            )
            AuraSliderRow(
                label = "Ganancia de compensación (Makeup)",
                valueText = "+${compressor.makeupGain.toInt()} dB",
                value = compressor.makeupGain,
                valueRange = 0f..24f,
                onValueChange = { viewModel.setCompressor(compressor.copy(makeupGain = it)) },
                enabled = effectsOn && state.modules.compressor
            )
            Text(
                text = "Nivela picos agresivos e incrementa la energía y densidad sonora sin distorsión armónica audible.",
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 6. Limitador True-Peak Lookahead
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Limitador True-Peak", trailing = if (state.modules.limiter) "protección activa" else "en bypass")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Techo de salida (Ceiling)",
                valueText = "-1.5 dBFS",
                value = -1.5f,
                valueRange = -6.0f..0.0f,
                onValueChange = { /* Techo calibrado para transmision A2DP/LE Audio */ },
                enabled = false
            )
            Text(
                text = "Ventana de lookahead de 256 muestras con estimación inter-muestra. Garantiza que los picos transitorios nunca alcancen 0 dBFS para evitar distorsión inter-muestra en conversores D/A externos y códecs Bluetooth.",
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 7. Reverb de Estudio
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Reverberación", trailing = "espacio acústico")
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
                        selected = state.reverb == preset.params && state.effectsEnabled && state.modules.reverb,
                        onClick = { viewModel.applyReverbPreset(preset.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AuraSliderRow(
                label = "Tamaño de sala",
                valueText = percent(reverb.size),
                value = reverb.size,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(size = it)) },
                enabled = effectsOn && state.modules.reverb
            )
            AuraSliderRow(
                label = "Tiempo de decaimiento",
                valueText = percent(reverb.decay),
                value = reverb.decay,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(decay = it)) },
                enabled = effectsOn && state.modules.reverb
            )
            AuraSliderRow(
                label = "Amortiguación de agudos",
                valueText = percent(reverb.damping),
                value = reverb.damping,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(damping = it)) },
                enabled = effectsOn && state.modules.reverb
            )
            AuraSliderRow(
                label = "Mezcla (Wet / Dry)",
                valueText = percent(reverb.mix),
                value = reverb.mix,
                valueRange = 0f..1f,
                onValueChange = { viewModel.setReverb(reverb.copy(mix = it)) },
                enabled = effectsOn && state.modules.reverb
            )
        }

        // 8. Botones de acción y calibración recomendada
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
                        mix = 0.25f
                    )
                )
                viewModel.setCompressor(
                    com.auraline.core.domain.CompressorParams(
                        threshold = -24f,
                        ratio = 3.5f,
                        attackMs = 15f,
                        releaseMs = 180f,
                        makeupGain = 4f
                    )
                )
            }) {
                Text("Ajustes de estudio recomendados", color = colors.accent, fontWeight = FontWeight.SemiBold)
            }
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
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        AuraSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
