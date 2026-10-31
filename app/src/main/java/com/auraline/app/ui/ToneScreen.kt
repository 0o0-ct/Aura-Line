package com.auraline.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.UsageProfiles
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraDepth
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.AuraSurface
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.controlAccent
import com.auraline.core.ui.theme.AuraTheme
import java.util.Locale
import kotlin.math.log10

@Composable
fun ToneScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val tone = state.tone
    val powered = state.motorEnabled
    // El perfil de uso decide cuánto margen de realce tiene cada equipo: en altavoz externo
    // se puede pedir hasta ±24 dB; en la barra 5.1, que ya procesa sola, ±12 dB.
    val boostMax = state.toneBoostMarginDb.coerceIn(12f, 24f)
    val activeProfile = UsageProfiles.firstOrNull { it.key == settings.usageProfile }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AuraSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = AuraDepth.Raised
        ) {
            SectionTitle(
                text = "Tono",
                trailing = "graves ${tone.bassFrequencyHz.toInt()} · agudos ${kilo(tone.trebleFrequencyHz)}"
            )
            Spacer(modifier = Modifier.height(10.dp))
            ToneCurve(
                bassDb = tone.bassDb,
                midDb = tone.midDb,
                trebleDb = tone.trebleDb,
                bassHz = tone.bassFrequencyHz,
                trebleHz = tone.trebleFrequencyHz,
                maxDb = boostMax
            )
            Spacer(modifier = Modifier.height(10.dp))
            AuraSliderRow(
                label = "Sub-bajo · 0–100 Hz",
                valueText = db(tone.subDb),
                value = tone.subDb,
                valueRange = -boostMax..boostMax,
                onValueChange = { viewModel.setSubBass(it) },
                enabled = powered
            )
            AuraSliderRow(
                label = "Corte del sub-bajo",
                valueText = "${tone.subCutoffHz.toInt()} Hz",
                value = tone.subCutoffHz,
                valueRange = 20f..100f,
                onValueChange = { viewModel.setSubCutoff(it) },
                enabled = powered
            )
            Text(
                text = if (activeProfile?.key == "mobile") {
                    "El altavoz del móvil no reproduce por debajo de ~150 Hz: aquí el refuerzo " +
                        "se centra en 70–100 Hz, lo más grave que de verdad saca tu teléfono. " +
                        "Con auriculares baja el corte a 30–50 Hz para sentir el sub-grave puro."
                } else {
                    "Por debajo del corte actúa el refuerzo extra de sub-bajo. Auriculares: " +
                        "30–50 Hz · altavoces con subwoofer: 40–80 Hz · barra o 5.1: 80 Hz " +
                        "(el subwoofer hace el resto)."
                },
                color = colors.textSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            AuraSliderRow(
                label = "Graves",
                valueText = db(tone.bassDb),
                value = tone.bassDb,
                valueRange = -boostMax..boostMax,
                onValueChange = { viewModel.setTone(it, tone.midDb, tone.trebleDb) },
                enabled = powered
            )
            AuraSliderRow(
                label = "Medios",
                valueText = db(tone.midDb),
                value = tone.midDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, it, tone.trebleDb) },
                enabled = powered
            )
            AuraSliderRow(
                label = "Agudos",
                valueText = db(tone.trebleDb),
                value = tone.trebleDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, tone.midDb, it) },
                enabled = powered
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Graves hasta ${tone.bassFrequencyHz.toInt()} Hz · medios hasta ${kilo(tone.trebleFrequencyHz)}",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Frecuencias de cruce")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Graves / medios",
                valueText = "${tone.bassFrequencyHz.toInt()} Hz",
                value = tone.bassFrequencyHz,
                valueRange = 40f..400f,
                onValueChange = { viewModel.setToneFrequencies(it, tone.trebleFrequencyHz) },
                enabled = powered
            )
            AuraSliderRow(
                label = "Medios / agudos",
                valueText = kilo(tone.trebleFrequencyHz),
                value = tone.trebleFrequencyHz,
                valueRange = 2000f..16000f,
                onValueChange = { viewModel.setToneFrequencies(tone.bassFrequencyHz, it) },
                enabled = powered
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cuerpo de voces y caja en 200–400 Hz · presencia en 2,5–5 kHz · aire por encima de 10 kHz",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Imagen estéreo y volumen")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
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
            AuraSliderRow(
                label = "Anchura estéreo",
                valueText = "${((state.stereo.expansion - 1f) * 100f).toInt()}%",
                value = state.stereo.expansion,
                valueRange = 1f..2f,
                onValueChange = { viewModel.setExpansion(it) },
                enabled = powered
            )
            AuraSliderRow(
                label = "Volumen multimedia",
                valueText = "${state.volume.percent.toInt()}%",
                value = state.volume.percent,
                valueRange = 0f..100f,
                onValueChange = { viewModel.setVolume(it) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableChip(
                text = "Restablecer controles",
                selected = false,
                onClick = { viewModel.resetControls() }
            )
        }
    }
}

/** Vista previa tipo osciloscopio de cristal de las tres regiones del control de tono. */
@Composable
private fun ToneCurve(
    bassDb: Float,
    midDb: Float,
    trebleDb: Float,
    bassHz: Float,
    trebleHz: Float,
    maxDb: Float = 12f
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.background, colors.neumorphicCavity)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(colors.textSecondary.copy(alpha = 0.20f), colors.textSecondary.copy(alpha = 0.04f))
                ),
                shape
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minF = 20f
            val maxF = 20000f
            val span = log10(maxF / minF)
            fun xOf(frequency: Float): Float =
                ((log10(frequency.coerceIn(minF, maxF) / minF)) / span) * size.width

            val centerY = size.height / 2f
            val scale = size.height / 2.3f
            fun yOf(db: Float) = centerY - (db / maxDb).coerceIn(-1f, 1f) * scale

            // Línea central punteada de 0 dB
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.16f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
            )

            // Líneas divisorias de frecuencias de cruce
            val xBass = xOf(bassHz)
            val xTreble = xOf(trebleHz)
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.10f),
                start = Offset(xBass, 0f),
                end = Offset(xBass, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colors.textSecondary.copy(alpha = 0.10f),
                start = Offset(xTreble, 0f),
                end = Offset(xTreble, size.height),
                strokeWidth = 1.dp.toPx()
            )

            val path = Path().apply {
                moveTo(0f, yOf(bassDb))
                lineTo(xBass, yOf(bassDb))
                lineTo(xBass, yOf(midDb))
                lineTo(xTreble, yOf(midDb))
                lineTo(xTreble, yOf(trebleDb))
                lineTo(size.width, yOf(trebleDb))
            }

            // Resplandor neón exterior
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.3f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Trazo nítido de tono
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Puntos nodales
            drawCircle(color = accent, radius = 2.dp.toPx(), center = Offset(xBass, yOf(bassDb)))
            drawCircle(color = accent, radius = 2.dp.toPx(), center = Offset(xTreble, yOf(trebleDb)))
        }
    }
}

private fun kilo(hz: Float): String =
    if (hz % 1000f == 0f) "${(hz / 1000f).toInt()} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000f)

private fun db(value: Float): String = String.format(Locale.US, "%+.1f dB", value)
