package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AudioToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AudioForegroundService.ACTION_TOGGLE_AUDIO) {
            // Se maneja desde la UI mediante el ViewModel; aquí solo se reenvía la acción
            context.startService(
                Intent(context, AudioForegroundService::class.java).apply {
                    action = AudioForegroundService.ACTION_TOGGLE_AUDIO
                }
            )
        }
    }
}
