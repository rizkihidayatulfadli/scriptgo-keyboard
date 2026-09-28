package com.magickeyboard.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefillTest {
    private val parsed = TemplateParser.parse("{nama|Kak} {kota} {kurir:JNE,J&T} {catatan}")
    private val budi = Contact(name = "Budi", fields = mapOf("kota" to "Bandung", "catatan" to ""))

    @Test
    fun `prefills from contact, then default, then first option`() {
        val prefilled = parsed.variables.associate { it.key to TemplateRenderer.prefill(it, budi) }
        assertEquals(mapOf("nama" to "Budi", "kota" to "Bandung", "kurir" to "JNE", "catatan" to ""), prefilled)
    }

    @Test
    fun `prefills defaults without a contact`() {
        val prefilled = parsed.variables.associate { it.key to TemplateRenderer.prefill(it, null) }
        assertEquals(mapOf("nama" to "Kak", "kota" to "", "kurir" to "JNE", "catatan" to ""), prefilled)
    }
}
