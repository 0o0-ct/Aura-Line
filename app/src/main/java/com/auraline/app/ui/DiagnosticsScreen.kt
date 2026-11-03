package com.auraline.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.EngineStatus
import com.auraline.core.ui.components.AuraCard
import com.auraline.core.ui.components.AuraSwitch
import com.auraline.core.ui.components.SectionTitle
import com.auraline.core.ui.components.SelectableChip
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.theme.AuraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AuraLineViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AuraTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineStatus by viewModel.engine.collectAsState()
    val eqState by viewModel.state.collectAsState()

    var activeTone by remember { mutableStateOf<String?>(null) }
    var toneTrack by remember { mutableStateOf<AudioTrack?>(null) }

    fun stopTone() {
        toneTrack?.runCatching {
            stop()
            release()
        }
        toneTrack = null
        activeTone = null
    }

    fun playTone(type: String) {
        stopTone()
        activeTone = type
        scope.launch(Dispatchers.IO) {
            val sampleRate = 48000
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
                AudioTrack.MODE_STREAM
            )
            toneTrack = track
            track.play()

            val buffer = ShortArray(1024)
            var phase = 0.0
            var sweepFreq = 20.0

            while (activeTone == type) {
                for (i in buffer.indices) {
                    val sample = when (type) {
                        "60Hz" -> sin(2.0 * Math.PI * 60.0 * i / sampleRate + phase)
                        "1kHz" -> sin(2.0 * Math.PI * 1000.0 * i / sampleRate + phase)
                        "8kHz" -> sin(2.0 * Math.PI * 8000.0 * i / sampleRate + phase)
                        "Pink" -> (Math.random() * 2.0 - 1.0) * 0.3
                        "Sweep" -> {
                            sweepFreq += 0.5
                            if (sweepFreq > 20000.0) sweepFreq = 20.0
                            sin(2.0 * Math.PI * sweepFreq * i / sampleRate + phase)
                        }
                        else -> 0.0
                    }
                    buffer[i] = (sample.coerceIn(-1.0, 1.0) * 16000).toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopTone() }
    }

    fun generateReportText(): String {
        return buildString {
            appendLine("=== INFORME DE DIAGNÓSTICO AURALINE ===")
            appendLine("Dispositivo: ${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("Estado del motor: ${if (engineStatus.running) "EN VIVO" else "INACTIVO"}")
            appendLine("Ruta de audio: ${engineStatus.route.label} (${engineStatus.outputName ?: "Altavoz"})")
            appendLine("Frecuencia de muestreo: ${engineStatus.sampleRateHz} Hz · ${engineStatus.channels} canales")
            appendLine("A/B Bypass: ${if (eqState.bypass) "ACTIVADO" else "Desactivado"}")
            appendLine("Ganancia de entrada (Preamp): ${eqState.preampDb} dB")
            appendLine("Sesiones activas: ${engineStatus.sessions.size}")
            engineStatus.sessions.forEach { id ->
                appendLine(" - Sesión ID: $id")
            }
            appendLine("Etapas de procesado:")
            engineStatus.stages.forEach { stage ->
                appendLine(" - ${stage.name}: ${if (stage.active) "ACTIVA" else "Inactiva"} (${stage.detail})")
            }
            engineStatus.lastError?.let {
                appendLine("Último error registrado: $it")
            }
            appendLine("=====================================")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico del Sistema", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- TARJETA 1: INFORMACIÓN DEL DISPOSITIVO Y HARDWARE ---
            item {
                AuraCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(text = "Dispositivo y OS", trailing = "API ${Build.VERSION.SDK_INT}")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.DEVICE})",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} · Kernel ${System.getProperty("os.version") ?: "N/A"}",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // --- TARJETA 2: ESTADO DEL MOTOR Y SESIONES ---
            item {
                AuraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(
                            text = "Estado del Motor DSP",
                            trailing = if (engineStatus.running) "EN VIVO" else "INACTIVO"
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Ruta activa: ${engineStatus.route.label} (${engineStatus.outputName ?: "Salida principal"})",
                        color = colors.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${engineStatus.sampleRateHz} Hz · ${engineStatus.channels} canales · ${engineStatus.sessions.size} sesión(es) conectada(s)",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    engineStatus.stages.forEach { stage ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stage.name, color = colors.textPrimary, fontSize = 13.sp)
                            StatusPill(
                                text = if (stage.active) "ACTIVA" else "INACTIVA",
                                color = if (stage.active) colors.positive else colors.textSecondary
                            )
                        }
                    }
                }
            }

            // --- TARJETA 3: GENERADOR DE TONOS DE PRUEBA ---
            item {
                AuraCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(text = "Generador de Tonos de Prueba", trailing = activeTone ?: "Apagado")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Inyecta señales sintéticas puras para medir la respuesta acústica del ecualizador y limitador:",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("60Hz", "1kHz", "8kHz", "Pink", "Sweep").forEach { tone ->
                            SelectableChip(
                                text = tone,
                                selected = activeTone == tone,
                                onClick = {
                                    if (activeTone == tone) stopTone() else playTone(tone)
                                }
                            )
                        }
                    }

                    if (activeTone != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { stopTone() },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Detener")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detener Tono de Prueba")
                        }
                    }
                }
            }

            // --- TARJETA 4: BOTÓN A/B CON COMPENSACIÓN DE VOLUMEN ---
            item {
                AuraCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(text = "Comparación A/B en Vivo")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (eqState.bypass) "Bypass Activo (Señal Virgen)" else "Procesado Activo",
                                color = if (eqState.bypass) colors.warning else colors.positive,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Alterna entre el audio ecualizado y el original sin saltos de nivel.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        AuraSwitch(
                            checked = eqState.bypass,
                            onCheckedChange = { viewModel.setBypass(it) }
                        )
                    }
                }
            }

            // --- TARJETA 5: EXPORTAR INFORME DE TEXTO ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val text = generateReportText()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("AuraLine Diagnostic", text))
                            Toast.makeText(context, "Informe copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar", tint = colors.textPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Informe", color = colors.textPrimary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val text = generateReportText()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir Informe AuraLine"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir", tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
