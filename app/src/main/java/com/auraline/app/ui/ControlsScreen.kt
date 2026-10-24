package com.auraline.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EqualizerState
import com.auraline.core.ui.components.NeumorphicButton
import com.auraline.core.ui.components.NeumorphicKnob
import com.auraline.core.ui.components.glass
import java.util.Locale

@Composable
fun ControlsScreen(
    viewModel: AuraLineViewModel,
    modifier: Modifier = Modifier
) {
    val state: EqualizerState by viewModel.state.collectAsState()
    val cfg = LocalConfiguration.current
    val smallKnob = (cfg.screenWidthDp.dp * 0.24f).coerceIn(90.dp, 130.dp)
    val bigKnob = (cfg.screenWidthDp.dp * 0.42f).coerceIn(150.dp, 200.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .glass(cornerRadius = 20.dp, alpha = 0.06f)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeumorphicKnob(
                value = state.stereo.balance,
                onValueChange = { viewModel.setBalance(it) },
                valueRange = -100f..100f,
                size = smallKnob,
                label = "Balance",
                valueText = String.format(Locale.US, "%.2f", state.stereo.balance / 100f)
            )

            val expansionPct = ((state.stereo.expansion - 1.0f) * 100f).coerceIn(0f, 100f)
            NeumorphicKnob(
                value = expansionPct,
                onValueChange = { pct ->
                    val factor = 1.0f + (pct / 100f)
                    viewModel.setExpansion(factor)
                },
                valueRange = 0f..100f,
                size = smallKnob,
                label = "Expans. Estéreo",
                valueText = "${expansionPct.toInt()}%"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeumorphicButton(
                text = "Mono",
                active = state.stereo.mono,
                onClick = { viewModel.setMono(!state.stereo.mono) },
                modifier = Modifier.widthIn(min = 120.dp, max = 160.dp)
            )

            NeumorphicButton(
                text = "Restablecer",
                active = false,
                onClick = { viewModel.resetControls() },
                modifier = Modifier.widthIn(min = 120.dp, max = 160.dp)
            )
        }

        NeumorphicKnob(
            value = state.volume.percent,
            onValueChange = { viewModel.setVolume(it) },
            valueRange = 0f..100f,
            size = bigKnob,
            label = "Volumen",
            valueText = "${state.volume.percent.toInt()}%"
        )
    }
}
