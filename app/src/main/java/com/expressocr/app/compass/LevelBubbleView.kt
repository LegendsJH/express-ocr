package com.expressocr.app.compass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class LevelBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** -1..1 relative offsets */
    var offsetX: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }
    var offsetY: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    private val bowlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 3f, Color.parseColor("#22000000"))
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFBDBDBD")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9E9E9E")
        strokeWidth = 2f
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF6D00")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 12f
        canvas.drawCircle(cx, cy, radius, bowlPaint)
        canvas.drawCircle(cx, cy, radius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.33f, ringPaint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint)

        val maxTravel = radius - 28f
        val bx = cx + offsetX * maxTravel
        val by = cy + offsetY * maxTravel
        canvas.drawCircle(bx, by, 26f, bubblePaint)
    }
}
