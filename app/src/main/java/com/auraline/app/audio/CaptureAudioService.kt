package com.auraline.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.auraline.app.R

/**
 * Servicio experimental de "Modo captura": captura el audio de otras apps con
 * AudioPlaybackCapture, lo procesa con el motor nativo (NativeEngine) y lo reproduce.
 *
 * Es un primer paso funcional para validar el camino de captura + salida propia en el
 * dispositivo. No sustituye todavía a DynamicsProcessing: se activa manualmente desde
 * Ajustes y se detiene al cerrar el servicio.
 */
class CaptureAudioService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Modo captura activo"))

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
            ?: android.app.Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (data != null && resultCode == android.app.Activity.RESULT_OK) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            startCapture()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        if (running) return
        val projection = mediaProjection ?: return

        val sampleRate = 48000
        val channelMaskIn = AudioFormat.CHANNEL_IN_STEREO
        val channelMaskOut = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMaskIn, encoding)
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)

        val captureConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .excludeUid(android.os.Process.myUid())
                .build()
        } else {
            null
        }

        val recordBuilder = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMaskIn)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)

        if (captureConfig != null) {
            recordBuilder.setAudioPlaybackCaptureConfig(captureConfig)
        }

        val rec = recordBuilder.build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMaskOut)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        NativeEngine.init(sampleRate)
        NativeEngine.setEnabled(true)
        NativeEngine.setBandCount(25)
        NativeEngine.setLimiterCeiling(-2f)

        audioRecord = rec
        audioTrack = track
        rec.startRecording()
        track.play()

        running = true
        worker = Thread {
            val shortBuf = ShortArray(bufferSize / 2)
            val floatBuf = FloatArray(shortBuf.size)
            while (running) {
                val read = rec.read(shortBuf, 0, shortBuf.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        floatBuf[i] = shortBuf[i] / 32768f
                    }
                    val frames = read / 2
                    NativeEngine.processBlock(floatBuf, frames)
                    for (i in 0 until read) {
                        val v = (floatBuf[i] * 32767f).toInt().coerceIn(-32768, 32767)
                        shortBuf[i] = v.toShort()
                    }
                    track.write(shortBuf, 0, read)
                }
            }
        }.apply { name = "auraline-capture"; start() }
    }

    private fun stopCapture() {
        running = false
        worker?.join(1000)
        worker = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioRecord = null
        audioTrack = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        NativeEngine.setEnabled(false)
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modo captura AuraLine",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AuraLine")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "auraline_capture"
        const val NOTIFICATION_ID = 42
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }
}