package com.expressocr.app.calculator

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import com.expressocr.app.R
import com.expressocr.app.databinding.ActivityCalculatorBinding
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private val math = MathCalculator()
    private val chain = mutableListOf<String>()
    private var selfIsMale = true

    private var heightCm = 170.0f
    private var weightKg = 60.0f

    private val dateFmt = SimpleDateFormat("yyyy-M-d", Locale.getDefault())
    private var diffStart: Calendar = Calendar.getInstance()
    private var diffEnd: Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 30) }
    private var offsetStart: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.calc_title)

        buildMathPad()
        buildKinshipPad()
        setupBmi()
        setupDate()
        refreshMath()
        refreshKinship()
        refreshDateLabels()

        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            showPanel(checkedId)
        }

        binding.genderToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selfIsMale = checkedId == R.id.btnMale
            refreshKinship()
        }

        binding.btnClearChain.setOnClickListener {
            chain.clear()
            refreshKinship()
        }
    }

    private fun showPanel(checkedId: Int) {
        binding.mathPanel.visibility = goneUnless(checkedId == R.id.btnModeMath)
        binding.kinshipPanel.visibility = goneUnless(checkedId == R.id.btnModeKinship)
        binding.bmiPanel.visibility = goneUnless(checkedId == R.id.btnModeBmi)
        binding.datePanel.visibility = goneUnless(checkedId == R.id.btnModeDate)
    }

    private fun goneUnless(visible: Boolean): Int =
        if (visible) View.VISIBLE else View.GONE

    private fun buildMathPad() {
        val keys = listOf(
            "AC", "%", "⌫", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "-",
            "1", "2", "3", "+",
            "00", "0", ".", "="
        )
        keys.forEachIndexed { index, label ->
            val btn = makePadButton(label)
            if (label in listOf("÷", "×", "-", "+", "=")) {
                btn.setBackgroundColor(
                    if (label == "=") Color.parseColor("#FFFF6D00")
                    else Color.parseColor("#FFE0E0E0")
                )
                if (label == "=") btn.setTextColor(Color.WHITE)
            }
            btn.setOnClickListener { onMathKey(label) }
            binding.mathPad.addView(btn, gridParams(index, 4))
        }
    }

    private fun buildKinshipPad() {
        KinshipEngine.keypad.forEachIndexed { index, (code, label) ->
            val btn = makePadButton(label)
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            btn.setOnClickListener {
                chain += code
                refreshKinship()
            }
            binding.kinshipPad.addView(btn, kinshipGridParams(index, 4))
        }
    }

    private fun gridParams(index: Int, columns: Int): GridLayout.LayoutParams {
        val rowH = (52 * resources.displayMetrics.density).toInt()
        return GridLayout.LayoutParams().apply {
            width = 0
            height = rowH
            columnSpec = GridLayout.spec(index % columns, 1f)
            rowSpec = GridLayout.spec(index / columns)
            setMargins(6, 4, 6, 4)
        }
    }

    private fun kinshipGridParams(index: Int, columns: Int): GridLayout.LayoutParams {
        val rowH = (48 * resources.displayMetrics.density).toInt()
        return GridLayout.LayoutParams().apply {
            width = 0
            height = rowH
            columnSpec = GridLayout.spec(index % columns, 1f)
            rowSpec = GridLayout.spec(index / columns)
            setMargins(6, 4, 6, 4)
        }
    }

    private fun makePadButton(label: String): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            insetTop = 0
            insetBottom = 0
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FFF5F5F5"))
            setTextColor(Color.parseColor("#FF222222"))
            cornerRadius = (12 * resources.displayMetrics.density).toInt()
        }
    }

    private fun onMathKey(key: String) {
        when (key) {
            "AC" -> math.clear()
            "⌫" -> math.backspace()
            "%" -> math.percent()
            "÷", "×", "-", "+" -> math.setOperator(key[0])
            "=" -> math.equals()
            "00" -> math.input00()
            else -> math.inputDigit(key)
        }
        refreshMath()
    }

    private fun refreshMath() {
        binding.displayText.text = math.getDisplay()
        binding.exprText.text = math.getExpression()
    }

    private fun refreshKinship() {
        val labels = mutableListOf(getString(R.string.calc_chain_me))
        chain.forEach { labels += KinshipEngine.labelOf(it) }
        binding.chainText.text = labels.joinToString(" → ")
        val result = KinshipEngine.resolve(chain, selfIsMale)
        binding.callThemText.text = getString(R.string.calc_call_them, result.callThem)
        binding.callMeText.text = getString(R.string.calc_call_me, result.callMe)
    }

    private fun setupBmi() {
        fun syncHeight() {
            heightCm = heightCm.coerceIn(50f, 300f)
            binding.heightSlider.value = heightCm
            binding.heightValue.text = getString(R.string.bmi_unit_cm, heightCm)
        }
        fun syncWeight() {
            weightKg = weightKg.coerceIn(20f, 300f)
            binding.weightSlider.value = weightKg
            binding.weightValue.text = getString(R.string.bmi_unit_kg, weightKg)
        }
        syncHeight()
        syncWeight()

        binding.heightSlider.addOnChangeListener { _, value, _ ->
            heightCm = value
            binding.heightValue.text = getString(R.string.bmi_unit_cm, heightCm)
        }
        binding.weightSlider.addOnChangeListener { _, value, _ ->
            weightKg = value
            binding.weightValue.text = getString(R.string.bmi_unit_kg, weightKg)
        }
        binding.btnHeightMinus.setOnClickListener {
            heightCm = (heightCm - 0.5f).coerceAtLeast(50f)
            syncHeight()
        }
        binding.btnHeightPlus.setOnClickListener {
            heightCm = (heightCm + 0.5f).coerceAtMost(300f)
            syncHeight()
        }
        binding.btnWeightMinus.setOnClickListener {
            weightKg = (weightKg - 0.5f).coerceAtLeast(20f)
            syncWeight()
        }
        binding.btnWeightPlus.setOnClickListener {
            weightKg = (weightKg + 0.5f).coerceAtMost(300f)
            syncWeight()
        }
        binding.btnCalcBmi.setOnClickListener { showBmiResult() }
    }

    private fun showBmiResult() {
        val m = heightCm / 100.0
        val bmi = weightKg / m.pow(2.0)
        val rounded = (bmi * 100).roundToInt() / 100.0

        val (cat, advice, color) = when {
            bmi < 18.5 -> Triple(
                getString(R.string.bmi_cat_under),
                getString(R.string.bmi_advice_under),
                Color.parseColor("#FF1976D2")
            )
            bmi < 24.0 -> Triple(
                getString(R.string.bmi_cat_normal),
                getString(R.string.bmi_advice_normal),
                Color.parseColor("#FF2E7D32")
            )
            bmi < 28.0 -> Triple(
                getString(R.string.bmi_cat_over),
                getString(R.string.bmi_advice_over),
                Color.parseColor("#FFF9A825")
            )
            else -> Triple(
                getString(R.string.bmi_cat_obese),
                getString(R.string.bmi_advice_obese),
                Color.parseColor("#FFE53935")
            )
        }

        binding.bmiResultCard.visibility = View.VISIBLE
        binding.bmiHwText.text = getString(R.string.bmi_hw_line, heightCm, weightKg)
        binding.bmiValueText.text = getString(R.string.bmi_value_line, rounded)
        binding.bmiCategoryText.text = cat
        binding.bmiCategoryText.setTextColor(color)
        binding.bmiAdviceText.text = advice
    }

    private fun setupDate() {
        binding.btnDiffStart.setOnClickListener {
            pickDate(diffStart) {
                diffStart = it
                refreshDateLabels()
                refreshDiffResult()
            }
        }
        binding.btnDiffEnd.setOnClickListener {
            pickDate(diffEnd) {
                diffEnd = it
                refreshDateLabels()
                refreshDiffResult()
            }
        }
        binding.btnOffsetStart.setOnClickListener {
            pickDate(offsetStart) {
                offsetStart = it
                refreshDateLabels()
            }
        }
        binding.btnCalcOffset.setOnClickListener { refreshOffsetResult() }
        refreshDiffResult()
        refreshOffsetResult()
    }

    private fun refreshDateLabels() {
        binding.btnDiffStart.text = getString(R.string.date_start, dateFmt.format(diffStart.time))
        binding.btnDiffEnd.text = getString(R.string.date_end, dateFmt.format(diffEnd.time))
        binding.btnOffsetStart.text = getString(R.string.date_start, dateFmt.format(offsetStart.time))
    }

    private fun refreshDiffResult() {
        val days = daysBetween(diffStart, diffEnd)
        binding.diffResult.text = getString(R.string.date_diff_result, abs(days))
    }

    private fun refreshOffsetResult() {
        val days = binding.offsetDaysInput.text?.toString()?.toIntOrNull() ?: 0
        val target = (offsetStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }
        binding.offsetResult.text = if (days >= 0) {
            getString(R.string.date_offset_result, days, dateFmt.format(target.time))
        } else {
            getString(R.string.date_offset_result_before, -days, dateFmt.format(target.time))
        }
    }

    private fun daysBetween(a: Calendar, b: Calendar): Long {
        val a0 = (a.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val b0 = (b.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return TimeUnit.MILLISECONDS.toDays(b0.timeInMillis - a0.timeInMillis)
    }

    private fun pickDate(initial: Calendar, onPicked: (Calendar) -> Unit) {
        DatePickerDialog(
            this,
            { _, y, m, d ->
                onPicked(Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                })
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
