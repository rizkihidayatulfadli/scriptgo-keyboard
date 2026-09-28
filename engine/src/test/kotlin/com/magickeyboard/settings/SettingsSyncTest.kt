package com.magickeyboard.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsSyncTest {
    @Test
    fun `keeps user choices and drops phone-only state`() {
        val prefs = mapOf(
            "theme_colors" to "scriptgo",
            "keyboard_height_scale" to 1.1f,
            "clipboard_history_retention_time" to 0,
            "enabled_subtypes" to setOf("id_ID", "en_US"),
            "recent_emojis" to "😀",
            "floating_pos_x" to 120,
            "saved_app_subtype_com.whatsapp" to "id_ID",
            "layout_main_id" to "custom",
            "version_code" to 3000,
            "odd" to listOf(1, 2),
            "nothing" to null,
        )
        assertEquals(
            mapOf(
                "theme_colors" to "scriptgo",
                "keyboard_height_scale" to 1.1f,
                "clipboard_history_retention_time" to 0,
                "enabled_subtypes" to setOf("id_ID", "en_US"),
            ),
            SettingsSync.snapshot(prefs),
        )
        assertTrue(SettingsSync.syncable("vibrate_on"))
        assertFalse(SettingsSync.syncable("saved_app_subtype_x"))
    }

    @Test
    fun `applying the account's settings writes differences and removes reset ones`() {
        val local = mapOf("vibrate_on" to true, "sound_on" to true, "font_scale" to 1f, "recent_emojis" to "x")
        val remote = mapOf<String, Any>("vibrate_on" to false, "font_scale" to 1f, "show_number_row" to true, "recent_emojis" to "y")
        val changes = SettingsSync.apply(local, remote)
        assertEquals(mapOf("vibrate_on" to false, "show_number_row" to true), changes.set)
        assertEquals(setOf("sound_on"), changes.remove)
        assertTrue(SettingsSync.apply(remote, remote).isEmpty)
    }
}
