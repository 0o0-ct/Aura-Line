package com.auraline.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auraline.app.audio.AudioForegroundService
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.AuraSwitch
import com.auraline.core.ui.components.LocalHapticsEnabled
import com.auraline.core.ui.components.LocalPowered
import com.auraline.core.ui.components.StatusPill
import com.auraline.core.ui.components.hapticFeedback
import com.auraline.core.ui.theme.AuraTheme
import kotlinx.coroutines.launch

private data class Destination(
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("Ecualizador", Icons.Rounded.GraphicEq),
    Destination("Tono", Icons.Rounded.Tune),
    Destination("Efectos", Icons.Rounded.AutoAwesome),
    Destination("Ajustes", Icons.Rounded.Settings)
)

@Composable
fun AuraLineApp(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val state by viewModel.state.collectAsState()
    val accent = if (state.motorEnabled) colors.accent else colors.textSecondary
    val settings by viewModel.settings.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val haptic = hapticFeedback()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var autoChecked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.motorEnabled) {
        if (state.motorEnabled) {
            runCatching { AudioForegroundService.start(context) }
        } else {
            runCatching { AudioForegroundService.stop(context) }
        }
    }

    LaunchedEffect(state.motorEnabled, settings.autoCheck) {
        if (!state.motorEnabled || !settings.autoCheck || autoChecked) return@LaunchedEffect
        autoChecked = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.runAudioCheck()
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    CompositionLocalProvider(
        LocalHapticsEnabled provides settings.hapticFeedback,
        LocalPowered provides state.motorEnabled
    ) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            // Fondo de lienzo ambiental (Apple ambient light blooms para iluminar el cristal translúcido)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowAlpha = if (state.motorEnabled) 0.14f else 0.04f
                // Resplandor superior derecho con el acento activo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.85f, size.height * 0.12f)
                )
                // Resplandor suave inferior izquierdo índigo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E2B44).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.88f),
                        radius = size.width * 0.8f
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.15f, size.height * 0.88f)
                )
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.background.copy(alpha = 0.85f),
                                        colors.background.copy(alpha = 0.40f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AuraLine",
                                    color = colors.textPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    StatusPill(
                                        text = if (state.motorEnabled) "Motor activo" else "Motor apagado",
                                        color = if (state.motorEnabled) colors.positive else colors.textSecondary
                                    )
                                    Text(
                                        text = "${engine.route.label} · ${engine.sampleRateHz} Hz",
                                        color = colors.textSecondary.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Interruptor táctil de estilo Apple Neumorphism
                            AuraSwitch(
                                checked = state.motorEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleMotor(enabled)
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (enabled) "Motor de audio encendido" else "Motor de audio apagado"
                                        )
                                    }
                                }
                            )
                        }
                    }
                },
                bottomBar = {
                    // Floating Frosted Glass Dock (Estilo macOS / iOS Dock flotante con cristal esmerilado)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        val dockShape = RoundedCornerShape(28.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .shadow(elevation = 16.dp, shape = dockShape, ambientColor = Color(0x66000000), spotColor = Color(0x80000000))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xD9161E2C),
                                            Color(0xF20F1520)
                                        )
                                    ),
                                    dockShape
                                )
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x4DFFFFFF),
                                            Color(0x14FFFFFF),
                                            Color(0x05FFFFFF)
                                        )
                                    ),
                                    dockShape
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                val selected = tab == index
                                val itemBgColor by animateColorAsState(
                                    targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "tabBg"
                                )
                                val itemBorderColor by animateColorAsState(
                                    targetValue = if (selected) accent.copy(alpha = 0.45f) else Color.Transparent,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "tabBorder"
                                )
                                val itemTextColor by animateColorAsState(
                                    targetValue = if (selected) accent else colors.textSecondary.copy(alpha = 0.7f),
                                    animationSpec = tween(durationMillis = 200),
                                    label = "tabText"
                                )

                                val itemShape = RoundedCornerShape(20.dp)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(itemShape)
                                        .background(itemBgColor, itemShape)
                                        .border(if (selected) 1.dp else 0.dp, itemBorderColor, itemShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptic()
                                            tab = index
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                        tint = itemTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = destination.label,
                                        color = itemTextColor,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        letterSpacing = (-0.1).sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (tab) {
                        0 -> EqualizerScreen(viewModel = viewModel)
                        1 -> ToneScreen(viewModel = viewModel)
                        2 -> EffectsScreen(viewModel = viewModel)
                        else -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
