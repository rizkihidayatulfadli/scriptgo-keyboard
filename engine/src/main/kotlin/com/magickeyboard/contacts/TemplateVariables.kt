package com.magickeyboard.contacts

import com.magickeyboard.template.TemplateParser

/**
 * A variable used in templates that is not a contact field yet, such as `{ongkir}` typed straight
 * into a template. The Field kontak tab offers to turn it into a field; its [key] stays the same,
 * so the templates start using the contact's value right away.
 */
data class UnlinkedVariable(
    val key: String,
    /** Choices from the first template that lists them (`{kurir:JNE,J&T}`), or null. */
    val options: List<String>?,
    /** How many templates use it. */
    val templateCount: Int,
) {
    /** A field name for the key: `kota_asal` → "Kota asal". */
    val suggestedLabel: String
        get() = key.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}

object TemplateVariables {
    /**
     * Variables in [templateBodies] that are neither [fieldKeys] nor built in (`nama`, `nomor`),
     * most used first, then by key.
     */
    fun unlinked(templateBodies: List<String>, fieldKeys: Set<String>): List<UnlinkedVariable> {
        val counts = linkedMapOf<String, Int>()
        val options = mutableMapOf<String, List<String>>()
        for (body in templateBodies) {
            for (variable in TemplateParser.parse(body).variables) {
                val key = variable.key
                if (key in fieldKeys || key in FieldKeys.RESERVED) continue
                counts[key] = (counts[key] ?: 0) + 1
                variable.options?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { options.putIfAbsent(key, it) }
            }
        }
        return counts.map { (key, count) -> UnlinkedVariable(key, options[key], count) }
            .sortedWith(compareByDescending<UnlinkedVariable> { it.templateCount }.thenBy { it.key })
    }
}
