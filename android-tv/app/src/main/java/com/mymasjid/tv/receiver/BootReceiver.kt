package com.mymasjid.tv.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mymasjid.tv.MainActivity
import com.mymasjid.tv.R
import com.mymasjid.tv.service.TimeKeeperService
import com.mymasjid.tv.utils.PrefsManager

/**
 * BootReceiver — Feature: Smart TV Boot Auto-Start & Auto-Landing Architecture
 *
 * নিশ্চিত করে যে Android TV বন্ধ হয়ে চালু হওয়া মাত্রই (Cold Boot, Restart, Standby Wakeup)
 * মসজিদ ডিসপ্লে অ্যাপটি স্বয়ংক্রিয়ভাবে ফুলস্ক্রিনে চালু হবে।
 *
 * ৪-স্তর বিশিষ্ট বুট ল্যান্ডিং টেকনোলজি:
 * ১. Direct Activity Launch (NEW_TASK | CLEAR_TOP | SINGLE_TOP)
 * ২. Delayed Safety Launch (TV OS/Launcher ইনিশিয়ালাইজেশন নিশ্চিত করার জন্য ১.৫ ও ৩.৫ সেকেন্ডে রিট্রাই)
 * ৩. FullScreenIntent Notification Fallback (Android 10+ Background Start Restriction বাইপাস)
 * ৪. Foreground TimeKeeperService চালু
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_CHANNEL_ID = "masjid_boot_autolaunch"
        private const val NOTIF_ID = 8888

        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_REBOOT,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.media.tv.action.INITIALIZE_PROGRAMS"
        )

        /**
         * Activity Launch Helper: Direct Launch + FullScreen Fallback
         */
        fun launchMainActivity(context: Context) {
            try {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    putExtra("LAUNCHED_FROM_BOOT", true)
                }
                context.startActivity(mainIntent)
                Log.i(TAG, "Direct launchMainActivity called successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Direct launch failed, using FullScreenIntent fallback: ${e.message}")
                launchWithFullScreenIntent(context)
            }
        }

        /**
         * Android 10+ (API 29+) Background Launch Restriction বাইপাস করার জন্য
         * Google-এর অফিসিয়াল FullScreenIntent মেকানিজম
         */
        fun launchWithFullScreenIntent(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        BOOT_CHANNEL_ID,
                        "Auto Start Service",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Mosque Display Auto Launch on TV Boot"
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("LAUNCHED_FROM_BOOT", true)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context, 777, fullScreenIntent, flags
                )

                val notification = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle("মসজিদ ডিসপ্লে সচল হচ্ছে")
                    .setContentText("স্মার্ট মসজিদ ডিজিটাল ডিসপ্লে প্রস্তুত হচ্ছে...")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(NOTIF_ID, notification)
                Log.i(TAG, "FullScreenIntent notification posted successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch with FullScreenIntent: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!BOOT_ACTIONS.contains(action)) return

        Log.i(TAG, "Boot/Power event received — action: $action")

        // ইউজার সেটিংসে বন্ধ না রাখলে স্বয়ংক্রিয়ভাবে চলবে
        if (!PrefsManager.isBootAutoStart(context)) {
            Log.i(TAG, "Auto-start is disabled in settings, skipping.")
            return
        }

        // টাইমকিপার সার্ভিস চালু করো
        startTimeKeeperService(context)

        // লেয়ার ১: তাৎক্ষণিক লঞ্চ
        launchMainActivity(context)

        // লেয়ার ২ ও ৩: টিভি হার্ডওয়্যার ও ডিসপ্লে ড্রাইভার সম্পূর্ণ রেডি হওয়ার জন্য ১.৫ ও ৩.৫ সেকেন্ড পর রিট্রাই
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed({
            if (!MainActivity.isActivityVisible) {
                Log.i(TAG, "Retry 1: Launching MainActivity after 1.5s delay...")
                launchMainActivity(context)
                launchWithFullScreenIntent(context)
            }
        }, 1500L)

        mainHandler.postDelayed({
            if (!MainActivity.isActivityVisible) {
                Log.i(TAG, "Retry 2: Launching MainActivity after 3.5s delay...")
                launchMainActivity(context)
                launchWithFullScreenIntent(context)
            }
        }, 3500L)
    }

    private fun startTimeKeeperService(context: Context) {
        try {
            val serviceIntent = Intent(context, TimeKeeperService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TimeKeeperService: ${e.message}")
        }
    }
}
