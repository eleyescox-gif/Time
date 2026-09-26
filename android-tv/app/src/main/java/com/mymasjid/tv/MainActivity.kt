package com.mymasjid.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mymasjid.tv.service.TimeKeeperService
import com.mymasjid.tv.utils.PrefsManager

/**
 * MainActivity — Professional Mosque Digital Display Application.
 *
 * Senior Android Engineer Architecture:
 * - 100% Offline Standalone: Bundled assets (display.html, logo.png, bg_display.jpg)
 * - Auto Multi-Device Responsive: Smooth vertical portrait layout for Mobile Phones,
 *   full-width widescreen 1080p/4K layout for Smart TVs.
 * - Hardware Accelerated 60fps rendering for smooth real-time clock & luxury countdown.
 * - Immersive Fullscreen Sticky Mode: Hides status bar, navigation bar & notch cutouts.
 * - Screen WakeLock: Screen never dims or sleeps while displaying prayer times.
 * - TV Boot Auto-Start: Launches automatically when Smart TV is powered on.
 * - Remote Control Friendly: D-Pad navigation, Back-press double confirmation to prevent accidental TV exit.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var lastBackPressTime: Long = 0
    private val BACK_PRESS_THRESHOLD = 2000L // ২ সেকেন্ডের মধ্যে ডাবল ব্যাক

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // ── ১. থিম ও উইন্ডো ফ্ল্যাগ সেটআপ ──
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // স্ক্রিন যেন কখনোই অফ না হয় (TV ও মোবাইলের জন্য অত্যাবশ্যকীয়)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // নচ এবং পাঞ্চ-হোল স্ক্রিনে ফুলস্ক্রিন ডিসপ্লে (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        // ── ২. ইমারসিভ ফুলস্ক্রিন অ্যাপ্লাই ──
        enableImmersiveFullscreen()

        // ── ৩. ওয়েবভিউ ইনিশিয়ালাইজেশন ──
        webView = findViewById(R.id.mainWebView)
        configureWebView()

        // ── ৪. ব্যাকগ্রাউন্ড টাইমকিপার সার্ভিস চালু (RTC + NTP Drift Correction) ──
        startTimeKeeperService()

        // ── ৫. অফলাইন মসজিদ ডিসপ্লে লোড ──
        webView.loadUrl("file:///android_asset/display.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.parseColor("#0a0a0a"))
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // অফলাইন অ্যাসেট অ্যাক্সেস পারমিশন
        try {
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
        } catch (e: Exception) {
            // Safe fallback
        }

        // অডিও/আজান বিজার স্বয়ংক্রিয়ভাবে বাজার অনুমতি
        settings.mediaPlaybackRequiresUserGesture = false

        // মোবাইল ও টিভি স্ক্রিন অপ্টিমাইজেশন
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // ক্যাশিং স্ট্র্যাটেজি: অফলাইন অগ্রাধিকার
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("file://") || url.contains("localhost")) {
                    return false
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // যদি নেটওয়ার্ক ফেইল করে তবে অফলাইন অ্যাসেট রিলোড করো
                if (request?.isForMainFrame == true) {
                    webView.post {
                        webView.loadUrl("file:///android_asset/display.html")
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return true
            }
        }
    }

    /**
     * ইমারসিভ ফুলস্ক্রিন মোড — স্ট্যাটাস বার এবং ন্যাভিগেশন বার সম্পূর্ণ লুকায়।
     */
    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveFullscreen()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        enableImmersiveFullscreen()
    }

    /**
     * টিভি রিমোট ও ব্যাক বাটন হ্যান্ডলিং:
     * - নামাজ বা প্রদর্শনের সময় ভুলবশত ব্যাক চাপলে যেন বন্ধ না হয়।
     * - ২ সেকেন্ডের মধ্যে দুবার ব্যাক চাপলে তবেই অ্যাপ বন্ধ হবে।
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < BACK_PRESS_THRESHOLD) {
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this, "আরেকবার ব্যাক চাপলে অ্যাপ বন্ধ হবে", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                // রিমোটের MENU বাটনে ক্লিক করলে সেটিংস খুলবে
                webView.evaluateJavascript("if (typeof openSettingsModal === 'function') openSettingsModal();", null)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startTimeKeeperService() {
        try {
            val intent = Intent(this, TimeKeeperService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Ignore if foreground service restriction
        }
    }
}
