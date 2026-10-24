package com.auraline.core.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Comprobación acústica: reproduce un tono puro, lo captura con el micrófono y mide
 * cuánto cambia con el motor activo. Es la prueba objetiva de que el efecto procesa
 * la salida real, no sólo que se haya podido crear.
 */
class AudioCheckRunner(private val context: Context) {

    suspend fun measure(toneHz: Double = TONE_HZ): Float = withContext(Dispatchers.IO) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * 0.75f).toInt(), 0)
        try {
            recordAndPlay(toneHz)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
        }
    }

    private fun recordAndPlay(toneHz: Double): Float {
        val sampleRate = 48_000
        val durationSeconds = 3.2
        val totalSamples = (sampleRate * durationSeconds).toInt()

        val tone = ShortArray(totalSamples) { index ->
            (sin(2.0 * PI * toneHz * index / sampleRate) * SHORT_AMPLITUDE).toInt().toShort()
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 4)

        val source = preferredSource()
        val recorder = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(tone.size * 2)
            .build()

        return try {
            recorder.startRecording()
            track.write(tone, 0, tone.size)
            track.play()

            val capture = ShortArray(totalSamples)
            var filled = 0
            while (filled < capture.size) {
                val read = recorder.read(capture, filled, capture.size - filled)
                if (read <= 0) break
                filled += read
            }
            track.stop()

            val window = capture.copyOfRange(capture.size / 3, capture.size * 2 / 3)
            val magnitude = goertzel(window, sampleRate, toneHz)
            val amplitude = (magnitude / window.size).coerceAtLeast(1e-9)
            (20.0 * log10(amplitude / 32768.0)).toFloat()
        } finally {
            runCatching { track.release() }
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
    }

    private fun preferredSource(): Int {
        val candidates = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (candidate in candidates) {
            val supported = runCatching {
                AudioRecord.getMinBufferSize(
                    48_000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ) > 0
            }.getOrDefault(false)
            if (supported) return candidate
        }
        return MediaRecorder.AudioSource.MIC
    }

    /** Energía en una única frecuencia; suficiente y preciso para un tono puro. */
    private fun goertzel(samples: ShortArray, sampleRate: Int, frequency: Double): Double {
        val omega = 2.0 * PI * frequency / sampleRate
        val coefficient = 2.0 * cos(omega)
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (sample in samples) {
            q0 = sample + coefficient * q1 - q2
            q2 = q1
            q1 = q0
        }
        val real = q1 - q2 * cos(omega)
        val imaginary = q2 * sin(omega)
        return sqrt(real * real + imaginary * imaginary)
    }

    companion object {
        const val TONE_HZ = 1000.0
        private const val SHORT_AMPLITUDE = 10_000.0
    }
}
