package com.mymasjid.tv.notification

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.mymasjid.tv.R
import com.mymasjid.tv.calculation.PrayerTimeCalculator
import com.mymasjid.tv.utils.PrefsManager
import java.util.Calendar

/**
 * PreJamaatNotifier — Feature 4: Pre-Jamaat Mobile-Silent Notification
 *
 * প্রতি জামাতের ঠিক ১ মিনিট আগে একটি slide-in banner দেখায়।
 * Banner ১৫ সেকেন্ড পর auto-dismiss হয়।
 *
 * দুটি অংশ:
 * 1. scheduleAll(): AlarmManager দিয়ে সেদিনের সব জামাত schedule করে
 * 2. showBanner(): Activity-র root view-এ slide-in overlay দেখায়
 */
object PreJamaatNotifier {

    private const val TAG = "PreJamaatNotifier"
    private const val BANNER_DURATION_MS = 15_000L  // ১৫ সেকেন্ড
    private const val PRE_JAMAAT_OFFSET_MS = 60_000L // ১ মিনিট আগে

    // AlarmManager request codes (প্রতিটি ওয়াক্তের জন্য আলাদা)
    private const val REQ_FAJR    = 200
    private const val REQ_DHUHR   = 201
    private const val REQ_ASR     = 202
    private const val REQ_MAGHRIB = 203
    private const val REQ_ISHA    = 204

    /**
     * সেদিনের সব জামাতের জন্য AlarmManager এ notification schedule করে।
     * প্রতিদিন একবার call করতে হবে (রাতে বা ফজর schedule-এ)।
     */
    fun scheduleAll(context: Context) {
        if (!PrefsManager.isPreJamaatNotifEnabled(context)) {
            cancelAll(context)
            return
        }

        val cal = Calendar.getInstance()
        val calc = PrayerTimeCalculator(
            latitude       = PrefsManager.getLatitude(context),
            longitude      = PrefsManager.getLongitude(context),
            timezoneOffset = PrefsManager.getTimezoneOffset(context),
            fajrAngle      = PrefsManager.getFajrAngle(context),
            ishaAngle      = PrefsManager.getIshaAngle(context),
            isHanafi       = PrefsManager.isHanafi(context)
        )
        val times = calc.calculate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )

        val prayers = mapOf(
            "fajr"    to Pair(times.fajr,    REQ_FAJR),
            "dhuhr"   to Pair(times.dhuhr,   REQ_DHUHR),
            "asr"     to Pair(times.asr,     REQ_ASR),
            "maghrib" to Pair(times.maghrib, REQ_MAGHRIB),
            "isha"    to Pair(times.isha,    REQ_ISHA)
        )

        for ((prayerId, pair) in prayers) {
            val (azanHour, reqCode) = pair
            val offsetMin = PrefsManager.getJamaatOffset(context, prayerId)
            // জামাত সময় = আজান + offset মিনিট
            val jamaatHour = azanHour + offsetMin / 60.0
            // Notification সময় = জামাত - ১ মিনিট
            val notifHour = jamaatHour - 1.0 / 60.0

            scheduleAlarm(context, notifHour, prayerId, reqCode)
            Log.d(TAG, "Scheduled [$prayerId] notif at ${PrayerTimeCalculator.formatTime(notifHour)}")
        }
    }

    private fun scheduleAlarm(
        context: Context,
        decimalHour: Double,
        prayerName: String,
        requestCode: Int
    ) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, decimalHour.toInt())
            set(Calendar.MINUTE, ((decimalHour % 1) * 60).toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // ইতোমধ্যে পেরিয়ে গেলে skip
        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
            putExtra("prayer_name", prayerName)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    fun cancelAll(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(REQ_FAJR, REQ_DHUHR, REQ_ASR, REQ_MAGHRIB, REQ_ISHA).forEach { code ->
            val pi = PendingIntent.getBroadcast(
                context, code, Intent(context, NotificationBroadcastReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmMgr.cancel(it) }
        }
    }

    /**
     * Feature 4: Slide-in banner overlay।
     * Activity root-এ inflate করে slide-in animation দিয়ে দেখায়,
     * ১৫ সেকেন্ড পর slide-out করে remove করে।
     *
     * @param rootView Activity-র root ViewGroup
     * @param prayerNameBn বাংলায় ওয়াক্তের নাম (যেমন "আসর")
     */
    fun showBanner(rootView: ViewGroup, prayerNameBn: String) {
        val inflater = LayoutInflater.from(rootView.context)
        val banner = inflater.inflate(R.layout.view_pre_jamaat_banner, rootView, false)

        // Prayer name set করো
        val tvMsg = banner.findViewById<android.widget.TextView>(R.id.tvBannerMsg)
        tvMsg?.text = "$prayerNameBn জামাতের ১ মিনিট বাকি\nদয়া করে মোবাইল সাইলেন্ট বা বন্ধ করুন 📵"

        // Root-এ add
        rootView.addView(banner)

        // Slide-in animation (top থেকে নেমে আসবে)
        slideIn(banner) {
            // ১৫ সেকেন্ড পর slide-out করে remove করবে
            Handler(Looper.getMainLooper()).postDelayed({
                slideOut(banner) {
                    rootView.removeView(banner)
                }
            }, BANNER_DURATION_MS)
        }
    }

    private fun slideIn(view: View, onEnd: () -> Unit) {
        view.translationY = -400f
        view.alpha = 0f
        val slide = ObjectAnimator.ofFloat(view, "translationY", -400f, 0f)
        val fade  = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        AnimatorSet().apply {
            playTogether(slide, fade)
            duration = 500
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }

    private fun slideOut(view: View, onEnd: () -> Unit) {
        val slide = ObjectAnimator.ofFloat(view, "translationY", 0f, -400f)
        val fade  = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        AnimatorSet().apply {
            playTogether(slide, fade)
            duration = 400
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }
}
