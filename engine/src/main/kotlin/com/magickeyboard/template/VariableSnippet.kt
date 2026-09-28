package com.magickeyboard.template

/**
 * Builds the `{...}` text the template editor inserts for a variable, so users never type the
 * syntax themselves: `{nama}`, `{nama|Kak}` with a fallback, or `{kurir:JNE,J&T}` with options.
 * Special characters in the fallback and options are escaped (docs/template-syntax.md), so the
 * parser reads back exactly what the user typed.
 */
object VariableSnippet {
    /**
     * [key] as typed or derived from a label; [fallback] is used when the value is empty;
     * [options] turns the variable into a dropdown (the first option is its default). Blank
     * options are dropped; with options, [fallback] is ignored.
     */
    fun build(key: String, fallback: String? = null, options: List<String> = emptyList()): String {
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return when {
            cleanOptions.isNotEmpty() -> "{$key:" + cleanOptions.joinToString(",") { escape(it, ",") } + "}"
            !fallback.isNullOrEmpty() -> "{$key|" + escape(fallback, "") + "}"
            else -> "{$key}"
        }
    }

    private fun escape(value: String, extra: String): String = buildString {
        for (ch in value) {
            if (ch == '\\' || ch == '{' || ch == '}' || ch in extra) append('\\')
            append(ch)
        }
    }
}
