package com.expressocr.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxes = mutableListOf<RectF>()
    private var imageWidth = 0
    private var imageHeight = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = sp(18f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val arrowPath = Path()
    private val label = "在这里！"

    fun clearBoxes() {
        boxes.clear()
        imageWidth = 0
        imageHeight = 0
        invalidate()
    }

    fun setBoxes(sourceBoxes: List<Rect>, imgWidth: Int, imgHeight: Int) {
        imageWidth = imgWidth
        imageHeight = imgHeight
        boxes.clear()
        sourceBoxes.forEach { boxes += RectF(it) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / imageWidth, viewH / imageHeight)
        val dx = (viewW - imageWidth * scale) / 2f
        val dy = (viewH - imageHeight * scale) / 2f

        boxes.forEach { box ->
            val mapped = RectF(
                box.left * scale + dx,
                box.top * scale + dy,
                box.right * scale + dx,
                box.bottom * scale + dy
            )
            // Expand outward so the stroke doesn't cover the digits
            val pad = dp(8f)
            mapped.inset(-pad, -pad)
            mapped.left = mapped.left.coerceAtLeast(0f)
            mapped.top = mapped.top.coerceAtLeast(0f)
            mapped.right = mapped.right.coerceAtMost(viewW)
            mapped.bottom = mapped.bottom.coerceAtMost(viewH)

            canvas.drawRect(mapped, boxPaint)
            drawArrowAndLabel(canvas, mapped)
        }
    }

    private fun drawArrowAndLabel(canvas: Canvas, box: RectF) {
        val textWidth = textPaint.measureText(label)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padH = dp(10f)
        val padV = dp(6f)
        val gap = dp(8f)
        val stem = dp(28f)
        val head = dp(14f)

        val labelW = textWidth + padH * 2
        val labelH = textHeight + padV * 2
        val placeOnRight = box.right + gap + stem + labelW <= width - dp(4f)
        val placeOnLeft = box.left - gap - stem - labelW >= dp(4f)

        val tipY = box.centerY()
        val labelTop = (tipY - labelH / 2f).coerceIn(dp(4f), (height - labelH - dp(4f)).coerceAtLeast(dp(4f)))

        when {
            placeOnRight || !placeOnLeft -> {
                val tipX = box.right
                var labelLeft = tipX + gap + stem
                if (labelLeft + labelW > width - dp(4f)) {
                    labelLeft = (width - dp(4f) - labelW).coerceAtLeast(dp(4f))
                }
                val labelRect = RectF(labelLeft, labelTop, labelLeft + labelW, labelTop + labelH)
                canvas.drawRoundRect(labelRect, dp(8f), dp(8f), labelBgPaint)
                canvas.drawText(
                    label,
                    labelLeft + padH,
                    labelRect.centerY() - (fm.ascent + fm.descent) / 2f,
                    textPaint
                )
                canvas.drawLine(
                    labelRect.left.coerceAtMost(tipX + stem),
                    labelRect.centerY(),
                    tipX + head,
                    tipY,
                    linePaint
                )
                drawHeadPointingLeft(canvas, tipX, tipY, head)
            }
            else -> {
                val tipX = box.left
                val labelRight = tipX - gap - stem
                val labelLeft = (labelRight - labelW).coerceAtLeast(dp(4f))
                val labelRect = RectF(labelLeft, labelTop, labelLeft + labelW, labelTop + labelH)
                canvas.drawRoundRect(labelRect, dp(8f), dp(8f), labelBgPaint)
                canvas.drawText(
                    label,
                    labelLeft + padH,
                    labelRect.centerY() - (fm.ascent + fm.descent) / 2f,
                    textPaint
                )
                canvas.drawLine(labelRect.right, labelRect.centerY(), tipX - head, tipY, linePaint)
                drawHeadPointingRight(canvas, tipX, tipY, head)
            }
        }
    }

    private fun drawHeadPointingLeft(canvas: Canvas, tipX: Float, tipY: Float, size: Float) {
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(tipX + size, tipY - size * 0.65f)
        arrowPath.lineTo(tipX + size, tipY + size * 0.65f)
        arrowPath.close()
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun drawHeadPointingRight(canvas: Canvas, tipX: Float, tipY: Float, size: Float) {
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(tipX - size, tipY - size * 0.65f)
        arrowPath.lineTo(tipX - size, tipY + size * 0.65f)
        arrowPath.close()
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
