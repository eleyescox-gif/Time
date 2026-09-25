package com.mymasjid.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.mymasjid.tv.databinding.ActivityMainBinding
import com.mymasjid.tv.notification.NotificationBroadcastReceiver
import com.mymasjid.tv.notification.PreJamaatNotifier
import com.mymasjid.tv.service.TimeKeeperService
import com.mymasjid.tv.ui.CalendarFragment
import com.mymasjid.tv.ui.ClockFragment
import com.mymasjid.tv.ui.HadithFragment
import com.mymasjid.tv.ui.PrayerScheduleFragment
import com.mymasjid.tv.utils.PrefsManager
import com.mymasjid.tv.utils.ScreenManager
import com.mymasjid.tv.utils.ThemeManager

/**
 * MainActivity — প্রধান Display Screen।
 *
 * Features integrated:
 * - Feature 2: D-pad navigation (remote control)
 * - Feature 4: Pre-Jamaat banner receiver
 * - Feature 5: Auto dark/light theme
 * - Feature 6: Immersive fullscreen
 * - Feature 7: Keep screen on
 * - Feature 8: Multiple display mode switching (OK/MENU button)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentMode = PrefsManager.MODE_PRAYER_SCHEDULE

    // Feature 4: Pre-Jamaat banner receiver
    private val bannerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == NotificationBroadcastReceiver.ACTION_SHOW_BANNER) {
                val prayerName = intent.getStringExtra(
                    NotificationBroadcastReceiver.EXTRA_PRAYER_NAME
                ) ?: "নামাজ"
                val rootView = binding.root as ViewGroup
                PreJamaatNotifier.showBanner(rootView, prayerName)
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // Feature 5: Theme apply করতে হবে setContentView-এর আগে
        ThemeManager.applyAutoTheme(this)

        // Feature 6: Fullscreen flag
        ScreenManager.applyFullscreenFlag(window)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Feature 6: Immersive mode
        ScreenManager.enableImmersiveMode(window)

        // Feature 7: Keep screen on
        ScreenManager.setKeepScreenOn(window, PrefsManager.isKeepScreenOn(this))

        // Feature 1: TimeKeeperService start
        startTimeKeeper()

        // Feature 4: Pre-Jamaat notification schedule
        if (PrefsManager.isPreJamaatNotifEnabled(this)) {
            PreJamaatNotifier.scheduleAll(this)
        }

        // Load saved display mode
        currentMode = PrefsManager.getDisplayMode(this)

        // Initial fragment load
        switchToMode(currentMode)

        // Bottom mode indicator setup
        setupModeIndicators()

        // Settings button
        binding.btnSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnSettings?.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1f else 0.6f
        }
    }

    override fun onResume() {
        super.onResume()

        // Feature 5: Theme re-check (মাগরিব/ফজর এর পর theme switch হলে)
        val shouldBeDark = ThemeManager.shouldUseDarkTheme(this)
        val isDark = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() ==
                     androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        if (shouldBeDark != isDark && PrefsManager.isAutoDarkTheme(this)) {
            ThemeManager.applyAutoTheme(this)
            recreate()
            return
        }

        // Feature 4: Banner broadcast register
        val filter = IntentFilter(NotificationBroadcastReceiver.ACTION_SHOW_BANNER)
        registerReceiver(bannerReceiver, filter)

        // Feature 6: Re-apply immersive on resume
        ScreenManager.enableImmersiveMode(window)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(bannerReceiver) } catch (_: Exception) {}
    }

    // Feature 6: Focus change-এ immersive re-apply
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ScreenManager.onWindowFocusChanged(window, hasFocus)
    }

    // ── Feature 2 + 8: D-pad key handling ─────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            // OK বা ENTER: পরবর্তী display mode-এ switch
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                cycleDisplayMode()
                true
            }
            // MENU: Settings খুলবে
            KeyEvent.KEYCODE_MENU -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            // Back: Settings-এ না থাকলে কিছু করবে না (app exit রোধ)
            KeyEvent.KEYCODE_BACK -> {
                // TV app-এ back press on root activity কিছু করবে না
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ── Feature 8: Display Mode Switching ──────────────────────────────────

    private fun cycleDisplayMode() {
        currentMode = (currentMode + 1) % 4
        PrefsManager.setInt(this, PrefsManager.KEY_DISPLAY_MODE, currentMode)
        switchToMode(currentMode)
        updateModeIndicators()
    }

    private fun switchToMode(mode: Int) {
        val fragment: Fragment = when (mode) {
            PrefsManager.MODE_PRAYER_SCHEDULE -> PrayerScheduleFragment()
            PrefsManager.MODE_CLOCK           -> ClockFragment()
            PrefsManager.MODE_CALENDAR        -> CalendarFragment()
            PrefsManager.MODE_HADITH          -> HadithFragment()
            else                              -> PrayerScheduleFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        updateModeIndicators()
    }

    private fun setupModeIndicators() {
        binding.tvModeSchedule?.text = "📋 সময়সূচি"
        binding.tvModeClock?.text    = "🕐 ঘড়ি"
        binding.tvModeCalendar?.text = "📅 ক্যালেন্ডার"
        binding.tvModeHadith?.text   = "📖 হাদিস"
        updateModeIndicators()
    }

    private fun updateModeIndicators() {
        val indicators = listOf(
            binding.tvModeSchedule,
            binding.tvModeClock,
            binding.tvModeCalendar,
            binding.tvModeHadith
        )
        indicators.forEachIndexed { i, tv ->
            tv?.alpha = if (i == currentMode) 1.0f else 0.35f
        }
    }

    // ── Feature 1: TimeKeeperService start ────────────────────────────────

    private fun startTimeKeeper() {
        val intent = Intent(this, TimeKeeperService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
