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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mymasjid.tv.MainActivity
import com.mymasjid.tv.R
import com.mymasjid.tv.service.TimeKeeperService
import com.mymasjid.tv.utils.PrefsManager

/**
 * BootReceiver — Smart TV Boot Auto-Start & Auto-Landing Architecture
 *
 * নিশ্চিত করে যে Android TV বন্ধ হয়ে চালু হওয়া মাত্রই (Cold Boot, Restart, Standby Wakeup)
 * মসজিদ ডিসপ্লে অ্যাপটি শতভাগ সফলভাবে ফুলস্ক্রিনে চালু হবে।
 *
 * ৫-স্তর বিশিষ্ট বুট ল্যান্ডিং টেকনোলজি:
 * ১. PowerManager WakeLock অধিগ্রহণ (টিভি বুট চলাকালীন CPU সচল রাখা)
 * ২. Direct Activity Launch (NEW_TASK | CLEAR_TOP | SINGLE_TOP)
 * ৩. PendingIntent Direct Send (অ্যান্ড্রয়েড টাস্ক স্ট্যাক সরাসরি কল)
 * ৪. FullScreenIntent Notification (Android 10+ Background Start Restriction বাইপাস)
 * ৫. goAsync() সহ Delayed Retry (টিভি সিস্টেম লঞ্চার ওভাররাইড করে অ্যাপ সামনে আনা)
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
            Intent.ACTION_SCREEN_ON,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.media.tv.action.INITIALIZE_PROGRAMS"
        )

        /**
         * Activity Launch Helper: Direct + PendingIntent + FullScreenIntent
         */
        fun launchMainActivity(context: Context) {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                putExtra("LAUNCHED_FROM_BOOT", true)
            }

            // লেয়ার ক: ডিরেক্ট স্টার্টঅ্যাক্টিভিটি
            try {
                context.startActivity(mainIntent)
                Log.i(TAG, "Direct startActivity executed successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Direct startActivity failed: ${e.message}")
            }

            // লেয়ার খ: পেন্ডিং ইনটেন্ট ডিরেক্ট সেন্ড
            try {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pi = PendingIntent.getActivity(context, 777, mainIntent, flags)
                pi.send()
                Log.i(TAG, "PendingIntent send executed.")
            } catch (e: Exception) {
                Log.w(TAG, "PendingIntent send failed: ${e.message}")
            }

            // লেয়ার গ: FullScreenIntent নোটিফিকেশন ফলব্যাক (Android 10+)
            launchWithFullScreenIntent(context)
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
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

        // রিসিভার প্রসেস যেন অ্যান্ড্রয়েড অকালে বন্ধ না করে সেজন্য goAsync()
        val pendingResult = goAsync()

        // ওয়েক-লক অধিগ্রহণ যাতে বুট প্রক্রিয়া চলাকালীন টিভি প্রসেসর ঘুমিয়ে না পড়ে
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyMasjid:BootReceiverWakeLock"
        )
        try {
            wakeLock?.acquire(10000L)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire error: ${e.message}")
        }

        // টাইমকিপার সার্ভিস চালু করো
        startTimeKeeperService(context)

        // লেয়ার ১: তাৎক্ষণিক লঞ্চ
        launchMainActivity(context)

        // লেয়ার ২ ও ৩: টিভি ওএস বা ডিফল্ট লঞ্চার লোড হওয়ার পর অ্যাপকে সামনে আনার জন্য রিট্রাই
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.postDelayed({
            try {
                if (!MainActivity.isActivityVisible) {
                    Log.i(TAG, "Retry 1: Launching MainActivity after 1.8s delay...")
                    launchMainActivity(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Retry 1 error: ${e.message}")
            }
        }, 1800L)

        mainHandler.postDelayed({
            try {
                if (!MainActivity.isActivityVisible) {
                    Log.i(TAG, "Retry 2: Launching MainActivity after 3.8s delay...")
                    launchMainActivity(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Retry 2 error: ${e.message}")
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {}
                try {
                    pendingResult.finish()
                } catch (e: Exception) {}
            }
        }, 3800L)
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
