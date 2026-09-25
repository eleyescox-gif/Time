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

/**
 * HadithFragment — Feature 8: Display Mode 3 (Islamic Quote/Hadith Slide View)
 * Hadith/Islamic Quote auto-cycle করে দেখায় (প্রতি ৩০ সেকেন্ডে পরিবর্তন)।
 */
class HadithFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private lateinit var tvArabic: TextView
    private lateinit var tvBangla: TextView
    private lateinit var tvSource: TextView

    // Hardcoded Islamic quotes/hadith (Arabic + Bengali)
    private val hadiths = listOf(
        Triple(
            "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ",
            "নিশ্চয়ই সকল আমল নিয়্যতের উপর নির্ভরশীল।",
            "— বুখারি ও মুসলিম"
        ),
        Triple(
            "الصَّلَاةُ عِمَادُ الدِّينِ",
            "নামাজ হলো দ্বীনের স্তম্ভ।",
            "— তিরমিযী"
        ),
        Triple(
            "مَنْ صَلَّى عَلَيَّ صَلاةً صَلَّى اللَّهُ عَلَيْهِ بِهَا عَشْرًا",
            "যে ব্যক্তি আমার উপর একবার দরুদ পড়বে, আল্লাহ তার উপর দশবার রহমত নাযিল করবেন।",
            "— মুসলিম"
        ),
        Triple(
            "طَلَبُ الْعِلْمِ فَرِيضَةٌ عَلَى كُلِّ مُسْلِمٍ",
            "প্রত্যেক মুসলমানের উপর জ্ঞান অর্জন করা ফরয।",
            "— ইবনে মাজাহ"
        ),
        Triple(
            "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ",
            "তোমাদের মধ্যে সেই ব্যক্তি সর্বোত্তম যে কুরআন শেখে এবং শেখায়।",
            "— বুখারি"
        ),
        Triple(
            "الْمُسْلِمُ مَنْ سَلِمَ الْمُسْلِمُونَ مِنْ لِسَانِهِ وَيَدِهِ",
            "মুসলিম সেই ব্যক্তি যার জিহ্বা ও হাত থেকে অন্য মুসলিমরা নিরাপদ।",
            "— বুখারি ও মুসলিম"
        ),
        Triple(
            "الدُّعَاءُ هُوَ الْعِبَادَةُ",
            "দুয়াই হলো ইবাদত।",
            "— তিরমিযী, আবু দাউদ"
        ),
        Triple(
            "مَنْ كَانَ يُؤْمِنُ بِاللَّهِ وَالْيَوْمِ الآخِرِ فَلْيَقُلْ خَيْرًا أَوْ لِيَصْمُتْ",
            "যে ব্যক্তি আল্লাহ ও আখিরাতে বিশ্বাস রাখে, সে যেন ভালো কথা বলে অথবা চুপ থাকে।",
            "— বুখারি ও মুসলিম"
        )
    )

    private val slideRunnable = object : Runnable {
        override fun run() {
            nextHadith()
            handler.postDelayed(this, 30_000L) // ৩০ সেকেন্ডে পরিবর্তন
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hadith, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvArabic = view.findViewById(R.id.tvHadithArabic)
        tvBangla = view.findViewById(R.id.tvHadithBangla)
        tvSource = view.findViewById(R.id.tvHadithSource)
        showCurrent()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(slideRunnable, 30_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(slideRunnable)
    }

    private fun showCurrent() {
        val h = hadiths[currentIndex]
        tvArabic.text = h.first
        tvBangla.text = h.second
        tvSource.text = h.third
    }

    private fun nextHadith() {
        currentIndex = (currentIndex + 1) % hadiths.size
        // Fade animation
        tvArabic.animate().alpha(0f).setDuration(500).withEndAction {
            showCurrent()
            tvArabic.animate().alpha(1f).setDuration(500).start()
        }.start()
        tvBangla.animate().alpha(0f).setDuration(500).withEndAction {
            tvBangla.animate().alpha(1f).setDuration(500).start()
        }.start()
    }
}
