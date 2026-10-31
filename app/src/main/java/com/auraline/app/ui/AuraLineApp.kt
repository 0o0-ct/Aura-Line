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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
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
import com.auraline.core.ui.components.neumorphicConvex
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

/** Ancho a partir del cual se usa riel lateral (tablets y plegables abiertos). */
private val ExpandedWidth = 600.dp

/** Ancho máximo del contenido en pantallas grandes para que los controles no se estiren. */
private val MaxContentWidth = 760.dp

@Composable
fun AuraLineApp(viewModel: AuraLineViewModel) {
    val colors = AuraTheme.colors
    val configuration = LocalConfiguration.current
    val expanded = configuration.screenWidthDp.dp >= ExpandedWidth
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
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }

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

    BackHandler(enabled = tab != 0 || showDiagnostics) {
        if (showDiagnostics) {
            showDiagnostics = false
        } else {
            tab = 0
        }
    }

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
                                    text = destinations[tab].label,
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
                                        text = buildString {
                                            append(engine.route.label)
                                            engine.outputName?.let { append(" · ").append(it) }
                                            if (engine.sampleRateHz > 0) {
                                                append(" · ").append(engine.sampleRateHz).append(" Hz")
                                            }
                                            append(" · ").append(engine.channels).append(" canales")
                                        },
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
                    if (!expanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingDock(
                                selected = tab,
                                accent = accent,
                                onSelect = { index -> haptic(); tab = index }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (expanded) {
                        SideRail(
                            selected = tab,
                            accent = accent,
                            onSelect = { index -> haptic(); tab = index }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = MaxContentWidth)
                        ) {
                            if (showDiagnostics) {
                                DiagnosticsScreen(
                                    viewModel = viewModel,
                                    onBack = { showDiagnostics = false }
                                )
                            } else {
                                when (tab) {
                                    0 -> EqualizerScreen(viewModel = viewModel)
                                    1 -> ToneScreen(viewModel = viewModel)
                                    2 -> EffectsScreen(viewModel = viewModel)
                                    else -> SettingsScreen(
                                        viewModel = viewModel,
                                        onOpenDiagnostics = { showDiagnostics = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dock flotante inferior (teléfonos): cristal esmerilado estilo iOS/macOS. */
@Composable
private fun FloatingDock(
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val dockShape = RoundedCornerShape(32.dp)
        Row(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.94f)
                .height(68.dp)
                .glassDock(dockShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { index, destination ->
                DockItem(
                    destination = destination,
                    selected = selected == index,
                    accent = accent,
                    modifier = Modifier.weight(1f).height(54.dp),
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

/** Riel lateral (tablets / pantallas ≥ 600 dp): mismo cristal, orientación vertical. */
@Composable
private fun SideRail(
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit
) {
    val railShape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
            .navigationBarsPadding()
            .width(88.dp)
            .glassDock(railShape)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        destinations.forEachIndexed { index, destination ->
            DockItem(
                destination = destination,
                selected = selected == index,
                accent = accent,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun Modifier.glassDock(shape: RoundedCornerShape): Modifier {
    val colors = AuraTheme.colors
    return this
        .background(colors.surface.copy(alpha = 0.82f), shape)
        .border(
            1.2.dp,
            Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)),
            shape
        )
}

@Composable
private fun DockItem(
    destination: Destination,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = AuraTheme.colors
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
        modifier = modifier
            .clip(itemShape)
            .background(itemBgColor, itemShape)
            .border(if (selected) 1.dp else 0.dp, itemBorderColor, itemShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
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
