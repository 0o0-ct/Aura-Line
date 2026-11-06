package com.auraline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.ToneParametricBand
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
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ToneScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val tone = state.tone
    val boostMax = state.toneBoostMarginDb.coerceIn(12f, 24f)
    val activeProfile = UsageProfiles.firstOrNull { it.key == settings.usageProfile }

    // Selected band for advanced parameter inspection (gain, frequency, Q, filter type)
    var selectedBandId by remember { mutableStateOf(tone.bands.firstOrNull()?.id ?: "bass") }
    var selectedPresetName by remember { mutableStateOf("Plano") }
    val selectedBand = tone.bands.firstOrNull { it.id == selectedBandId } ?: tone.bands.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 148.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Selector directo de perfil de uso
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

        // 2. Panel Profesional de Respuesta en Frecuencia (Analizador Paramétrico Interactivo)
        AuraSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = AuraDepth.Raised
        ) {
            SectionTitle(
                text = "Ecualización Paramétrica & Tono",
                trailing = "${tone.bands.count { it.enabled }} bandas activas"
            )
            Spacer(modifier = Modifier.height(10.dp))

            ParametricToneCurveView(
                bands = tone.bands,
                selectedBandId = selectedBandId,
                maxDb = boostMax,
                onSelectBand = { selectedBandId = it },
                onUpdateBand = { id, gain, freq, q, type, enabled ->
                    viewModel.setToneParametricBand(id, gain, freq, q, type, enabled)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Selector rápido de bandas para inspección paramétrica
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tone.bands.forEach { band ->
                    val isSelected = band.id == selectedBandId
                    SelectableChip(
                        text = band.name,
                        selected = isSelected,
                        onClick = { selectedBandId = band.id }
                    )
                }
            }
        }

        // 3. Inspector Avanzado de la Banda Seleccionada (Frecuencia, Ganancia, Q, Filtro)
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionTitle(
                        text = "Banda: ${selectedBand.name}",
                        trailing = String.format(Locale.US, "%.1f Hz", selectedBand.frequencyHz)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = getBandContextHint(selectedBand.id),
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(selectedBand.colorHex))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            AuraSliderRow(
                label = "Ganancia (${selectedBand.name})",
                valueText = db(selectedBand.gainDb),
                value = selectedBand.gainDb,
                valueRange = -boostMax..boostMax,
                onValueChange = { newGain ->
                    viewModel.setToneParametricBand(
                        bandId = selectedBand.id,
                        gainDb = newGain,
                        frequencyHz = selectedBand.frequencyHz,
                        q = selectedBand.q,
                        filterType = selectedBand.filterType,
                        enabled = selectedBand.enabled
                    )
                }
            )

            val minF = 20f
            val maxF = 20000f
            val freqSpan = log10(maxF / minF)
            val currentFreqFraction = ((log10(selectedBand.frequencyHz.coerceIn(minF, maxF) / minF) / freqSpan) * 100f).coerceIn(0f, 100f)

            AuraSliderRow(
                label = "Frecuencia central",
                valueText = if (selectedBand.frequencyHz >= 1000f) kilo(selectedBand.frequencyHz) else "${selectedBand.frequencyHz.toInt()} Hz",
                value = currentFreqFraction,
                valueRange = 0f..100f,
                onValueChange = { newFraction ->
                    val normalized = (newFraction / 100f).coerceIn(0f, 1f)
                    val newHz = minF * 10f.pow(normalized * freqSpan)
                    viewModel.setToneParametricBand(
                        bandId = selectedBand.id,
                        gainDb = selectedBand.gainDb,
                        frequencyHz = newHz,
                        q = selectedBand.q,
                        filterType = selectedBand.filterType,
                        enabled = selectedBand.enabled
                    )
                }
            )

            AuraSliderRow(
                label = "Factor Q (Ancho de banda)",
                valueText = String.format(Locale.US, "Q: %.2f", selectedBand.q),
                value = selectedBand.q,
                valueRange = 0.1f..10.0f,
                onValueChange = { newQ ->
                    viewModel.setToneParametricBand(
                        bandId = selectedBand.id,
                        gainDb = selectedBand.gainDb,
                        frequencyHz = selectedBand.frequencyHz,
                        q = newQ,
                        filterType = selectedBand.filterType,
                        enabled = selectedBand.enabled
                    )
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("LOW_SHELF", "PEAKING", "HIGH_SHELF", "NOTCH", "HIGH_PASS", "LOW_PASS").forEach { type ->
                    SelectableChip(
                        text = type,
                        selected = selectedBand.filterType == type,
                        onClick = {
                            viewModel.setToneParametricBand(
                                bandId = selectedBand.id,
                                gainDb = selectedBand.gainDb,
                                frequencyHz = selectedBand.frequencyHz,
                                q = selectedBand.q,
                                filterType = type,
                                enabled = selectedBand.enabled
                            )
                        }
                    )
                }
            }
        }

        // 4. Presets Tonales Profesionales
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Presets Tonales Profesionales")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Plano", "Subgrave controlado", "Bass Focus", "Warm Sound",
                    "Vocal Clarity", "Vocal Presence", "Studio Neutral",
                    "Acoustic", "Instrument Detail", "Bright Sound", "Air & Detail"
                ).forEach { presetName ->
                    SelectableChip(
                        text = presetName,
                        selected = presetName == selectedPresetName,
                        onClick = {
                            selectedPresetName = presetName
                            applyTonePreset(viewModel, presetName)
                        }
                    )
                }
            }
        }

        // 5. Control de Frecuencias de Cruce y Sub-bajo
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Frecuencias de Cruce y Sub-bajo",
                trailing = "${tone.bassFrequencyHz.toInt()} Hz | ${kilo(tone.trebleFrequencyHz)}"
            )
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Corte del sub-bajo",
                valueText = "${tone.subCutoffHz.toInt()} Hz",
                value = tone.subCutoffHz,
                valueRange = 20f..100f,
                onValueChange = { viewModel.setSubCutoff(it) }
            )
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
        }

        // 6. Imagen Estéreo y Volumen (Conservado sin alteraciones)
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

/**
 * Gráfica profesional de respuesta en frecuencia con escala logarítmica (20 Hz - 20 kHz),
 * bandas diferenciadas por color, nodos interactivos y arrastrables en tiempo real,
 * y curva DSP real calculada por transferencia.
 */
@Composable
private fun ParametricToneCurveView(
    bands: List<ToneParametricBand>,
    selectedBandId: String,
    maxDb: Float,
    onSelectBand: (String) -> Unit,
    onUpdateBand: (String, Float, Float, Float, String, Boolean) -> Unit
) {
    val colors = AuraTheme.colors
    val accent = controlAccent()
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
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
            .pointerInput(bands, maxDb) {
                var draggingBandId: String? = null
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val minF = 20f
                        val maxF = 20000f
                        val span = log10(maxF / minF)
                        fun xOf(f: Float): Float = ((log10(f.coerceIn(minF, maxF) / minF)) / span) * size.width
                        val centerY = size.height / 2f
                        val scale = size.height / 2.3f
                        fun yOf(db: Float) = centerY - (db / maxDb).coerceIn(-1f, 1f) * scale

                        val closest = bands.minByOrNull { b ->
                            val nx = xOf(b.frequencyHz)
                            val ny = yOf(b.gainDb)
                            val dx = startOffset.x - nx
                            val dy = startOffset.y - ny
                            dx * dx + dy * dy
                        }
                        closest?.let {
                            draggingBandId = it.id
                            onSelectBand(it.id)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        draggingBandId?.let { id ->
                            val band = bands.firstOrNull { it.id == id } ?: return@let
                            val minF = 20f
                            val maxF = 20000f
                            val span = log10(maxF / minF)
                            fun xOf(f: Float): Float = ((log10(f.coerceIn(minF, maxF) / minF)) / span) * size.width

                            val dampenedDragX = dragAmount.x * 0.75f
                            val dampenedDragY = dragAmount.y * 0.5f

                            val currentX = xOf(band.frequencyHz) + dampenedDragX
                            val normalizedX = (currentX / size.width).coerceIn(0f, 1f)
                            val newFreq = minF * 10f.pow(normalizedX * span)

                            val centerY = size.height / 2f
                            val scale = size.height / 2.3f
                            fun yOf(db: Float) = centerY - (db / maxDb).coerceIn(-1f, 1f) * scale

                            val currentY = yOf(band.gainDb) + dampenedDragY
                            val newGain = ((centerY - currentY) / scale) * maxDb

                            onUpdateBand(
                                band.id,
                                newGain.coerceIn(-maxDb, maxDb),
                                newFreq.coerceIn(20f, 20000f),
                                band.q,
                                band.filterType,
                                band.enabled
                            )
                        }
                    },
                    onDragEnd = { draggingBandId = null },
                    onDragCancel = { draggingBandId = null }
                )
            }
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

                // Cálculo de la respuesta combinada de todas las bandas paramétricas
                fun responseAt(f: Float): Float {
                    var totalDb = 0f
                    for (b in bands) {
                        if (!b.enabled) continue
                        val bf = b.frequencyHz.coerceAtLeast(10f)
                        val bg = b.gainDb
                        val q = b.q.coerceAtLeast(0.1f)
                        when (b.filterType) {
                            "LOW_SHELF" -> {
                                val ratio = f / bf
                                totalDb += bg / (1f + ratio * ratio)
                            }
                            "HIGH_SHELF" -> {
                                val ratio = bf / f.coerceAtLeast(10f)
                                totalDb += bg / (1f + ratio * ratio)
                            }
                            else -> { // PEAKING / bell / notch
                                val octaveDist = abs(ln(f.coerceAtLeast(10f) / bf) / ln(2f))
                                val weighting = 1f + (octaveDist * q * 2.5f).let { it * it }
                                totalDb += bg / weighting
                            }
                        }
                    }
                    return totalDb.coerceIn(-maxDb, maxDb)
                }

                val numPoints = 120
                val path = Path().apply {
                    moveTo(0f, yOf(responseAt(minF)))
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
                val glowStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                val gridStroke = 1.dp.toPx()

                onDrawBehind {
                    // 1. Línea central de 0 dB
                    drawLine(
                        color = colors.textSecondary.copy(alpha = 0.25f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = gridStroke,
                        pathEffect = dashEffect
                    )

                    // 2. Líneas de referencia de ganancia (+6dB, -6dB)
                    val yPlus6 = yOf(6f)
                    val yMinus6 = yOf(-6f)
                    drawLine(
                        color = colors.textSecondary.copy(alpha = 0.10f),
                        start = Offset(0f, yPlus6),
                        end = Offset(size.width, yPlus6),
                        strokeWidth = gridStroke
                    )
                    drawLine(
                        color = colors.textSecondary.copy(alpha = 0.10f),
                        start = Offset(0f, yMinus6),
                        end = Offset(size.width, yMinus6),
                        strokeWidth = gridStroke
                    )

                    // 3. Relleno degradado bajo la curva
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = size.height
                        )
                    )

                    // 4. Resplandor neón
                    drawPath(
                        path = path,
                        color = accent.copy(alpha = 0.35f),
                        style = glowStroke
                    )

                    // 5. Curva principal
                    drawPath(
                        path = path,
                        color = accent,
                        style = lineStroke
                    )

                    // 6. Nodos interactivos y arrastrables por cada banda
                    for (b in bands) {
                        val nx = xOf(b.frequencyHz)
                        val ny = yOf(responseAt(b.frequencyHz))
                        val isSelected = b.id == selectedBandId
                        val nodeColor = Color(b.colorHex)

                        drawCircle(
                            color = colors.textPrimary,
                            radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx(),
                            center = Offset(nx, ny)
                        )
                        drawCircle(
                            color = nodeColor,
                            radius = if (isSelected) 9.5.dp.toPx() else 6.dp.toPx(),
                            center = Offset(nx, ny),
                            style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.5.dp.toPx())
                        )
                    }
                }
            }
    )
}

private fun getBandContextHint(bandId: String): String = when (bandId) {
    "sub" -> "Profundidad masiva (20–40 Hz): sub-bajo puro y vibración."
    "deep_bass" -> "Cuerpo del bombo y sintetizadores graves (40–60 Hz)."
    "bass" -> "Graves contundentes y calidez general (80–120 Hz)."
    "low_mid" -> "Relleno y cuerpo en voces y baja frecuencia (200–300 Hz)."
    "mid" -> "Cuerpo de voces e instrumentos acústicos (800–1200 Hz)."
    "upper_mid" -> "Claridad y definición vocal (2–3 kHz)."
    "presence" -> "Presencia y mordiente en guitarras y voces (3.5–5 kHz)."
    "brilliance" -> "Brillo natural y definición de platillos (7–9 kHz)."
    "treble" -> "Agudos cristalinos y aire superior (10–14 kHz)."
    "air" -> "Apertura suprema y espacio acústico (16–20 kHz)."
    else -> "Ajuste tonal preciso."
}

private fun applyTonePreset(viewModel: AuraLineViewModel, presetName: String) {
    val defaults = com.auraline.core.domain.defaultToneParametricBands()
    val gains = when (presetName) {
        "Plano" -> emptyMap()
        "Subgrave controlado" -> mapOf("sub" to 4f, "bass" to 2f)
        "Bass Focus" -> mapOf("sub" to 6f, "deep_bass" to 5f, "bass" to 4f, "low_mid" to 1f)
        "Warm Sound" -> mapOf("bass" to 4f, "low_mid" to 3f, "mid" to 1f, "treble" to -2f)
        "Vocal Clarity" -> mapOf("sub" to -4f, "bass" to -2f, "upper_mid" to 4f, "presence" to 5f, "brilliance" to 2f)
        "Vocal Presence" -> mapOf("mid" to 2f, "upper_mid" to 5f, "presence" to 4f)
        "Studio Neutral" -> mapOf("sub" to 1f, "treble" to 1f, "air" to 2f)
        "Acoustic" -> mapOf("bass" to 3f, "mid" to 2f, "air" to 3f)
        "Instrument Detail" -> mapOf("low_mid" to 2f, "upper_mid" to 3f, "presence" to 4f, "brilliance" to 3f)
        "Bright Sound" -> mapOf("presence" to 3f, "brilliance" to 4f, "treble" to 5f, "air" to 4f)
        "Air & Detail" -> mapOf("treble" to 4f, "air" to 6f)
        else -> emptyMap()
    }
    defaults.forEach { band ->
        val g = gains[band.id] ?: 0f
        viewModel.setToneParametricBand(band.id, g, band.frequencyHz, band.q, band.filterType, true)
    }
}

private fun kilo(hz: Float): String =
    if (hz % 1000f == 0f) "${(hz / 1000f).toInt()} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000f)

private fun db(value: Float): String = String.format(Locale.US, "%+.1f dB", value)
