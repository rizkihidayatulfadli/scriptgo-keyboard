package com.magickeyboard.contacts

/**
 * Minimal RFC 4180 reader for contact imports. Handles quoted fields (with `""` escapes and line
 * breaks), CRLF, a UTF-8 BOM, and detects the delimiter: Excel in Indonesian locales saves `;`.
 * Values are trimmed and blank lines are dropped. Never throws.
 */
object Csv {
    private val DELIMITERS = charArrayOf(',', ';', '\t')

    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix("﻿")
        val delimiter = detectDelimiter(input)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row += field.toString().trim()
            field.clear()
        }

        fun endRow() {
            endField()
            if (row.any { it.isNotEmpty() }) rows += row
            row = mutableListOf()
        }

        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' && (inQuotes || field.isBlank()) -> {
                    if (!inQuotes) field.clear()
                    inQuotes = !inQuotes
                }
                !inQuotes && c == delimiter -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    endRow()
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** The candidate delimiter that occurs most often outside quotes on the first line. */
    private fun detectDelimiter(text: String): Char {
        val counts = IntArray(DELIMITERS.size)
        var inQuotes = false
        for (c in text) {
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes && (c == '\n' || c == '\r')) break
            if (!inQuotes) DELIMITERS.indexOf(c).takeIf { it >= 0 }?.let { counts[it]++ }
        }
        val best = counts.indices.maxBy { counts[it] }
        return if (counts[best] == 0) ',' else DELIMITERS[best]
    }
}
