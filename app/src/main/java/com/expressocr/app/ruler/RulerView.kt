package com.expressocr.app.ruler

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class RulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Pixels per millimeter after calibration. */
    var pxPerMm: Float = context.resources.displayMetrics.ydpi / 25.4f
        set(value) {
            field = value.coerceIn(2f, 40f)
            invalidate()
        }

    var calibrating: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var guideMm: Float = 50f

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF66BB6A")
        textSize = 28f
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1565C0")
        strokeWidth = 3f
        textSize = 36f
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 2f
    }

    fun setGuideFromTouch(y: Float) {
        guideMm = (y / pxPerMm).coerceIn(0f, height / pxPerMm)
        invalidate()
    }

    fun currentGuideMm(): Float = guideMm

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val maxMm = (height / pxPerMm).toInt()
        for (mm in 0..maxMm) {
            val y = mm * pxPerMm
            val len = when {
                mm % 10 == 0 -> width * 0.28f
                mm % 5 == 0 -> width * 0.18f
                else -> width * 0.10f
            }
            canvas.drawLine(0f, y, len, y, tickPaint)
            if (mm % 10 == 0) {
                canvas.drawText((mm / 10).toString(), len + 12f, y + 10f, textPaint)
            }
        }
        canvas.drawLine(0f, 0f, 0f, height.toFloat(), edgePaint)

        if (calibrating) {
            val gy = guideMm * pxPerMm
            canvas.drawLine(0f, gy, width.toFloat(), gy, guidePaint)
            canvas.drawText(
                String.format("%.0f mm", guideMm),
                width * 0.4f,
                gy - 12f,
                guidePaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!calibrating) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                setGuideFromTouch(event.y)
                return true
            }
        }
        return true
    }

    fun applyCalibration(realMm: Float) {
        if (realMm <= 0f || guideMm <= 0f) return
        // User aligned guide to realMm on a physical ruler; adjust scale so guideMm maps to realMm
        // Actually: user places physical ruler, moves guide to a known mark (e.g. 50mm on physical).
        // guideMm is current displayed mm under old scale. We want displayed mm at that pixel to become realMm.
        // pixel = guideMm * pxPerMm_old = realMm * pxPerMm_new => pxPerMm_new = guideMm * pxPerMm_old / realMm
        // Simpler UX: user sets guide to where physical 100mm is, then we set pxPerMm = guideY / 100
        val guideY = guideMm * pxPerMm
        pxPerMm = guideY / realMm
    }

    fun calibrateToPhysicalMm(physicalMm: Float) {
        val guideY = max(1f, guideMm * pxPerMm)
        pxPerMm = guideY / physicalMm
        guideMm = physicalMm
        invalidate()
    }

    companion object {
        fun defaultPxPerMm(context: Context): Float =
            context.resources.displayMetrics.ydpi / 25.4f
    }
}
