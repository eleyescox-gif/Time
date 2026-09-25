package com.mymasjid.tv.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * ScreenManager
 *
 * Feature 6: Full-Screen Immersive Mode
 * Feature 7: Keep Screen Always On
 *
 * TV app-এ সবসময় immersive mode + optional screen-always-on।
 * Low-API (21+) compatibility নিশ্চিত।
 */
object ScreenManager {

    /**
     * Feature 6: Immersive full-screen — status bar + nav bar সম্পূর্ণ লুকিয়ে রাখে।
     * Activity.onWindowFocusChanged() এও call করতে হবে যাতে swipe-back restore না হয়।
     */
    fun enableImmersiveMode(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: Modern WindowInsetsController
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // API 21-29: Legacy system UI flags
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    /**
     * Feature 7: Screen সবসময় জ্বলতে রাখে (TV sleep/timeout বন্ধ)।
     * Settings toggle দিয়ে on/off করা যায়।
     */
    fun setKeepScreenOn(window: Window, enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Activity.onWindowFocusChanged() এ call করুন —
     * system UI যদি কোনোভাবে দেখা যায়, এটি আবার লুকিয়ে দেয়।
     */
    fun onWindowFocusChanged(window: Window, hasFocus: Boolean) {
        if (hasFocus) enableImmersiveMode(window)
    }

    /**
     * Feature 6: Full-screen window flag (Activity create-এর আগে)।
     * TV-তে সাধারণত FLAG_FULLSCREEN যথেষ্ট।
     */
    fun applyFullscreenFlag(window: Window) {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }
}
