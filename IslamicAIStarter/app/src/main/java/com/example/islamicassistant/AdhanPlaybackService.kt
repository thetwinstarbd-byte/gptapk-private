package com.example.islamicassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

object AdhanNotifications {
    const val CHANNEL_ID = "prayer_adhan_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "নামাজ ও আজান", NotificationManager.IMPORTANCE_HIGH)
                channel.description = "নামাজের সময়ের বিজ্ঞপ্তি ও আজান"
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notification(context: Context, title: String, body: String): Notification {
        ensureChannel(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(body).setOngoing(true).build()
    }

    fun show(context: Context, prayerName: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(5200, notification(context, "$prayerName-এর সময়", body))
    }
}

/** Plays the user's supplied recorded adhan. Fajr uses its separate recording. */
class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    private var prayerName: String = "নামাজ"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prayerName = intent?.getStringExtra("prayer_name") ?: "নামাজ"
        val isFajr = prayerName.contains("ফজর") || intent?.getIntExtra("prayer_id", -1) == 0
        startForeground(5201, AdhanNotifications.notification(
            this, "$prayerName-এর সময় হয়েছে", "আপনার দেওয়া আজানের অডিও চালানো হচ্ছে।"
        ))
        releasePlayer()
        try {
            val audioRes = if (isFajr) R.raw.adhan_fajr else R.raw.adhan_regular
            player = MediaPlayer.create(this, audioRes)?.apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setOnCompletionListener { finishService() }
                setOnErrorListener { _, _, _ ->
                    AdhanNotifications.show(this@AdhanPlaybackService, prayerName, "আজানের অডিও চালানো যায়নি।")
                    finishService()
                    true
                }
                start()
            }
            if (player == null) {
                AdhanNotifications.show(this, prayerName, "আজানের অডিও ফাইল চালু করা যায়নি।")
                finishService()
            }
        } catch (_: Exception) {
            AdhanNotifications.show(this, prayerName, "আজানের অডিও চালু করা যায়নি।")
            finishService()
        }
        return START_NOT_STICKY
    }

    private fun releasePlayer() {
        try { player?.setOnCompletionListener(null); player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun finishService() {
        releasePlayer()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
