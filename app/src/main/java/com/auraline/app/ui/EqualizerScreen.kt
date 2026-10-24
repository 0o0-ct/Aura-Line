package com.auraline.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.audio.AuraLineSpectrum
import com.auraline.core.domain.EqBand
import com.auraline.core.domain.FactoryPresets
import com.auraline.core.domain.FrequencyGuides
import com.auraline.core.domain.bandCenterHz
import com.auraline.core.domain.generateEqualizerBands
import com.auraline.core.ui.components.FrequencyCurveView
import com.auraline.core.ui.components.PixelCard
import com.auraline.core.ui.components.PixelChip
import com.auraline.core.ui.components.PixelSectionTitle
import com.auraline.core.ui.components.PixelSliderRow
import com.auraline.core.ui.components.PixelSpectrumBars
import com.auraline.core.ui.components.PixelVerticalSlider
import com.auraline.core.ui.theme.LocalMonoPalette
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EqualizerScreen(viewModel: AuraLineViewModel) {
    val palette = LocalMonoPalette.current
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val spectrum by AuraLineSpectrum.levels.collectAsState()

    var windowIndex by remember { mutableIntStateOf(0) }
    var showGuide by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("") }
    var statusExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bands = remember(state.bands, state.numBands) {
        state.bands.ifEmpty { generateEqualizerBands(state.numBands) }
    }
    val range = state.gainRangeDb.coerceIn(6f, 24f)
    val perWindow = listOf(8, 12, bands.size.coerceAtLeast(1))[windowIndex]
    val pages = remember(bands, perWindow) { bands.chunked(perWindow) }
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val selectedFactory = FactoryPresets.firstOrNull { it.name == state.presetName }
    val strongest = bands.maxByOrNull { abs(it.gainDb) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Estado real del motor ---
        PixelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cadena de procesado",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (engine.running) {
                            "${engine.activeStages}/${engine.stages.size} etapas · ${engine.sessions.size} sesión(es)"
                        } else {
                            "Inactiva: activa el motor para procesar la salida"
                        },
                        color = palette.secondary,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (engine.running) "EN VIVO" else "INACTIVA",
                        color = if (engine.running) palette.primary else palette.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (statusExpanded) {
                Spacer(Modifier.height(8.dp))
                engine.stages.forEach { stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .then(Modifier)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .padding(0.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stage.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (stage.active) "activa" else "—",
                            color = if (stage.active) palette.primary else palette.secondary,
                            fontSize = 11.sp
                        )
                    }
                }
                engine.lastError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
            TextButton(onClick = { statusExpanded = !statusExpanded }) {
                Text(
                    text = if (statusExpanded) "Ocultar detalle" else "Ver estado real del motor",
                    color = palette.primary,
                    fontSize = 12.sp
                )
            }
        }

        // --- Curva + espectro ---
        PixelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSectionTitle(
                    text = "Curva de respuesta",
                    trailing = state.presetName,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showGuide = true }) {
                    Text("Guía", color = palette.primary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            FrequencyCurveView(bands = bands, gainRangeDb = range)
            Spacer(Modifier.height(8.dp))
            PixelSpectrumBars(levels = spectrum, edges = bands.map { it.frequencyHz })
        }

        // --- Preajustes de fábrica y de usuario ---
        PixelCard {
            PixelSectionTitle(text = "Preajustes", trailing = "según tablas de ecualización")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FactoryPresets.forEach { preset ->
                    PixelChip(
                        text = preset.name,
                        selected = state.presetName == preset.name,
                        onClick = {
                            if (preset.name == "Plano") viewModel.resetAll()
                            else viewModel.applyFactoryPreset(preset.name)
                            scope.launch { pager.animateScrollToPage(0) }
                        }
                    )
                }
            }
            selectedFactory?.hint?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, color = palette.secondary, fontSize = 11.sp)
            }
            if (strongest != null && abs(strongest.gainDb) >= 0.5f) {
                Text(
                    text = "Aplicado: ${formatGain(strongest.gainDb)} dB en " +
                        "${formatFrequency(bandCenterHz(bands, bands.indexOf(strongest)))} Hz",
                    color = palette.primary,
                    fontSize = 11.sp
                )
            }
            if (!state.motorEnabled) {
                Text(
                    text = "El motor está apagado: actívalo para escuchar el preajuste.",
                    color = palette.secondary,
                    fontSize = 11.sp
                )
            }
            if (presets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { name ->
                        PixelChip(
                            text = name,
                            selected = state.presetName == name,
                            onClick = { viewModel.applyPreset(name) }
                        )
                    }
                }
            }
            TextButton(onClick = {
                pendingName = state.presetName
                showSave = true
            }) {
                Text("Guardar preajuste", color = palette.primary, fontSize = 12.sp)
            }
        }

        // --- Bandas ---
        PixelCard {
            PixelSectionTitle(
                text = "Bandas",
                trailing = "${state.numBands} · ±${range.toInt()} dB"
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PixelChip(text = "Ventana 8", selected = windowIndex == 0, onClick = { windowIndex = 0 })
                PixelChip(text = "12", selected = windowIndex == 1, onClick = { windowIndex = 1 })
                PixelChip(text = "Todas", selected = windowIndex == 2, onClick = { windowIndex = 2 })
                Spacer(Modifier.width(8.dp))
                listOf(6f, 12f, 24f).forEach { value ->
                    PixelChip(
                        text = "±${value.toInt()}",
                        selected = range.toInt() == value.toInt(),
                        onClick = { viewModel.setGainRange(value) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (windowIndex == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    bands.forEachIndexed { index, band ->
                        PixelVerticalSlider(
                            value = band.gainDb,
                            onValueChange = { viewModel.setBandGain(band.id, it) },
                            valueRange = -range..range,
                            height = 160.dp,
                            width = 30.dp,
                            label = formatFrequency(bandCenterHz(bands, index)),
                            valueText = formatGain(band.gainDb)
                        )
                    }
                }
            } else {
                HorizontalPager(state = pager) { page ->
                    BandPagePixel(
                        bands = bands,
                        chunk = pages.getOrElse(page) { emptyList() },
                        range = range,
                        onGainChange = viewModel::setBandGain
                    )
                }
                Spacer(Modifier.height(6.dp))
                val current = pages.getOrNull(pager.currentPage).orEmpty()
                val firstIndex = current.firstOrNull()?.let { bands.indexOf(it) } ?: 0
                val lastIndex = current.lastOrNull()?.let { bands.indexOf(it) } ?: 0
                Text(
                    text = if (current.isEmpty()) {
                        "${pager.currentPage + 1}/${pages.size.coerceAtLeast(1)}"
                    } else {
                        "${formatFrequency(bandCenterHz(bands, firstIndex))} – " +
                            "${formatFrequency(bandCenterHz(bands, lastIndex))} Hz · " +
                            "${pager.currentPage + 1}/${pages.size.coerceAtLeast(1)}"
                    },
                    color = palette.secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Arrastra el pulsador de cada banda · doble toque lo devuelve a 0 dB",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }

        // --- Entrada ---
        PixelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelSectionTitle(
                    text = "Ganancia de entrada",
                    trailing = "${formatGain(state.preampDb)} dB",
                    modifier = Modifier.weight(1f)
                )
                PixelChip(
                    text = if (state.bypass) "A/B activo" else "A/B",
                    selected = state.bypass,
                    onClick = { viewModel.setBypass(!state.bypass) }
                )
            }
            PixelSliderRow(
                label = "Preamp",
                valueText = "${formatGain(state.preampDb)} dB",
                value = state.preampDb,
                valueRange = -15f..15f,
                onValueChange = { viewModel.setPreamp(it) }
            )
            if (state.bypass) {
                Text(
                    text = "Estás escuchando la cadena plana para comparar.",
                    color = palette.secondary,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = { viewModel.resetAll() }) {
                Text("Restablecer ecualizador", color = palette.secondary)
            }
        }
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = palette.surface,
            title = { Text("Guardar preajuste", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = pendingName,
                    onValueChange = { pendingName = it },
                    singleLine = true,
                    label = { Text("Nombre") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePreset(pendingName)
                    showSave = false
                }) { Text("Guardar", color = palette.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) {
                    Text("Cancelar", color = palette.secondary)
                }
            }
        )
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            containerColor = palette.surface,
            title = { Text("Guía de frecuencias", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(FrequencyGuides) { guide ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = guide.label,
                                color = palette.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = guide.uses, color = palette.secondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) {
                    Text("Cerrar", color = palette.primary)
                }
            }
        )
    }
}

@Composable
private fun BandPagePixel(
    bands: List<EqBand>,
    chunk: List<EqBand>,
    range: Float,
    onGainChange: (Int, Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(215.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        chunk.forEach { band ->
            val index = bands.indexOf(band).coerceAtLeast(0)
            PixelVerticalSlider(
                value = band.gainDb,
                onValueChange = { onGainChange(band.id, it) },
                valueRange = -range..range,
                height = 170.dp,
                width = 30.dp,
                label = formatFrequency(bandCenterHz(bands, index)),
                valueText = formatGain(band.gainDb)
            )
        }
    }
}

private fun formatFrequency(hz: Float): String = when {
    hz >= 1000f -> {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    }
    else -> hz.toInt().toString()
}

private fun formatGain(db: Float): String {
    val rounded = (db * 10).toInt() / 10f
    return if (rounded > 0f) "+$rounded" else "$rounded"
}
