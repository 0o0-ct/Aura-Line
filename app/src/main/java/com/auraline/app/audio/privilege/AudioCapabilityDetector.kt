package com.auraline.app.audio.privilege

import android.content.Context
import android.media.AudioManager
import android.os.Build

data class AudioCapabilityReport(
    val capabilities: DeviceCapabilities,
    val nativeSampleRateHz: Int,
    val dynamicsProcessingSupported: Boolean,
    val captureSupported: Boolean,
    val selectedBackend: AudioIntegrationMode,
    val activeOutputDeviceName: String
)

object AudioCapabilityDetector {

    fun detect(context: Context): AudioCapabilityReport {
        val caps = PrivilegeManager.inspectCapabilities(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val nativeSampleRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        val dpSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val captureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val selectedBackend = AudioBackendSelector.selectBackend(caps, dpSupported, captureSupported)

        @Suppress("DEPRECATION")
        val outputDeviceName = when {
            audioManager?.isBluetoothA2dpOn == true -> "Bluetooth A2DP"
            audioManager?.isWiredHeadsetOn == true -> "Auriculares Cableados"
            else -> "Altavoz Integrado"
        }

        return AudioCapabilityReport(
            capabilities = caps,
            nativeSampleRateHz = nativeSampleRate,
            dynamicsProcessingSupported = dpSupported,
            captureSupported = captureSupported,
            selectedBackend = selectedBackend,
            activeOutputDeviceName = outputDeviceName
        )
    }
}
