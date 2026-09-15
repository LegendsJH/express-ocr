package com.expressocr.app.match

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

data class MatchedBlock(
    val text: String,
    val box: Rect
)

object CodeMatcher {
    const val MIN_DIGITS = 3

    fun digitsOnly(input: String): String = input.filter { it.isDigit() }

    fun isValidQuery(input: String): Boolean = digitsOnly(input).length >= MIN_DIGITS

    /**
     * Match OCR blocks against user input.
     * Example: OCR "2-2-2302" matches user "2302".
     */
    fun findMatches(text: Text, userInput: String): List<MatchedBlock> {
        val query = digitsOnly(userInput)
        if (query.length < MIN_DIGITS) return emptyList()

        val matches = mutableListOf<MatchedBlock>()

        for (block in text.textBlocks) {
            collectMatches(block.text, block.boundingBox, query, matches)
            for (line in block.lines) {
                collectMatches(line.text, line.boundingBox, query, matches)
                for (element in line.elements) {
                    collectMatches(element.text, element.boundingBox, query, matches)
                }
            }
        }

        return matches
            .distinctBy { "${it.box.flattenToString()}|${it.text}" }
            .sortedByDescending { it.box.width() * it.box.height() }
    }

    private fun collectMatches(
        raw: String,
        box: Rect?,
        query: String,
        out: MutableList<MatchedBlock>
    ) {
        if (box == null) return
        val digits = digitsOnly(raw)
        if (digits.isEmpty()) return
        if (digits == query || digits.endsWith(query) || digits.contains(query)) {
            out += MatchedBlock(raw, Rect(box))
        }
    }
}
