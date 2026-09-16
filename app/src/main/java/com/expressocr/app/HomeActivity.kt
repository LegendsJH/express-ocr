package com.expressocr.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.expressocr.app.barrage.BarrageSetupActivity
import com.expressocr.app.calculator.CalculatorActivity
import com.expressocr.app.clock.ClockActivity
import com.expressocr.app.compass.CompassActivity
import com.expressocr.app.databinding.ActivityHomeBinding
import com.expressocr.app.reaction.ReactionActivity
import com.expressocr.app.ruler.RulerActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

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
    }
}
