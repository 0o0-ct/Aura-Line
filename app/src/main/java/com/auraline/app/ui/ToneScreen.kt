package com.auraline.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraKnob
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale
import kotlin.math.log10

@Composable
fun ToneScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val tone = state.tone

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Tono",
                trailing = "graves ${tone.bassFrequencyHz.toInt()} · agudos ${(tone.trebleFrequencyHz / 1000f).formatK()}k"
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToneCurve(
                bassDb = tone.bassDb,
                midDb = tone.midDb,
                trebleDb = tone.trebleDb,
                bassHz = tone.bassFrequencyHz,
                trebleHz = tone.trebleFrequencyHz
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AuraKnob(
                    value = tone.bassDb,
                    onValueChange = { viewModel.setTone(it, tone.midDb, tone.trebleDb) },
                    valueRange = -12f..12f,
                    diameter = 88.dp,
                    label = "Graves",
                    valueText = formatDb(tone.bassDb)
                )
                AuraKnob(
                    value = tone.midDb,
                    onValueChange = { viewModel.setTone(tone.bassDb, it, tone.trebleDb) },
                    valueRange = -12f..12f,
                    diameter = 88.dp,
                    label = "Medios",
                    valueText = formatDb(tone.midDb)
                )
                AuraKnob(
                    value = tone.trebleDb,
                    onValueChange = { viewModel.setTone(tone.bassDb, tone.midDb, it) },
                    valueRange = -12f..12f,
                    diameter = 88.dp,
                    label = "Agudos",
                    valueText = formatDb(tone.trebleDb)
                )
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Frecuencias de cruce")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Graves hasta ${tone.bassFrequencyHz.toInt()} Hz · medios hasta ${(tone.trebleFrequencyHz / 1000f).formatK()} kHz",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
            Slider(
                value = tone.bassFrequencyHz,
                onValueChange = { viewModel.setToneFrequencies(it, tone.trebleFrequencyHz) },
                valueRange = 40f..400f,
                colors = accentSlider()
            )
            Slider(
                value = tone.trebleFrequencyHz,
                onValueChange = { viewModel.setToneFrequencies(tone.bassFrequencyHz, it) },
                valueRange = 2000f..16000f,
                colors = accentSlider()
            )
            Text(
                text = "Cuerpo de voces y caja en 200–400 Hz · presencia en 2,5–5 kHz · aire por encima de 10 kHz",
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
                colors = accentSlider()
            )
            Text(
                text = "Se aplica al volumen multimedia del sistema",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.resetControls() }) {
                Text("Restablecer controles", color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun accentSlider() = SliderDefaults.colors(
    thumbColor = AuraTheme.colors.accent,
    activeTrackColor = AuraTheme.colors.accent,
    inactiveTrackColor = AuraTheme.colors.outline
)

/** Vista previa de las tres regiones del control de tono. */
@Composable
private fun ToneCurve(
    bassDb: Float,
    midDb: Float,
    trebleDb: Float,
    bassHz: Float,
    trebleHz: Float
) {
    val colors = AuraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(top = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minF = 20f
            val maxF = 20000f
            val span = log10(maxF / minF)
            fun xOf(frequency: Float): Float =
                ((log10(frequency.coerceIn(minF, maxF) / minF)) / span) * size.width

            val centerY = size.height / 2f
            val scale = size.height / 2.4f
            fun yOf(db: Float) = centerY - (db / 12f).coerceIn(-1f, 1f) * scale

            drawLine(
                color = colors.outline.copy(alpha = 0.6f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx()
            )

            val path = Path().apply {
                moveTo(0f, yOf(bassDb))
                lineTo(xOf(bassHz), yOf(bassDb))
                lineTo(xOf(bassHz), yOf(midDb))
                lineTo(xOf(trebleHz), yOf(midDb))
                lineTo(xOf(trebleHz), yOf(trebleDb))
                lineTo(size.width, yOf(trebleDb))
            }
            drawPath(
                path = path,
                color = colors.accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun Float.formatK(): String =
    if (this % 1f == 0f) toInt().toString() else String.format(Locale.US, "%.1f", this)

private fun formatDb(value: Float): String = String.format(Locale.US, "%+.1f dB", value)
