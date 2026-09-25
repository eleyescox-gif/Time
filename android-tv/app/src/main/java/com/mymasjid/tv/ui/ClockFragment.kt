package com.mymasjid.tv.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mymasjid.tv.R
import java.util.Calendar

/**
 * ClockFragment — Feature 8: Display Mode 1 (Digital Clock View)
 * বড় ডিজিটাল ঘড়ি + তারিখ দেখায়।
 */
class ClockFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvClock: TextView
    private lateinit var tvDate:  TextView
    private lateinit var tvSeconds: TextView

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_clock, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvClock   = view.findViewById(R.id.tvClockMain)
        tvDate    = view.findViewById(R.id.tvClockDate)
        tvSeconds = view.findViewById(R.id.tvClockSeconds)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    private fun tick() {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)

        val hStr = if (h < 10) "0$h" else "$h"
        val mStr = if (m < 10) "0$m" else "$m"
        val sStr = if (s < 10) "0$s" else "$s"

        tvClock.text   = "$hStr:$mStr"
        tvSeconds.text = ":$sStr"

        val days   = arrayOf("রবিবার","সোমবার","মঙ্গলবার","বুধবার","বৃহস্পতিবার","শুক্রবার","শনিবার")
        val months = arrayOf("জানুয়ারি","ফেব্রুয়ারি","মার্চ","এপ্রিল","মে","জুন",
                             "জুলাই","আগস্ট","সেপ্টেম্বর","অক্টোবর","নভেম্বর","ডিসেম্বর")

        val day = days[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val mon = months[cal.get(Calendar.MONTH)]
        val d   = cal.get(Calendar.DAY_OF_MONTH)
        val yr  = cal.get(Calendar.YEAR)

        tvDate.text = "$day, $d $mon $yr"
    }
}
