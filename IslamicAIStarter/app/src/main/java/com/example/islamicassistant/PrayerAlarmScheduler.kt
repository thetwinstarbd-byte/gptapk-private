package com.example.islamicassistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object PrayerAlarmScheduler {
    private val prayerNames = listOf("ফজর", "যোহর", "আসর", "মাগরিব", "ইশা")

    fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences("islamic_ai", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("adhan_enabled", false)) return
        val lat = (prefs.getString("latitude", "23.8103") ?: "23.8103").toDoubleOrNull() ?: 23.8103
        val lon = (prefs.getString("longitude", "90.4125") ?: "90.4125").toDoubleOrNull() ?: 90.4125
        val hanafi = prefs.getBoolean("hanafi_asr", true)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val schedule = PrayerCalculator.calculate(lat, lon, hanafiAsr = hanafi)
        val entries = listOf(schedule.fajr, schedule.dhuhr, schedule.asr, schedule.maghrib, schedule.isha)
        val now = System.currentTimeMillis()
        entries.forEachIndexed { index, text ->
            val parts = text.split(":")
            if (parts.size != 2) return@forEachIndexed
            val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
            val minute = parts[1].toIntOrNull() ?: return@forEachIndexed
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            val intent = Intent(context, PrayerAlarmReceiver::class.java).putExtra("prayer_name", prayerNames[index]).putExtra("prayer_id", index)
            val pending = PendingIntent.getBroadcast(context, 4100 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
                }
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repeat(5) { index ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, 4100 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.cancel(pending)
        }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("islamic_ai", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("adhan_enabled", false)) return
        val name = intent?.getStringExtra("prayer_name") ?: "নামাজ"
        val service = Intent(context, AdhanPlaybackService::class.java).putExtra("prayer_name", name)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
        } catch (_: Exception) {
            AdhanNotifications.show(context, name, "নামাজের সময় হয়েছে।")
        }
        PrayerAlarmScheduler.scheduleNext(context)
    }
}

class PrayerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            PrayerAlarmScheduler.scheduleNext(context)
        }
    }
}
