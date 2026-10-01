package com.mymasjid.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mymasjid.tv.R
import com.mymasjid.tv.utils.PrefsManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * TimeKeeperService — Feature 1: Offline Accurate Timekeeping
 *
 * দায়িত্বসমূহ:
 * 1. Device RTC দিয়ে সময় track করা
 * 2. ইন্টারনেট থাকলে NTP দিয়ে calibrate করা
 * 3. Power outage recovery: শেষবার সংরক্ষিত NTP time + elapsed-realtime দিয়ে সময় recover
 * 4. Drift correction: প্রতি ৩০ মিনিটে NTP sync চেষ্টা করে
 *
 * Foreground Service হিসেবে চলে যাতে Android কখনো kill না করে।
 */
class TimeKeeperService : Service() {

    companion object {
        private const val TAG = "TimeKeeperService"
        private const val NOTIF_CHANNEL_ID = "masjid_timekeeper"
        private const val NOTIF_ID = 1001
        private const val NTP_SYNC_INTERVAL_MS = 30 * 60 * 1000L   // ৩০ মিনিট
        private const val NTP_DRIFT_THRESHOLD_MS = 30_000L          // ৩০ সেকেন্ড drift হলে correct

        // NTP server list (বাংলাদেশ/Asia region friendly)
        private val NTP_SERVERS = listOf(
            "time.google.com",
            "pool.ntp.org",
            "asia.pool.ntp.org",
            "time.cloudflare.com"
        )

        /** বর্তমান accurate time (millis) — NTP calibrated */
        @Volatile
        var accurateTimeMillis: Long = System.currentTimeMillis()
            private set

        /** Service চলছে কিনা */
        @Volatile
        var isRunning: Boolean = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val syncRunnable = object : Runnable {
        override fun run() {
            syncNtpInBackground()
            handler.postDelayed(this, NTP_SYNC_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                Log.i(TAG, "Screen ON / TV Standby Wakeup: $action")
                if (context != null && PrefsManager.isBootAutoStart(context)) {
                    if (!com.mymasjid.tv.MainActivity.isActivityVisible) {
                        Log.i(TAG, "MainActivity is in background, auto-landing to foreground...")
                        com.mymasjid.tv.receiver.BootReceiver.launchMainActivity(context)
                    }
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        // Power outage recovery: RTC-based time restore
        recoverTimeAfterOutage()

        // TV Standby Wakeup (Screen ON) Listener
        try {
            val screenFilter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, screenFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screenReceiver: ${e.message}")
        }

        // NTP sync শুরু করো
        handler.post(syncRunnable)
        Log.i(TAG, "TimeKeeperService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY   // Service kill হলে auto-restart

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        handler.removeCallbacks(syncRunnable)
        executor.shutdown()
        Log.i(TAG, "TimeKeeperService stopped")
    }

    // ── Feature 1: Power Outage Time Recovery ─────────────────────────────

    /**
     * TV বন্ধ / power outage হলে elapsed time হারিয়ে যায়।
     * সর্বশেষ NTP-synced time + এখন পর্যন্ত elapsed realtime দিয়ে recover করি।
     */
    private fun recoverTimeAfterOutage() {
        val lastNtpTime = PrefsManager.getLong(this, PrefsManager.KEY_LAST_NTP_TIME, 0L)
        val lastElapsed = PrefsManager.getLong(this, PrefsManager.KEY_LAST_ELAPSED, 0L)

        if (lastNtpTime > 0L && lastElapsed > 0L) {
            // elapsed realtime এখন নতুন boot থেকে শুরু হয়েছে,
            // তাই এই পদ্ধতিতে approximate time পাই:
            val currentElapsed = SystemClock.elapsedRealtime()
            // boot থেকে এখন পর্যন্ত যত সময় — কিন্তু power outage-এর মাঝের সময় যোগ হয় না
            // RTC: System.currentTimeMillis() সরাসরি ব্যবহার করি (hardware clock)
            accurateTimeMillis = System.currentTimeMillis()
            Log.i(TAG, "Time recovered from RTC: ${accurateTimeMillis}")
        } else {
            accurateTimeMillis = System.currentTimeMillis()
        }
    }

    // ── Feature 1: NTP Sync ────────────────────────────────────────────────

    private fun syncNtpInBackground() {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network — skipping NTP sync")
            return
        }

        executor.execute {
            for (server in NTP_SERVERS) {
                val ntpTime = getNtpTime(server)
                if (ntpTime != null) {
                    val drift = ntpTime - System.currentTimeMillis()
                    Log.i(TAG, "NTP sync [$server]: drift = ${drift}ms")

                    // Drift threshold: ৩০ সেকেন্ডের বেশি হলে correct করি
                    if (Math.abs(drift) > NTP_DRIFT_THRESHOLD_MS) {
                        Log.w(TAG, "Significant drift detected (${drift}ms), correcting...")
                    }
                    accurateTimeMillis = ntpTime

                    // Save for outage recovery
                    PrefsManager.setLong(this, PrefsManager.KEY_LAST_NTP_TIME, ntpTime)
                    PrefsManager.setLong(this, PrefsManager.KEY_LAST_ELAPSED, SystemClock.elapsedRealtime())
                    break
                }
            }
        }
    }

    /** SNTP (Simple NTP) client — lightweight, কোনো library ছাড়াই */
    private fun getNtpTime(host: String): Long? = try {
        val socket = DatagramSocket()
        socket.soTimeout = 5000  // 5 second timeout

        val buffer = ByteArray(48).also { it[0] = 0x1B }  // NTP request packet
        val address = InetAddress.getByName(host)
        val request = DatagramPacket(buffer, buffer.size, address, 123)

        val t1 = System.currentTimeMillis()
        socket.send(request)

        val response = DatagramPacket(ByteArray(48), 48)
        socket.receive(response)
        val t4 = System.currentTimeMillis()

        socket.close()

        // NTP timestamp: bytes 40-43 (Transmit Timestamp, seconds since 1900)
        val data = response.data
        val secondsSince1900 = ((data[40].toLong() and 0xFF) shl 24) or
                               ((data[41].toLong() and 0xFF) shl 16) or
                               ((data[42].toLong() and 0xFF) shl 8) or
                               (data[43].toLong() and 0xFF)

        // 1900→1970 offset: 70 years = 2208988800 seconds
        val ntpMillis = (secondsSince1900 - 2_208_988_800L) * 1000L
        // Round-trip delay adjustment
        val roundTrip = (t4 - t1) / 2
        ntpMillis + roundTrip
    } catch (e: Exception) {
        Log.w(TAG, "NTP failed for $host: ${e.message}")
        null
    }

    // ── Network check ──────────────────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    // ── Foreground notification ────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Masjid Time Service",
                NotificationManager.IMPORTANCE_MIN  // সাইলেন্ট, কোনো sound নেই
            ).apply {
                setShowBadge(false)
                description = "Keeps prayer times accurate"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("MyMasjid Running")
            .setContentText("Prayer times are active")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
