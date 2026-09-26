package com.mymasjid.tv.notification

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * PreJamaatNotifier — Feature 4
 *
 * FIX: Android 12+ SecurityException: SCHEDULE_EXACT_ALARM
 * Multi-version safe:
 *   API 33+    → setExact() [USE_EXACT_ALARM, no user grant]
 *   API 31-32  → canScheduleExactAlarms() → setExact() or setWindow()
 *   API 23-30  → setExactAndAllowWhileIdle()
 *   API 21-22  → setExact()
 */
object PreJamaatNotifier {

    private const val TAG = "PreJamaatNotifier"
    private const val BANNER_DURATION_MS   = 15_000L
    private const val PRE_JAMAAT_OFFSET_MS = 60_000L

    private const val REQ_FAJR    = 200
    private const val REQ_DHUHR   = 201
    private const val REQ_ASR     = 202
    private const val REQ_MAGHRIB = 203
    private const val REQ_ISHA    = 204

    fun scheduleAll(context: Context) {
        if (!PrefsManager.isPreJamaatNotifEnabled(context)) {
            cancelAll(context)
            return
        }
        val cal  = Calendar.getInstance()
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
            val offsetMin  = PrefsManager.getJamaatOffset(context, prayerId)
            val jamaatHour = azanHour + offsetMin / 60.0
            val notifHour  = jamaatHour - 1.0 / 60.0
            scheduleAlarm(context, notifHour, prayerId, reqCode)
        }
    }

    /**
     * CRASH-SAFE — SecurityException থেকে সম্পূর্ণ সুরক্ষিত।
     * App কখনো crash হবে না, শুধু alarm skip হবে।
     */
    private fun scheduleAlarm(
        context: Context,
        decimalHour: Double,
        prayerName: String,
        requestCode: Int
    ) {
        try {
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, decimalHour.toInt())
                set(Calendar.MINUTE, ((decimalHour % 1) * 60).toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) return

            val intent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
                putExtra("prayer_name", prayerName)
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            when {
                // Android 13+ (API 33): USE_EXACT_ALARM — user grant লাগে না
                Build.VERSION.SDK_INT >= 33 -> {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    Log.d(TAG, "[$prayerName] setExact() [API 33+]")
                }
                // Android 12 (API 31-32): permission check করে
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmMgr.canScheduleExactAlarms()) {
                        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                        Log.d(TAG, "[$prayerName] setExact() [API 31-32, granted]")
                    } else {
                        // ±5 min window fallback — crash-free
                        alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 5 * 60 * 1000L, pi)
                        Log.w(TAG, "[$prayerName] setWindow() fallback [permission not granted]")
                    }
                }
                // Android 6-11 (API 23-30): Doze-safe
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    Log.d(TAG, "[$prayerName] setExactAndAllowWhileIdle() [API 23-30]")
                }
                // Android 5.0-5.1 (API 21-22)
                else -> {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    Log.d(TAG, "[$prayerName] setExact() [API 21-22]")
                }
            }
        } catch (se: SecurityException) {
            // Crash হবে না — শুধু log হবে
            Log.e(TAG, "[$prayerName] SecurityException — alarm skipped (SCHEDULE_EXACT_ALARM not granted)", se)
        } catch (e: Exception) {
            Log.e(TAG, "[$prayerName] Alarm scheduling failed: ${e.message}", e)
        }
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

    fun showBanner(rootView: ViewGroup, prayerNameBn: String) {
        try {
            val inflater = LayoutInflater.from(rootView.context)
            val banner   = inflater.inflate(R.layout.view_pre_jamaat_banner, rootView, false)
            val tvMsg    = banner.findViewById<android.widget.TextView>(R.id.tvBannerMsg)
            tvMsg?.text  = "$prayerNameBn জামাতের ১ মিনিট বাকি\nদয়া করে মোবাইল সাইলেন্ট বা বন্ধ করুন 📵"
            rootView.addView(banner)
            slideIn(banner) {
                Handler(Looper.getMainLooper()).postDelayed({
                    slideOut(banner) {
                        try { rootView.removeView(banner) } catch (_: Exception) {}
                    }
                }, BANNER_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "showBanner failed: ${e.message}", e)
        }
    }

    private fun slideIn(view: View, onEnd: () -> Unit) {
        view.translationY = -400f
        view.alpha = 0f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "translationY", -400f, 0f),
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }

    private fun slideOut(view: View, onEnd: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "translationY", 0f, -400f),
                ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
            )
            duration = 400
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = onEnd()
            })
            start()
        }
    }
}
