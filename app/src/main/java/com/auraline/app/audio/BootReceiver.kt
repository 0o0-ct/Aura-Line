package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.auraline.app.di.AuraLineModule

/**
 * Arranque automático opcional. El componente está deshabilitado en el manifest y sólo se
 * activa desde Ajustes, de modo que la app no se ejecuta en el arranque si el usuario no lo pide.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = AuraLineModule.repository(context)
        if (!repository.getSettings().autoStart) return
        repository.toggleMotor(true)
        runCatching { AudioForegroundService.start(context) }
    }
}
