package com.magickeyboard.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateDuplicatesTest {
    private fun copy(id: String, shortcut: String = "salam", body: String = "Halo {nama|Kak}") =
        TemplateCopy(id, shortcut, "Salam pembuka", body)

    @Test
    fun `keeps one copy, the smallest id, of identical templates`() {
        val templates = listOf(copy("c"), copy("a"), copy("b"), copy("x", shortcut = "ongkir"))
        assertEquals(listOf("b", "c"), TemplateDuplicates.toDelete(templates))
    }

    @Test
    fun `prefers the starter id so every device keeps the same copy`() {
        val templates = listOf(copy("0-random"), copy("starter-salam"))
        assertEquals(listOf("0-random"), TemplateDuplicates.toDelete(templates))
    }

    @Test
    fun `leaves templates that differ in anything`() {
        val templates = listOf(copy("a"), copy("b", body = "Halo kak"), copy("c", shortcut = "salam2"))
        assertEquals(emptyList<String>(), TemplateDuplicates.toDelete(templates))
    }
}
