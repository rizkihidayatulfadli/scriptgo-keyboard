package com.magickeyboard.template

/**
 * Parses template text as specified in docs/template-syntax.md.
 * Never throws: malformed syntax is kept as plain text.
 */
object TemplateParser {

    private val KEY = Regex("[A-Za-z0-9_]+")

    /** Characters that `\` escapes outside a variable. */
    private const val TEXT_ESCAPABLE = "{}\\"

    /** Characters that `\` escapes inside a default or an option. */
    private const val VALUE_ESCAPABLE = "{}\\,|"

    fun parse(template: String): ParsedTemplate {
        val tokens = mutableListOf<Token>()
        val text = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '\\' && i + 1 < template.length && template[i + 1] in TEXT_ESCAPABLE) {
                text.append(template[i + 1])
                i += 2
                continue
            }
            if (c == '{') {
                val close = findClosingBrace(template, i + 1)
                if (close == -1) {
                    // Unclosed brace: everything from here on is plain text.
                    text.append(template, i, template.length)
                    break
                }
                val token = parseBody(template.substring(i + 1, close))
                if (token == null) {
                    text.append(template, i, close + 1)
                } else {
                    if (text.isNotEmpty()) {
                        tokens += Token.Text(text.toString())
                        text.clear()
                    }
                    tokens += token
                }
                i = close + 1
                continue
            }
            text.append(c)
            i++
        }
        if (text.isNotEmpty()) tokens += Token.Text(text.toString())
        return ParsedTemplate(tokens)
    }

    /**
     * The variables [parse] recognizes, with where they are in [template], for the message editor
     * (tap a variable to change or remove it). Built-ins such as `{@kursor}` are included with
     * [SourceVariable.builtin] set; escaped braces and invalid keys are not variables.
     */
    fun sourceVariables(template: String): List<SourceVariable> {
        val out = mutableListOf<SourceVariable>()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '\\' && i + 1 < template.length && template[i + 1] in TEXT_ESCAPABLE) {
                i += 2
                continue
            }
            if (c == '{') {
                val close = findClosingBrace(template, i + 1)
                if (close == -1) break
                when (val token = parseBody(template.substring(i + 1, close))) {
                    is Token.Var -> out += SourceVariable(i, close + 1, token.variable.key, builtin = false, token.variable.default, token.variable.options)
                    is Token.Builtin -> out += SourceVariable(i, close + 1, "@" + token.kind.key, builtin = true)
                    else -> {}
                }
                i = close + 1
                continue
            }
            i++
        }
        return out
    }

    /** Index of the first unescaped `}` at or after [from], or -1. */
    private fun findClosingBrace(s: String, from: Int): Int {
        var j = from
        while (j < s.length) {
            when (s[j]) {
                '\\' -> j += 2
                '}' -> return j
                else -> j++
            }
        }
        return -1
    }

    private fun parseBody(body: String): Token? {
        if (body.startsWith("@")) {
            return BuiltinKind.fromKey(body.substring(1))?.let { Token.Builtin(it) }
        }
        val sep = indexOfUnescaped(body, "|:")
        val key = if (sep == -1) body else body.substring(0, sep)
        if (!KEY.matches(key)) return null
        val normalizedKey = key.lowercase()
        if (sep == -1) return Token.Var(Variable(normalizedKey))

        val rest = body.substring(sep + 1)
        return if (body[sep] == '|') {
            Token.Var(Variable(normalizedKey, default = unescape(rest)))
        } else {
            val options = splitUnescaped(rest, ',').map { unescape(it).trim() }
            Token.Var(Variable(normalizedKey, options = options))
        }
    }

    private fun indexOfUnescaped(s: String, chars: String): Int {
        var j = 0
        while (j < s.length) {
            when {
                s[j] == '\\' -> j += 2
                s[j] in chars -> return j
                else -> j++
            }
        }
        return -1
    }

    private fun splitUnescaped(s: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var j = 0
        while (j < s.length) {
            when {
                s[j] == '\\' -> j += 2
                s[j] == delimiter -> {
                    parts += s.substring(start, j)
                    start = ++j
                }
                else -> j++
            }
        }
        parts += s.substring(start)
        return parts
    }

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var j = 0
        while (j < s.length) {
            if (s[j] == '\\' && j + 1 < s.length && s[j + 1] in VALUE_ESCAPABLE) {
                out.append(s[j + 1])
                j += 2
            } else {
                out.append(s[j])
                j++
            }
        }
        return out.toString()
    }
}

/**
 * A variable in template source, from [TemplateParser.sourceVariables]: `template.substring(start, end)`
 * is its `{...}`. [key] is lowercase (`@kursor` for built-ins); at most one of [default] and
 * [options] is set.
 */
data class SourceVariable(
    val start: Int,
    val end: Int,
    val key: String,
    val builtin: Boolean,
    val default: String? = null,
    val options: List<String>? = null,
)
