package com.auraline.app.ui

import android.app.Activity
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.util.Log
import com.auraline.core.audio.AuraLineEngine

/**
 * Actividad oficial de panel de control de efectos para Android.
 *
 * Se invoca cuando un reproductor compatible (Spotify, Poweramp, Musicolet, VLC, etc.)
 * solicita abrir el ecualizador mediante [AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL].
 *
 * Registra la sesión de audio del reproductor en el motor [AuraLineEngine] y abre la interfaz
 * principal de AuraLine para que el usuario controle los efectos en tiempo real sobre esa sesión.
 */
class EffectPanelActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) ?: 0
        val packageName = intent?.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            ?: callingPackage
            ?: intent?.extras?.getString("package_name")

        Log.i(TAG, "Panel de efectos solicitado: session=$sessionId, package=$packageName")

        if (sessionId != 0) {
            AuraLineEngine.onSessionOpened(sessionId, packageName)
        }

        // Abre la pantalla principal de AuraLine para controlar los efectos de la sesión
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            if (packageName != null) {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        }
        startActivity(mainIntent)

        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val TAG = "EffectPanelActivity"
    }
}
