package com.expressocr.app.compass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CompassDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var azimuthDeg: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 4f, Color.parseColor("#33000000"))
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF666666")
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF333333")
        textAlign = Paint.Align.CENTER
        textSize = 36f
        isFakeBoldText = true
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF6D00")
        textAlign = Paint.Align.CENTER
        textSize = 40f
        isFakeBoldText = true
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF6D00")
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - 16f

        canvas.drawCircle(cx, cy, radius, dialPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        canvas.save()
        canvas.rotate(-azimuthDeg, cx, cy)

        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val x1 = cx + (radius - 18) * sin(rad).toFloat()
            val y1 = cy - (radius - 18) * cos(rad).toFloat()
            val x2 = cx + radius * sin(rad).toFloat()
            val y2 = cy - radius * cos(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
            val label = when (deg) {
                0 -> "北"
                90 -> "东"
                180 -> "南"
                270 -> "西"
                else -> deg.toString()
            }
            val lx = cx + (radius - 48) * sin(rad).toFloat()
            val ly = cy - (radius - 48) * cos(rad).toFloat() + 12f
            canvas.drawText(label, lx, ly, if (deg == 0) northPaint else textPaint)
        }
        canvas.restore()

        // Fixed phone-forward marker at top
        val markerPathX = floatArrayOf(cx, cx - 14f, cx + 14f)
        val markerPathY = floatArrayOf(cy - radius + 8f, cy - radius + 36f, cy - radius + 36f)
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(markerPathX[0], markerPathY[0])
            lineTo(markerPathX[1], markerPathY[1])
            lineTo(markerPathX[2], markerPathY[2])
            close()
        }, needlePaint)
    }
}
