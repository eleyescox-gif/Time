package com.mymasjid.tv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * PrefsManager — SharedPreferences-এর centralized wrapper।
 * সব settings এক জায়গায় manage হয়, key typo সম্ভাবনা শূন্য।
 */
object PrefsManager {

    private const val PREF_FILE = "mymasjid_prefs"

    // ── Key constants ──────────────────────────────────────
    const val KEY_BOOT_AUTO_START   = "boot_auto_start"       // Feature 3
    const val KEY_KEEP_SCREEN_ON    = "keep_screen_on"        // Feature 7
    const val KEY_AUTO_THEME        = "auto_dark_theme"       // Feature 5
    const val KEY_PRE_JAMAAT_NOTIF  = "pre_jamaat_notif"      // Feature 4
    const val KEY_DISPLAY_MODE      = "display_mode"          // Feature 8
    const val KEY_LATITUDE          = "latitude"
    const val KEY_LONGITUDE         = "longitude"
    const val KEY_TIMEZONE_OFFSET   = "timezone_offset"
    const val KEY_FAJR_ANGLE        = "fajr_angle"
    const val KEY_ISHA_ANGLE        = "isha_angle"
    const val KEY_ASR_METHOD        = "asr_method"            // "hanafi" | "shafii"
    const val KEY_FAJR_OFFSET_MIN   = "fajr_jamaat_offset"    // আজানের X মিনিট পর জামাত
    const val KEY_DHUHR_OFFSET_MIN  = "dhuhr_jamaat_offset"
    const val KEY_ASR_OFFSET_MIN    = "asr_jamaat_offset"
    const val KEY_MAGHRIB_OFFSET_MIN= "maghrib_jamaat_offset"
    const val KEY_ISHA_OFFSET_MIN   = "isha_jamaat_offset"
    const val KEY_LAST_NTP_TIME     = "last_ntp_millis"       // Feature 1: drift correction
    const val KEY_LAST_ELAPSED      = "last_elapsed_realtime"

    // Display mode values
    const val MODE_PRAYER_SCHEDULE  = 0
    const val MODE_CLOCK            = 1
    const val MODE_CALENDAR         = 2
    const val MODE_HADITH           = 3

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Generic getters/setters ───────────────────────────

    fun getBoolean(ctx: Context, key: String, default: Boolean = false): Boolean =
        prefs(ctx).getBoolean(key, default)

    fun setBoolean(ctx: Context, key: String, value: Boolean) =
        prefs(ctx).edit().putBoolean(key, value).apply()

    fun getInt(ctx: Context, key: String, default: Int = 0): Int =
        prefs(ctx).getInt(key, default)

    fun setInt(ctx: Context, key: String, value: Int) =
        prefs(ctx).edit().putInt(key, value).apply()

    fun getFloat(ctx: Context, key: String, default: Float = 0f): Float =
        prefs(ctx).getFloat(key, default)

    fun setFloat(ctx: Context, key: String, value: Float) =
        prefs(ctx).edit().putFloat(key, value).apply()

    fun getLong(ctx: Context, key: String, default: Long = 0L): Long =
        prefs(ctx).getLong(key, default)

    fun setLong(ctx: Context, key: String, value: Long) =
        prefs(ctx).edit().putLong(key, value).apply()

    // ── Typed convenience methods ─────────────────────────

    /** ডিফল্ট Latitude (Lat: 21.8355, Lng: 92.0780) */
    fun getLatitude(ctx: Context): Double =
        getFloat(ctx, KEY_LATITUDE, 21.8355f).toDouble()

    /** ডিফল্ট Longitude */
    fun getLongitude(ctx: Context): Double =
        getFloat(ctx, KEY_LONGITUDE, 92.0780f).toDouble()

    /** GMT+6 ডিফল্ট */
    fun getTimezoneOffset(ctx: Context): Double =
        getFloat(ctx, KEY_TIMEZONE_OFFSET, 6.0f).toDouble()

    fun getFajrAngle(ctx: Context): Double =
        getFloat(ctx, KEY_FAJR_ANGLE, 18.0f).toDouble()

    fun getIshaAngle(ctx: Context): Double =
        getFloat(ctx, KEY_ISHA_ANGLE, 18.0f).toDouble()

    fun isHanafi(ctx: Context): Boolean =
        prefs(ctx).getString(KEY_ASR_METHOD, "hanafi") == "hanafi"

    /** জামাত offset (মিনিট): আজানের কত মিনিট পর জামাত */
    fun getJamaatOffset(ctx: Context, prayerId: String): Int = when (prayerId) {
        "fajr"    -> getInt(ctx, KEY_FAJR_OFFSET_MIN,    20)
        "dhuhr"   -> getInt(ctx, KEY_DHUHR_OFFSET_MIN,   15)
        "asr"     -> getInt(ctx, KEY_ASR_OFFSET_MIN,     15)
        "maghrib" -> getInt(ctx, KEY_MAGHRIB_OFFSET_MIN,  5)
        "isha"    -> getInt(ctx, KEY_ISHA_OFFSET_MIN,    20)
        else      -> 15
    }

    fun getDisplayMode(ctx: Context): Int =
        getInt(ctx, KEY_DISPLAY_MODE, MODE_PRAYER_SCHEDULE)

    fun isBootAutoStart(ctx: Context): Boolean =
        getBoolean(ctx, KEY_BOOT_AUTO_START, true)

    fun isKeepScreenOn(ctx: Context): Boolean =
        getBoolean(ctx, KEY_KEEP_SCREEN_ON, true)

    fun isAutoDarkTheme(ctx: Context): Boolean =
        getBoolean(ctx, KEY_AUTO_THEME, true)

    fun isPreJamaatNotifEnabled(ctx: Context): Boolean =
        getBoolean(ctx, KEY_PRE_JAMAAT_NOTIF, true)
}
