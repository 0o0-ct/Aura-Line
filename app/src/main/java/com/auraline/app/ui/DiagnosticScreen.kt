package com.auraline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.audio.GeneratorMode
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EngineStatus
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.theme.AuraTheme

@Composable
fun DiagnosticScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val engine by viewModel.engine.collectAsState()
    val state by viewModel.state.collectAsState()
    val telemetry by viewModel.nativeTelemetry.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tarjeta Principal: Estado del Motor
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        text = "Diagnóstico en Vivo",
                        trailing = if (state.motorEnabled) "MOTOR ENCENDIDO" else "APAGADO"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Modo: ${telemetry.activeMode}",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Frecuencia: ${telemetry.sampleRateHz} Hz · ${telemetry.channels} canales · ${telemetry.processedBlocks} bloques",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                StatusPill(
                    text = if (state.motorEnabled) "ACTIVO" else "EN ESPERA",
                    color = if (state.motorEnabled) colors.positive else colors.textSecondary
                )
            }
        }

        // Generador Interno de Pruebas Auditivas (A/B Test)
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Probar Motor (Generador A/B)",
                trailing = if (state.bypass) "BYPASS ACTIVO" else "PROCESANDO"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Genera audio interno para verificar inmediatamente el funcionamiento audible del ecualizador y los efectos sin depender de Spotify u otras apps.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Selector de señal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeneratorMode.values().forEach { mode ->
                    SelectableChip(
                        text = mode.label,
                        selected = telemetry.generatorMode == mode,
                        onClick = { viewModel.setGeneratorMode(mode) }
                    )
                }
            }

            AnimatedVisibility(visible = telemetry.generatorMode != GeneratorMode.OFF) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nivel del generador",
                            color = colors.textPrimary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${(telemetry.generatorLevel * 100).toInt()}%",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = telemetry.generatorLevel,
                        onValueChange = { viewModel.setGeneratorLevel(it) },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accent,
                            activeTrackColor = colors.accent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón Conmutador A/B Instantáneo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Comparación A/B en Vivo",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (state.bypass) "Escuchando señal pura sin EQ" else "Escuchando señal procesada por el DSP",
                                color = if (state.bypass) colors.warning else colors.positive,
                                fontSize = 11.sp
                            )
                        }
                        SelectableChip(
                            text = if (state.bypass) "Bypass: SÍ" else "Bypass: NO",
                            selected = state.bypass,
                            onClick = { viewModel.setBypass(!state.bypass) }
                        )
                    }
                }
            }
        }

        // Medidores en Tiempo Real (RMS, Pico dBFS, Clipping)
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Medidores de Nivel (dBFS)",
                    trailing = "L / R"
                )
                Spacer(modifier = Modifier.weight(1f))
                if (telemetry.clippingCount > 0) {
                    StatusPill(
                        text = "CLIPPING (${telemetry.clippingCount})",
                        color = colors.warning
                    )
                } else {
                    StatusPill(
                        text = "SIN RECORTE",
                        color = colors.positive
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barra Canal Izquierdo
            LevelMeterBar(
                channelLabel = "L",
                rmsDb = telemetry.rmsL_dBFS,
                peakDb = telemetry.peakL_dBFS,
                peakLin = telemetry.peakL
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Barra Canal Derecho
            LevelMeterBar(
                channelLabel = "R",
                rmsDb = telemetry.rmsR_dBFS,
                peakDb = telemetry.peakR_dBFS,
                peakLin = telemetry.peakR
            )
        }

        // Estado por Efecto (4 Niveles)
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Cadena de Efectos y Estado Real",
                trailing = "${engine.activeStages} activas"
            )
            Spacer(modifier = Modifier.height(10.dp))

            val effectStages = listOf(
                QuadStatusEffect("1. PreEQ (25 Bandas)", true, true, state.modules.equalizer && !state.bypass, state.motorEnabled && !state.bypass),
                QuadStatusEffect("2. Filtro Subsónico (LR4)", true, true, true, state.motorEnabled),
                QuadStatusEffect("3. Tono y Crossovers LR4", true, true, true, state.motorEnabled && !state.bypass),
                QuadStatusEffect("4. Realzador Graves (15 Instrumentos)", true, true, true, state.motorEnabled && !state.bypass),
                QuadStatusEffect("5. Compresor Multibanda / MBC", true, true, state.modules.compressor && !state.bypass, state.motorEnabled && !state.bypass),
                QuadStatusEffect("6. Puerta de Ruido y Expansor", true, true, state.modules.gate && !state.bypass, state.motorEnabled && !state.bypass),
                QuadStatusEffect("7. Reverb DSP (FDN Estéreo)", true, true, state.modules.reverb && state.effectsEnabled && !state.bypass, state.motorEnabled && state.effectsEnabled && !state.bypass),
                QuadStatusEffect("8. Ampliador Estéreo (Mid/Side)", true, true, state.modules.width && !state.bypass, state.motorEnabled && !state.bypass),
                QuadStatusEffect("9. Limitador True-Peak", true, true, state.modules.limiter && !state.bypass, state.motorEnabled && !state.bypass)
            )

            effectStages.forEach { eff ->
                EffectStatusRow(eff)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Enrutamiento y Sesiones de Apps Externas
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Enrutamiento y Sesiones",
                trailing = engine.route.label
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dispositivo de salida:",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = engine.outputName ?: engine.route.label,
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sesiones detectadas:",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = if (engine.sessions.isNotEmpty()) {
                        "${engine.sessions.size} app(s) conectadas (${engine.sessions.joinToString { "#${it.id}" }})"
                    } else {
                        "Sin sesiones externas (sesión 0 fallback)"
                    },
                    color = if (engine.sessions.isNotEmpty()) colors.positive else colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

data class QuadStatusEffect(
    val name: String,
    val available: Boolean,
    val initialized: Boolean,
    val hasControl: Boolean,
    val processingSignal: Boolean
)

@Composable
private fun EffectStatusRow(effect: QuadStatusEffect) {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = effect.name,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        StatusIndicatorBadge("Disp", effect.available)
        Spacer(modifier = Modifier.width(4.dp))
        StatusIndicatorBadge("Init", effect.initialized)
        Spacer(modifier = Modifier.width(4.dp))
        StatusIndicatorBadge("Ctrl", effect.hasControl)
        Spacer(modifier = Modifier.width(4.dp))
        StatusIndicatorBadge("Run", effect.processingSignal)
    }
}

@Composable
private fun StatusIndicatorBadge(label: String, active: Boolean) {
    val colors = AuraTheme.colors
    val bg = if (active) colors.positive.copy(alpha = 0.2f) else colors.outline.copy(alpha = 0.1f)
    val textCol = if (active) colors.positive else colors.textSecondary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LevelMeterBar(
    channelLabel: String,
    rmsDb: Float,
    peakDb: Float,
    peakLin: Float
) {
    val colors = AuraTheme.colors
    // Map -60 dB .. 0 dB to 0.0 .. 1.0 fraction
    val peakFrac = ((peakDb + 60f) / 60f).coerceIn(0f, 1f)
    val rmsFrac = ((rmsDb + 60f) / 60f).coerceIn(0f, 1f)

    val meterColor = when {
        peakDb >= -0.1f -> colors.warning
        peakDb >= -3.0f -> Color(0xFFFFB800)
        else -> colors.positive
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channelLabel,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(16.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF131822))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp))
        ) {
            // Peak bar
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction = peakFrac)
                    .background(meterColor.copy(alpha = 0.6f))
            )
            // RMS solid inner bar
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction = rmsFrac)
                    .background(meterColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (peakDb > -80f) "${peakDb.toInt()} dBFS" else "−∞ dBFS",
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(55.dp)
        )
    }
}
