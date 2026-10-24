package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.auraline.app.di.AuraLineModule

/** Acción "Apagar" de la notificación: apaga el motor y retira el servicio. */
class MotorToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_STOP_MOTOR) return
        AuraLineModule.repository(context).toggleMotor(false)
        AudioForegroundService.stop(context)
    }

    companion object {
        const val ACTION_STOP_MOTOR = "com.auraline.app.action.STOP_MOTOR"
    }
}
