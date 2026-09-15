package com.expressocr.app.camera

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import java.util.concurrent.TimeUnit

/**
 * Pinch-to-zoom and tap-to-focus for live camera preview.
 */
class CameraPreviewGestures(
    private val previewView: PreviewView,
    private val focusRing: View,
    private var camera: Camera? = null
) {
    private val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val zoomState = cam.cameraInfo.zoomState.value ?: return false
                val next = (zoomState.zoomRatio * detector.scaleFactor)
                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                cam.cameraControl.setZoomRatio(next)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        previewView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                focusAt(e.x, e.y)
                return true
            }
        }
    )

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }

    fun bindCamera(camera: Camera?) {
        this.camera = camera
    }

    private fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .addPoint(point, FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
        showFocusRing(x, y)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.animate().cancel()
        focusRing.visibility = View.VISIBLE
        focusRing.x = x - focusRing.width / 2f
        focusRing.y = y - focusRing.height / 2f
        // width may be 0 before layout; post if needed
        if (focusRing.width == 0 || focusRing.height == 0) {
            focusRing.post {
                focusRing.x = x - focusRing.width / 2f
                focusRing.y = y - focusRing.height / 2f
            }
        }
        focusRing.scaleX = 1.3f
        focusRing.scaleY = 1.3f
        focusRing.alpha = 1f
        focusRing.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                focusRing.animate()
                    .alpha(0f)
                    .setStartDelay(600)
                    .setDuration(220)
                    .withEndAction { focusRing.visibility = View.GONE }
                    .start()
            }
            .start()
    }
}
