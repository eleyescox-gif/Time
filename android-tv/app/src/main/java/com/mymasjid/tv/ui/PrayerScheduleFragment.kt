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
import com.mymasjid.tv.calculation.PrayerTimeCalculator
import com.mymasjid.tv.utils.PrefsManager
import java.util.Calendar

/**
 * PrayerScheduleFragment
 *
 * ১. সবার উপরে: মসজিদের নাম + বাংলা ও হিজরি তারিখ
 * ২. তার পর: বড় করে সময় (ঘড়ি + সেকেন্ড + AM/PM) + বর্তমান ওয়াক্ত ব্যাজ
 * ৩. নিচে: ফজর, সূর্যোদয়, যোহর, আসর, মাগরিব, এশা (৬টি কার্ড)
 */
class PrayerScheduleFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())

    // UI Elements
    private lateinit var tvMosqueName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvBigClockMain: TextView
    private lateinit var tvBigClockSeconds: TextView
    private lateinit var tvBigClockAmPm: TextView
    private lateinit var tvCurrentWaqtBadge: TextView

    // 6 Cards
    private lateinit var cardFajr: View
    private lateinit var cardSunrise: View
    private lateinit var cardDhuhr: View
    private lateinit var cardAsr: View
    private lateinit var cardMaghrib: View
    private lateinit var cardIsha: View

    // Time text views
    private lateinit var tvFajrAzan: TextView
    private lateinit var tvFajrJamaat: TextView
    private lateinit var tvSunriseTime: TextView
    private lateinit var tvDhuhrAzan: TextView
    private lateinit var tvDhuhrJamaat: TextView
    private lateinit var tvAsrAzan: TextView
    private lateinit var tvAsrJamaat: TextView
    private lateinit var tvMaghribAzan: TextView
    private lateinit var tvMaghribJamaat: TextView
    private lateinit var tvIshaAzan: TextView
    private lateinit var tvIshaJamaat: TextView

    // প্রতি ১ সেকেন্ডে ঘড়ি ও তারিখ আপডেট হবে
    private val clockTickRunnable = object : Runnable {
        override fun run() {
            tickClock()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_prayer_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        tickClock()
        updatePrayerTimes()
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockTickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockTickRunnable)
    }

    private fun bindViews(view: View) {
        tvMosqueName       = view.findViewById(R.id.tvScheduleMosqueName)
        tvDate             = view.findViewById(R.id.tvScheduleDate)
        tvBigClockMain     = view.findViewById(R.id.tvBigClockMain)
        tvBigClockSeconds  = view.findViewById(R.id.tvBigClockSeconds)
        tvBigClockAmPm     = view.findViewById(R.id.tvBigClockAmPm)
        tvCurrentWaqtBadge = view.findViewById(R.id.tvCurrentWaqtBadge)

        cardFajr     = view.findViewById(R.id.cardFajr)
        cardSunrise  = view.findViewById(R.id.cardSunrise)
        cardDhuhr    = view.findViewById(R.id.cardDhuhr)
        cardAsr      = view.findViewById(R.id.cardAsr)
        cardMaghrib  = view.findViewById(R.id.cardMaghrib)
        cardIsha     = view.findViewById(R.id.cardIsha)

        tvFajrAzan      = view.findViewById(R.id.tvFajrAzan)
        tvFajrJamaat    = view.findViewById(R.id.tvFajrJamaat)
        tvSunriseTime   = view.findViewById(R.id.tvSunriseTime)
        tvDhuhrAzan     = view.findViewById(R.id.tvDhuhrAzan)
        tvDhuhrJamaat   = view.findViewById(R.id.tvDhuhrJamaat)
        tvAsrAzan       = view.findViewById(R.id.tvAsrAzan)
        tvAsrJamaat     = view.findViewById(R.id.tvAsrJamaat)
        tvMaghribAzan   = view.findViewById(R.id.tvMaghribAzan)
        tvMaghribJamaat = view.findViewById(R.id.tvMaghribJamaat)
        tvIshaAzan      = view.findViewById(R.id.tvIshaAzan)
        tvIshaJamaat    = view.findViewById(R.id.tvIshaJamaat)
    }

    /** প্রতি সেকেন্ডে ঘড়ি ও তারিখ আপডেট */
    private fun tickClock() {
        val cal = Calendar.getInstance()
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val h12 = cal.get(Calendar.HOUR)
        val displayH = if (h12 == 0) 12 else h12
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val isAm = cal.get(Calendar.AM_PM) == Calendar.AM

        val hStr = if (displayH < 10) "0$displayH" else "$displayH"
        val mStr = if (m < 10) "0$m" else "$m"
        val sStr = if (s < 10) "0$s" else "$s"

        tvBigClockMain.text    = "$hStr:$mStr"
        tvBigClockSeconds.text = ":$sStr"
        tvBigClockAmPm.text    = if (isAm) "AM" else "PM"

        // প্রতি সেকেন্ডের শুরুতে নামাজের সময় ও ওয়াক্ত রিফ্রেশ (s == 0)
        if (s == 0) {
            updatePrayerTimes()
        }

        // তারিখ
        val days   = arrayOf("রবিবার","সোমবার","মঙ্গলবার","বুধবার","বৃহস্পতিবার","শুক্রবার","শনিবার")
        val months = arrayOf("জানুয়ারি","ফেব্রুয়ারি","মার্চ","এপ্রিল","মে","জুন",
                             "জুলাই","আগস্ট","সেপ্টেম্বর","অক্টোবর","নভেম্বর","ডিসেম্বর")

        val dayName = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val monName = months[cal.get(Calendar.MONTH)]
        val d       = cal.get(Calendar.DAY_OF_MONTH)
        val yr      = cal.get(Calendar.YEAR)

        val hijri = toHijri(yr, cal.get(Calendar.MONTH) + 1, d)
        val hijriMonths = arrayOf(
            "মুহররম","সফর","রবিউল আউয়াল","রবিউস সানি",
            "জমাদিউল আউয়াল","জমাদিউস সানি","রজব",
            "শাবান","রমজান","শাওয়াল","জিলকদ","জিলহজ"
        )
        val hijriStr = "${hijri.third} ${hijriMonths[hijri.second - 1]}, ${hijri.first} হিজরি"

        tvDate.text = "$dayName, $d $monName $yr  |  $hijriStr"
    }

    /** ৬টি নামাজের সময়সূচি ও অ্যাক্টিভ কার্ড আপডেট */
    private fun updatePrayerTimes() {
        val ctx = context ?: return
        val cal = Calendar.getInstance()

        val calc = PrayerTimeCalculator(
            latitude       = PrefsManager.getLatitude(ctx),
            longitude      = PrefsManager.getLongitude(ctx),
            timezoneOffset = PrefsManager.getTimezoneOffset(ctx),
            fajrAngle      = PrefsManager.getFajrAngle(ctx),
            ishaAngle      = PrefsManager.getIshaAngle(ctx),
            isHanafi       = PrefsManager.isHanafi(ctx)
        )
        val times = calc.calculate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        // জামায়াত সময় হিসাব (আজান + offset মিনিট)
        val fajrJm    = times.fajr + PrefsManager.getJamaatOffset(ctx, "fajr") / 60.0
        val dhuhrJm   = times.dhuhr + PrefsManager.getJamaatOffset(ctx, "dhuhr") / 60.0
        val asrJm     = times.asr + PrefsManager.getJamaatOffset(ctx, "asr") / 60.0
        val maghribJm = times.maghrib + PrefsManager.getJamaatOffset(ctx, "maghrib") / 60.0
        val ishaJm    = times.isha + PrefsManager.getJamaatOffset(ctx, "isha") / 60.0

        // টেক্সট সেট
        tvFajrAzan.text      = PrayerTimeCalculator.formatTime(times.fajr)
        tvFajrJamaat.text    = PrayerTimeCalculator.formatTime(fajrJm)

        tvSunriseTime.text   = PrayerTimeCalculator.formatTime(times.sunrise)

        val isFriday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val tvDhuhrName: TextView? = view?.findViewById(R.id.tvDhuhrName)
        if (isFriday) {
            tvDhuhrName?.text = "জুমা"
            tvDhuhrJamaat.text = "01:30"
        } else {
            tvDhuhrName?.text = "যোহর"
            tvDhuhrJamaat.text = PrayerTimeCalculator.formatTime(dhuhrJm)
        }

        tvAsrAzan.text       = PrayerTimeCalculator.formatTime(times.asr)
        tvAsrJamaat.text     = PrayerTimeCalculator.formatTime(asrJm)

        tvMaghribAzan.text   = PrayerTimeCalculator.formatTime(times.maghrib)
        tvMaghribJamaat.text = PrayerTimeCalculator.formatTime(maghribJm)

        tvIshaAzan.text      = PrayerTimeCalculator.formatTime(times.isha)
        tvIshaJamaat.text    = PrayerTimeCalculator.formatTime(ishaJm)

        // বর্তমান ওয়াক্ত চিহ্নিতকরণ
        val nowDecHour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
        val activeIndex = getActiveIndex(times, nowDecHour)

        val cards = listOf(cardFajr, cardSunrise, cardDhuhr, cardAsr, cardMaghrib, cardIsha)
        cards.forEachIndexed { index, card ->
            if (index == activeIndex) {
                card.setBackgroundResource(R.drawable.bg_active_prayer_row)
            } else {
                card.setBackgroundResource(R.drawable.bg_prayer_row)
            }
        }

        // Badge টেক্সট
        val waqtNames = listOf("ফজর", "সূর্যোদয় (ইশরাক)", "যোহর", "আসর", "মাগরিব", "এশা")
        tvCurrentWaqtBadge.text = "চলমান ওয়াক্ত: ${waqtNames[activeIndex]}"
    }

    /** ০=ফজর, ১=সূর্যোদয়, ২=যোহর, ৩=আসর, ৪=মাগরিব, ৫=এশা */
    private fun getActiveIndex(
        times: PrayerTimeCalculator.PrayerTimes,
        nowH: Double
    ): Int {
        return when {
            nowH >= times.isha    || nowH < times.fajr    -> 5 // এশা
            nowH >= times.maghrib                         -> 4 // মাগরিব
            nowH >= times.asr                             -> 3 // আসর
            nowH >= times.dhuhr                           -> 2 // যোহর
            nowH >= times.sunrise                         -> 1 // সূর্যোদয়
            nowH >= times.fajr                            -> 0 // ফজর
            else                                          -> 5
        }
    }

    private fun toHijri(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val jd = julianDayNumber(gy, gm, gd)
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

    private fun julianDayNumber(y: Int, m: Int, d: Int): Long {
        var yy = y.toLong(); var mm = m.toLong()
        if (mm <= 2) { yy--; mm += 12 }
        val a = yy / 100
        val b = 2 - a + a / 4
        return (365.25 * (yy + 4716)).toLong() + (30.6001 * (mm + 1)).toLong() + d + b - 1524
    }
}
