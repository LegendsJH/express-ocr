package com.expressocr.app.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** Simple four-operation calculator. */
class MathCalculator {
    private var display = "0"
    private var operand: BigDecimal? = null
    private var pendingOp: Char? = null
    private var resetOnNextDigit = false
    private var expression = ""

    fun getDisplay(): String = display
    fun getExpression(): String = expression

    fun inputDigit(d: String) {
        if (resetOnNextDigit || display == "0") {
            display = if (d == ".") "0." else d
            resetOnNextDigit = false
        } else {
            if (d == "." && display.contains('.')) return
            if (display == "0" && d != ".") display = d else display += d
        }
    }

    fun input00() {
        if (resetOnNextDigit) {
            display = "0"
            resetOnNextDigit = false
            return
        }
        if (display == "0") return
        display += "00"
    }

    fun setOperator(op: Char) {
        val current = display.toBigDecimalOrNull() ?: return
        if (operand != null && pendingOp != null && !resetOnNextDigit) {
            val result = compute(operand!!, current, pendingOp!!)
            display = format(result)
            operand = result
        } else {
            operand = current
        }
        pendingOp = op
        expression = "${format(operand!!)} $op"
        resetOnNextDigit = true
    }

    fun equals(): String {
        val current = display.toBigDecimalOrNull() ?: return display
        val op = pendingOp
        val left = operand
        if (op == null || left == null) return display
        val result = compute(left, current, op)
        expression = "${format(left)} $op ${format(current)}"
        display = format(result)
        operand = null
        pendingOp = null
        resetOnNextDigit = true
        return display
    }

    fun percent() {
        val v = display.toBigDecimalOrNull() ?: return
        display = format(v.divide(BigDecimal(100), 12, RoundingMode.HALF_UP))
        resetOnNextDigit = true
    }

    fun backspace() {
        if (resetOnNextDigit) return
        display = if (display.length <= 1) "0" else display.dropLast(1)
    }

    fun clear() {
        display = "0"
        operand = null
        pendingOp = null
        expression = ""
        resetOnNextDigit = false
    }

    private fun compute(a: BigDecimal, b: BigDecimal, op: Char): BigDecimal = when (op) {
        '+' -> a.add(b)
        '-' -> a.subtract(b)
        '×', '*' -> a.multiply(b)
        '÷', '/' -> {
            if (b.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
            else a.divide(b, 12, RoundingMode.HALF_UP)
        }
        else -> b
    }

    private fun format(v: BigDecimal): String {
        val s = v.stripTrailingZeros().toPlainString()
        return if (s == "-0") "0" else s
    }
}
