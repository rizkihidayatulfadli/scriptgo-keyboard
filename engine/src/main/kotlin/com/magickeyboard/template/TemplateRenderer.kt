package com.magickeyboard.template

import java.time.LocalDateTime
import java.util.Locale

/** Renders a [ParsedTemplate] as specified in docs/template-syntax.md. Never throws. */
object TemplateRenderer {

    /**
     * Each variable takes the first available of: [inputs], the [contact]'s non-empty value,
     * its default, its first option, its field's fallback in [fieldFallbacks], or an empty string.
     * [now] is the device's local time. With [markMissing], a variable with none of these renders
     * as `[key]` so the user can spot and edit it after inserting without the form.
     */
    fun render(
        parsed: ParsedTemplate,
        contact: Contact?,
        inputs: Map<String, String>,
        now: LocalDateTime,
        markMissing: Boolean = false,
        fieldFallbacks: Map<String, String> = emptyMap(),
    ): RenderResult {
        val out = StringBuilder()
        var cursor: Int? = null
        val firstOccurrence = parsed.variables.associateBy { it.key }
        for (token in parsed.tokens) {
            when (token) {
                is Token.Text -> out.append(token.value)
                is Token.Var -> {
                    val variable = firstOccurrence.getValue(token.variable.key)
                    out.append(resolve(variable, contact, inputs, fieldFallbacks) ?: if (markMissing) "[${variable.key}]" else "")
                }
                is Token.Builtin ->
                    if (token.kind == BuiltinKind.CURSOR) {
                        if (cursor == null) cursor = out.length
                    } else {
                        out.append(formatBuiltin(token.kind, now))
                    }
            }
        }
        return RenderResult(out.toString(), cursor)
    }

    /** The value the form is prefilled with, before the user edits it. */
    fun prefill(variable: Variable, contact: Contact?, fieldFallbacks: Map<String, String> = emptyMap()): String =
        resolve(variable, contact, emptyMap(), fieldFallbacks).orEmpty()

    /** The variable's value, or null when no source provides one. */
    private fun resolve(
        variable: Variable,
        contact: Contact?,
        inputs: Map<String, String>,
        fieldFallbacks: Map<String, String>,
    ): String? {
        inputs[variable.key]?.let { return it }
        contactValue(variable.key, contact)?.takeIf { it.isNotEmpty() }?.let { return it }
        variable.default?.let { return it }
        variable.options?.firstOrNull()?.let { return it }
        return fieldFallbacks[variable.key]?.takeIf { it.isNotEmpty() }
    }

    private fun contactValue(key: String, contact: Contact?): String? = when {
        contact == null -> null
        key == "nama" -> contact.name
        key == "nomor" -> contact.phones.firstOrNull()?.let { formatFieldValue(FieldType.PHONE, it) }
        else -> contact.fields[key]
    }

    private fun formatBuiltin(kind: BuiltinKind, now: LocalDateTime): String = when (kind) {
        BuiltinKind.DATE -> IndonesianDates.longDate(now.toLocalDate())
        BuiltinKind.SHORT_DATE -> "%02d/%02d/%04d".format(Locale.ROOT, now.dayOfMonth, now.monthValue, now.year)
        BuiltinKind.DAY -> IndonesianDates.dayName(now.dayOfWeek)
        BuiltinKind.MONTH -> IndonesianDates.monthName(now.monthValue)
        BuiltinKind.YEAR -> now.year.toString()
        BuiltinKind.TIME -> "%02d.%02d".format(Locale.ROOT, now.hour, now.minute)
        BuiltinKind.CURSOR -> ""
    }
}
