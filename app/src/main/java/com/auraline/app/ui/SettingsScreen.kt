package com.auraline.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.theme.AuraPalettes
import com.auraline.core.ui.theme.AuraTheme

@Composable
fun SettingsScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val checkResult by viewModel.checkResult.collectAsState()

    var permissionDenied by remember { mutableStateOf(false) }

    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.runAudioCheck()
        } else {
            permissionDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Paleta", trailing = settings.theme.paletteKey)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AuraPalettes.forEach { palette ->
                    val selected = settings.theme.paletteKey == palette.key
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(palette.accent.copy(alpha = if (selected) 1f else 0.25f), RoundedCornerShape(20.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) colors.textPrimary else colors.outline,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.setPalette(palette.key, palette.accent.value.toInt()) }
                    )
                }
            }
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Ecualizador", trailing = "${settings.numBands} bandas")
            Slider(
                value = settings.numBands.toFloat(),
                onValueChange = { viewModel.setNumBands(it.toInt()) },
                valueRange = 20f..25f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.outline
                )
            )
            Text(
                text = "Más bandas dan más control; el rango audible va de 20 Hz a 20 kHz.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Comportamiento")
            Spacer(modifier = Modifier.height(4.dp))
            SettingSwitch(
                title = "Perfiles por salida",
                subtitle = "Recuerda la curva para altavoz, cable o Bluetooth",
                checked = settings.profilesByOutput,
                onCheckedChange = { viewModel.setProfilesByOutput(it) }
            )
            SettingSwitch(
                title = "Respuesta háptica",
                subtitle = "Vibración ligera al ajustar controles",
                checked = settings.hapticFeedback,
                onCheckedChange = { viewModel.setHapticFeedback(it) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Motor en segundo plano", color = colors.textPrimary, fontSize = 14.sp)
                    Text(
                        text = if (batteryExempt) {
                            "Protegido: el sistema no cerrará AuraLine al cambiar de app"
                        } else {
                            "El sistema puede cerrar AuraLine al cambiar de app o de canción"
                        },
                        color = if (batteryExempt) colors.positive else colors.warning,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }) {
                    Text("Proteger", color = colors.accent, fontSize = 12.sp)
                }
            }

            SettingSwitch(
                title = "Inicio automático",
                subtitle = "Arranca el motor al encender el dispositivo",
                checked = settings.autoStart,
                onCheckedChange = { viewModel.setAutoStart(it) }
            )
        }

        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Comprobación del motor", trailing = engine.route.label)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reproduce un tono de 1 kHz, lo captura con el micrófono y mide cuánto lo atenúa el ecualizador. Es la prueba de que el efecto procesa la salida real.",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    permissionDenied = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.runAudioCheck() else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.background,
                    disabledContainerColor = colors.surfaceElevated,
                    disabledContentColor = colors.textSecondary
                )
            ) {
                Text(if (checking) "Midiendo…" else "Comprobar el motor")
            }

            if (permissionDenied) {
                Text(
                    text = "Sin permiso de micrófono no se puede medir. Puedes activarlo en los ajustes del sistema.",
                    color = colors.warning,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            checkResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (result.passed) "VERIFICADO" else "SIN CAMBIO",
                        color = if (result.passed) colors.positive else colors.warning
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Δ ${"%.1f".format(result.deltaDb)} dB",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = result.message,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "Referencia %.1f dBFS · procesado %.1f dBFS".format(result.referenceDb, result.processedDb),
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = "AuraLine 2.0 · ${engine.sampleRateHz} Hz · ${if (engine.globalScope) "procesado global" else "sin cadena activa"}",
            color = colors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AuraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = colors.outline
            )
        )
    }
}
