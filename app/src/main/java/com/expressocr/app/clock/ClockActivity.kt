package com.expressocr.app.clock

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.expressocr.app.databinding.ActivityClockBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClockBinding
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var themeIndex = 0

    private val themes = listOf(
        Theme(Color.parseColor("#FFFAFAFA"), Color.parseColor("#FF111111"), Color.parseColor("#FFF0F0F0")),
        Theme(Color.parseColor("#FF121212"), Color.parseColor("#FFFFFFFF"), Color.parseColor("#FF2A2A2A")),
        Theme(Color.parseColor("#FF1B5E20"), Color.parseColor("#FFE8F5E9"), Color.parseColor("#FF2E7D32")),
        Theme(Color.parseColor("#FF0D47A1"), Color.parseColor("#FFE3F2FD"), Color.parseColor("#FF1565C0"))
    )

    private val tick = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        applyTheme(0)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnTheme.setOnClickListener {
            themeIndex = (themeIndex + 1) % themes.size
            applyTheme(themeIndex)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun updateTime() {
        val parts = timeFormat.format(Date()).split(":")
        if (parts.size == 3) {
            binding.hourText.text = parts[0]
            binding.minuteText.text = parts[1]
            binding.secondText.text = parts[2]
        }
    }

    private fun applyTheme(index: Int) {
        val t = themes[index]
        binding.root.setBackgroundColor(t.bg)
        listOf(binding.hourText, binding.minuteText, binding.secondText).forEach {
            it.setTextColor(t.fg)
            it.setBackgroundColor(t.card)
        }
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private data class Theme(val bg: Int, val fg: Int, val card: Int)
}
