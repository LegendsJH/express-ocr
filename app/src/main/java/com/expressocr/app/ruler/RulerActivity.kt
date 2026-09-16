package com.expressocr.app.ruler

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.expressocr.app.R
import com.expressocr.app.databinding.ActivityRulerBinding

class RulerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulerBinding
    private val prefs by lazy { getSharedPreferences("ruler", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        hideSystemUi()

        val saved = prefs.getFloat(KEY_PX_PER_MM, -1f)
        binding.rulerView.pxPerMm = if (saved > 0f) saved else RulerView.defaultPxPerMm(this)

        binding.btnCalibrate.setOnClickListener { enterCalibrate() }
        binding.btnCancelCalibrate.setOnClickListener { exitCalibrate(save = false) }
        binding.btnFinishCalibrate.setOnClickListener { finishCalibrate() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun enterCalibrate() {
        binding.rulerView.calibrating = true
        binding.rulerView.post {
            binding.rulerView.setGuideFromTouch(binding.rulerView.height * 0.4f)
        }
        binding.hintText.visibility = View.VISIBLE
        binding.hintText.setText(R.string.ruler_calibrate_hint)
        binding.btnCalibrate.visibility = View.GONE
        binding.btnFinishCalibrate.visibility = View.VISIBLE
        binding.btnCancelCalibrate.visibility = View.VISIBLE
    }

    private fun exitCalibrate(save: Boolean) {
        binding.rulerView.calibrating = false
        binding.hintText.visibility = View.GONE
        binding.btnCalibrate.visibility = View.VISIBLE
        binding.btnFinishCalibrate.visibility = View.GONE
        binding.btnCancelCalibrate.visibility = View.GONE
        if (!save) {
            val saved = prefs.getFloat(KEY_PX_PER_MM, -1f)
            binding.rulerView.pxPerMm = if (saved > 0f) saved else RulerView.defaultPxPerMm(this)
        }
    }

    private fun finishCalibrate() {
        val options = arrayOf("50 mm", "100 mm", "150 mm", "200 mm")
        val values = floatArrayOf(50f, 100f, 150f, 200f)
        AlertDialog.Builder(this)
            .setTitle("蓝线对齐真实尺子的哪一刻度？")
            .setItems(options) { _, which ->
                binding.rulerView.calibrateToPhysicalMm(values[which])
                prefs.edit().putFloat(KEY_PX_PER_MM, binding.rulerView.pxPerMm).apply()
                exitCalibrate(save = true)
                Toast.makeText(this, R.string.ruler_calibrate_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.ruler_cancel_calibrate, null)
            .show()
    }

    companion object {
        private const val KEY_PX_PER_MM = "px_per_mm"
    }
}
