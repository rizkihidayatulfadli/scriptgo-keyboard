package com.magickeyboard.contacts

import java.text.Normalizer

/** Derives the template key of a custom field from its label, e.g. "Kota Asal" → `kota_asal`. */
object FieldKeys {
    /** Keys the template engine already maps to contact properties (see docs/template-syntax.md). */
    val RESERVED = setOf("nama", "nomor")

    private val NON_KEY = Regex("[^a-z0-9]+")
    private val DIACRITICS = Regex("\\p{M}+")

    /** A key unique among [existing] and not [RESERVED], suffixed `_2`, `_3`… on clashes. */
    fun fromLabel(label: String, existing: Set<String> = emptySet()): String {
        val base = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace(NON_KEY, "_")
            .trim('_')
            .ifEmpty { "field" }
        if (base !in existing && base !in RESERVED) return base
        var n = 2
        while ("${base}_$n" in existing || "${base}_$n" in RESERVED) n++
        return "${base}_$n"
    }
}
