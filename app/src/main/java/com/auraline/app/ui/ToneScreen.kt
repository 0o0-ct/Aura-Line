package com.auraline.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.auraline.core.ui.components.PixelCard
import com.auraline.core.ui.components.PixelSectionTitle
import com.auraline.core.ui.components.PixelSliderRow
import com.auraline.core.ui.theme.LocalMonoPalette
import java.util.Locale
import kotlin.math.log10

@Composable
fun ToneScreen(viewModel: AuraLineViewModel) {
    val palette = LocalMonoPalette.current
    val state by viewModel.state.collectAsState()
    val tone = state.tone
    val powered = state.motorEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PixelCard {
            PixelSectionTitle(
                text = "Tono",
                trailing = "graves ${tone.bassFrequencyHz.toInt()} · agudos ${kilo(tone.trebleFrequencyHz)}"
            )
            Spacer(Modifier.height(8.dp))
            ToneCurvePreview(
                subDb = tone.subDb,
                bassDb = tone.bassDb,
                midDb = tone.midDb,
                trebleDb = tone.trebleDb,
                bassHz = tone.bassFrequencyHz,
                trebleHz = tone.trebleFrequencyHz
            )
            Spacer(Modifier.height(8.dp))
            PixelSliderRow(
                label = "Sub-bajo · 0–100 Hz",
                valueText = db(tone.subDb),
                value = tone.subDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setSubBass(it) },
                enabled = powered
            )
            Text(
                text = "El altavoz del teléfono no reproduce por debajo de ~150 Hz: el sub-bajo se nota con auriculares o un equipo externo.",
                color = palette.secondary,
                fontSize = 11.sp
            )
            PixelSliderRow(
                label = "Graves",
                valueText = db(tone.bassDb),
                value = tone.bassDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(it, tone.midDb, tone.trebleDb) },
                enabled = powered
            )
            PixelSliderRow(
                label = "Medios",
                valueText = db(tone.midDb),
                value = tone.midDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, it, tone.trebleDb) },
                enabled = powered
            )
            PixelSliderRow(
                label = "Agudos",
                valueText = db(tone.trebleDb),
                value = tone.trebleDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, tone.midDb, it) },
                enabled = powered
            )
        }

        PixelCard {
            PixelSectionTitle(text = "Frecuencias de cruce")
            Spacer(Modifier.height(4.dp))
            PixelSliderRow(
                label = "Graves / medios",
                valueText = "${tone.bassFrequencyHz.toInt()} Hz",
                value = tone.bassFrequencyHz,
                valueRange = 40f..400f,
                onValueChange = { viewModel.setToneFrequencies(it, tone.trebleFrequencyHz) },
                enabled = powered
            )
            PixelSliderRow(
                label = "Medios / agudos",
                valueText = kilo(tone.trebleFrequencyHz),
                value = tone.trebleFrequencyHz,
                valueRange = 2000f..16000f,
                onValueChange = { viewModel.setToneFrequencies(tone.bassFrequencyHz, it) },
                enabled = powered
            )
            Text(
                text = "Cuerpo de voces y caja en 200–400 Hz · presencia en 2,5–5 kHz · aire por encima de 10 kHz",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }

        PixelCard {
            PixelSectionTitle(text = "Imagen estéreo")
            PixelSliderRow(
                label = "Balance",
                valueText = when {
                    state.stereo.balance > 1f -> "D ${state.stereo.balance.toInt()}%"
                    state.stereo.balance < -1f -> "I ${(-state.stereo.balance).toInt()}%"
                    else -> "Centro"
                },
                value = state.stereo.balance,
                valueRange = -100f..100f,
                onValueChange = { viewModel.setBalance(it) },
                enabled = powered
            )
            PixelSliderRow(
                label = "Anchura estéreo",
                valueText = "${((state.stereo.expansion - 1f) * 100f).toInt()}%",
                value = state.stereo.expansion,
                valueRange = 1f..2f,
                onValueChange = { viewModel.setExpansion(it) },
                enabled = powered
            )
        }

        PixelCard {
            PixelSectionTitle(text = "Volumen", trailing = "${state.volume.percent.toInt()}%")
            PixelSliderRow(
                label = "Volumen multimedia",
                valueText = "${state.volume.percent.toInt()}%",
                value = state.volume.percent,
                valueRange = 0f..100f,
                onValueChange = { viewModel.setVolume(it) }
            )
            Text(
                text = "Sigue al volumen del sistema: si lo cambias con los botones, la barra se actualiza.",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.resetControls() }) {
                Text("Restablecer controles", color = palette.secondary)
            }
        }
    }
}

@Composable
private fun ToneCurvePreview(
    subDb: Float,
    bassDb: Float,
    midDb: Float,
    trebleDb: Float,
    bassHz: Float,
    trebleHz: Float
) {
    val palette = LocalMonoPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
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
                color = palette.secondary.copy(alpha = 0.5f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx()
            )

            val path = Path().apply {
                moveTo(0f, yOf(subDb + bassDb))
                lineTo(xOf(bassHz), yOf(subDb + bassDb))
                lineTo(xOf(bassHz), yOf(midDb))
                lineTo(xOf(trebleHz), yOf(midDb))
                lineTo(xOf(trebleHz), yOf(trebleDb))
                lineTo(size.width, yOf(trebleDb))
            }
            drawPath(
                path = path,
                color = palette.primary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun kilo(hz: Float): String =
    if (hz % 1000f == 0f) "${(hz / 1000f).toInt()} kHz"
    else String.format(Locale.US, "%.1f kHz", hz / 1000f)

private fun db(value: Float): String = String.format(Locale.US, "%+.1f dB", value)
