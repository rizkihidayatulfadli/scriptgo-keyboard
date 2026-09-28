package com.magickeyboard.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VariableSnippetTest {
    @Test
    fun `plain, fallback, and options`() {
        assertEquals("{nama}", VariableSnippet.build("nama"))
        assertEquals("{nama}", VariableSnippet.build("nama", fallback = ""))
        assertEquals("{nama|Kak}", VariableSnippet.build("nama", fallback = "Kak"))
        assertEquals("{kurir:JNE,J&T}", VariableSnippet.build("kurir", fallback = "Kak", options = listOf(" JNE ", "", "J&T", "JNE")))
    }

    @Test
    fun `special characters survive a round trip through the parser`() {
        val fallback = "a {b} c\\d | e: f"
        val parsedFallback = TemplateParser.parse(VariableSnippet.build("x", fallback = fallback)).variables.single()
        assertEquals(Variable("x", default = fallback), parsedFallback)

        val options = listOf("Rp10.000, cepat", "{kilat}", "a|b:c")
        val parsedOptions = TemplateParser.parse(VariableSnippet.build("ongkir", options = options)).variables.single()
        assertEquals(Variable("ongkir", options = options), parsedOptions)
    }
}
