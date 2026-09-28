package com.magickeyboard.template

/**
 * A variable the keyboard form may ask for, in order of first appearance.
 * [key] is lowercase. At most one of [default] and [options] is set.
 */
data class Variable(
    val key: String,
    val default: String? = null,
    val options: List<String>? = null,
)

/**
 * The contact a template is rendered for. [phones] are E.164; [fields] maps a field key
 * to text already produced by [formatFieldValue].
 */
data class Contact(
    val name: String,
    val phones: List<String> = emptyList(),
    val fields: Map<String, String> = emptyMap(),
)

/** [cursor] is a UTF-16 index into [text], or null when the template has no `{@kursor}`. */
data class RenderResult(val text: String, val cursor: Int?)

class ParsedTemplate internal constructor(internal val tokens: List<Token>) {
    val variables: List<Variable> = tokens
        .filterIsInstance<Token.Var>()
        .distinctBy { it.variable.key }
        .map { it.variable }
}

internal sealed interface Token {
    data class Text(val value: String) : Token
    data class Var(val variable: Variable) : Token
    data class Builtin(val kind: BuiltinKind) : Token
}

internal enum class BuiltinKind(val key: String) {
    DATE("tanggal"),
    SHORT_DATE("tanggal_pendek"),
    DAY("hari"),
    MONTH("bulan"),
    YEAR("tahun"),
    TIME("jam"),
    CURSOR("kursor");

    companion object {
        fun fromKey(key: String): BuiltinKind? = entries.firstOrNull { it.key == key }
    }
}
