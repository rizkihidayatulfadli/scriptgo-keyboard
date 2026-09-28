package com.magickeyboard.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelsTest {
    @Test
    fun `known chat apps map to their default option`() {
        val options = Channels.DEFAULT_OPTIONS
        assertEquals("WhatsApp", Channels.optionFor("com.whatsapp", options))
        assertEquals("WA Business", Channels.optionFor("com.whatsapp.w4b", options))
        assertEquals("Instagram", Channels.optionFor("com.instagram.android", options))
        assertEquals("TikTok", Channels.optionFor("com.zhiliaoapp.musically", options))
        assertEquals("TikTok", Channels.optionFor("com.ss.android.ugc.trill", options))
        assertEquals("Shopee", Channels.optionFor("com.shopee.id", options))
        assertEquals("Tokopedia", Channels.optionFor("com.tokopedia.tkpd", options))
        assertEquals("Facebook", Channels.optionFor("com.facebook.orca", options))
        assertEquals("Facebook", Channels.optionFor("com.facebook.katana", options))
        assertEquals("Telegram", Channels.optionFor("org.telegram.messenger", options))
    }

    @Test
    fun `unknown or missing app gives no channel`() {
        assertNull(Channels.optionFor("com.android.chrome", Channels.DEFAULT_OPTIONS))
        assertNull(Channels.optionFor(null, Channels.DEFAULT_OPTIONS))
        assertNull(Channels.optionFor("", Channels.DEFAULT_OPTIONS))
    }

    @Test
    fun `uses the user's spelling of an option and skips removed ones`() {
        assertEquals("whatsapp", Channels.optionFor("com.whatsapp", listOf("whatsapp", "Offline")))
        assertNull(Channels.optionFor("com.instagram.android", listOf("WhatsApp", "Offline")))
    }

    @Test
    fun `WhatsApp Web in the browser is WhatsApp`() {
        assertEquals("WhatsApp", Channels.optionFor(Channels.WHATSAPP_WEB, Channels.DEFAULT_OPTIONS))
    }

    @Test
    fun `default options end with Lainnya and have no duplicates`() {
        assertEquals("Lainnya", Channels.DEFAULT_OPTIONS.last())
        assertEquals(Channels.DEFAULT_OPTIONS.size, Channels.DEFAULT_OPTIONS.map { it.lowercase() }.toSet().size)
        assertTrue(Channels.KEY !in FieldKeys.RESERVED)
    }
}
