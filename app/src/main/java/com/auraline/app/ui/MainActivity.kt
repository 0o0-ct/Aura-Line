package com.auraline.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.auraline.app.audio.AudioForegroundService
import com.auraline.app.audio.BootReceiver
import com.auraline.app.di.AuraLineModule
import com.auraline.app.domain.AuraLineViewModel
import com.auraline.core.ui.theme.AuraLineTheme
import com.auraline.core.ui.theme.paletteFor

class MainActivity : ComponentActivity() {

    private val viewModel: AuraLineViewModel by lazy {
        AuraLineViewModel(AuraLineModule.repository(applicationContext))
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val paletteId = settings.theme.paletteKey.toIntOrNull() ?: 1

            com.auraline.core.ui.theme.PixelTheme(paletteId = paletteId) {
                LaunchedEffect(Unit) {
                    requestNotificationPermission()
                }
                LaunchedEffect(settings.autoStart) {
                    syncBootReceiver(settings.autoStart)
                }
                AuraLineApp(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    /** El receiver de arranque sólo existe cuando el usuario activa el inicio automático. */
    private fun syncBootReceiver(enabled: Boolean) {
        val component = ComponentName(this, BootReceiver::class.java)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        runCatching {
            packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
