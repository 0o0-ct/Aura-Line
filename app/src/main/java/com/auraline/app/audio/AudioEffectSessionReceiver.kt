package com.auraline.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.auraline.core.audio.GlobalAudioEffectManager

class AudioEffectSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: 0
        when (intent?.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                GlobalAudioEffectManager.onSessionOpened(sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                GlobalAudioEffectManager.onSessionClosed(sessionId)
            }
        }
    }
}
