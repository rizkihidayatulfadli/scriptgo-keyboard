package com.magickeyboard.contacts

import java.text.Normalizer

/**
 * The icon of a contact field (Phosphor Fill names from design/icons/field-icons.json). A field
 * stores an icon only when the user picked one; otherwise [pick] chooses from its label and key,
 * so renaming a field can change its icon. Same rules as extension/src/engine/fieldIcons.ts; both
 * are checked against docs/field-icon-fixtures.json.
 */
object FieldIcons {
    /** Every icon a field may have, in gallery order. */
    val all: List<String> get() = FieldIconCatalog.all

    private val known: Set<String> by lazy { FieldIconCatalog.all.toSet() }

    /** The icon to show: [stored] when it is a known icon, otherwise [pick]. */
    fun resolve(stored: String, label: String, key: String, type: String): String =
        stored.takeIf { it in known } ?: pick(label, key, type)

    /**
     * The first catalog icon with a keyword among the words of [label] or [key] (whole words, so
     * "rek" does not match "rekomendasi"), else the default for [type], else a text icon.
     */
    fun pick(label: String, key: String, type: String): String {
        val words = " ${normalize(label)} ${normalize(key)} "
        for ((icon, keywords) in FieldIconCatalog.icons) {
            if (keywords.any { words.contains(" $it ") }) return icon
        }
        return FieldIconCatalog.typeDefaults[type] ?: FieldIconCatalog.typeDefaults.getValue("text")
    }

    /** Lowercase ASCII words separated by single spaces ("Tgl_Lahir (anak)" → "tgl lahir anak"). */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
