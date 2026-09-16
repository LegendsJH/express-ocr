package com.expressocr.app.barrage

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.expressocr.app.databinding.ActivityBarrageSetupBinding
import com.google.android.material.slider.Slider

class BarrageSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarrageSetupBinding
    private var bgColor = Color.parseColor("#FFEEFF41")
    private var textColor = Color.BLACK
    private var bgViews = emptyList<View>()
    private var textViews = emptyList<View>()

    private val bgColors = listOf(
        "#FFEEFF41", "#FF2196F3", "#FFF44336", "#FF000000",
        "#FFFFFFFF", "#FF4CAF50", "#FFFF9800", "#FF9C27B0"
    )
    private val textColors = listOf(
        "#FF000000", "#FFFFFFFF", "#FFFFEB3B", "#FFF44336",
        "#FF2196F3", "#FF4CAF50"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarrageSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(com.expressocr.app.R.string.barrage_title)

        bgViews = fillColorRow(binding.bgColorRow, bgColors, bgColor) { bgColor = it }
        textViews = fillColorRow(binding.textColorRow, textColors, textColor) { textColor = it }

        binding.sliderSize.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            binding.labelSize.text = getString(com.expressocr.app.R.string.barrage_text_size) +
                "  ${value.toInt()}"
        }
        binding.labelSize.text = getString(com.expressocr.app.R.string.barrage_text_size) + "  72"

        fun refreshSpeedVisibility() {
            val scroll = binding.radioScroll.isChecked
            binding.labelSpeed.visibility = if (scroll) View.VISIBLE else View.GONE
            binding.sliderSpeed.visibility = if (scroll) View.VISIBLE else View.GONE
        }
        binding.scrollGroup.setOnCheckedChangeListener { _, _ -> refreshSpeedVisibility() }
        refreshSpeedVisibility()

        binding.sliderSpeed.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            binding.labelSpeed.text = getString(com.expressocr.app.R.string.barrage_speed) +
                "  ${value.toInt()}"
        }
        binding.labelSpeed.text = getString(com.expressocr.app.R.string.barrage_speed) + "  5"

        binding.btnShow.setOnClickListener {
            val text = binding.inputText.text?.toString()?.ifBlank {
                getString(com.expressocr.app.R.string.barrage_default)
            } ?: getString(com.expressocr.app.R.string.barrage_default)
            val intent = Intent(this, BarrageDisplayActivity::class.java).apply {
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_SIZE, binding.sliderSize.value)
                putExtra(EXTRA_SCROLL, binding.radioScroll.isChecked)
                putExtra(EXTRA_SPEED, binding.sliderSpeed.value)
                putExtra(EXTRA_BG, bgColor)
                putExtra(EXTRA_FG, textColor)
            }
            startActivity(intent)
        }
    }

    private fun fillColorRow(
        row: LinearLayout,
        colors: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ): List<View> {
        val size = (44 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        val views = mutableListOf<View>()
        colors.forEach { hex ->
            val color = Color.parseColor(hex)
            val view = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = margin
                }
                tag = color
                background = colorSwatch(color, selected = color == selected)
                setOnClickListener {
                    onPick(color)
                    refreshSelection(views, color)
                }
            }
            views += view
            row.addView(view)
        }
        return views
    }

    private fun refreshSelection(views: List<View>, selected: Int) {
        views.forEach { v ->
            val color = v.tag as Int
            v.background = colorSwatch(color, selected = color == selected)
        }
    }

    private fun colorSwatch(color: Int, selected: Boolean): GradientDrawable {
        val strokeColor = when {
            selected -> Color.parseColor("#FF1976D2")
            color == Color.WHITE || color == Color.parseColor("#FFFFFFFF") -> Color.parseColor("#FFBDBDBD")
            else -> Color.parseColor("#66000000")
        }
        val strokeW = if (selected) {
            (4 * resources.displayMetrics.density).toInt()
        } else {
            (2 * resources.displayMetrics.density).toInt()
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(strokeW, strokeColor)
        }
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_SIZE = "size"
        const val EXTRA_SCROLL = "scroll"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_BG = "bg"
        const val EXTRA_FG = "fg"
    }
}
