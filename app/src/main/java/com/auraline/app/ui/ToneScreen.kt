package com.auraline.app.ui

import kotlin.math.pow

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.auraline.core.ui.components.AuraKnob
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
    val boostMax = state.toneBoostMarginDb.coerceIn(12f, 24f)
    val activeProfile = UsageProfiles.firstOrNull { it.key == settings.usageProfile }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 148.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Selector directo de perfil de uso en pantalla de Tono
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Perfil de salida acústica",
                trailing = activeProfile?.name ?: "Sin elegir"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageProfiles.forEach { profile ->
                    SelectableChip(
                        text = profile.name,
                        selected = settings.usageProfile == profile.key,
                        onClick = { viewModel.applyUsageProfile(profile.key) }
                    )
                }
            }
            activeProfile?.let { profile ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${profile.description} · Corte ${profile.subCutoffHz.toInt()} Hz · Cruces ${profile.bassFrequencyHz.toInt()} Hz / ${kilo(profile.trebleFrequencyHz)}",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

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
                subDb = tone.subDb,
                subCutoffHz = tone.subCutoffHz,
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
                onValueChange = { viewModel.setSubBass(it) }
            )
            AuraSliderRow(
                label = "Corte del sub-bajo",
                valueText = "${tone.subCutoffHz.toInt()} Hz",
                value = tone.subCutoffHz,
                valueRange = 20f..100f,
                onValueChange = { viewModel.setSubCutoff(it) }
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
                onValueChange = { viewModel.setTone(it, tone.midDb, tone.trebleDb) }
            )
            AuraSliderRow(
                label = "Medios",
                valueText = db(tone.midDb),
                value = tone.midDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, it, tone.trebleDb) }
            )
            AuraSliderRow(
                label = "Agudos",
                valueText = db(tone.trebleDb),
                value = tone.trebleDb,
                valueRange = -12f..12f,
                onValueChange = { viewModel.setTone(tone.bassDb, tone.midDb, it) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Graves hasta ${tone.bassFrequencyHz.toInt()} Hz · medios hasta ${kilo(tone.trebleFrequencyHz)}",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Frecuencias de cruce",
                trailing = "${tone.bassFrequencyHz.toInt()} Hz | ${kilo(tone.trebleFrequencyHz)}"
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Graves / medios",
                valueText = "${tone.bassFrequencyHz.toInt()} Hz",
                value = tone.bassFrequencyHz,
                valueRange = 40f..400f,
                onValueChange = { viewModel.setToneFrequencies(it, tone.trebleFrequencyHz) }
            )
            AuraSliderRow(
                label = "Medios / agudos",
                valueText = kilo(tone.trebleFrequencyHz),
                value = tone.trebleFrequencyHz,
                valueRange = 2000f..16000f,
                onValueChange = { viewModel.setToneFrequencies(tone.bassFrequencyHz, it) }
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Presets rápidos de cruce
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    text = "Sub 80 / 4k",
                    selected = tone.bassFrequencyHz == 80f && tone.trebleFrequencyHz == 4000f,
                    onClick = { viewModel.setToneFrequencies(80f, 4000f) }
                )
                SelectableChip(
                    text = "Estudio 120 / 6k",
                    selected = tone.bassFrequencyHz == 120f && tone.trebleFrequencyHz == 6000f,
                    onClick = { viewModel.setToneFrequencies(120f, 6000f) }
                )
                SelectableChip(
                    text = "Voz 220 / 8k",
                    selected = tone.bassFrequencyHz == 220f && tone.trebleFrequencyHz == 8000f,
                    onClick = { viewModel.setToneFrequencies(220f, 8000f) }
                )
            }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                AuraKnob(
                    value = state.stereo.balance,
                    onValueChange = { viewModel.setBalance(it) },
                    valueRange = -100f..100f,
                    diameter = 92.dp,
                    label = "Balance",
                    valueText = when {
                        state.stereo.balance > 1f -> "D ${state.stereo.balance.toInt()}%"
                        state.stereo.balance < -1f -> "I ${(-state.stereo.balance).toInt()}%"
                        else -> "Centro"
                    }
                )
                AuraKnob(
                    value = state.stereo.expansion,
                    onValueChange = { viewModel.setExpansion(it) },
                    valueRange = 1f..2f,
                    diameter = 92.dp,
                    label = "Anchura",
                    valueText = "${((state.stereo.expansion - 1f) * 100f).toInt()}%"
                )
                AuraKnob(
                    value = state.volume.percent,
                    onValueChange = { viewModel.setVolume(it) },
                    valueRange = 0f..100f,
                    diameter = 92.dp,
                    label = "Volumen",
                    valueText = "${state.volume.percent.toInt()}%"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SelectableChip(
                text = "Restablecer controles",
                selected = false,
                onClick = { viewModel.resetControls() }
            )
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ToneCurve(
    bassDb: Float,
    midDb: Float,
    trebleDb: Float,
    subDb: Float = 0f,
    subCutoffHz: Float = 60f,
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
            .height(105.dp)
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
            .drawWithCache {
                val minF = 20f
                val maxF = 20000f
                val span = log10(maxF / minF)
                fun xOf(frequency: Float): Float =
                    ((log10(frequency.coerceIn(minF, maxF) / minF)) / span) * size.width

                val centerY = size.height / 2f
                val scale = size.height / 2.3f
                fun yOf(db: Float) = centerY - (db / maxDb).coerceIn(-1f, 1f) * scale

                val xBass = xOf(bassHz)
                val xTreble = xOf(trebleHz)
                val xSub = xOf(subCutoffHz)

                val midCenter = kotlin.math.sqrt(bassHz * trebleHz)
                fun responseAt(f: Float): Float {
                    val lfRatio = f / bassHz
                    val bassResp = bassDb / (1f + lfRatio * lfRatio)
                    val subRatio = f / subCutoffHz.coerceAtLeast(20f)
                    val subResp = subDb / (1f + subRatio * subRatio)
                    val hfRatio = trebleHz / f.coerceAtLeast(20f)
                    val trebleResp = trebleDb / (1f + hfRatio * hfRatio)
                    val logDist = kotlin.math.log10(f / midCenter)
                    val midResp = midDb / (1f + logDist * logDist * 16f)
                    return bassResp + subResp + midResp + trebleResp
                }

                val numPoints = 60
                val path = Path().apply {
                    val f0 = minF
                    moveTo(0f, yOf(responseAt(f0)))
                    for (i in 1..numPoints) {
                        val f = minF * Math.pow((maxF / minF).toDouble(), (i.toFloat() / numPoints).toDouble()).toFloat()
                        lineTo(xOf(f), yOf(responseAt(f)))
                    }
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(size.width, centerY)
                    lineTo(0f, centerY)
                    close()
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                val subDashEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                val glowStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val lineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val gridStroke = 1.dp.toPx()
                val dividerStroke = 1.5.dp.toPx()
                val nodeRadius = 3.dp.toPx()
                val bassNode = Offset(xBass, yOf(responseAt(bassHz)))
                val trebleNode = Offset(xTreble, yOf(responseAt(trebleHz)))

                onDrawBehind {
                    // 1. Zonas espectrales sombreadas (Graves, Medios, Agudos)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(colors.positive.copy(alpha = 0.12f), colors.positive.copy(alpha = 0.03f)),
                            startX = 0f,
                            endX = xBass
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(xBass, size.height)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(colors.outline.copy(alpha = 0.04f), colors.outline.copy(alpha = 0.08f), colors.outline.copy(alpha = 0.04f)),
                            startX = xBass,
                            endX = xTreble
                        ),
                        topLeft = Offset(xBass, 0f),
                        size = Size(xTreble - xBass, size.height)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(colors.accent.copy(alpha = 0.03f), colors.accent.copy(alpha = 0.12f)),
                            startX = xTreble,
                            endX = size.width
                        ),
                        topLeft = Offset(xTreble, 0f),
                        size = Size(size.width - xTreble, size.height)
                    )

                    // 2. Línea central punteada de 0 dB
                    drawLine(
                        color = colors.textSecondary.copy(alpha = 0.16f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = gridStroke,
                        pathEffect = dashEffect
                    )

                    // 3. Corte sub-bajo
                    if (subCutoffHz > 20f) {
                        drawLine(
                            color = colors.warning.copy(alpha = 0.65f),
                            start = Offset(xSub, 0f),
                            end = Offset(xSub, size.height),
                            strokeWidth = gridStroke,
                            pathEffect = subDashEffect
                        )
                    }

                    // 4. Líneas divisorias de frecuencias de cruce resaltadas
                    drawLine(
                        color = colors.positive.copy(alpha = 0.75f),
                        start = Offset(xBass, 0f),
                        end = Offset(xBass, size.height),
                        strokeWidth = dividerStroke
                    )
                    drawLine(
                        color = colors.accent.copy(alpha = 0.85f),
                        start = Offset(xTreble, 0f),
                        end = Offset(xTreble, size.height),
                        strokeWidth = dividerStroke
                    )

                    // 5. Relleno bajo la curva
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = size.height
                        )
                    )

                    // 6. Resplandor neón exterior
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = 0.35f),
                        style = glowStroke
                    )

                    // 7. Trazo nítido de tono
                    drawPath(
                        path = path,
                        color = accent,
                        style = lineStroke
                    )

                    // 8. Puntos nodales en las frecuencias de cruce
                    drawCircle(color = colors.textPrimary, radius = nodeRadius, center = bassNode)
                    drawCircle(color = colors.positive, radius = nodeRadius + 1.5f, center = bassNode, style = Stroke(1.5f))

                    drawCircle(color = colors.textPrimary, radius = nodeRadius, center = trebleNode)
                    drawCircle(color = accent, radius = nodeRadius + 1.5f, center = trebleNode, style = Stroke(1.5f))
                }
            }
    )
}

private fun kilo(hz: Float): String =
    if (hz % 1000f == 0f) "${(hz / 1000f).toInt()} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000f)

private fun db(value: Float): String = String.format(Locale.US, "%+.1f dB", value)
