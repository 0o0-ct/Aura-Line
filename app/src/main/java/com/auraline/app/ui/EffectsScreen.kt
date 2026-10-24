package com.auraline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.ReverbParams
import com.auraline.core.ui.components.NeumorphicChip
import com.auraline.core.ui.components.NeumorphicKnob
import com.auraline.core.ui.components.glass
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.SurfaceDark
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary
import java.util.Locale

data class AcousticPresetItem(
    val name: String,
    val params: ReverbParams
)

val acousticPresetsList = listOf(
    AcousticPresetItem("Auditorio", ReverbParams(damping = 0.35f, filter = 0.70f, decay = 0.80f, preDelay = 0.08f, predelayMix = 0.60f, size = 0.85f, mix = 0.50f)),
    AcousticPresetItem("Eco", ReverbParams(damping = 0.10f, filter = 0.20f, decay = 0.90f, preDelay = 0.15f, predelayMix = 0.80f, size = 0.60f, mix = 0.60f)),
    AcousticPresetItem("Escenario", ReverbParams(damping = 0.40f, filter = 0.60f, decay = 0.60f, preDelay = 0.05f, predelayMix = 0.50f, size = 0.70f, mix = 0.40f)),
    AcousticPresetItem("Estadio", ReverbParams(damping = 0.20f, filter = 0.80f, decay = 0.95f, preDelay = 0.12f, predelayMix = 0.70f, size = 0.95f, mix = 0.65f)),
    AcousticPresetItem("Estudio", ReverbParams(damping = 0.50f, filter = 0.50f, decay = 0.30f, preDelay = 0.02f, predelayMix = 0.30f, size = 0.30f, mix = 0.25f)),
    AcousticPresetItem("Gran salón", ReverbParams(damping = 0.35f, filter = 0.65f, decay = 0.75f, preDelay = 0.06f, predelayMix = 0.55f, size = 0.80f, mix = 0.45f)),
    AcousticPresetItem("Ligera reverberación", ReverbParams(damping = 0.70f, filter = 0.40f, decay = 0.20f, preDelay = 0.01f, predelayMix = 0.20f, size = 0.20f, mix = 0.15f)),
    AcousticPresetItem("Sala pequeña", ReverbParams(damping = 0.60f, filter = 0.30f, decay = 0.25f, preDelay = 0.015f, predelayMix = 0.25f, size = 0.25f, mix = 0.20f))
)

@Composable
fun EffectsScreen(
    viewModel: AuraLineViewModel,
    modifier: Modifier = Modifier
) {
    val state: EqualizerState by viewModel.state.collectAsState()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var selectedPresetChip by remember { mutableIntStateOf(1) }
    var activePresetName by remember { mutableStateOf("Estudio") }
    var showPresetDialog by remember { mutableStateOf(false) }

    val reverb = state.reverb
    val comp = state.compressor
    val cfg = LocalConfiguration.current
    val knob = (cfg.screenWidthDp.dp * 0.21f).coerceIn(60.dp, 80.dp)
    val bigKnob = (cfg.screenWidthDp.dp * 0.24f).coerceIn(80.dp, 98.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .glass(cornerRadius = 20.dp, alpha = 0.06f)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // --- HEADER: Switch ON/OFF ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.effectsEnabled) "Efectos: ACTIVOS" else "Efectos: APAGADOS",
                color = if (state.effectsEnabled) CyanNeon else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = state.effectsEnabled,
                onCheckedChange = { viewModel.setEffectsEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon)
            )
        }

        // --- SUB-TABS: Reverb / Compresor / Otros ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val subTabs = listOf("Reverb", "Compresor", "Otros")
            subTabs.forEachIndexed { index, title ->
                NeumorphicChip(
                    text = title,
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // --- MAIN PARAMETERS AREA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (state.effectsEnabled) 1f else 0.45f)
        ) {
            if (selectedSubTab == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NeumorphicKnob(value = reverb.damping, onValueChange = { viewModel.setReverb(reverb.copy(damping = it)) }, valueRange = 0f..1f, size = knob, label = "Amortiguación", valueText = String.format(Locale.US, "%.2f", reverb.damping))
                        NeumorphicKnob(value = reverb.filter, onValueChange = { viewModel.setReverb(reverb.copy(filter = it)) }, valueRange = 0f..1f, size = knob, label = "Filtro", valueText = String.format(Locale.US, "%.2f", reverb.filter))
                        NeumorphicKnob(value = reverb.decay, onValueChange = { viewModel.setReverb(reverb.copy(decay = it)) }, valueRange = 0f..1f, size = knob, label = "Fundido", valueText = String.format(Locale.US, "%.2f", reverb.decay))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NeumorphicKnob(value = reverb.preDelay, onValueChange = { viewModel.setReverb(reverb.copy(preDelay = it)) }, valueRange = 0f..1f, size = knob, label = "Preretardo", valueText = String.format(Locale.US, "%.2f", reverb.preDelay))
                        NeumorphicKnob(value = reverb.predelayMix, onValueChange = { viewModel.setReverb(reverb.copy(predelayMix = it)) }, valueRange = 0f..1f, size = knob, label = "Mezcla preretar...", valueText = String.format(Locale.US, "%.2f", reverb.predelayMix))
                        NeumorphicKnob(value = reverb.size, onValueChange = { viewModel.setReverb(reverb.copy(size = it)) }, valueRange = 0f..1f, size = knob, label = "Tamaño", valueText = String.format(Locale.US, "%.2f", reverb.size))
                    }
                }
            } else if (selectedSubTab == 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NeumorphicKnob(value = comp.threshold, onValueChange = { viewModel.setCompressor(comp.copy(threshold = it)) }, valueRange = -60f..0f, size = knob, label = "Umbral", valueText = String.format(Locale.US, "%.1f", comp.threshold))
                        NeumorphicKnob(value = comp.ratio, onValueChange = { viewModel.setCompressor(comp.copy(ratio = it)) }, valueRange = 1f..20f, size = knob, label = "Ratio", valueText = String.format(Locale.US, "%.1f", comp.ratio))
                        NeumorphicKnob(value = comp.attackMs, onValueChange = { viewModel.setCompressor(comp.copy(attackMs = it)) }, valueRange = 1f..100f, size = knob, label = "Ataque", valueText = String.format(Locale.US, "%.0f", comp.attackMs))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NeumorphicKnob(value = comp.releaseMs, onValueChange = { viewModel.setCompressor(comp.copy(releaseMs = it)) }, valueRange = 10f..500f, size = knob, label = "Liberac.", valueText = String.format(Locale.US, "%.0f", comp.releaseMs))
                        NeumorphicKnob(value = comp.makeupGain, onValueChange = { viewModel.setCompressor(comp.copy(makeupGain = it)) }, valueRange = 0f..24f, size = knob, label = "Makeup", valueText = String.format(Locale.US, "%.1f", comp.makeupGain))
                        NeumorphicKnob(value = comp.limiterCeiling, onValueChange = { viewModel.setCompressor(comp.copy(limiterCeiling = it)) }, valueRange = -12f..0f, size = knob, label = "Ceiling", valueText = String.format(Locale.US, "%.1f", comp.limiterCeiling))
                    }
                }
            } else {
                // "Otros" subtab: List of Acoustic Environment Presets
                AcousticPresetListContent(
                    activePresetName = activePresetName,
                    onPresetSelected = { item ->
                        activePresetName = item.name
                        viewModel.setReverb(item.params)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // --- PRESETS CHIPS (Scrollable to prevent any truncation) ---
        val presetScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(presetScroll)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf("Reverb", "Preajuste", "Guardar", "Restablecer")
            presets.forEachIndexed { index, name ->
                NeumorphicChip(
                    text = name,
                    selected = selectedPresetChip == index,
                    onClick = {
                        selectedPresetChip = index
                        if (name == "Preajuste" || name == "Reverb") {
                            showPresetDialog = true
                        } else if (name == "Restablecer") {
                            viewModel.setReverb(ReverbParams())
                            activePresetName = "Estudio"
                        } else if (name == "Guardar") {
                            viewModel.savePreset(activePresetName)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // --- BOTTOM MEZCLADO SECTION (100% Fitted) ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "Mezclado",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format(Locale.US, "%.2f", reverb.mix),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            NeumorphicKnob(
                modifier = Modifier.alpha(if (state.effectsEnabled) 1f else 0.45f),
                value = reverb.mix,
                onValueChange = { viewModel.setReverb(reverb.copy(mix = it)) },
                valueRange = 0f..1f,
                size = bigKnob,
                label = "",
                valueText = ""
            )
        }
    }

    // Popup Dialog for Preset selection
    if (showPresetDialog) {
        Dialog(onDismissRequest = { showPresetDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .background(SurfaceDark, shape = RoundedCornerShape(24.dp))
                    .border(1.dp, DarkShadowDark, shape = RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                AcousticPresetListContent(
                    activePresetName = activePresetName,
                    onPresetSelected = { item ->
                        activePresetName = item.name
                        viewModel.setReverb(item.params)
                        showPresetDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AcousticPresetListContent(
    activePresetName: String,
    onPresetSelected: (AcousticPresetItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(acousticPresetsList) { item ->
            val isSelected = item.name == activePresetName

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isSelected) DarkShadowDark else SurfaceDark,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onPresetSelected(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "(o)",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (isSelected) {
                    Text(
                        text = "✓",
                        color = CyanNeon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
