package com.example.islamicassistant

import java.util.Calendar
import kotlin.math.*

data class PrayerSchedule(val fajr: String, val sunrise: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String)

/** Approximate local solar-time calculation. Confirm with a trusted local timetable. */
object PrayerCalculator {
    private const val D = Math.PI / 180.0
    private const val R = 180.0 / Math.PI

    fun qiblaBearing(latitude: Double, longitude: Double): Double {
        val kaabaLat = 21.4225 * D
        val kaabaLon = 39.8262 * D
        val lat = latitude * D
        val dLon = kaabaLon - longitude * D
        val y = sin(dLon)
        val x = cos(lat) * tan(kaabaLat) - sin(lat) * cos(dLon)
        return ((atan2(y, x) * R) + 360.0) % 360.0
    }

    fun calculate(latitude: Double, longitude: Double, date: Calendar = Calendar.getInstance(), hanafiAsr: Boolean = true): PrayerSchedule {
        val day = date.get(Calendar.DAY_OF_YEAR)
        val gamma = 2.0 * Math.PI / 365.0 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val tz = date.get(Calendar.ZONE_OFFSET) / 3600000.0 + date.get(Calendar.DST_OFFSET) / 3600000.0
        val noon = 720.0 - 4.0 * longitude - eqTime + tz * 60.0
        fun hourAngle(altitudeDegrees: Double): Double {
            val lat = latitude * D
            val alt = altitudeDegrees * D
            val cosH = (sin(alt) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
            if (cosH < -1 || cosH > 1) return Double.NaN
            return acos(cosH) * R * 4.0
        }
        fun fmt(minutes: Double): String {
            if (!minutes.isFinite()) return "অঞ্চলভেদে নির্ণয় করা যায়নি"
            val normalized = ((minutes % 1440.0) + 1440.0) % 1440.0
            val h = normalized.toInt() / 60
            val m = normalized.toInt() % 60
            return String.format(java.util.Locale.US, "%02d:%02d", h, m)
        }
        val sunrise = noon - hourAngle(-0.833)
        val sunset = noon + hourAngle(-0.833)
        val fajr = noon - hourAngle(-18.0)
        val isha = noon + hourAngle(-17.0)
        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val lat = latitude * D
        val asrAltitude = atan(1.0 / (asrFactor + tan(abs(lat - decl)))) * R
        val asr = noon + hourAngle(asrAltitude)
        return PrayerSchedule(fmt(fajr), fmt(sunrise), fmt(noon), fmt(asr), fmt(sunset), fmt(isha))
    }
}
