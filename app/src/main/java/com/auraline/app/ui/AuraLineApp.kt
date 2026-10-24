package com.auraline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.auraline.app.audio.AudioForegroundService
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.components.LocalHapticsEnabled
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
    val settings by viewModel.settings.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val haptic = hapticFeedback()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.motorEnabled) {
        if (state.motorEnabled) {
            runCatching { AudioForegroundService.start(context) }
        } else {
            runCatching { AudioForegroundService.stop(context) }
        }
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalHapticsEnabled provides settings.hapticFeedback
    ) {

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AuraLine",
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.4).sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            StatusPill(
                                text = if (state.motorEnabled) "Motor activo" else "Motor apagado",
                                color = if (state.motorEnabled) colors.positive else colors.textSecondary
                            )
                            Text(
                                text = "${engine.route.label} · ${engine.sampleRateHz} Hz",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Switch(
                        checked = state.motorEnabled,
                        onCheckedChange = { enabled ->
                            haptic()
                            viewModel.toggleMotor(enabled)
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (enabled) "Motor de audio encendido" else "Motor de audio apagado"
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.background,
                            checkedTrackColor = colors.accent,
                            uncheckedTrackColor = colors.surfaceElevated,
                            uncheckedBorderColor = colors.outline
                        )
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.surface,
                tonalElevation = 0.dp
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            haptic()
                            tab = index
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accent,
                            selectedTextColor = colors.accent,
                            indicatorColor = colors.accent.copy(alpha = 0.14f),
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary
                        )
                    )
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
