package com.mymasjid.tv.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.mymasjid.tv.calculation.PrayerTimeCalculator
import java.util.Calendar

/**
 * ThemeManager
 *
 * Feature 5: মাগরিবের পর স্বয়ংক্রিয় Dark Theme, ফজরে Light Theme।
 * Settings toggle: AUTO_DARK_THEME — disable করলে manual control।
 */
object ThemeManager {

    /** থিম check করে apply করে (Activity create / resume-এ call করুন) */
    fun applyAutoTheme(ctx: Context) {
        if (!PrefsManager.isAutoDarkTheme(ctx)) return

        val mode = if (shouldUseDarkTheme(ctx)) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        // যদি ইতোমধ্যে সঠিক mode থাকে তাহলে recreate দরকার নেই
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
            // Note: Activity.recreate() caller-এ করতে হবে (if needed)
        }
    }

    /**
     * মাগরিব → ফজরের মধ্যে dark, বাকি সময় light।
     */
    fun shouldUseDarkTheme(ctx: Context): Boolean {
        val cal = Calendar.getInstance()
        val year  = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day   = cal.get(Calendar.DAY_OF_MONTH)
        val nowH  = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0

        val calculator = PrayerTimeCalculator(
            latitude       = PrefsManager.getLatitude(ctx),
            longitude      = PrefsManager.getLongitude(ctx),
            timezoneOffset = PrefsManager.getTimezoneOffset(ctx),
            fajrAngle      = PrefsManager.getFajrAngle(ctx),
            ishaAngle      = PrefsManager.getIshaAngle(ctx),
            isHanafi       = PrefsManager.isHanafi(ctx)
        )
        val times = calculator.calculate(year, month, day)

        // মাগরিব থেকে ফজর পর্যন্ত dark
        return nowH >= times.maghrib || nowH < times.fajr
    }

    /**
     * Manual override: user যদি নিজে থেকে toggle করে।
     * AUTO_DARK_THEME = false হলে এই function কাজ করে।
     */
    fun setDarkMode(ctx: Context, dark: Boolean) {
        PrefsManager.setBoolean(ctx, PrefsManager.KEY_AUTO_THEME, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else      AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
