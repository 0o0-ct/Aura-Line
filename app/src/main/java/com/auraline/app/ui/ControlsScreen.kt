package com.auraline.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EqualizerState
import com.auraline.core.ui.components.GlassFloatingCard
import com.auraline.core.ui.components.PixelChip
import com.auraline.core.ui.components.PixelKnob
import java.util.Locale

@Composable
fun ControlsScreen(
    viewModel: AuraLineViewModel,
    modifier: Modifier = Modifier
) {
    val state: EqualizerState by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- TOP FLOATING CARD: Balance & Stereo Expansion ---
        GlassFloatingCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Procesamiento Estéreo y Balance",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelKnob(
                        value = state.stereo.balance,
                        onValueChange = { viewModel.setBalance(it) },
                        valueRange = -100f..100f,
                        size = 110.dp,
                        label = "Balance",
                        valueText = String.format(Locale.US, "%.2f", state.stereo.balance / 100f)
                    )

                    val expansionPct = ((state.stereo.expansion - 1.0f) * 100f).coerceIn(0f, 100f)
                    PixelKnob(
                        value = expansionPct,
                        onValueChange = { pct ->
                            val factor = 1.0f + (pct / 100f)
                            viewModel.setExpansion(factor)
                        },
                        valueRange = 0f..100f,
                        size = 110.dp,
                        label = "Expansión Estéreo",
                        valueText = "${expansionPct.toInt()}%"
                    )
                }
            }
        }

        // --- MIDDLE FLOATING CARD: Mono & Reset Options ---
        GlassFloatingCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelChip(
                    text = if (state.stereo.mono) "Mezcla Mono: ACTIVADA" else "Mezcla Mono",
                    selected = state.stereo.mono,
                    onClick = { viewModel.setMono(!state.stereo.mono) }
                )

                PixelChip(
                    text = "Restablecer Controles",
                    selected = false,
                    onClick = { viewModel.resetControls() }
                )
            }
        }

        // --- BOTTOM FLOATING CARD: Master Volume Dial ---
        GlassFloatingCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nivel de Salida Máster",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                PixelKnob(
                    value = state.volume.percent,
                    onValueChange = { viewModel.setVolume(it) },
                    valueRange = 0f..100f,
                    size = 180.dp,
                    label = "Volumen",
                    valueText = "${state.volume.percent.toInt()}%"
                )
            }
        }
    }
}
