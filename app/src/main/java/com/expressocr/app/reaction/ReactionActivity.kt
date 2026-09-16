package com.expressocr.app.reaction

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.expressocr.app.R
import com.expressocr.app.databinding.ActivityReactionBinding
import kotlin.random.Random

class ReactionActivity : AppCompatActivity() {

    private enum class State { IDLE, WAITING, GO, RESULT, EARLY }

    private lateinit var binding: ActivityReactionBinding
    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var goAtElapsed = 0L
    private val prefs by lazy { getSharedPreferences("reaction", MODE_PRIVATE) }

    private val triggerGo = Runnable {
        if (state != State.WAITING) return@Runnable
        state = State.GO
        goAtElapsed = SystemClock.elapsedRealtime()
        binding.playArea.setBackgroundResource(R.drawable.bg_reaction_go)
        binding.playText.setText(R.string.reaction_go)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.reaction_title)
        refreshStats()
        binding.playArea.setOnClickListener { onTap() }
        binding.btnReset.setOnClickListener { resetStats() }
    }

    private fun onTap() {
        when (state) {
            State.IDLE, State.RESULT, State.EARLY -> startWaiting()
            State.WAITING -> {
                handler.removeCallbacks(triggerGo)
                state = State.EARLY
                binding.playArea.setBackgroundResource(R.drawable.bg_reaction_idle)
                binding.playText.setText(R.string.reaction_early)
            }
            State.GO -> {
                val ms = (SystemClock.elapsedRealtime() - goAtElapsed).toInt().coerceAtLeast(0)
                recordResult(ms)
                state = State.RESULT
                binding.playArea.setBackgroundResource(R.drawable.bg_reaction_idle)
                binding.playText.text = getString(R.string.reaction_result, ms)
            }
        }
    }

    private fun startWaiting() {
        handler.removeCallbacks(triggerGo)
        state = State.WAITING
        binding.playArea.setBackgroundResource(R.drawable.bg_reaction_wait)
        binding.playText.setText(R.string.reaction_wait)
        val delay = Random.nextLong(1200L, 4000L)
        handler.postDelayed(triggerGo, delay)
    }

    private fun recordResult(ms: Int) {
        val best = prefs.getInt(KEY_BEST, Int.MAX_VALUE)
        if (ms < best) prefs.edit().putInt(KEY_BEST, ms).apply()

        val history = prefs.getString(KEY_HISTORY, "").orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .toMutableList()
        history.add(0, ms)
        while (history.size > 5) history.removeAt(history.lastIndex)
        prefs.edit().putString(KEY_HISTORY, history.joinToString(",")).apply()
        refreshStats()
    }

    private fun refreshStats() {
        val best = prefs.getInt(KEY_BEST, Int.MAX_VALUE)
        binding.bestText.text = if (best == Int.MAX_VALUE) getString(R.string.reaction_none) else "${best}ms"

        val history = prefs.getString(KEY_HISTORY, "").orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
        binding.avgText.text = if (history.isEmpty()) {
            getString(R.string.reaction_none)
        } else {
            "${history.average().toInt()}ms"
        }
    }

    private fun resetStats() {
        prefs.edit().clear().apply()
        refreshStats()
        handler.removeCallbacks(triggerGo)
        state = State.IDLE
        binding.playArea.setBackgroundResource(R.drawable.bg_reaction_idle)
        binding.playText.setText(R.string.reaction_start)
    }

    override fun onDestroy() {
        handler.removeCallbacks(triggerGo)
        super.onDestroy()
    }

    companion object {
        private const val KEY_BEST = "best"
        private const val KEY_HISTORY = "history"
    }
}
