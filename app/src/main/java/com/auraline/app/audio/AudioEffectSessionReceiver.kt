package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.auraline.core.audio.AuraLineEngine

/**
 * Los reproductores que lo soportan anuncian su sesión con estos broadcasts; el motor se
 * engancha a ella para procesar su salida y detectar el paquete activo (EXTRA_PACKAGE_NAME).
 */
class AudioEffectSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: 0
        if (sessionId == 0) return
        val packageName = intent?.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        when (intent?.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> AuraLineEngine.onSessionOpened(sessionId, packageName)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> AuraLineEngine.onSessionClosed(sessionId)
        }
    }
}
