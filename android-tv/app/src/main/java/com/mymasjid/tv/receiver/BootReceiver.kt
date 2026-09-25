package com.mymasjid.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mymasjid.tv.MainActivity
import com.mymasjid.tv.service.TimeKeeperService
import com.mymasjid.tv.utils.PrefsManager

/**
 * BootReceiver — Feature 3: Boot Auto-Start (Toggleable)
 *
 * TV/Device restart হলে BOOT_COMPLETED broadcast ধরে:
 * 1. SharedPreferences থেকে toggle check করে
 * 2. MainActivity auto-launch করে
 * 3. TimeKeeperService start করে
 *
 * China TV ব্র্যান্ডের জন্য QUICKBOOT_POWERON ও handle করে।
 * Manifest-এ RECEIVE_BOOT_COMPLETED permission দরকার।
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                     action == "android.intent.action.QUICKBOOT_POWERON" ||
                     action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (!isBoot) return

        Log.i(TAG, "Boot completed — action: $action")

        // Feature 3: Toggle check — settings থেকে OFF করলে auto-launch হবে না
        if (!PrefsManager.isBootAutoStart(context)) {
            Log.i(TAG, "Auto-start is disabled in settings, skipping.")
            return
        }

        // TimeKeeperService আগে start করি (time accuracy)
        startTimeKeeperService(context)

        // MainActivity launch (TV home screen-এ app দেখাবে)
        launchMainActivity(context)

        Log.i(TAG, "App launched after boot successfully.")
    }

    private fun startTimeKeeperService(context: Context) {
        try {
            val serviceIntent = Intent(context, TimeKeeperService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TimeKeeperService: ${e.message}")
        }
    }

    private fun launchMainActivity(context: Context) {
        try {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(mainIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity: ${e.message}")
        }
    }
}
