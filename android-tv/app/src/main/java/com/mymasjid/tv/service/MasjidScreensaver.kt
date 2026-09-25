package com.mymasjid.tv.service

import android.service.dreams.DreamService
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import com.mymasjid.tv.R
import com.mymasjid.tv.calculation.PrayerTimeCalculator
import com.mymasjid.tv.utils.PrefsManager
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask

/**
 * MasjidScreensaver — Feature 9: Android TV Screensaver (DreamService)
 *
 * TV idle থাকলে এই screensaver auto-launch হয় এবং প্রার্থনার সময়সূচি দেখায়।
 * Settings > Display > Screen saver > MyMasjid সিলেক্ট করতে হবে।
 *
 * DreamService: Android 4.2+ (API 17+) সাপোর্ট।
 */
class MasjidScreensaver : DreamService() {

    companion object {
        private const val TAG = "MasjidScreensaver"
    }

    private var updateTimer: Timer? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Fullscreen interactive (remote দিয়ে dismiss করা যাবে)
        isInteractive = true
        isFullscreen = true

        // Screensaver layout inflate
        val view = LayoutInflater.from(this)
            .inflate(R.layout.screensaver_layout, null)
        setContentView(view)

        updatePrayerTimes(view)
        startAutoUpdate(view)

        Log.i(TAG, "Screensaver started")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateTimer?.cancel()
        updateTimer = null
        Log.i(TAG, "Screensaver stopped")
    }

    private fun updatePrayerTimes(view: View) {
        val cal = Calendar.getInstance()
        val calc = PrayerTimeCalculator(
            latitude       = PrefsManager.getLatitude(this),
            longitude      = PrefsManager.getLongitude(this),
            timezoneOffset = PrefsManager.getTimezoneOffset(this),
            fajrAngle      = PrefsManager.getFajrAngle(this),
            ishaAngle      = PrefsManager.getIshaAngle(this),
            isHanafi       = PrefsManager.isHanafi(this)
        )
        val times = calc.calculate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        // Prayer times UI update
        view.findViewById<TextView>(R.id.tvSsFajr)?.text    = PrayerTimeCalculator.formatTime(times.fajr)
        view.findViewById<TextView>(R.id.tvSsDhuhr)?.text   = PrayerTimeCalculator.formatTime(times.dhuhr)
        view.findViewById<TextView>(R.id.tvSsAsr)?.text     = PrayerTimeCalculator.formatTime(times.asr)
        view.findViewById<TextView>(R.id.tvSsMaghrib)?.text = PrayerTimeCalculator.formatTime(times.maghrib)
        view.findViewById<TextView>(R.id.tvSsIsha)?.text    = PrayerTimeCalculator.formatTime(times.isha)

        // Date display
        val dateStr = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH)+1}/${cal.get(Calendar.YEAR)}"
        view.findViewById<TextView>(R.id.tvSsDate)?.text = dateStr
    }

    /** প্রতি মিনিটে UI refresh */
    private fun startAutoUpdate(view: View) {
        updateTimer = Timer()
        updateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread { updatePrayerTimes(view) }
            }
        }, 60_000L, 60_000L)
    }

    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }
}
