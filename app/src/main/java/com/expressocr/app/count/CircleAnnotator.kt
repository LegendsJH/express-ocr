package com.expressocr.app.count

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object CircleAnnotator {

    fun annotate(source: Bitmap, circles: List<DetectedCircle>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, source.width / 400f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        circles.forEachIndexed { index, circle ->
            canvas.drawCircle(circle.cx, circle.cy, circle.radius, ringPaint)

            val label = (index + 1).toString()
            textPaint.textSize = maxOf(18f, circle.radius * 0.9f)
            val fm = textPaint.fontMetrics
            val textHeight = fm.descent - fm.ascent
            val textWidth = textPaint.measureText(label)
            val pad = textHeight * 0.25f
            val bgR = maxOf(textWidth, textHeight) / 2f + pad

            canvas.drawCircle(circle.cx, circle.cy, bgR, textBgPaint)
            val textY = circle.cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, circle.cx, textY, textPaint)
        }
        return output
    }
}
