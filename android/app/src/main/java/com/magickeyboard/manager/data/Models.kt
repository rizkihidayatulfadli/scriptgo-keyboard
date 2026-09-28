package com.magickeyboard.manager.data

import androidx.annotation.StringRes
import com.magickeyboard.template.FieldCurrency
import com.magickeyboard.template.FieldType
import helium314.keyboard.latin.R

data class Template(
    val id: String,
    val title: String,
    /** Typed after the trigger character, without it: `ongkir` for `/ongkir`. */
    val shortcut: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
)

/**
 * [phones] are E.164. [fields] maps a field key to its raw stored value (see "Format Nilai per Tipe
 * Field" in docs/template-syntax.md).
 */
data class Contact(
    val id: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val fields: Map<String, String> = emptyMap(),
    val lastUsedAt: Long? = null,
)

/** Totals over all template insertions: how many, and how many characters they typed for the user. */
data class UsageTotals(val uses: Long, val chars: Long)

data class FieldDefinition(
    val id: String,
    /** Template variable key, fixed after creation so templates keep working. */
    val key: String,
    val label: String,
    val type: FieldType,
    /** Choices for [FieldType.CHOICE]; for [FieldType.CURRENCY], the currency code (empty means IDR). */
    val options: List<String> = emptyList(),
    val position: Int = 0,
    /** Raw value used when a contact has none and the template has no fallback; empty for none. */
    val fallback: String = "",
    /** Icon the user picked (a [com.magickeyboard.contacts.FieldIcons] name); empty means automatic. */
    val icon: String = "",
) {
    val currency: FieldCurrency get() = FieldCurrency.fromOptions(options)

    /** The icon to show: the picked one, or one chosen from the label, key, and type. */
    val shownIcon: String get() = com.magickeyboard.contacts.FieldIcons.resolve(icon, label, key, type.key)
}

/** Non-empty fallback per field key, for [com.magickeyboard.contacts.TemplateForm]. */
fun List<FieldDefinition>.fallbacks(): Map<String, String> =
    filter { it.fallback.isNotEmpty() }.associate { it.key to it.fallback }

/** Currency code per currency field key, for [com.magickeyboard.contacts.TemplateForm.render]. */
fun List<FieldDefinition>.currencies(): Map<String, String> =
    filter { it.type == FieldType.CURRENCY }.associate { it.key to it.currency.code }

/** Field types offered in the app for now (P0 in docs/PRD.md). */
val EDITABLE_FIELD_TYPES = listOf(
    FieldType.TEXT, FieldType.LONGTEXT, FieldType.NUMBER, FieldType.CURRENCY, FieldType.DATE, FieldType.CHOICE,
)

@StringRes
fun FieldType.labelRes(): Int = when (this) {
    FieldType.TEXT -> R.string.sg_field_type_text
    FieldType.LONGTEXT -> R.string.sg_field_type_longtext
    FieldType.NUMBER -> R.string.sg_field_type_number
    FieldType.CURRENCY -> R.string.sg_field_type_currency
    FieldType.DATE -> R.string.sg_field_type_date
    FieldType.CHOICE -> R.string.sg_field_type_choice
    FieldType.PHONE -> R.string.sg_field_type_phone
    FieldType.EMAIL -> R.string.sg_field_type_email
    FieldType.URL -> R.string.sg_field_type_url
    FieldType.BOOLEAN -> R.string.sg_field_type_boolean
    FieldType.MULTICHOICE -> R.string.sg_field_type_multichoice
}
