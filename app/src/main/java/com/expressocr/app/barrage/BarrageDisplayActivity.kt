package com.expressocr.app.barrage

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.expressocr.app.databinding.ActivityBarrageDisplayBinding

class BarrageDisplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarrageDisplayBinding
    private var animator: ObjectAnimator? = null
    private var scrollSpeed = 5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarrageDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        val text = intent.getStringExtra(BarrageSetupActivity.EXTRA_TEXT).orEmpty()
        val sizeSp = intent.getFloatExtra(BarrageSetupActivity.EXTRA_SIZE, 72f)
        val scroll = intent.getBooleanExtra(BarrageSetupActivity.EXTRA_SCROLL, true)
        scrollSpeed = intent.getFloatExtra(BarrageSetupActivity.EXTRA_SPEED, 5f).coerceIn(1f, 10f)
        val bg = intent.getIntExtra(BarrageSetupActivity.EXTRA_BG, Color.YELLOW)
        val fg = intent.getIntExtra(BarrageSetupActivity.EXTRA_FG, Color.BLACK)

        binding.root.setBackgroundColor(bg)
        val tv = binding.barrageText
        tv.text = text
        tv.setTextColor(fg)
        tv.ellipsize = null
        tv.includeFontPadding = false
        // Prevent parent AT_MOST width from truncating long/large text
        tv.setHorizontallyScrolling(true)
        tv.maxWidth = Int.MAX_VALUE / 4

        binding.btnBack.setOnClickListener { finish() }

        binding.root.post {
            val targetSp = mapSizeToScreen(sizeSp)
            if (scroll) {
                setupScrollText(targetSp)
                startScrollLoop()
            } else {
                setupStaticText(targetSp)
            }
        }
    }

    /** Slider 24..160 → about 40%..92% of screen height. */
    private fun mapSizeToScreen(sliderSp: Float): Float {
        val h = binding.root.height.coerceAtLeast(1)
        val t = ((sliderSp - 24f) / (160f - 24f)).coerceIn(0f, 1f)
        val fill = 0.40f + t * 0.52f
        return (h * fill) / resources.displayMetrics.scaledDensity
    }

    private fun setupScrollText(preferredSp: Float) {
        val tv = binding.barrageText
        tv.isSingleLine = true
        tv.maxLines = 1
        tv.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        // Height only constrained; width must be full text width (not screen width)
        var sp = preferredSp
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)

        val maxH = binding.root.height * 0.92f
        measureFullWidth(tv)
        if (tv.measuredHeight > maxH && maxH > 0 && tv.measuredHeight > 0) {
            sp *= maxH / tv.measuredHeight
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            measureFullWidth(tv)
        }
        applyExactMeasuredSize(tv)
    }

    private fun setupStaticText(preferredSp: Float) {
        val tv = binding.barrageText
        tv.setHorizontallyScrolling(false)
        tv.isSingleLine = false
        tv.maxLines = Int.MAX_VALUE
        tv.gravity = Gravity.CENTER
        tv.layoutParams = tv.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        tv.requestLayout()
        tv.post { fitAllTextOnScreen(tv, preferredSp) }
    }

    /** Shrink (and wrap if needed) so every character is visible. */
    private fun fitAllTextOnScreen(tv: TextView, preferredSp: Float) {
        val availW = (tv.width - tv.paddingLeft - tv.paddingRight).coerceAtLeast(1)
        val availH = (tv.height - tv.paddingTop - tv.paddingBottom).coerceAtLeast(1)
        val content = tv.text.toString()
        if (content.isEmpty()) return

        // Try single line first at largest size that fits both W and H
        var lo = 8f
        var hi = preferredSp.coerceAtLeast(8f)
        var bestOneLine = 8f
        while (hi - lo > 0.4f) {
            val mid = (lo + hi) / 2f
            val paint = tv.paint
            paint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, mid, resources.displayMetrics
            )
            val w = paint.measureText(content)
            val h = paint.fontMetrics.let { it.descent - it.ascent }
            if (w <= availW && h <= availH) {
                bestOneLine = mid
                lo = mid
            } else {
                hi = mid
            }
        }

        // If one-line at preferred is too wide, wrap and binary-search so full text fits height
        paintCheck(tv, bestOneLine)
        val oneLineW = tv.paint.measureText(content)
        if (oneLineW <= availW) {
            tv.isSingleLine = true
            tv.maxLines = 1
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, bestOneLine)
            return
        }

        tv.isSingleLine = false
        tv.maxLines = 20
        lo = 8f
        hi = preferredSp.coerceAtLeast(8f)
        var best = 8f
        while (hi - lo > 0.4f) {
            val mid = (lo + hi) / 2f
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, mid)
            tv.measure(
                View.MeasureSpec.makeMeasureSpec(availW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (tv.measuredHeight <= availH) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, best)
    }

    private fun paintCheck(tv: TextView, sp: Float) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    private fun measureFullWidth(tv: TextView) {
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
    }

    private fun applyExactMeasuredSize(tv: TextView) {
        measureFullWidth(tv)
        // Also verify with Paint in case TextView still under-measures
        val paintW = tv.paint.measureText(tv.text.toString()).toInt() + tv.paddingLeft + tv.paddingRight + 8
        val w = maxOf(tv.measuredWidth, paintW)
        val h = tv.measuredHeight.coerceAtLeast(1)
        tv.layoutParams = (tv.layoutParams as ViewGroup.MarginLayoutParams).apply {
            width = w
            height = h
        }
        tv.requestLayout()
    }

    private fun startScrollLoop() {
        animator?.cancel()
        val tv = binding.barrageText
        tv.post {
            applyExactMeasuredSize(tv)
            val textWidth = tv.layoutParams.width.toFloat().coerceAtLeast(1f)
            val screenWidth = binding.root.width.toFloat().coerceAtLeast(1f)
            val pxPerMs = 0.06f + scrollSpeed * 0.055f
            val duration = ((textWidth + screenWidth) / pxPerMs).toLong().coerceIn(1800L, 120000L)

            tv.translationX = screenWidth
            animator = ObjectAnimator.ofFloat(tv, View.TRANSLATION_X, screenWidth, -textWidth).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                start()
            }
        }
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        animator?.cancel()
        super.onDestroy()
    }
}
