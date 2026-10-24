package com.auraline.core.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Comprobación acústica: reproduce tonos puros, los captura con el micrófono y mide su nivel
 * con Goertzel.
 *
 * Cuando hacen falta dos frecuencias se emiten **a la vez** y se capturan en la misma
 * grabación: así el control automático de ganancia del micrófono afecta igual a ambas y el
 * cociente entre ellas es fiable.
 */
class AudioCheckRunner(private val context: Context) {

    suspend fun measure(
        toneHz: Double = TONE_HZ,
        durationSeconds: Double = DEFAULT_DURATION
    ): Float = withContext(Dispatchers.IO) {
        level(playAndCapture(doubleArrayOf(toneHz), durationSeconds, TONE_AMPLITUDE)[0])
    }

    /** Nivel de cada frecuencia en dBFS, medidos en la misma captura. */
    suspend fun measureTwo(
        firstHz: Double,
        secondHz: Double,
        durationSeconds: Double = DEFAULT_DURATION
    ): Pair<Float, Float> = withContext(Dispatchers.IO) {
        val levels = playAndCapture(
            doubleArrayOf(firstHz, secondHz),
            durationSeconds,
            TONE_AMPLITUDE / 2.0
        )
        level(levels[0]) to level(levels[1])
    }

    private fun level(amplitude: Double): Float =
        (20.0 * log10(amplitude.coerceAtLeast(1e-9) / 32768.0)).toFloat()

    private fun playAndCapture(
        frequencies: DoubleArray,
        durationSeconds: Double,
        amplitude: Double
    ): DoubleArray {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * 0.75f).toInt(), 0)
        return try {
            capture(frequencies, durationSeconds, amplitude)
        } finally {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
        }
    }

    private fun capture(
        frequencies: DoubleArray,
        durationSeconds: Double,
        amplitude: Double
    ): DoubleArray {
        val sampleRate = 48_000
        val totalSamples = (sampleRate * durationSeconds).toInt()

        val tone = ShortArray(totalSamples) { index ->
            var value = 0.0
            for (frequency in frequencies) {
                value += sin(2.0 * PI * frequency * index / sampleRate)
            }
            (value / frequencies.size * amplitude).toInt().coerceIn(-32768, 32767).toShort()
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 4)

        val recorder = AudioRecord.Builder()
            .setAudioSource(preferredSource())
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

            val captureBuffer = ShortArray(totalSamples)
            var filled = 0
            while (filled < captureBuffer.size) {
                val read = recorder.read(captureBuffer, filled, captureBuffer.size - filled)
                if (read <= 0) break
                filled += read
            }
            track.stop()

            // Se descarta el primer tercio: el AGC necesita asentarse.
            val window = captureBuffer.copyOfRange(captureBuffer.size / 3, captureBuffer.size * 5 / 6)
            DoubleArray(frequencies.size) { index ->
                goertzel(window, sampleRate, frequencies[index]) / window.size * 2.0
            }
        } finally {
            runCatching { track.release() }
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
    }

    private fun preferredSource(): Int {
        // UNPROCESSED evita el filtrado del fabricante cuando está disponible.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }

    /** Energía en una única frecuencia; suficiente y preciso para tonos puros. */
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
        private const val DEFAULT_DURATION = 2.4
        private const val TONE_AMPLITUDE = 20_000.0
    }
}
