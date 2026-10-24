package com.auraline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.generateEqualizerBands
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.BandFader
import com.auraline.core.ui.components.ResponseCurve
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.components.formatFrequency
import com.auraline.core.ui.components.formatGain
import com.auraline.core.ui.theme.AuraTheme

@Composable
fun EqualizerScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val presets by viewModel.presets.collectAsState()

    var pendingPresetName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val bands = remember(state.bands, state.numBands) {
        state.bands.ifEmpty { generateEqualizerBands(state.numBands) }
    }
    val range = state.gainRangeDb.coerceIn(6f, 15f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EngineStatusCard(engine = engine, motorEnabled = state.motorEnabled)

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Curva de respuesta", trailing = state.presetName)
            Spacer(modifier = Modifier.height(10.dp))
            ResponseCurve(bands = bands, gainRangeDb = range)

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    text = "Plano",
                    selected = state.presetName == "Plano",
                    onClick = { viewModel.resetAll() }
                )
                presets.forEach { name ->
                    SelectableChip(
                        text = name,
                        selected = state.presetName == name,
                        onClick = { viewModel.applyPreset(name) }
                    )
                }
                SelectableChip(
                    text = "+ Guardar",
                    selected = false,
                    onClick = {
                        pendingPresetName = state.presetName
                        showSaveDialog = true
                    }
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Ganancia de entrada", trailing = formatGain(state.preampDb) + " dB")
            Slider(
                value = state.preampDb,
                onValueChange = { viewModel.setPreamp(it) },
                valueRange = -15f..15f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.outline
                )
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Bandas", trailing = "${state.numBands} · desliza en horizontal")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                bands.forEach { band ->
                    BandFader(
                        value = band.gainDb,
                        onValueChange = { viewModel.setBandGain(band.id, it) },
                        valueRange = -range..range,
                        height = 170.dp,
                        label = formatFrequency(band.frequencyHz),
                        valueText = formatGain(band.gainDb),
                        highlighted = band.gainDb != 0f
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toca dos veces un fader para volver a 0 dB",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.resetAll() }) {
                Text("Restablecer ecualizador", color = colors.textSecondary)
            }
            Text(
                text = "± ${range.toInt()} dB",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = AuraTheme.colors.surface,
            title = { Text("Guardar preajuste", color = AuraTheme.colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = pendingPresetName,
                    onValueChange = { pendingPresetName = it },
                    singleLine = true,
                    label = { Text("Nombre") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePreset(pendingPresetName)
                    showSaveDialog = false
                }) { Text("Guardar", color = AuraTheme.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = AuraTheme.colors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun EngineStatusCard(engine: EngineStatus, motorEnabled: Boolean) {
    val colors = AuraTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val running = engine.running

    AuraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cadena de procesado",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (running) {
                        "${engine.activeStages}/${engine.stages.size} etapas activas · ${engine.sessions.size} sesión(es)"
                    } else {
                        "Inactiva: activa el motor para procesar la salida"
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            StatusPill(
                text = if (running) "EN VIVO" else "INACTIVA",
                color = if (running) colors.positive else colors.textSecondary
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                engine.stages.forEach { stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (stage.active) colors.positive else colors.outline,
                                    RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stage.name,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stage.detail,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
                engine.lastError?.let { error ->
                    Text(
                        text = error,
                        color = colors.danger,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = if (expanded) "Ocultar detalle" else "Ver estado real del motor",
                color = colors.accent,
                fontSize = 12.sp
            )
        }
    }
}
