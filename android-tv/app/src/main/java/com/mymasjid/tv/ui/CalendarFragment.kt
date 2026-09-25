package com.mymasjid.tv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mymasjid.tv.R
import java.util.Calendar

/**
 * CalendarFragment — Feature 8: Display Mode 2 (Calendar View)
 * Gregorian + Hijri calendar দেখায়।
 * Hijri calculation: simplified offset-based (UTC+6 for Bangladesh)।
 */
class CalendarFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateUI() {
        val view = view ?: return
        val cal = Calendar.getInstance()

        // Gregorian date
        val gregDays   = arrayOf("রবিবার","সোমবার","মঙ্গলবার","বুধবার","বৃহস্পতিবার","শুক্রবার","শনিবার")
        val gregMonths = arrayOf("জানুয়ারি","ফেব্রুয়ারি","মার্চ","এপ্রিল","মে","জুন",
                                 "জুলাই","আগস্ট","সেপ্টেম্বর","অক্টোবর","নভেম্বর","ডিসেম্বর")

        val dayName = gregDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val monName = gregMonths[cal.get(Calendar.MONTH)]
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val yr  = cal.get(Calendar.YEAR)

        view.findViewById<TextView>(R.id.tvCalGregDate)?.text = "$dayName"
        view.findViewById<TextView>(R.id.tvCalGregFull)?.text = "$day $monName, $yr"

        // Hijri date (simplified calculation)
        val hijri = toHijri(yr, cal.get(Calendar.MONTH) + 1, day)
        val hijriMonths = arrayOf(
            "মুহররম","সফর","রবিউল আউয়াল","রবিউস সানি",
            "জমাদিউল আউয়াল","জমাদিউস সানি","রজব",
            "শাবান","রমজান","শাওয়াল","জিলকদ","জিলহজ"
        )
        view.findViewById<TextView>(R.id.tvCalHijriDate)?.text =
            "${hijri.third} ${hijriMonths[hijri.second - 1]}, ${hijri.first} হিজরি"

        // Jummah notice
        val isJummah = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        view.findViewById<TextView>(R.id.tvCalJummah)?.visibility =
            if (isJummah) View.VISIBLE else View.GONE
    }

    /**
     * Simplified Gregorian → Hijri conversion।
     * Returns Triple(year, month, day)。
     * সূত্র: Jean Meeus "Astronomical Algorithms" অনুসারে।
     */
    private fun toHijri(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        // Julian Day Number
        val jd = julianDayNumber(gy, gm, gd)
        return julianToHijri(jd)
    }

    private fun julianDayNumber(y: Int, m: Int, d: Int): Long {
        var yy = y.toLong(); var mm = m.toLong()
        if (mm <= 2) { yy--; mm += 12 }
        val a = yy / 100
        val b = 2 - a + a / 4
        return (365.25 * (yy + 4716)).toLong() + (30.6001 * (mm + 1)).toLong() + d + b - 1524
    }

    private fun julianToHijri(jd: Long): Triple<Int, Int, Int> {
        val l = jd - 1948440L + 10632L
        val n = (l - 1) / 10631L
        val ll = l - 10631L * n + 354L
        val j = ((10985L - ll) / 5316L) * ((50L * ll) / 17719L) +
                (ll / 5670L) * ((43L * ll) / 15238L)
        val lll = ll - ((30L - j) / 15L) * ((17719L * j) / 50L) -
                  (j / 16L) * ((15238L * j) / 43L) + 29L
        val month = (24L * lll) / 709L
        val day   = lll - (709L * month) / 24L
        val year  = 30L * n + j - 30L + (month - 1) / 12L + 1L
        return Triple(year.toInt(), ((month % 12) + 1).toInt(), day.toInt())
    }
}
