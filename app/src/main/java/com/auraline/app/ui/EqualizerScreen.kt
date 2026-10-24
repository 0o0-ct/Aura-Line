package com.auraline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.EqBand
import com.auraline.core.domain.FactoryPresets
import com.auraline.core.domain.FrequencyGuides
import com.auraline.core.domain.bandCenterHz
import com.auraline.core.domain.generateEqualizerBands
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.BandFader
import com.auraline.core.ui.components.ResponseCurve
import com.auraline.core.ui.components.SpectrumBars
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.components.formatFrequency
import com.auraline.core.ui.components.formatGain
import com.auraline.core.ui.theme.AuraTheme

private val WindowSizes = listOf(8, 12, 25)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EqualizerScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val presets by viewModel.presets.collectAsState()

    var windowSize by remember { mutableStateOf(0) } // índice en WindowSizes; 0 = todas
    var pendingPresetName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val bands = remember(state.bands, state.numBands) {
        state.bands.ifEmpty { generateEqualizerBands(state.numBands) }
    }
    val range = state.gainRangeDb.coerceIn(6f, 24f)
    val spectrum by com.auraline.core.audio.AuraLineSpectrum.levels.collectAsState()
    val scope = rememberCoroutineScope()
    val selectedFactory = FactoryPresets.firstOrNull { it.name == state.presetName }
    val perWindow = WindowSizes[windowSize]
    val pages = remember(bands, perWindow) { bands.chunked(perWindow) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Curva de respuesta",
                    trailing = state.presetName,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showGuide = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Guía", color = colors.accent, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            ResponseCurve(bands = bands, gainRangeDb = range)
            Spacer(modifier = Modifier.height(8.dp))
            SpectrumBars(levels = spectrum)
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Preajustes", trailing = "según tablas de ecualización")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FactoryPresets.forEach { preset ->
                    SelectableChip(
                        text = preset.name,
                        selected = state.presetName == preset.name,
                        onClick = {
                            if (preset.name == "Plano") viewModel.resetAll()
                            else viewModel.applyFactoryPreset(preset.name)
                            scope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                }
            }
            selectedFactory?.hint?.takeIf { it.isNotEmpty() }?.let { hint ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = hint, color = colors.textSecondary, fontSize = 11.sp)
            }
            val strongest = bands.maxByOrNull { kotlin.math.abs(it.gainDb) }
            if (strongest != null && kotlin.math.abs(strongest.gainDb) >= 0.5f) {
                val index = bands.indexOf(strongest)
                Text(
                    text = "Aplicado: ${formatGain(strongest.gainDb)} dB en " +
                        "${formatFrequency(bandCenterHz(bands, index))} Hz",
                    color = colors.accent,
                    fontSize = 11.sp
                )
            }
            if (!state.motorEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "El motor está apagado: actívalo para escuchar el preajuste.",
                    color = colors.warning,
                    fontSize = 11.sp
                )
            }
            if (presets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { name ->
                        SelectableChip(
                            text = name,
                            selected = state.presetName == name,
                            onClick = { viewModel.applyPreset(name) }
                        )
                    }
                }
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Bandas",
                trailing = "${state.numBands} · ±${range.toInt()} dB"
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SelectableChip(
                    text = "Ventana 8",
                    selected = windowSize == 0,
                    onClick = { windowSize = 0 }
                )
                SelectableChip(
                    text = "12",
                    selected = windowSize == 1,
                    onClick = { windowSize = 1 }
                )
                SelectableChip(
                    text = "Todas",
                    selected = windowSize == 2,
                    onClick = { windowSize = 2 }
                )
                Spacer(modifier = Modifier.width(10.dp))
                listOf(6f, 12f, 24f).forEach { value ->
                    SelectableChip(
                        text = "±${value.toInt()}",
                        selected = range.toInt() == value.toInt(),
                        onClick = { viewModel.setGainRange(value) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (windowSize == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    bands.forEachIndexed { index, band ->
                        BandFader(
                            value = band.gainDb,
                            onValueChange = { viewModel.setBandGain(band.id, it) },
                            valueRange = -range..range,
                            height = 160.dp,
                            label = formatFrequency(bandCenterHz(bands, index)),
                            valueText = formatGain(band.gainDb),
                            highlighted = band.gainDb != 0f
                        )
                    }
                }
            } else {
                HorizontalPager(state = pagerState) { page ->
                    val chunk = pages.getOrElse(page) { emptyList() }
                    BandPage(
                        bands = bands,
                        chunk = chunk,
                        range = range,
                        onGainChange = viewModel::setBandGain
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val first = pages.getOrNull(pagerState.currentPage)?.firstOrNull()?.let { band ->
                        bands.indexOf(band).let { bandCenterHz(bands, it) }
                    }
                    val last = pages.getOrNull(pagerState.currentPage)?.lastOrNull()?.let { band ->
                        bands.indexOf(band).let { bandCenterHz(bands, it) }
                    }
                    Text(
                        text = buildString {
                            if (first != null && last != null) {
                                append("${formatFrequency(first)} – ${formatFrequency(last)} Hz · ")
                            }
                            append("${pagerState.currentPage + 1}/${pages.size.coerceAtLeast(1)}")
                        },
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Doble toque en un fader para volver a 0 dB",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Ganancia de entrada",
                    trailing = "${formatGain(state.preampDb)} dB",
                    modifier = Modifier.weight(1f)
                )
                SelectableChip(
                    text = if (state.bypass) "A/B activo" else "A/B",
                    selected = state.bypass,
                    onClick = { viewModel.setBypass(!state.bypass) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            AuraSliderRow(
                label = "Nivel de preamplificación",
                valueText = "${formatGain(state.preampDb)} dB",
                value = state.preampDb,
                valueRange = -15f..15f,
                onValueChange = { viewModel.setPreamp(it) },
                enabled = state.motorEnabled
            )
            if (state.bypass) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estás escuchando la curva plana para comparar. Pulsa A/B para volver.",
                    color = colors.warning,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableChip(
                text = "Restablecer",
                selected = false,
                onClick = { viewModel.resetAll() }
            )
            SelectableChip(
                text = "Guardar preajuste",
                selected = true,
                onClick = {
                    pendingPresetName = state.presetName
                    showSaveDialog = true
                }
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = colors.surface,
            title = { Text("Guardar preajuste", color = colors.textPrimary) },
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
                }) { Text("Guardar", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = colors.textSecondary)
                }
            }
        )
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            containerColor = colors.surface,
            title = { Text("Guía de frecuencias", color = colors.textPrimary) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(FrequencyGuides) { guide ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = guide.label,
                                color = colors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = guide.uses, color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) {
                    Text("Cerrar", color = colors.accent)
                }
            }
        )
    }
}

@Composable
private fun BandPage(
    bands: List<EqBand>,
    chunk: List<EqBand>,
    range: Float,
    onGainChange: (Int, Float) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(215.dp)
    ) {
        val count = chunk.size.coerceAtLeast(1)
        // El ancho se reparte entre las bandas visibles: con 12 no caben a 34 dp.
        val faderWidth = ((maxWidth - 8.dp) / count).coerceIn(18.dp, 40.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            chunk.forEach { band ->
                val index = bands.indexOf(band)
                BandFader(
                    value = band.gainDb,
                    onValueChange = { onGainChange(band.id, it) },
                    valueRange = -range..range,
                    height = 148.dp,
                    label = formatFrequency(bandCenterHz(bands, index.coerceAtLeast(0))),
                    valueText = formatGain(band.gainDb),
                    highlighted = band.gainDb != 0f,
                    faderWidth = faderWidth
                )
            }
        }
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
