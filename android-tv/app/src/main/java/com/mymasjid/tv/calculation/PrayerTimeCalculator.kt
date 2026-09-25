package com.mymasjid.tv.calculation

import kotlin.math.*

/**
 * PrayerTimeCalculator — সম্পূর্ণ offline local prayer time calculation।
 *
 * Algorithm: জ্যোতির্বিদ্যার সূর্যের কোণ-ভিত্তিক method
 * - Fajr/Isha: customizable angle (ডিফল্ট 18°)
 * - Asr: Hanafi (shadow = 2x object) বা Shafi (shadow = 1x object)
 * - Sunrise/Maghrib: sun at -0.833° below horizon
 * - Dhuhr: solar noon
 */
class PrayerTimeCalculator(
    private val latitude: Double,
    private val longitude: Double,
    private val timezoneOffset: Double,
    private val fajrAngle: Double = 18.0,
    private val ishaAngle: Double = 18.0,
    private val isHanafi: Boolean = true
) {
    data class PrayerTimes(
        val fajr: Double,       // decimal hours
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    )

    /**
     * [year], [month] (1-12), [day] দিয়ে সেদিনের prayer times হিসাব করে।
     * Return: PrayerTimes (decimal hours in local time)
     */
    fun calculate(year: Int, month: Int, day: Int): PrayerTimes {
        val jd = julianDay(year, month, day)
        return computeTimes(jd)
    }

    // ── Julian Day Number ──────────────────────────────────────────────────
    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y--; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    // ── Sun position ───────────────────────────────────────────────────────
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = toRad(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val l = toRad(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = toRad(23.439 - 0.00000036 * d)
        val ra = toDeg(atan2(cos(e) * sin(l), cos(l))) / 15.0
        val dec = toDeg(asin(sin(e) * sin(l)))
        // Equation of time
        val eqT = q / 15.0 - fixHour(ra)
        return Pair(dec, eqT)
    }

    // ── Dhuhr (solar noon) ─────────────────────────────────────────────────
    private fun midDay(jd: Double): Double {
        val (_, eqT) = sunPosition(jd)
        return 12.0 - eqT
    }

    // ── Hour angle for given altitude ──────────────────────────────────────
    private fun hourAngle(dec: Double, latitude: Double, targetAlt: Double): Double {
        val lat = toRad(latitude)
        val d = toRad(dec)
        val a = toRad(targetAlt)
        val cos_h = (sin(a) - sin(d) * sin(lat)) / (cos(d) * cos(lat))
        return if (cos_h.absoluteValue > 1.0) Double.NaN
        else toDeg(acos(cos_h)) / 15.0
    }

    // ── Asr altitude (Hanafi: factor=2, Shafii: factor=1) ─────────────────
    private fun asrAlt(dec: Double): Double {
        val factor = if (isHanafi) 2.0 else 1.0
        val lat = toRad(latitude)
        val d = toRad(dec)
        val t = atan(1.0 / (factor + tan((lat - d).absoluteValue)))
        return toDeg(t)
    }

    // ── Main computation ───────────────────────────────────────────────────
    private fun computeTimes(jd: Double): PrayerTimes {
        val (dec, _) = sunPosition(jd)
        val noon = midDay(jd)

        // Fajr / Isha: Sun below horizon at given angle
        val fajrHA   = hourAngle(dec, latitude, -fajrAngle)
        val ishaHA   = hourAngle(dec, latitude, -ishaAngle)
        // Sunrise / Maghrib: Sun at -0.833° (atmospheric refraction + disc size)
        val sunriseHA = hourAngle(dec, latitude, -0.833)
        // Asr
        val asrAltDeg = asrAlt(dec)
        val asrHA    = hourAngle(dec, latitude, asrAltDeg)

        val offset = longitude / 15.0 - timezoneOffset   // longitude → timezone diff

        return PrayerTimes(
            fajr    = (noon - fajrHA  + timezoneOffset - longitude / 15.0).let { localHours(it) },
            sunrise = (noon - sunriseHA + timezoneOffset - longitude / 15.0).let { localHours(it) },
            dhuhr   = (noon + timezoneOffset - longitude / 15.0).let { localHours(it) },
            asr     = (noon + asrHA + timezoneOffset - longitude / 15.0).let { localHours(it) },
            maghrib = (noon + sunriseHA + timezoneOffset - longitude / 15.0).let { localHours(it) },
            isha    = (noon + ishaHA + timezoneOffset - longitude / 15.0).let { localHours(it) }
        )
    }

    private fun localHours(h: Double) = fixHour(h)

    // ── Math helpers ───────────────────────────────────────────────────────
    private fun toRad(deg: Double) = deg * PI / 180.0
    private fun toDeg(rad: Double) = rad * 180.0 / PI
    private fun fixHour(h: Double): Double {
        var r = h % 24.0
        if (r < 0) r += 24.0
        return r
    }

    // ── Format helper ──────────────────────────────────────────────────────
    companion object {
        /** Decimal hours → "HH:MM" string */
        fun formatTime(hours: Double): String {
            if (hours.isNaN()) return "--:--"
            val h = hours.toInt()
            val m = Math.round((hours - h) * 60).toInt()
            val hh = if (h % 24 < 10) "0${h % 24}" else "${h % 24}"
            val mm = if (m < 10) "0$m" else "$m"
            return "$hh:$mm"
        }

        /** Decimal hours → total minutes from midnight */
        fun toMinutes(hours: Double): Int = (hours * 60).toInt()

        /** Decimal hours → milliseconds from midnight */
        fun toMillis(hours: Double): Long = (hours * 3_600_000).toLong()
    }
}
