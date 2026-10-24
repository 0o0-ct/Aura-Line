package com.auraline.app.ui

import android.os.Bundle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.Brush
import com.auraline.core.ui.components.glass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraline.app.di.AuraLineModule
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.domain.AudioRepository
import com.auraline.core.domain.EqualizerState
import com.auraline.core.ui.components.NeumorphicTabBar
import com.auraline.core.ui.theme.BackgroundDark
import com.auraline.core.ui.theme.CyanNeon
import com.auraline.core.ui.theme.DarkShadowDark
import com.auraline.core.ui.theme.NeumorphicTheme
import com.auraline.core.ui.theme.TextPrimary
import com.auraline.core.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val repository: AudioRepository by lazy {
        AuraLineModule.provideRepository(this)
    }
    private val viewModel: AuraLineViewModel by lazy {
        AuraLineViewModel(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeumorphicTheme {
                AuraLineMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AuraLineMainApp(viewModel: AuraLineViewModel) {
    val state: EqualizerState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0=EQ, 1=Controls, 2=Effects, 3=Settings
    val context = LocalContext.current
    LaunchedEffect(state.motorEnabled) {
        val intent = Intent(context, com.auraline.app.audio.AudioForegroundService::class.java)
        if (state.motorEnabled) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BackgroundDark, DarkShadowDark)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- TOP APP HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .glass(cornerRadius = 16.dp, alpha = 0.08f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AuraLine",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.motorEnabled) "Motor: ACTIVO" else "Motor: APAGADO",
                    color = if (state.motorEnabled) CyanNeon else TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Settings Icon Button
                Text(
                    text = "⚙️",
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable { selectedTab = 3 }
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Master Motor Switch
                Switch(
                    checked = state.motorEnabled,
                    onCheckedChange = { viewModel.toggleMotor(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanNeon,
                        checkedTrackColor = DarkShadowDark
                    )
                )
            }
        }

        // --- TOP NEUMORPHIC TAB BAR (EQ / CONTROLES / EFECTOS) ---
        if (selectedTab != 3) {
            NeumorphicTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        // --- SCREEN CONTENT VIEW ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Crossfade(targetState = selectedTab, label = "tab") { tab ->
                when (tab) {
                    0 -> EqualizerScreen(viewModel = viewModel)
                    1 -> ControlsScreen(viewModel = viewModel)
                    2 -> EffectsScreen(viewModel = viewModel)
                    3 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
