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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.UsageProfiles
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraSliderRow
import com.auraline.core.ui.components.AuraSwitch
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.theme.AuraPalettes
import com.auraline.core.ui.theme.AuraTheme

@Composable
fun SettingsScreen(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val eqState by viewModel.state.collectAsState()
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
        // Perfil de uso: ajusta rango, protección, efectos y curva base de golpe
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Perfil de uso",
                trailing = UsageProfiles.firstOrNull { it.key == settings.usageProfile }?.name ?: "sin elegir"
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            UsageProfiles.firstOrNull { it.key == settings.usageProfile }?.let { profile ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile.description,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Paleta de colores con discos táctiles Neumórficos
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(
                text = "Paleta",
                trailing = AuraPalettes.firstOrNull { it.key == settings.theme.paletteKey }?.label ?: settings.theme.paletteKey
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuraPalettes.forEach { palette ->
                    val selected = settings.theme.paletteKey == palette.key
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(
                                elevation = if (selected) 8.dp else 2.dp,
                                shape = CircleShape,
                                ambientColor = if (selected) palette.accent.copy(alpha = 0.5f) else Color.Black
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        palette.accent,
                                        palette.accent.copy(alpha = if (selected) 0.85f else 0.45f)
                                    )
                                ),
                                CircleShape
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                brush = if (selected) {
                                    Brush.verticalGradient(
                                        listOf(Color.White, palette.accent)
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))
                                    )
                                },
                                shape = CircleShape
                            )
                            .clickable { viewModel.setPalette(palette.key, palette.accent.value.toInt()) }
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // Resolución de bandas
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Ecualizador", trailing = "${settings.numBands} bandas")
            Spacer(modifier = Modifier.height(6.dp))
            AuraSliderRow(
                label = "Resolución de bandas",
                valueText = "${settings.numBands} bandas",
                value = settings.numBands.toFloat(),
                valueRange = 20f..25f,
                onValueChange = { viewModel.setNumBands(it.toInt()) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Más bandas dan más control; el rango audible va de 20 Hz a 20 kHz.",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        // Comportamiento del sistema
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
                subtitle = "Vibración al mover faders y cambiar interruptores",
                checked = settings.hapticFeedback,
                onCheckedChange = { viewModel.setHapticFeedback(it) }
            )
            SettingSwitch(
                title = "Protección de nivel",
                subtitle = "Compensa parte del refuerzo para que el códec no sature. Desactívala si prefieres volumen máximo",
                checked = eqState.autoPreamp,
                onCheckedChange = { viewModel.setAutoPreamp(it) }
            )
            SettingSwitch(
                title = "Iniciar al encender",
                subtitle = "Activa el ecualizador automáticamente tras reiniciar el teléfono",
                checked = settings.autoStart,
                onCheckedChange = { viewModel.setAutoStart(it) }
            )
        }

        // Protección de batería
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Segundo plano")
            Text(
                text = "One UI y otros sistemas cierran servicios de audio en reposo. Para mantener AuraLine activo mientras la pantalla está apagada, exímelo de la optimización de batería.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (batteryExempt) "Batería protegida" else "Sin protección",
                    color = if (batteryExempt) colors.positive else colors.warning
                )
                if (!batteryExempt) {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    }) {
                        Text("Configurar", color = colors.accent)
                    }
                }
            }
        }

        // Comprobación acústica
        AuraCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle(text = "Comprobación del motor", trailing = "prueba acústica")
            Text(
                text = "Mide con el micrófono si el ecualizador modifica realmente el sonido del altavoz. " +
                    "Emite dos tonos simultáneos (grave y agudo) y comprueba si al subir la banda del tono agudo " +
                    "este se vuelve más dominante respecto al grave.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "Requisitos: salida por altavoz del teléfono (no auriculares ni Bluetooth), " +
                    "volumen al 50% o más, teléfono apoyado y silencio durante la medición (≈1,5 s).",
                color = colors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Botón estilo Apple Pill
            Button(
                onClick = {
                    permissionDenied = false
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.runAudioCheck()
                    } else {
                        audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !checking,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.background,
                    disabledContainerColor = colors.surfaceElevated,
                    disabledContentColor = colors.textSecondary
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = if (checking) "Midiendo…" else "Comprobar el motor",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Comprobar al encender el motor", color = colors.textPrimary, fontSize = 14.sp)
                    Text(
                        text = "Opcional: verifica el motor automáticamente cada vez que lo activas",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                AuraSwitch(
                    checked = settings.autoCheck,
                    onCheckedChange = { viewModel.setAutoCheck(it) }
                )
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
            color = colors.textSecondary.copy(alpha = 0.7f),
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
            Text(text = title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.sp)
        }
        AuraSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
