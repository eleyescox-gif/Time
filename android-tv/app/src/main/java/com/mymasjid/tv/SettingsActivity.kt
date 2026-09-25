package com.mymasjid.tv

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mymasjid.tv.databinding.ActivitySettingsBinding
import com.mymasjid.tv.notification.PreJamaatNotifier
import com.mymasjid.tv.utils.PrefsManager
import com.mymasjid.tv.utils.ScreenManager
import com.mymasjid.tv.utils.ThemeManager

/**
 * SettingsActivity — Centralized Settings Screen।
 *
 * সব ফিচারের toggle এখানে:
 * - Feature 3: Boot Auto-Start toggle
 * - Feature 4: Pre-Jamaat notification toggle
 * - Feature 5: Auto Dark Theme toggle
 * - Feature 7: Keep Screen On toggle
 * - Location settings (Latitude, Longitude)
 * - Prayer calculation method (Hanafi/Shafii)
 * - Jamaat offsets per prayer
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ScreenManager.applyFullscreenFlag(window)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ScreenManager.enableImmersiveMode(window)
        setupUI()
        loadCurrentSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ScreenManager.onWindowFocusChanged(window, hasFocus)
    }

    // ── UI Setup ───────────────────────────────────────────────────────────

    private fun setupUI() {

        // ─ Feature 3: Boot Auto-Start ─
        binding.switchBootAutoStart.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setBoolean(this, PrefsManager.KEY_BOOT_AUTO_START, checked)
            showStatus("Boot auto-start: ${if (checked) "চালু" else "বন্ধ"}")
        }

        // ─ Feature 4: Pre-Jamaat Notification ─
        binding.switchPreJamaatNotif.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setBoolean(this, PrefsManager.KEY_PRE_JAMAAT_NOTIF, checked)
            if (checked) {
                PreJamaatNotifier.scheduleAll(this)
                showStatus("জামাত নোটিফিকেশন: চালু ✅")
            } else {
                PreJamaatNotifier.cancelAll(this)
                showStatus("জামাত নোটিফিকেশন: বন্ধ ❌")
            }
        }

        // ─ Feature 5: Auto Dark Theme ─
        binding.switchAutoDarkTheme.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setBoolean(this, PrefsManager.KEY_AUTO_THEME, checked)
            ThemeManager.applyAutoTheme(this)
            showStatus("অটো ডার্ক থিম: ${if (checked) "চালু" else "বন্ধ"}")
        }

        // ─ Feature 7: Keep Screen On ─
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, checked ->
            PrefsManager.setBoolean(this, PrefsManager.KEY_KEEP_SCREEN_ON, checked)
            ScreenManager.setKeepScreenOn(window, checked)
            showStatus("স্ক্রিন সবসময় জ্বলন্ত: ${if (checked) "চালু" else "বন্ধ"}")
        }

        // ─ Asr method (Hanafi/Shafii) ─
        binding.btnHanafi.setOnClickListener {
            getSharedPreferences("mymasjid_prefs", MODE_PRIVATE)
                .edit().putString(PrefsManager.KEY_ASR_METHOD, "hanafi").apply()
            showStatus("আসর পদ্ধতি: হানাফি ✅")
            binding.tvCurrentAsrMethod.text = "হানাফি (বর্তমান)"
        }
        binding.btnShafii.setOnClickListener {
            getSharedPreferences("mymasjid_prefs", MODE_PRIVATE)
                .edit().putString(PrefsManager.KEY_ASR_METHOD, "shafii").apply()
            showStatus("আসর পদ্ধতি: শাফেয়ী ✅")
            binding.tvCurrentAsrMethod.text = "শাফেয়ী (বর্তমান)"
        }

        // ─ Save location button ─
        binding.btnSaveLocation.setOnClickListener {
            saveLocation()
        }

        // ─ Save jamaat offsets button ─
        binding.btnSaveOffsets.setOnClickListener {
            saveJamaatOffsets()
        }

        // ─ Screensaver instructions ─
        binding.btnScreensaverHelp.setOnClickListener {
            showStatus("সেটিংস > ডিভাইস প্রেফারেন্সেস > ডিসপ্লে ও সাউন্ড > স্ক্রিনসেভার > MyMasjid বেছে নিন")
        }

        // D-pad: Back key
        binding.btnBack.setOnClickListener { finish() }
        binding.btnBack.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1f else 0.6f
        }
    }

    private fun loadCurrentSettings() {
        binding.switchBootAutoStart.isChecked  = PrefsManager.isBootAutoStart(this)
        binding.switchPreJamaatNotif.isChecked = PrefsManager.isPreJamaatNotifEnabled(this)
        binding.switchAutoDarkTheme.isChecked  = PrefsManager.isAutoDarkTheme(this)
        binding.switchKeepScreenOn.isChecked   = PrefsManager.isKeepScreenOn(this)

        // Location
        binding.etLatitude.setText(PrefsManager.getLatitude(this).toString())
        binding.etLongitude.setText(PrefsManager.getLongitude(this).toString())

        // Asr method
        binding.tvCurrentAsrMethod.text =
            if (PrefsManager.isHanafi(this)) "হানাফি (বর্তমান)" else "শাফেয়ী (বর্তমান)"

        // Jamaat offsets
        binding.etFajrOffset.setText(PrefsManager.getJamaatOffset(this, "fajr").toString())
        binding.etDhuhrOffset.setText(PrefsManager.getJamaatOffset(this, "dhuhr").toString())
        binding.etAsrOffset.setText(PrefsManager.getJamaatOffset(this, "asr").toString())
        binding.etMaghribOffset.setText(PrefsManager.getJamaatOffset(this, "maghrib").toString())
        binding.etIshaOffset.setText(PrefsManager.getJamaatOffset(this, "isha").toString())
    }

    private fun saveLocation() {
        try {
            val lat = binding.etLatitude.text.toString().toFloat()
            val lng = binding.etLongitude.text.toString().toFloat()
            PrefsManager.setFloat(this, PrefsManager.KEY_LATITUDE, lat)
            PrefsManager.setFloat(this, PrefsManager.KEY_LONGITUDE, lng)
            PreJamaatNotifier.scheduleAll(this)  // নতুন location দিয়ে re-schedule
            showStatus("অবস্থান সংরক্ষিত: $lat, $lng ✅")
        } catch (e: Exception) {
            showStatus("ভুল মান! সংখ্যা লিখুন।")
        }
    }

    private fun saveJamaatOffsets() {
        try {
            PrefsManager.setInt(this, PrefsManager.KEY_FAJR_OFFSET_MIN,
                binding.etFajrOffset.text.toString().toInt())
            PrefsManager.setInt(this, PrefsManager.KEY_DHUHR_OFFSET_MIN,
                binding.etDhuhrOffset.text.toString().toInt())
            PrefsManager.setInt(this, PrefsManager.KEY_ASR_OFFSET_MIN,
                binding.etAsrOffset.text.toString().toInt())
            PrefsManager.setInt(this, PrefsManager.KEY_MAGHRIB_OFFSET_MIN,
                binding.etMaghribOffset.text.toString().toInt())
            PrefsManager.setInt(this, PrefsManager.KEY_ISHA_OFFSET_MIN,
                binding.etIshaOffset.text.toString().toInt())
            PreJamaatNotifier.scheduleAll(this)
            showStatus("জামাতের সময় সংরক্ষিত ✅")
        } catch (e: Exception) {
            showStatus("ভুল মান!")
        }
    }

    private fun showStatus(msg: String) {
        binding.tvSettingsStatus.text = msg
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
