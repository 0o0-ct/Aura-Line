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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.UsageProfiles
import com.auraline.core.ui.components.PixelCard
import com.auraline.core.ui.components.PixelChip
import com.auraline.core.ui.components.PixelSectionTitle
import com.auraline.core.ui.components.PixelSwitchRow
import com.auraline.core.ui.theme.LocalMonoPalette
import com.auraline.core.ui.theme.allMonoPalettes

@Composable
fun SettingsScreen(viewModel: AuraLineViewModel) {
    val palette = LocalMonoPalette.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val eqState by viewModel.state.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val checkResult by viewModel.checkResult.collectAsState()

    var permissionDenied by remember { mutableStateOf(false) }

    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.runAudioCheck() else permissionDenied = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Perfil de uso ---
        PixelCard {
            PixelSectionTitle(
                text = "Perfil de uso",
                trailing = UsageProfiles.firstOrNull { it.key == settings.usageProfile }?.name ?: "sin elegir"
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UsageProfiles.forEach { profile ->
                    PixelChip(
                        text = profile.name,
                        selected = settings.usageProfile == profile.key,
                        onClick = { viewModel.applyUsageProfile(profile.key) }
                    )
                }
            }
            UsageProfiles.firstOrNull { it.key == settings.usageProfile }?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it.description, color = palette.secondary, fontSize = 11.sp)
            }
        }

        // --- Paletas ---
        PixelCard {
            PixelSectionTitle(
                text = "Paleta",
                trailing = allMonoPalettes.firstOrNull { it.id.toString() == settings.theme.paletteKey }?.name ?: "1"
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allMonoPalettes.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mono ->
                            val selected = settings.theme.paletteKey == mono.id.toString()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(mono.primary)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) palette.primary else palette.secondary.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setPalette(mono.id.toString(), mono.primary.value.toInt())
                                    }
                            )
                        }
                    }
                }
            }
        }

        // --- Ecualizador ---
        PixelCard {
            PixelSectionTitle(text = "Ecualizador", trailing = "${settings.numBands} bandas")
            Slider(
                value = settings.numBands.toFloat(),
                onValueChange = { viewModel.setNumBands(it.toInt()) },
                valueRange = 20f..25f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = palette.primary,
                    activeTrackColor = palette.primary,
                    inactiveTrackColor = palette.tertiary.copy(alpha = 0.4f)
                )
            )
            Text(
                text = "Más bandas dan más control; el rango audible va de 20 Hz a 20 kHz.",
                color = palette.secondary,
                fontSize = 11.sp
            )
        }

        // --- Comportamiento ---
        PixelCard {
            PixelSectionTitle(text = "Comportamiento")
            PixelSwitchRow(
                title = "Perfiles por salida",
                subtitle = "Recuerda la curva para altavoz, cable o Bluetooth",
                checked = settings.profilesByOutput,
                onCheckedChange = { viewModel.setProfilesByOutput(it) }
            )
            PixelSwitchRow(
                title = "Respuesta háptica",
                subtitle = "Vibración al mover faders y cambiar interruptores",
                checked = settings.hapticFeedback,
                onCheckedChange = { viewModel.setHapticFeedback(it) }
            )
            PixelSwitchRow(
                title = "Protección de nivel",
                subtitle = "Compensa parte del refuerzo para que el códec no sature",
                checked = eqState.autoPreamp,
                onCheckedChange = { viewModel.setAutoPreamp(it) }
            )
            PixelSwitchRow(
                title = "Iniciar al encender",
                subtitle = "Activa el ecualizador automáticamente tras reiniciar el teléfono",
                checked = settings.autoStart,
                onCheckedChange = { viewModel.setAutoStart(it) }
            )
        }

        // --- Segundo plano ---
        PixelCard {
            PixelSectionTitle(text = "Segundo plano")
            Text(
                text = if (batteryExempt) {
                    "Protegido: One UI no cerrará AuraLine con la pantalla apagada."
                } else {
                    "One UI y otros sistemas cierran servicios de audio en reposo. Exímelo de la optimización de batería para mantener el motor activo."
                },
                color = if (batteryExempt) palette.primary else palette.secondary,
                fontSize = 11.sp
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            }) {
                Text(
                    text = if (batteryExempt) "Batería protegida" else "Proteger batería",
                    color = palette.primary
                )
            }
        }

        // --- Comprobación del motor ---
        PixelCard {
            PixelSectionTitle(text = "Comprobación del motor", trailing = "prueba acústica")
            Text(
                text = "Emite dos tonos, los captura con el micrófono y comprueba que sólo se mueve la banda que toca.",
                color = palette.secondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Funciona con el altavoz del móvil y con altavoces o barras externas por Bluetooth o cable: sube el volumen y acerca el teléfono. Con auriculares puestos no se puede medir, pero se verifica que la cadena esté activa.",
                color = palette.secondary,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    permissionDenied = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.runAudioCheck()
                    else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                enabled = !checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = palette.surface,
                    disabledContentColor = palette.secondary
                )
            ) {
                Text(if (checking) "Midiendo…" else "Comprobar el motor")
            }
            PixelSwitchRow(
                title = "Comprobar al encender el motor",
                subtitle = "Opcional: verifica el motor automáticamente al activarlo",
                checked = settings.autoCheck,
                onCheckedChange = { viewModel.setAutoCheck(it) }
            )
            if (permissionDenied) {
                Text(
                    text = "Sin permiso de micrófono no se puede medir.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
            checkResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (result.passed) "VERIFICADO" else "SIN CAMBIO",
                    color = if (result.passed) palette.primary else palette.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Δ ${"%.1f".format(result.deltaDb)} dB · ${result.message}",
                    color = palette.secondary,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = "AuraLine 4.0 · procesado ${if (eqState.motorEnabled) "activo" else "inactivo"}",
            color = palette.secondary,
            fontSize = 11.sp
        )
    }
}
