package com.mymasjid.tv.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * NotificationBroadcastReceiver — AlarmManager-এর broadcast receive করে।
 * MainActivity-তে currently active হলে banner দেখায়।
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHOW_BANNER = "com.mymasjid.tv.SHOW_PRE_JAMAAT_BANNER"
        const val EXTRA_PRAYER_NAME  = "prayer_name"

        // Prayer ID → বাংলা নাম map
        val PRAYER_NAMES_BN = mapOf(
            "fajr"    to "ফজর",
            "dhuhr"   to "যোহর",
            "asr"     to "আসর",
            "maghrib" to "মাগরিব",
            "isha"    to "এশা"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerId = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val nameBn   = PRAYER_NAMES_BN[prayerId] ?: prayerId

        Log.i("NotifReceiver", "Pre-jamaat notif for $prayerId ($nameBn)")

        // MainActivity-তে LocalBroadcast পাঠাই যাতে banner দেখানো যায়
        val bannerIntent = Intent(ACTION_SHOW_BANNER).apply {
            putExtra(EXTRA_PRAYER_NAME, nameBn)
            setPackage(context.packageName)
        }
        context.sendBroadcast(bannerIntent)
    }
}
