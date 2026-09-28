package com.magickeyboard.template

/** A trigger being typed right before the cursor, e.g. `/ong`. */
data class TriggerMatch(
    /** Text after the trigger character, possibly empty. */
    val query: String,
    /** How many characters before the cursor the trigger occupies (trigger character included). */
    val length: Int,
)

object TriggerMatcher {
    const val DEFAULT_TRIGGER = '/'
    const val MAX_QUERY_LENGTH = 30

    private fun isQueryChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

    /**
     * Finds a trigger at the end of [textBeforeCursor]. The trigger must start the text or follow
     * whitespace, so `http://`, `dan/atau`, and `1/2` do not count.
     */
    fun find(textBeforeCursor: CharSequence, trigger: Char = DEFAULT_TRIGGER): TriggerMatch? {
        var i = textBeforeCursor.length - 1
        while (i >= 0 && isQueryChar(textBeforeCursor[i])) i--
        if (i < 0 || textBeforeCursor[i] != trigger) return null
        if (i > 0 && !textBeforeCursor[i - 1].isWhitespace()) return null
        val query = textBeforeCursor.subSequence(i + 1, textBeforeCursor.length).toString()
        if (query.length > MAX_QUERY_LENGTH) return null
        return TriggerMatch(query, query.length + 1)
    }
}

/** What the keyboard needs to rank a template; see [TemplateSearch]. */
data class TemplateEntry(val id: String, val shortcut: String, val title: String, val usageCount: Int = 0)

object TemplateSearch {
    /**
     * Templates matching [query], best first. Shortcut prefix beats a word prefix in the title,
     * which beats a substring anywhere, which beats the query's letters appearing in order in the
     * shortcut. Ties go to the more used template. An empty query lists everything by usage.
     */
    fun rank(query: String, templates: List<TemplateEntry>, limit: Int = 10): List<TemplateEntry> {
        val q = query.lowercase()
        return templates
            .mapNotNull { t -> score(q, t)?.let { t to it } }
            .sortedWith(compareBy<Pair<TemplateEntry, Int>> { it.second }
                .thenByDescending { it.first.usageCount }
                .thenBy { it.first.shortcut })
            .take(limit)
            .map { it.first }
    }

    private fun score(q: String, t: TemplateEntry): Int? {
        if (q.isEmpty()) return 0
        val shortcut = t.shortcut.lowercase()
        val title = t.title.lowercase()
        return when {
            shortcut == q -> 0
            shortcut.startsWith(q) -> 1
            title.split(' ', '-', '_').any { it.startsWith(q) } -> 2
            shortcut.contains(q) || title.contains(q) -> 3
            isSubsequence(q, shortcut) -> 4
            else -> null
        }
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var j = 0
        for (c in s) if (j < q.length && c == q[j]) j++
        return j == q.length
    }
}
