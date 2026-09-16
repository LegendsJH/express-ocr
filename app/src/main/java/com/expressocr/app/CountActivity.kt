package com.expressocr.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.expressocr.app.camera.CameraPreviewGestures
import com.expressocr.app.camera.ImageConverters
import com.expressocr.app.count.CircleAnnotator
import com.expressocr.app.count.CircleDetector
import com.expressocr.app.count.CountPreset
import com.expressocr.app.databinding.ActivityCountBinding
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCountBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewGestures: CameraPreviewGestures

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var openCvReady = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openCvReady = OpenCVLoader.initLocal()
        if (!openCvReady) {
            Toast.makeText(this, R.string.opencv_init_failed, Toast.LENGTH_LONG).show()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        previewGestures = CameraPreviewGestures(binding.previewView, binding.focusRing)
        previewGestures.attach()

        binding.btnCapture.setOnClickListener { captureAndCount() }
        binding.btnRetake.setOnClickListener { showPreviewMode() }

        ensureCameraPermission()
    }

    private fun currentPreset(): CountPreset {
        return if (binding.chipPipe.isChecked) CountPreset.PIPE else CountPreset.CHOPSTICK
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
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        try {
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            previewGestures.bindCamera(camera)
        } catch (e: Exception) {
            camera = null
            previewGestures.bindCamera(null)
            Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun captureAndCount() {
        if (!openCvReady) {
            Toast.makeText(this, R.string.opencv_init_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false
        binding.statusText.setText(R.string.counting)

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = try {
                        ImageConverters.imageProxyToBitmap(imageProxy)
                    } finally {
                        imageProxy.close()
                    }
                    cameraExecutor.execute {
                        try {
                            val circles = CircleDetector.detect(bitmap, currentPreset())
                            val annotated = CircleAnnotator.annotate(bitmap, circles)
                            runOnUiThread {
                                showResult(annotated, circles.size)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                binding.btnCapture.isEnabled = true
                                binding.statusText.setText(R.string.count_failed)
                                Toast.makeText(
                                    this@CountActivity,
                                    e.message ?: getString(R.string.count_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    binding.statusText.setText(R.string.count_failed)
                }
            }
        )
    }

    private fun showResult(bitmap: Bitmap, count: Int) {
        binding.previewContainer.visibility = View.INVISIBLE
        binding.zoomLayout.visibility = View.VISIBLE
        binding.zoomLayout.resetZoom()
        binding.resultImage.setImageBitmap(bitmap)
        binding.statusText.text = getString(R.string.count_result, count)
        binding.btnCapture.visibility = View.GONE
        binding.btnRetake.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = true
        binding.presetGroup.isEnabled = false
        binding.chipChopstick.isEnabled = false
        binding.chipPipe.isEnabled = false
    }

    private fun showPreviewMode() {
        binding.resultImage.setImageDrawable(null)
        binding.zoomLayout.resetZoom()
        binding.zoomLayout.visibility = View.GONE
        binding.previewContainer.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.VISIBLE
        binding.btnRetake.visibility = View.GONE
        binding.statusText.setText(R.string.count_hint)
        binding.presetGroup.isEnabled = true
        binding.chipChopstick.isEnabled = true
        binding.chipPipe.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
