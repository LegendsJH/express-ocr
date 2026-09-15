package com.expressocr.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageConverters {

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = when (imageProxy.format) {
            ImageFormat.JPEG -> decodeJpeg(imageProxy)
            else -> yuv420ToBitmap(imageProxy)
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun decodeJpeg(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Failed to decode JPEG frame")
    }

    private fun yuv420ToBitmap(imageProxy: ImageProxy): Bitmap {
        val nv21 = yuv420ToNv21(imageProxy)
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: error("Failed to decode YUV frame")
    }

    private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val nv21 = ByteArray(width * height * 3 / 2)
        var outputOffset = 0

        // Copy Y with row stride
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            var inputOffset = row * yRowStride
            for (col in 0 until width) {
                nv21[outputOffset++] = yBuffer.get(inputOffset)
                inputOffset += yPixelStride
            }
        }

        // Interleave V/U for NV21
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val index = row * uvRowStride + col * uvPixelStride
                nv21[outputOffset++] = vBuffer.get(index)
                nv21[outputOffset++] = uBuffer.get(index)
            }
        }
        return nv21
    }
}
