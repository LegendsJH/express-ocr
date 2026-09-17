package com.expressocr.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.expressocr.app.barrage.BarrageSetupActivity
import com.expressocr.app.calculator.CalculatorActivity
import com.expressocr.app.clock.ClockActivity
import com.expressocr.app.compass.CompassActivity
import com.expressocr.app.databinding.ActivityHomeBinding
import com.expressocr.app.reaction.ReactionActivity
import com.expressocr.app.ruler.RulerActivity
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var pendingSaveResId: Int? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val resId = pendingSaveResId
        pendingSaveResId = null
        if (granted && resId != null) {
            saveDrawableToGallery(resId)
        } else if (!granted) {
            Toast.makeText(this, R.string.tip_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = Color.parseColor("#FFF7F8FA")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnPickup.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.btnBarrage.setOnClickListener {
            startActivity(Intent(this, BarrageSetupActivity::class.java))
        }
        binding.btnRuler.setOnClickListener {
            startActivity(Intent(this, RulerActivity::class.java))
        }
        binding.btnClock.setOnClickListener {
            startActivity(Intent(this, ClockActivity::class.java))
        }
        binding.btnCompass.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }
        binding.btnCalculator.setOnClickListener {
            startActivity(Intent(this, CalculatorActivity::class.java))
        }
        binding.btnReaction.setOnClickListener {
            startActivity(Intent(this, ReactionActivity::class.java))
        }
        binding.btnTipAuthor.setOnClickListener { showTipAuthorDialog() }
    }

    private fun showTipAuthorDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tip_author, null)
        val imgWechat = view.findViewById<ImageView>(R.id.imgWechat)
        val imgAlipay = view.findViewById<ImageView>(R.id.imgAlipay)

        imgWechat.setOnLongClickListener {
            showQrActions(R.drawable.qr_wechat)
            true
        }
        imgAlipay.setOnLongClickListener {
            showQrActions(R.drawable.qr_alipay)
            true
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.tip_author_title)
            .setView(view)
            .setPositiveButton(R.string.tip_close, null)
            .show()
    }

    private fun showQrActions(resId: Int) {
        val labels = arrayOf(
            getString(R.string.tip_recognize_qr),
            getString(R.string.tip_save_image)
        )
        AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> recognizeAndOpenQr(resId)
                    1 -> requestSaveImage(resId)
                }
            }
            .show()
    }

    /** Decode QR from image (like browser) and open the payload URI. */
    private fun recognizeAndOpenQr(resId: Int) {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        if (bitmap == null) {
            Toast.makeText(this, R.string.tip_recognize_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val content = decodeQrContent(bitmap)
        if (content.isNullOrBlank()) {
            Toast.makeText(this, R.string.tip_recognize_failed, Toast.LENGTH_LONG).show()
            return
        }
        openQrContent(content)
    }

    private fun decodeQrContent(bitmap: Bitmap): String? {
        val candidates = listOf(
            bitmap,
            // Focus on center where QR usually sits in payment screenshots
            centerCrop(bitmap, 0.72f),
            centerCrop(bitmap, 0.55f)
        )
        for (candidate in candidates) {
            decodeOnce(candidate)?.let { return it }
            decodeOnce(invertBitmap(candidate))?.let { return it }
        }
        return null
    }

    private fun centerCrop(src: Bitmap, keep: Float): Bitmap {
        val w = src.width
        val h = src.height
        val cw = (w * keep).toInt().coerceAtLeast(1)
        val ch = (h * keep).toInt().coerceAtLeast(1)
        val x = ((w - cw) / 2).coerceAtLeast(0)
        val y = ((h - ch) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(src, x, y, cw.coerceAtMost(w - x), ch.coerceAtMost(h - y))
    }

    private fun invertBitmap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = 255 - ((c shr 16) and 0xFF)
            val g = 255 - ((c shr 8) and 0xFF)
            val b = 255 - (c and 0xFF)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun decodeOnce(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            MultiFormatReader().decode(binary, hints).text
        } catch (_: Exception) {
            null
        }
    }

    private fun openQrContent(content: String) {
        val uri = runCatching { Uri.parse(content.trim()) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank()) {
            Toast.makeText(this, R.string.tip_recognize_failed, Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Custom schemes (wxp:// etc.) may need package-specific handling
            val fallback = when {
                content.contains("weixin", ignoreCase = true) ||
                    content.startsWith("wxp://") ||
                    content.startsWith("weixin://") ->
                    Intent(Intent.ACTION_VIEW, Uri.parse("weixin://"))
                content.contains("alipay", ignoreCase = true) ->
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("alipayqr://platformapi/startapp?saId=10000007")
                    )
                else -> null
            }
            if (fallback != null) {
                try {
                    startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: Exception) {
                    // fall through
                }
            }
            Toast.makeText(this, R.string.tip_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestSaveImage(resId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveDrawableToGallery(resId)
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            saveDrawableToGallery(resId)
        } else {
            pendingSaveResId = resId
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun saveDrawableToGallery(resId: Int) {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, resId)
                ?: throw IllegalStateException("decode failed")
            val name = "toolbox_tip_${System.currentTimeMillis()}.jpg"
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Toolbox"
                    )
                }
                val outUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                outUri?.let { target ->
                    contentResolver.openOutputStream(target)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    }
                }
                outUri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(dir, "Toolbox").apply { mkdirs() }
                val file = File(folder, name)
                FileOutputStream(file).use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
                @Suppress("DEPRECATION")
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
            if (uri != null) {
                Toast.makeText(this, R.string.tip_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.tip_save_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.tip_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
