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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraKnob
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale

@Composable
fun ToneScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Tono", trailing = "shelves de graves y agudos")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AuraKnob(
                    value = state.tone.bassDb,
                    onValueChange = { viewModel.setTone(it, state.tone.trebleDb) },
                    valueRange = -12f..12f,
                    diameter = 96.dp,
                    label = "Graves",
                    valueText = formatDb(state.tone.bassDb)
                )
                AuraKnob(
                    value = state.tone.trebleDb,
                    onValueChange = { viewModel.setTone(state.tone.bassDb, it) },
                    valueRange = -12f..12f,
                    diameter = 96.dp,
                    label = "Agudos",
                    valueText = formatDb(state.tone.trebleDb)
                )
            }
            Text(
                text = "Graves por debajo de 100 Hz · agudos por encima de 8 kHz",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Imagen estéreo", trailing = "balance y anchura")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AuraKnob(
                    value = state.stereo.balance,
                    onValueChange = { viewModel.setBalance(it) },
                    valueRange = -100f..100f,
                    diameter = 96.dp,
                    label = "Balance",
                    valueText = when {
                        state.stereo.balance > 1f -> "R ${state.stereo.balance.toInt()}%"
                        state.stereo.balance < -1f -> "L ${(-state.stereo.balance).toInt()}%"
                        else -> "Centro"
                    }
                )
                AuraKnob(
                    value = state.stereo.expansion,
                    onValueChange = { viewModel.setExpansion(it) },
                    valueRange = 1f..2f,
                    diameter = 96.dp,
                    label = "Anchura",
                    valueText = "${((state.stereo.expansion - 1f) * 100f).toInt()}%"
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Volumen", trailing = "${state.volume.percent.toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = state.volume.percent,
                onValueChange = { viewModel.setVolume(it) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.outline
                )
            )
            Text(
                text = "Se aplica al volumen multimedia del sistema",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.resetControls() }) {
                Text("Restablecer controles", color = colors.textSecondary)
            }
        }
    }
}

private fun formatDb(value: Float): String =
    String.format(Locale.US, "%+.1f dB", value)
