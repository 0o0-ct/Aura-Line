package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import com.auraline.core.audio.AuraLineEngine

/**
 * Receptor de sesiones de audio de reproductores externos en Android.
 *
 * Los reproductores compatibles (Spotify, Poweramp, Musicolet, Deezer, etc.) anuncian
 * el inicio y fin de reproducción mediante estos broadcasts oficiales. AuraLine acopla
 * la cadena de efectos directamente al ID de sesión de audio (audioSessionId) de la app emisora.
 */
class AudioEffectSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra("package_name")

        Log.i(TAG, "Broadcast de sesión recibido: action=$action, session=$sessionId, package=$packageName")

        if (sessionId == 0) {
            Log.w(TAG, "Sesión inválida (0 o ausente) ignorada")
            return
        }

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
            "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION" -> {
                AuraLineEngine.onSessionOpened(sessionId, packageName)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION,
            "android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION" -> {
                AuraLineEngine.onSessionClosed(sessionId)
            }
        }
    }

    companion object {
        private const val TAG = "AudioEffectReceiver"
    }
}
