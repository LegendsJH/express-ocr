package com.expressocr.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * Pinch-zoom + pan. Scales the first child so image and overlay stay aligned.
 */
class ZoomLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f

    private val minScale = 1f
    private val maxScale = 5f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
                val focusX = detector.focusX
                val focusY = detector.focusY
                translateX = focusX - (focusX - translateX) * (scaleFactor / oldScale)
                translateY = focusY - (focusY - translateY) * (scaleFactor / oldScale)
                clampTranslation()
                applyTransform()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleFactor <= 1.01f) return false
                translateX -= distanceX
                translateY -= distanceY
                clampTranslation()
                applyTransform()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > 1.05f) {
                    resetZoom()
                } else {
                    scaleFactor = 2.5f
                    translateX = e.x * (1 - scaleFactor)
                    translateY = e.y * (1 - scaleFactor)
                    clampTranslation()
                    applyTransform()
                }
                return true
            }
        }
    )

    fun resetZoom() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        applyTransform()
    }

    private fun applyTransform() {
        if (childCount == 0) return
        val child = getChildAt(0)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scaleFactor
        child.scaleY = scaleFactor
        child.translationX = translateX
        child.translationY = translateY
    }

    private fun clampTranslation() {
        if (width == 0 || height == 0) return
        val contentW = width * scaleFactor
        val contentH = height * scaleFactor

        if (scaleFactor <= 1.01f) {
            translateX = 0f
            translateY = 0f
            return
        }

        if (contentW <= width) {
            translateX = (width - contentW) / 2f
        } else {
            translateX = translateX.coerceIn(width - contentW, 0f)
        }

        if (contentH <= height) {
            translateY = (height - contentH) / 2f
        } else {
            translateY = translateY.coerceIn(height - contentH, 0f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampTranslation()
        applyTransform()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaleFactor > 1f) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }
}
