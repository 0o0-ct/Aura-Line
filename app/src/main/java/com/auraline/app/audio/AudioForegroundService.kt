package com.auraline.app.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.auraline.app.R
import com.auraline.app.ui.MainActivity

class AudioForegroundService : Service() {

    private lateinit var notificationManager: NotificationManagerCompat
    private var currentChannelId = "auraline_audio_channel"

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                currentChannelId,
                "AuraLine Audio",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Control del motor de audio de AuraLine"
                enableLights(true)
                lightColor = 0x00E5CC.toInt()
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, AudioToggleReceiver::class.java)
        val togglePending = PendingIntent.getBroadcast(
            this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, currentChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AuraLine")
            .setContentText("Motor de audio activo")
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Apagar",
                    togglePending
                ).build()
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_TOGGLE_AUDIO = "com.auraline.app.action.TOGGLE_AUDIO"
    }
}
