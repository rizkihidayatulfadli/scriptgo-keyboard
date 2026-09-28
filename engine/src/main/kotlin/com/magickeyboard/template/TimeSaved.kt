package com.magickeyboard.template

/**
 * Estimates typing time saved by inserted templates: characters inserted ÷ phone typing speed.
 * The speed is an assumption (an open question in docs/PRD.md) and may become a user setting.
 */
object TimeSaved {
    /** Roughly 36 words per minute, a common figure for typing on a phone. */
    const val DEFAULT_CHARS_PER_SECOND = 3.0

    fun seconds(charsInserted: Long, charsPerSecond: Double = DEFAULT_CHARS_PER_SECOND): Long =
        if (charsInserted <= 0 || charsPerSecond <= 0) 0 else Math.round(charsInserted / charsPerSecond)

    /**
     * Duration in Indonesian by default: "45 detik", "12 menit", "3 jam 20 menit". Other languages
     * pass their unit words, e.g. "sec", "min", "h" gives "3 h 20 min".
     */
    fun format(seconds: Long, second: String = "detik", minute: String = "menit", hour: String = "jam"): String {
        if (seconds < 60) return "$seconds $second"
        val minutes = seconds / 60
        if (minutes < 60) return "$minutes $minute"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0L) "$hours $hour" else "$hours $hour $rest $minute"
    }
}
