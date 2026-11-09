package com.auraline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.AcousticRegion
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.EqBand
import com.auraline.core.domain.FactoryPreset
import com.auraline.core.domain.FactoryPresets
import com.auraline.core.domain.FrequencyGuides
import com.auraline.core.domain.LowFrequencyRoles
import com.auraline.core.domain.LowFrequencySources
import com.auraline.core.domain.LowSource
import com.auraline.core.domain.PresetCategory
import com.auraline.core.domain.PresetExporterImporter
import com.auraline.core.domain.SourceCategory
import com.auraline.core.domain.bandCenterHz
import com.auraline.core.domain.bandRoleFor
import com.auraline.core.domain.focusFor
import com.auraline.core.domain.generateEqualizerBands
import com.auraline.core.domain.getAcousticRegionFor
import com.auraline.core.domain.getBandAcousticInfo
import com.auraline.core.domain.ownerShortNameFor
import com.auraline.core.domain.resolveFocus
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraChip
import com.auraline.core.ui.components.AuraChipState
import com.auraline.core.ui.components.AuraDepth
import com.auraline.core.ui.components.AuraKnob
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.AuraSurface
import com.auraline.core.ui.components.BandFader
import com.auraline.core.ui.components.ResponseCurve
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.SpectrumBars
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.components.formatFrequency
import com.auraline.core.ui.components.formatGain
import com.auraline.core.ui.components.hapticFeedback
import com.auraline.core.ui.theme.AuraTheme

private val WindowSizes = listOf(8, 12, 25)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EqualizerScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val haptic = hapticFeedback()
    val scope = rememberCoroutineScope()

    var windowSize by remember { mutableStateOf(0) } // 0 = 8, 1 = 12, 2 = todas
    var pendingPresetName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf(false) }
    var exportJsonText by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var showAllPresetsDialog by remember { mutableStateOf(false) }
    var selectedPresetCategory by remember { mutableStateOf<PresetCategory?>(null) }
    var highlightedSourceKey by remember { mutableStateOf<String?>(null) }
    var inspectedBand by remember { mutableStateOf<EqBand?>(null) }
    var showGroupMacros by remember { mutableStateOf(false) }

    val bands = remember(state.bands, state.numBands) {
        state.bands.ifEmpty { generateEqualizerBands(state.numBands) }
    }
    val roleBandHz = remember(bands) {
        val centers = bands.indices.map { bandCenterHz(bands, it) }
        LowFrequencyRoles.associateWith { role ->
            centers.firstOrNull { center -> bandRoleFor(center)?.owner?.key == role.owner.key }
        }
    }
    val range = state.gainRangeDb.coerceIn(6f, 36f)
    val spectrum by com.auraline.core.audio.AuraLineSpectrum.levels.collectAsState()
    val selectedFactory = FactoryPresets.firstOrNull { it.name == state.presetName }
    val perWindow = WindowSizes[windowSize]
    val pages = remember(bands, perWindow) { bands.chunked(perWindow) }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    DisposableEffect(state.motorEnabled) {
        if (state.motorEnabled) {
            com.auraline.core.audio.AuraLineSpectrum.start()
        } else {
            com.auraline.core.audio.AuraLineSpectrum.stop()
        }
        onDispose {
            com.auraline.core.audio.AuraLineSpectrum.stop()
        }
    }

    LaunchedEffect(bands) {
        if (bands.isNotEmpty()) {
            com.auraline.core.audio.AuraLineSpectrum.setBandEdges(bands.map { it.frequencyHz }.toFloatArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 148.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // CADENA Y ESTADO DEL MOTOR
        EngineStatusCard(engine = engine, state = state, onToggleBypass = { viewModel.setBypass(!state.bypass) })

        // CURVA DE RESPUESTA Y ANALIZADOR ESPECTRAL
        AuraSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = AuraDepth.Raised
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Curva de respuesta",
                    trailing = state.presetName,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        haptic()
                        showGuide = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Guía acústica", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Rejilla de curvas
            ResponseCurve(bands = bands, gainRangeDb = range)
            Spacer(modifier = Modifier.height(8.dp))
            SpectrumBars(levels = spectrum, edges = bands.map { it.frequencyHz })
        }

        // BIBLIOTECA DE PREAJUSTES CATEGORIZADA
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Biblioteca de preajustes",
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        haptic()
                        showAllPresetsDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Explorar catálogo (${FactoryPresets.size})", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Categorías rápidas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SelectableChip(
                    text = "Todos",
                    selected = selectedPresetCategory == null,
                    onClick = { selectedPresetCategory = null }
                )
                PresetCategory.values().forEach { category ->
                    SelectableChip(
                        text = category.label,
                        selected = selectedPresetCategory == category,
                        onClick = { selectedPresetCategory = category }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chips de preajustes filtrados por categoría
            val filteredPresets = remember(selectedPresetCategory) {
                if (selectedPresetCategory == null) FactoryPresets else FactoryPresets.filter { it.category == selectedPresetCategory }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredPresets.forEach { preset ->
                    AuraChip(
                        label = preset.name,
                        state = if (state.presetName == preset.name) AuraChipState.Selected else AuraChipState.Default,
                        onClick = {
                            haptic()
                            if (preset.name == "Plano") viewModel.resetAll()
                            else viewModel.applyFactoryPreset(preset.name)
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        modifier = Modifier.height(40.dp)
                    )
                }
            }

            selectedFactory?.hint?.takeIf { it.isNotEmpty() }?.let { hint ->
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(text = "Ficha: ${selectedFactory.name}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = hint, color = colors.textSecondary, fontSize = 11.sp)
                    if (selectedFactory.tonalCurveSummary.isNotBlank()) {
                        Text(text = "Curva: ${selectedFactory.tonalCurveSummary}", color = colors.textPrimary, fontSize = 10.sp)
                    }
                }
            }

            if (presets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Mis preajustes personalizados", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { name ->
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        SelectableChip(
                            text = name,
                            selected = state.presetName == name,
                            onClick = {
                                haptic()
                                viewModel.applyPreset(name)
                            },
                            onLongClick = {
                                haptic()
                                showDeleteDialog = true
                            }
                        )

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                containerColor = colors.surface,
                                title = { Text("Eliminar preajuste", color = colors.textPrimary) },
                                text = { Text("¿Deseas eliminar el preajuste personalizado '$name'?", color = colors.textSecondary) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.deletePreset(name)
                                        showDeleteDialog = false
                                    }) { Text("Eliminar", color = colors.danger) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancelar", color = colors.textPrimary)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // CONTROL DE LAS 25 BANDAS
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Consola de ${state.numBands} bandas",
                    trailing = "±${range.toInt()} dB",
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        haptic()
                        showGroupMacros = !showGroupMacros
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text(
                        text = if (showGroupMacros) "Ocultar grupos" else "Grupos acústicos",
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Controles de macrogrupo de bandas
            AnimatedVisibility(visible = showGroupMacros) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ajuste por regiones espectrales",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    AcousticRegion.values().forEach { region ->
                        val regionColor = Color(region.colorHex)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(regionColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = region.label,
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    haptic()
                                    viewModel.setBandGroupGainOffset(region, -1f)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) { Text("-1", color = colors.textSecondary, fontSize = 10.sp) }
                            TextButton(
                                onClick = {
                                    haptic()
                                    viewModel.setBandGroupGainOffset(region, 1f)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(28.dp)
                            ) { Text("+1", color = colors.accent, fontSize = 10.sp) }
                            TextButton(
                                onClick = {
                                    haptic()
                                    viewModel.resetBandGroup(region)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("0", color = colors.warning, fontSize = 10.sp) }
                        }
                    }
                }
            }

            // Selectores de ventana y rango
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
                    text = "Todas (${bands.size})",
                    selected = windowSize == 2,
                    onClick = { windowSize = 2 }
                )
                Spacer(modifier = Modifier.width(8.dp))
                listOf(6f, 12f, 24f, 30f, 36f).forEach { value ->
                    SelectableChip(
                        text = "±${value.toInt()} dB",
                        selected = range.toInt() == value.toInt(),
                        onClick = { viewModel.setGainRange(value) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Faders de las bandas
            if (windowSize == 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    bands.forEachIndexed { index, band ->
                        val center = bandCenterHz(bands, index)
                        val region = getAcousticRegionFor(center)
                        val regionColor = Color(region.colorHex)
                        val isHighlighted = highlightedSourceKey?.let { key ->
                            focusFor(key)?.let { resolveFocus(bands, it) }?.any { it.first == index }
                        } ?: (band.gainDb != 0f)

                        BandFaderItem(
                            band = band,
                            center = center,
                            range = range,
                            highlighted = isHighlighted,
                            regionColor = regionColor,
                            onGainChange = { viewModel.setBandGain(band.id, it) },
                            onInspect = { inspectedBand = band }
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
                        highlightedSourceKey = highlightedSourceKey,
                        onGainChange = viewModel::setBandGain,
                        onInspectBand = { inspectedBand = it }
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
                            append("Página ${pagerState.currentPage + 1}/${pages.size.coerceAtLeast(1)}")
                        },
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Doble toque: 0 dB · Toque largo: inspeccionar",
                    color = colors.textSecondary,
                    fontSize = 10.sp
                )
                TextButton(
                    onClick = {
                        haptic()
                        viewModel.resetAll()
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Reiniciar 25 bandas", color = colors.warning, fontSize = 11.sp)
                }
            }
        }

        // FUENTES GRAVES (15 FUENTES)
        LowSourcesCard(
            bands = bands,
            range = range,
            highlightedKey = highlightedSourceKey,
            onHighlightSource = { key ->
                highlightedSourceKey = if (highlightedSourceKey == key) null else key
            },
            onBandGain = { bandId, gain -> viewModel.setBandGain(bandId, gain) },
            onFocus = { viewModel.focusLowSource(it) }
        )

        // PREAMPLIFICACIÓN Y COMPARACIÓN A/B
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    text = "Preamplificación de entrada",
                    trailing = "${formatGain(state.preampDb)} dB",
                    modifier = Modifier.weight(1f)
                )
                SelectableChip(
                    text = if (state.bypass) "A/B Comparación (Plana)" else "A/B Comparación",
                    selected = state.bypass,
                    onClick = {
                        haptic()
                        viewModel.setBypass(!state.bypass)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AuraKnob(
                    value = state.preampDb,
                    onValueChange = { viewModel.setPreamp(it) },
                    valueRange = -15f..15f,
                    label = "Preamplificador",
                    valueText = "${formatGain(state.preampDb)} dB",
                    diameter = 132.dp
                )
            }
            if (state.autoPreamp) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "AutoPreamp activo: protege automáticamente contra recortes digitales y clipping.",
                    color = colors.positive,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.bypass) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Comparación A/B activa: escuchando la señal plana sin curvas.",
                    color = colors.warning,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // HERRAMIENTAS DE GESTIÓN DE PREAJUSTES (Iconos)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableChip(
                text = "",
                selected = false,
                icon = Icons.Rounded.RestartAlt,
                onClick = {
                    haptic()
                    viewModel.resetAll()
                },
                modifier = Modifier.weight(1f)
            )
            SelectableChip(
                text = "",
                selected = false,
                icon = Icons.Rounded.ImportExport,
                onClick = {
                    haptic()
                    exportJsonText = PresetExporterImporter.exportPresetToJson(state.presetName, state)
                    showExportImportDialog = true
                },
                modifier = Modifier.weight(1f)
            )
            SelectableChip(
                text = "",
                selected = true,
                icon = Icons.Rounded.BookmarkAdd,
                onClick = {
                    haptic()
                    pendingPresetName = state.presetName
                    showSaveDialog = true
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    // DIÁLOGOS Y PANELES EMERGENTES

    // Diálogo de guardar preajuste
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = colors.surface,
            title = { Text("Guardar preajuste personalizado", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Guarda la curva actual de 25 bandas, preamp y parámetros:", color = colors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = pendingPresetName,
                        onValueChange = { pendingPresetName = it },
                        singleLine = true,
                        label = { Text("Nombre del preajuste") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePreset(pendingPresetName)
                    showSaveDialog = false
                }) { Text("Guardar", color = colors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancelar", color = colors.textSecondary)
                }
            }
        )
    }

    // Diálogo de Exportar/Importar JSON
    if (showExportImportDialog) {
        AlertDialog(
            onDismissRequest = { showExportImportDialog = false },
            containerColor = colors.surfaceElevated,
            title = { Text("Exportación / Importación JSON", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Formato de preajuste JSON validado de AuraLine:", color = colors.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = exportJsonText,
                        onValueChange = { exportJsonText = it },
                        label = { Text("JSON exportado / para importar") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                    importError?.let { err ->
                        Text(text = err, color = colors.danger, fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            runCatching {
                                val userPreset = PresetExporterImporter.importPresetFromJson(exportJsonText)
                                viewModel.savePreset(userPreset.name)
                                viewModel.applyPreset(userPreset.name)
                                importError = null
                                showExportImportDialog = false
                            }.onFailure { ex ->
                                importError = "Error al importar JSON: ${ex.message}"
                            }
                        }) {
                            Text("Importar JSON", color = colors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportImportDialog = false }) {
                    Text("Cerrar", color = colors.accent)
                }
            }
        )
    }

    // Diálogo de inspección individual de banda
    inspectedBand?.let { band ->
        val center = bandCenterHz(bands, bands.indexOf(band).coerceAtLeast(0))
        val acousticInfo = getBandAcousticInfo(center)
        val regionColor = Color(acousticInfo.region.colorHex)

        AlertDialog(
            onDismissRequest = { inspectedBand = null },
            containerColor = colors.surfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(regionColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Banda #${band.id + 1} · ${formatFrequency(center)} Hz",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Región: ${acousticInfo.region.label}", color = regionColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Ganancia: ${formatGain(band.gainDb)} dB", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    AuraSliderRow(
                        label = "Ganancia de banda",
                        valueText = "${formatGain(band.gainDb)} dB",
                        value = band.gainDb,
                        valueRange = -range..range,
                        onValueChange = { viewModel.setBandGain(band.id, it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { step ->
                            TextButton(onClick = {
                                viewModel.setBandGain(band.id, (band.gainDb + step).coerceIn(-range, range))
                            }) {
                                Text(if (step > 0) "+$step" else "$step", color = colors.accent, fontSize = 11.sp)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Instrumento principal:", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = acousticInfo.primaryInstrument, color = colors.accent, fontSize = 12.sp)
                        Text(text = "Comparten rango:", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = acousticInfo.secondaryInstruments.joinToString(", "), color = colors.textSecondary, fontSize = 11.sp)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Efecto al aumentar (+):", color = colors.positive, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = acousticInfo.boostEffect, color = colors.textSecondary, fontSize = 11.sp)
                        Text(text = "Efecto al atenuar (-):", color = colors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = acousticInfo.cutEffect, color = colors.textSecondary, fontSize = 11.sp)
                        Text(text = "Consejo de ecualización:", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = acousticInfo.recommendation, color = colors.textPrimary, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetBand(band.id)
                }) {
                    Text("Reiniciar a 0 dB", color = colors.warning)
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedBand = null }) {
                    Text("Cerrar", color = colors.accent)
                }
            }
        )
    }

    // Guía acústica educativa
    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            containerColor = colors.surfaceElevated,
            title = { Text("Guía acústica de frecuencias", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text(
                            text = "Regiones del espectro audible (20 Hz - 20 kHz)",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    items(AcousticRegion.values()) { reg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(reg.colorHex), CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = reg.label, color = Color(reg.colorHex), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "(${reg.rangeHz.start.toInt()} - ${reg.rangeHz.endInclusive.toInt()} Hz)", color = colors.textSecondary, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = reg.description, color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Guía rápida por instrumento",
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    items(FrequencyGuides) { guide ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(text = guide.label, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = guide.uses, color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) {
                    Text("Cerrar", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Explorador completo de catálogo de preajustes
    if (showAllPresetsDialog) {
        AlertDialog(
            onDismissRequest = { showAllPresetsDialog = false },
            containerColor = colors.surfaceElevated,
            title = { Text("Catálogo completo de preajustes", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetCategory.values().forEach { cat ->
                        val categoryPresets = FactoryPresets.filter { it.category == cat }
                        if (categoryPresets.isNotEmpty()) {
                            Text(text = cat.label, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            categoryPresets.forEach { preset ->
                                val isSelected = state.presetName == preset.name
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.surface)
                                        .border(1.dp, if (isSelected) colors.accent else colors.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic()
                                            if (preset.name == "Plano") viewModel.resetAll()
                                            else viewModel.applyFactoryPreset(preset.name)
                                            showAllPresetsDialog = false
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = preset.name, color = if (isSelected) colors.accent else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = preset.contentType, color = colors.textSecondary, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = preset.hint, color = colors.textSecondary, fontSize = 11.sp)
                                    Text(text = "Recomendación: ${preset.recommendedUse}", color = colors.textSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllPresetsDialog = false }) {
                    Text("Cerrar", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun BandFaderItem(
    band: EqBand,
    center: Float,
    range: Float,
    highlighted: Boolean,
    regionColor: Color,
    onGainChange: (Float) -> Unit,
    onInspect: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BandFader(
            value = band.gainDb,
            onValueChange = onGainChange,
            valueRange = -range..range,
            height = 160.dp,
            label = formatFrequency(center),
            valueText = formatGain(band.gainDb),
            highlighted = highlighted,
            subtitle = ownerShortNameFor(center) ?: ""
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .background(regionColor, CircleShape)
                .clickable { onInspect() }
        )
    }
}

@Composable
private fun BandPage(
    bands: List<EqBand>,
    chunk: List<EqBand>,
    range: Float,
    highlightedSourceKey: String?,
    onGainChange: (Int, Float) -> Unit,
    onInspectBand: (EqBand) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val count = chunk.size.coerceAtLeast(1)
        val wide = maxWidth >= 560.dp
        val faderHeight = if (wide) 196.dp else 148.dp
        val faderWidth = ((maxWidth - 8.dp) / count).coerceIn(18.dp, if (wide) 56.dp else 40.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(faderHeight + 72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            chunk.forEach { band ->
                val index = bands.indexOf(band)
                val center = bandCenterHz(bands, index.coerceAtLeast(0))
                val region = getAcousticRegionFor(center)
                val regionColor = Color(region.colorHex)
                val isHighlighted = highlightedSourceKey?.let { key ->
                    focusFor(key)?.let { resolveFocus(bands, it) }?.any { it.first == index }
                } ?: (band.gainDb != 0f)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BandFader(
                        value = band.gainDb,
                        onValueChange = { onGainChange(band.id, it) },
                        valueRange = -range..range,
                        height = faderHeight,
                        label = formatFrequency(center),
                        valueText = formatGain(band.gainDb),
                        highlighted = isHighlighted,
                        faderWidth = faderWidth,
                        subtitle = ownerShortNameFor(center) ?: ""
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(7.dp)
                            .background(regionColor, CircleShape)
                            .clickable { onInspectBand(band) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LowSourcesCard(
    bands: List<EqBand>,
    range: Float,
    highlightedKey: String?,
    onHighlightSource: (String) -> Unit,
    onBandGain: (Int, Float) -> Unit,
    onFocus: (String) -> Unit
) {
    val colors = AuraTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<SourceCategory?>(null) }
    val haptic = hapticFeedback()

    AuraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic()
                    expanded = !expanded
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FUENTES GRAVES",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    haptic()
                    expanded = !expanded
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = if (expanded) "Ocultar" else "Ver 15 fuentes (${LowFrequencySources.size})",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Herramienta especializada para moldear el sonido de instrumentos de baja frecuencia.",
            color = colors.textSecondary,
            fontSize = 11.sp
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filtros de categoría de fuentes graves
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectableChip(
                        text = "Todas",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    SourceCategory.values().forEach { cat ->
                        SelectableChip(
                            text = cat.label,
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                val sourcesToDisplay = remember(selectedCategory) {
                    if (selectedCategory == null) LowFrequencySources else LowFrequencySources.filter { it.category == selectedCategory }
                }

                sourcesToDisplay.forEach { source ->
                    val steps = focusFor(source.key)?.let { resolveFocus(bands, it) }.orEmpty()
                    val primaryStep = steps.firstOrNull()
                    val primaryIndex = primaryStep?.first
                    val role = LowFrequencyRoles.firstOrNull { it.owner.key == source.key }
                    val shared = role?.companions?.map { it.name }.orEmpty()
                    val rangeScale = range / 12f
                    val targetDelta = primaryStep?.let { it.second * rangeScale } ?: 0f
                    val gain = primaryIndex?.let { bands.getOrNull(it)?.gainDb } ?: 0f
                    val applied = primaryStep != null && kotlin.math.abs(gain) >= kotlin.math.abs(targetDelta) - 0.5f &&
                        (kotlin.math.sign(gain) == kotlin.math.sign(targetDelta) || targetDelta == 0f)
                    val isHighlighted = highlightedKey == source.key

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isHighlighted) colors.accent.copy(alpha = 0.12f) else colors.surface)
                            .border(1.dp, if (isHighlighted) colors.accent else colors.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(categoryColor(source.category), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.name,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append("${source.fundamentalHz.first}–${source.fundamentalHz.last} Hz")
                                        if (shared.isNotEmpty()) append(" · con ${shared.joinToString(", ")}")
                                    },
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            SelectableChip(
                                text = if (isHighlighted) "Ocultar resaltado" else "Resaltar bandas",
                                selected = isHighlighted,
                                onClick = { onHighlightSource(source.key) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SelectableChip(
                                text = if (applied) "Afinación aplicada" else "+ Afinación",
                                selected = applied,
                                onClick = { onFocus(source.key) }
                            )
                        }

                        Text(text = source.detail, color = colors.textSecondary, fontSize = 11.sp)
                        Text(text = "Consejo: ${source.tip}", color = colors.accent, fontSize = 10.sp)

                        if (primaryIndex != null) {
                            val primaryBand = bands.getOrNull(primaryIndex)
                            if (primaryBand != null) {
                                AuraSliderRow(
                                    label = "Banda principal ${source.shortName} (${formatFrequency(bandCenterHz(bands, primaryIndex))} Hz)",
                                    valueText = formatGain(primaryBand.gainDb),
                                    value = primaryBand.gainDb,
                                    valueRange = -range..range,
                                    onValueChange = { onBandGain(primaryBand.id, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: SourceCategory): Color {
    val colors = AuraTheme.colors
    return when (category) {
        SourceCategory.PERCUSION -> colors.warning
        SourceCategory.ELECTRONIC -> colors.accent
        SourceCategory.STRING -> colors.positive
        SourceCategory.WIND -> colors.textSecondary
    }
}

@Composable
fun EngineStatusCard(
    engine: EngineStatus,
    state: com.auraline.core.domain.EqualizerState,
    onToggleBypass: () -> Unit
) {
    val colors = AuraTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val running = engine.running

    AuraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cadena de procesado de audio",
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
                                    CircleShape
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (expanded) "Ocultar detalle" else "Ver estado del motor",
                    color = colors.accent,
                    fontSize = 12.sp
                )
            }
            SelectableChip(
                text = if (state.bypass) "Bypass activo" else "Bypass",
                selected = state.bypass,
                onClick = onToggleBypass
            )
        }
    }
}
