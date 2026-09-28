package com.magickeyboard.template

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TriggerMatcherTest {
    @Test
    fun `finds a trigger at the start or after whitespace`() {
        assertEquals(TriggerMatch("ong", 4), TriggerMatcher.find("/ong"))
        assertEquals(TriggerMatch("ongkir-jkt", 11), TriggerMatcher.find("Halo kak\n/ongkir-jkt"))
        assertEquals(TriggerMatch("", 1), TriggerMatcher.find("Siap /"))
    }

    @Test
    fun `ignores slashes inside words, urls, and fractions`() {
        listOf("dan/atau", "https://toko.id", "1/2", "/ong kir", "halo").forEach { assertNull(TriggerMatcher.find(it), it) }
    }

    @Test
    fun `supports a custom trigger character`() {
        assertEquals(TriggerMatch("salam", 6), TriggerMatcher.find("ok ;salam", ';'))
        assertNull(TriggerMatcher.find("ok /salam", ';'))
    }

    @Test
    fun `rejects overly long queries`() {
        assertNull(TriggerMatcher.find("/" + "a".repeat(TriggerMatcher.MAX_QUERY_LENGTH + 1)))
    }
}

class TemplateSearchTest {
    private val ongkir = TemplateEntry("1", "ongkir", "Ongkir Jabodetabek", usageCount = 5)
    private val ongkirLuar = TemplateEntry("2", "ongkir-luar", "Ongkir Luar Jawa", usageCount = 9)
    private val salam = TemplateEntry("3", "salam", "Salam pembuka", usageCount = 20)
    private val rekening = TemplateEntry("4", "rek", "Nomor rekening BCA")
    private val all = listOf(ongkir, ongkirLuar, salam, rekening)

    @Test
    fun `empty query lists by usage`() {
        assertEquals(listOf(salam, ongkirLuar, ongkir, rekening), TemplateSearch.rank("", all))
    }

    @Test
    fun `exact shortcut first, then prefix by usage`() {
        assertEquals(listOf(ongkir, ongkirLuar), TemplateSearch.rank("ongkir", all))
        assertEquals(listOf(ongkirLuar, ongkir), TemplateSearch.rank("ong", all))
    }

    @Test
    fun `matches title words, substrings, and letters in order`() {
        assertEquals(listOf(rekening), TemplateSearch.rank("bca", all))
        assertEquals(listOf(ongkirLuar), TemplateSearch.rank("jawa", all))
        assertEquals(listOf(ongkirLuar), TemplateSearch.rank("okl", all))
    }

    @Test
    fun `respects the limit`() {
        assertEquals(2, TemplateSearch.rank("", all, limit = 2).size)
    }
}

class TimeSavedTest {
    @Test
    fun `estimates seconds from characters`() {
        assertEquals(0, TimeSaved.seconds(0))
        assertEquals(40, TimeSaved.seconds(120))
        assertEquals(60, TimeSaved.seconds(120, charsPerSecond = 2.0))
    }

    @Test
    fun `formats durations in Indonesian`() {
        assertEquals("45 detik", TimeSaved.format(45))
        assertEquals("12 menit", TimeSaved.format(12 * 60 + 30))
        assertEquals("3 jam", TimeSaved.format(3 * 3600))
        assertEquals("3 jam 20 menit", TimeSaved.format(3 * 3600 + 20 * 60))
    }

    @Test
    fun `formats durations with other unit words`() {
        assertEquals("45 sec", TimeSaved.format(45, "sec", "min", "h"))
        assertEquals("3 h 20 min", TimeSaved.format(3 * 3600 + 20 * 60, "sec", "min", "h"))
    }
}
