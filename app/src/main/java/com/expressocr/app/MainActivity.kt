package com.expressocr.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.expressocr.app.camera.CameraPreviewGestures
import com.expressocr.app.camera.ImageConverters
import com.expressocr.app.databinding.ActivityMainBinding
import com.expressocr.app.match.CodeMatcher
import com.expressocr.app.ocr.OcrHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewGestures: CameraPreviewGestures

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private val ocrHelper = OcrHelper()
    private val videoScanning = AtomicBoolean(false)
    private val ocrBusy = AtomicBoolean(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        previewGestures = CameraPreviewGestures(binding.previewView, binding.focusRing)
        previewGestures.attach()
        setupButtons()
        setStatus(getString(R.string.camera_gesture_hint))
        ensureCameraPermission()
    }

    private fun setupButtons() {
        binding.btnPhoto.setOnClickListener { takePhotoAndRecognize() }
        binding.btnVideo.setOnClickListener { startVideoRecognize() }
        binding.btnStopOrResume.setOnClickListener {
            if (videoScanning.get()) {
                stopVideoRecognize(resumePreview = true)
            } else {
                resumeLivePreview()
            }
        }
    }

    private fun ensureCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> startCamera()
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        try {
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalysis
            )
            previewGestures.bindCamera(camera)
        } catch (e: Exception) {
            camera = null
            previewGestures.bindCamera(null)
            Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun currentQuery(): String? {
        val input = binding.inputCode.text?.toString().orEmpty()
        if (!CodeMatcher.isValidQuery(input)) {
            Toast.makeText(this, R.string.input_too_short, Toast.LENGTH_SHORT).show()
            return null
        }
        return input
    }

    private fun takePhotoAndRecognize() {
        val query = currentQuery() ?: return
        val capture = imageCapture ?: return

        stopVideoRecognize(resumePreview = false)
        setStatus(getString(R.string.scanning))
        setButtonsEnabled(false)

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = try {
                        ImageConverters.imageProxyToBitmap(imageProxy)
                    } finally {
                        imageProxy.close()
                    }
                    runOcrAndShowResult(bitmap, query)
                }

                override fun onError(exception: ImageCaptureException) {
                    setButtonsEnabled(true)
                    setStatus(getString(R.string.ocr_failed))
                    Toast.makeText(
                        this@MainActivity,
                        exception.message ?: getString(R.string.ocr_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun startVideoRecognize() {
        val query = currentQuery() ?: return
        val analysis = imageAnalysis ?: return

        resumeLivePreview()
        videoScanning.set(true)
        ocrBusy.set(false)
        setStatus(getString(R.string.scanning))
        binding.btnStopOrResume.visibility = View.VISIBLE
        binding.btnStopOrResume.setText(R.string.btn_stop)
        setButtonsEnabled(false)

        analysis.clearAnalyzer()
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!videoScanning.get()) {
                imageProxy.close()
                return@setAnalyzer
            }
            if (!ocrBusy.compareAndSet(false, true)) {
                imageProxy.close()
                return@setAnalyzer
            }

            val bitmap = try {
                ImageConverters.imageProxyToBitmap(imageProxy)
            } catch (_: Exception) {
                ocrBusy.set(false)
                imageProxy.close()
                return@setAnalyzer
            }
            imageProxy.close()

            ocrHelper.recognize(bitmap)
                .addOnSuccessListener { text ->
                    if (!videoScanning.get()) {
                        ocrBusy.set(false)
                        return@addOnSuccessListener
                    }
                    val matches = CodeMatcher.findMatches(text, query)
                    if (matches.isNotEmpty()) {
                        videoScanning.set(false)
                        runOnUiThread {
                            showFrozenResult(bitmap, matches.map { it.box })
                            setStatus(
                                getString(R.string.matched) +
                                    " · ${matches.first().text} · " +
                                    getString(R.string.zoom_hint)
                            )
                            binding.btnStopOrResume.setText(R.string.btn_resume)
                            setButtonsEnabled(true)
                        }
                    }
                    ocrBusy.set(false)
                }
                .addOnFailureListener {
                    ocrBusy.set(false)
                }
        }
    }

    private fun stopVideoRecognize(resumePreview: Boolean) {
        videoScanning.set(false)
        imageAnalysis?.clearAnalyzer()
        ocrBusy.set(false)
        setButtonsEnabled(true)
        if (resumePreview) {
            resumeLivePreview()
            setStatus("")
            binding.btnStopOrResume.visibility = View.GONE
        }
    }

    private fun runOcrAndShowResult(bitmap: Bitmap, query: String) {
        ocrHelper.recognize(bitmap)
            .addOnSuccessListener { text ->
                val matches = CodeMatcher.findMatches(text, query)
                if (matches.isEmpty()) {
                    setStatus(getString(R.string.not_found))
                    binding.overlayView.clearBoxes()
                } else {
                    showFrozenResult(bitmap, matches.map { it.box })
                    setStatus(
                        getString(R.string.matched) +
                            " · ${matches.first().text} · " +
                            getString(R.string.zoom_hint)
                    )
                }
                setButtonsEnabled(true)
            }
            .addOnFailureListener {
                setStatus(getString(R.string.ocr_failed))
                setButtonsEnabled(true)
            }
    }

    private fun showFrozenResult(bitmap: Bitmap, boxes: List<Rect>) {
        imageAnalysis?.clearAnalyzer()
        videoScanning.set(false)

        binding.previewContainer.visibility = View.INVISIBLE
        binding.zoomLayout.visibility = View.VISIBLE
        binding.zoomLayout.resetZoom()
        binding.freezeImage.setImageBitmap(bitmap)
        binding.overlayView.setBoxes(boxes, bitmap.width, bitmap.height)

        binding.btnStopOrResume.visibility = View.VISIBLE
        binding.btnStopOrResume.setText(R.string.btn_resume)
        setButtonsEnabled(true)
    }

    private fun resumeLivePreview() {
        binding.freezeImage.setImageDrawable(null)
        binding.overlayView.clearBoxes()
        binding.zoomLayout.resetZoom()
        binding.zoomLayout.visibility = View.GONE
        binding.previewContainer.visibility = View.VISIBLE
        binding.btnStopOrResume.visibility = View.GONE
        setStatus("")
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnPhoto.isEnabled = enabled
        binding.btnVideo.isEnabled = enabled
    }

    private fun setStatus(message: String) {
        binding.statusText.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        videoScanning.set(false)
        imageAnalysis?.clearAnalyzer()
        cameraExecutor.shutdown()
        ocrHelper.close()
    }
}
