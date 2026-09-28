package com.magickeyboard.manager.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsWithoutKey

/** How one setting is explained: a looping drawing, and for on/off settings the default value. */
class Demo(
    val boolDefault: Boolean? = null,
    val periodMs: Int = 3200,
    val draw: DrawScope.(k: DemoKit, t: Float, on: Boolean) -> Unit,
)

/**
 * Animated explanation for every keyboard setting, keyed by preference key. On/off settings pass
 * their current value, so the animation shows what happens with the switch as it is now.
 */
object SettingDemos {
    fun forKey(key: String): Demo? = demos[key]

    private val demos: Map<String, Demo> = buildMap {
        fun d(key: String, def: Boolean? = null, period: Int = 3200, draw: DrawScope.(DemoKit, Float, Boolean) -> Unit) {
            put(key, Demo(def, period, draw))
        }

        // ---- Preferences: feel and keys ----
        d(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON) { k, t, on -> vibrateScene(k, t, on) }
        d(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE) { k, t, on -> vibrateScene(k, t, on, dnd = true) }
        d(Settings.PREF_VIBRATION_DURATION_SETTINGS) { k, t, _ -> vibrateScene(k, t, true, amp = lerp(2f, 7f, swing(t))) }
        d(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON) { k, t, on -> soundScene(k, t, on) }
        d(Settings.PREF_KEYPRESS_SOUND_VOLUME) { k, t, _ -> soundScene(k, t, true, volume = lerp(0.3f, 1f, swing(t * 0.5f))) }
        d(Settings.PREF_POPUP_ON) { k, t, on ->
            val keys = kbLow(k, Kb(pressed = if (t in 0.3f..0.6f) "d" else null), 0.45f)
            keys["d"]?.let { with(k) { tap(it.center, t, 0.2f, 0.7f); if (on) bubble(it, "d", seg(t, 0.3f, 0.35f) * (1f - seg(t, 0.58f, 0.63f))) } }
        }
        d(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS) { k, t, on -> kbFull(k, Kb(hints = if (on) 1f else 0f, pressed = if (on && t in 0.4f..0.7f) "w" else null)) }
        d(Settings.PREF_SHOW_POPUP_HINTS, Defaults.PREF_SHOW_POPUP_HINTS) { k, _, on -> kbFull(k, Kb(funcHints = if (on) 1f else 0f, hints = if (on) 0.6f else 0f)) }
        d(Settings.PREF_POPUP_KEYS_HINT_ORDER) { k, t, _ -> popupScene(k, t, "e", listOf("3", "é", "è", "ê", "ë"), highlight = 0) }
        d(Settings.PREF_POPUP_KEYS_ORDER) { k, t, _ -> popupScene(k, t, "a", if (t < 0.55f) listOf("à", "á", "â", "ä", "4") else listOf("4", "à", "á", "â", "ä"), highlight = if (t < 0.55f) 1 else 0) }
        d(Settings.PREF_SHOW_TLD_POPUP_KEYS, Defaults.PREF_SHOW_TLD_POPUP_KEYS) { k, t, on -> popupScene(k, t, ".", if (on) listOf(".id", ".com", ".net", ".org") else listOf("!", "?", "…"), highlight = 0) }
        d(Settings.PREF_REMOVE_REDUNDANT_POPUPS, Defaults.PREF_REMOVE_REDUNDANT_POPUPS) { k, t, on -> popupScene(k, t, "o", if (on) listOf("ó", "ö", "ô") else listOf("ó", "ö", "ô", "9", "ò"), highlight = 0) }
        d(Settings.PREF_MORE_POPUP_KEYS) { k, t, _ -> popupScene(k, t, "u", listOf("ú", "ü", "û", "ù", "ū", "ų"), highlight = 1) }
        d(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Defaults.PREF_SHOW_EMOJI_DESCRIPTIONS) { k, t, on ->
            emojiScene(k, 1f)
            with(k) {
                val x = pad + (size.width - 2 * pad) / 6f * 2.5f
                tap(Offset(x, size.height / 2f), t, 0.2f, 0.8f)
                if (on && t in 0.35f..0.8f) {
                    val r = Rect(x - 70f * density, 14f * density, x + 70f * density, 38f * density)
                    round(r, c.ink, 10f)
                    text("wajah bermata hati", r.center, 11f, c.onInk)
                }
            }
        }
        d(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW) { k, t, on -> kbFull(k, Kb(numberRow = if (on) 1f else 0f, pressed = if (on && t in 0.4f..0.6f) "7" else null)) }
        d(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS) { k, _, on -> kbFull(k, Kb(symbols = true, numberRow = if (on) 1f else 0f)) }
        d(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW) { k, t, on -> kbFull(k, Kb(numberRow = 1f, numbers = if (on && t > 0.5f) "١٢٣٤٥٦٧٨٩٠" else "1234567890")) }
        d(Settings.PREF_SHOW_NUMBER_ROW_HINTS, Defaults.PREF_SHOW_NUMBER_ROW_HINTS) { k, _, on -> kbFull(k, Kb(numberRow = 1f, numberHints = if (on) 1f else 0f)) }
        d(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY) { k, t, on ->
            val keys = kbFull(k, Kb(langKey = on, spaceLabel = if (on && t > 0.5f) "English" else "Indonesia", pressed = if (on && t in 0.4f..0.5f) "globe" else null))
            keys["globe"]?.let { with(k) { tap(it.center, t, 0.3f, 0.6f) } }
        }
        d(Settings.PREF_LANGUAGE_SWITCH_KEY) { k, t, _ ->
            val keys = kbFull(k, Kb(langKey = true, spaceLabel = if (t > 0.5f) "English" else "Indonesia", pressed = if (t in 0.4f..0.5f) "globe" else null))
            keys["globe"]?.let { with(k) { tap(it.center, t, 0.3f, 0.6f) } }
        }
        d(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY) { k, t, on ->
            val keys = kbFull(k, Kb(emojiKey = on, pressed = if (on && t in 0.4f..0.55f) "emoji" else null))
            keys["emoji"]?.let { with(k) { tap(it.center, t, 0.3f, 0.65f); bubble(it, "😊", seg(t, 0.42f, 0.46f) * (1f - seg(t, 0.6f, 0.64f))) } }
        }
        d(Settings.PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP) { k, t, on ->
            with(k) {
                val apps = listOf("WhatsApp" to "ID", "Instagram" to "EN")
                val active = if (t < 0.5f) 0 else 1
                apps.forEachIndexed { i, (name, lang) ->
                    val cx = size.width * (0.3f + 0.4f * i)
                    val r = Rect(cx - 52f * density, 20f * density, cx + 52f * density, 56f * density)
                    round(r, if (i == active) c.accentSoft else key, 12f)
                    text(name, r.center, 13f, if (i == active) c.accentInk else keyText, bold = i == active)
                }
                val lang = if (on) apps[active].second else "ID"
                kbLow(k, Kb(labels = true, spaceLabel = if (lang == "ID") "Indonesia" else "English"), 0.5f)
            }
        }

        // ---- Clipboard ----
        d(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY) { k, t, on -> clipboardScene(k, t, on) }
        d(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME) { k, t, _ -> clipboardScene(k, t, true, expire = true) }
        d(Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST, Defaults.PREF_CLIPBOARD_HISTORY_PINNED_FIRST) { k, t, on -> clipboardScene(k, t, true, pinnedFirst = on) }
        d(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES) { k, t, on -> clipboardScene(k, t, true, files = on) }
        d(Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT) { k, t, _ -> clipboardScene(k, t, true, files = true, expire = true) }
        d(Settings.PREF_ABC_AFTER_CLIP, Defaults.PREF_ABC_AFTER_CLIP) { k, t, on -> switchBackScene(k, t, on, "⎘") }

        // ---- Appearance ----
        d(Settings.PREF_THEME_STYLE) { k, t, _ -> kbFull(k, Kb(corner = lerp(0.1f, 2f, swing(t)))) }
        d(Settings.PREF_ICON_STYLE) { k, t, _ -> kbFull(k, Kb(toolbar = 1f, toolbarIcons = if (t < 0.5f) listOf("⎘", "☺", "⚙", "↶", "↷") else listOf("📋", "😊", "🔧", "⤺", "⤻"))) }
        d(Settings.PREF_CUSTOM_ICON_NAMES) { k, t, _ -> kbFull(k, Kb(toolbar = 1f, toolbarIcons = listOf("⎘", if (t < 0.5f) "☺" else "😎", "⚙", "↶", "↷"))) }
        d(Settings.PREF_THEME_COLORS) { k, t, _ -> themeCycle(k, t, dark = false) }
        d(Settings.PREF_THEME_COLORS_NIGHT) { k, t, _ -> themeCycle(k, t, dark = true) }
        d(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS) { k, _, on -> kbFull(k, Kb(borders = if (on) 1f else 0f)) }
        d(Settings.PREF_THEME_DAY_NIGHT) { k, t, _ ->
            val night = t > 0.5f
            val keys = kbLow(k, if (night) Kb(bg = Color(0xFF0B0E12), keyColor = Color(0xFF262D36), funcColor = Color(0xFF1C222A), textColor = Color(0xFFEEF2F6)) else Kb(), 0.34f)
            with(k) { text(if (night) "☾" else "☀", Offset(size.width / 2f, 22f * density), 20f, c.ink) }
            keys.size
        }
        d(Settings.PREF_NAVBAR_COLOR) { k, _, on ->
            kbFull(k, Kb(bottomPad = 12f))
            with(k) {
                val r = Rect(pad + 4f * density, size.height - 22f * density, size.width - pad - 4f * density, size.height - 13f * density)
                round(r, if (on) kbBg else c.ink, 4f)
                drawLine(if (on) keyText.copy(alpha = 0.5f) else c.onInk, Offset(size.width / 2f - 20f * density, r.center.y), Offset(size.width / 2f + 20f * density, r.center.y), 2f * density)
            }
        }
        d(SettingsWithoutKey.BACKGROUND_IMAGE) { k, t, _ -> kbFull(k, Kb(background = t > 0.3f, borders = 0.85f)) }
        d(SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE) { k, t, _ -> kbFull(k, Kb(background = t > 0.3f, borders = 0.85f, height = 0.75f)) }
        d(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) { k, t, on -> kbFull(k, Kb(split = if (on) 0.22f * ease(seg(t, 0.1f, 0.4f)) else 0f)) }
        d(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(split = lerp(0.1f, 0.32f, swing(t)))) }
        d(Settings.PREF_KEY_GAP_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(gap = lerp(0.4f, 2.4f, swing(t)))) }
        d(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(height = lerp(0.6f, 1f, swing(t)))) }
        d(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(bottomRow = lerp(0.7f, 1.6f, swing(t)))) }
        d(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(bottomPad = lerp(0f, 18f, swing(t)))) }
        d(Settings.PREF_SIDE_PADDING_SCALE_PREFIX) { k, t, _ -> kbFull(k, Kb(sidePad = lerp(0f, 40f, swing(t)))) }
        d(Settings.PREF_SPACE_BAR_TEXT) { k, t, _ -> kbFull(k, Kb(spaceLabel = typed("ScriptGo", t, 0.1f, 0.5f))) }
        d(SettingsWithoutKey.CUSTOM_FONT) { k, t, _ -> kbFull(k, Kb(fontScale = if (t < 0.5f) 1f else 1.15f)) }
        d(Settings.PREF_FONT_SCALE) { k, t, _ -> kbFull(k, Kb(fontScale = lerp(0.7f, 1.4f, swing(t)))) }
        d(Settings.PREF_HINT_FONT_SCALE) { k, t, _ -> kbFull(k, Kb(hints = 1f, fontScale = lerp(0.8f, 1.5f, swing(t)))) }
        d(SettingsWithoutKey.CUSTOM_EMOJI_FONT) { k, t, _ -> emojiScene(k, if (t < 0.5f) 1f else 1.1f) }
        d(Settings.PREF_EMOJI_FONT_SCALE) { k, t, _ -> emojiScene(k, lerp(0.7f, 1.4f, swing(t))) }
        d(Settings.PREF_EMOJI_KEY_FIT, Defaults.PREF_EMOJI_KEY_FIT) { k, t, on -> emojiScene(k, if (on) lerp(0.8f, 1.3f, swing(t)) else 1f) }
        d(Settings.PREF_EMOJI_SKIN_TONE) { k, t, _ ->
            val tones = listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")
            emojiScene(k, 1.2f, tones[(t * tones.size).toInt().coerceIn(0, tones.lastIndex)].repeat(3))
        }

        // ---- Text correction ----
        d(SettingsWithoutKey.EDIT_PERSONAL_DICTIONARY) { k, t, _ -> wordListScene(k, t, listOf("ScriptGo", "Rina", "COD", "resi")) }
        d(Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE, Defaults.PREF_BLOCK_POTENTIALLY_OFFENSIVE) { k, t, on ->
            typingScene(k, t, "baj", "baj", false, words = listOf("baju", if (on) "bajak" else "b*****", "baja"))
        }
        d(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION) { k, t, on -> typingScene(k, t, "trimakasih ", "terima kasih ", on) }
        d(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION) { k, t, on -> typingScene(k, t, "gimna ", "gimana ", on) }
        d(Settings.PREF_AUTO_CORRECT_CONFIDENCE) { k, t, _ -> typingScene(k, t, "brp ", "berapa ", t > 0.5f) }
        d(Settings.PREF_AUTOCORRECT_SHORTCUTS, Defaults.PREF_AUTOCORRECT_SHORTCUTS) { k, t, on -> typingScene(k, t, "yg ", "yang ", on) }
        d(Settings.PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION, Defaults.PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION) { k, t, on -> typingScene(k, t, "Jakrta ", "Jakarta ", on) }
        d(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, Defaults.PREF_BACKSPACE_REVERTS_AUTOCORRECT) { k, t, on ->
            val value = when {
                t < 0.35f -> typed("trimakasih ", t, 0.05f, 0.3f)
                t < 0.6f -> "terima kasih "
                else -> if (on) "trimakasih" else "terima kasih"
            }
            with(k) { field(fieldRect(), value, true, if (t in 0.35f..0.55f) 0..11 else null, t) }
            val keys = kbLow(k, Kb(pressed = if (t in 0.6f..0.7f) "delete" else null), 0.4f)
            keys["delete"]?.let { with(k) { tap(it.center, t, 0.55f, 0.75f) } }
        }
        d(Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP) { k, t, on ->
            val value = typed("halo kak", t)
            val shown = if (on) value.replaceFirstChar { it.uppercaseChar() } else value
            with(k) { field(fieldRect(), shown, true, if (on && value.isNotEmpty() && t < 0.3f) 0..0 else null, t) }
            kbLow(k, Kb(pressed = pressedKey("halo kak", t)), 0.4f)
        }
        d(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD) { k, t, on -> typingScene(k, t, "oke kak  ", "oke kak. ", on) }
        d(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION) { k, t, on -> typingScene(k, t, "oke,kak", "oke, kak", on) }
        d(Settings.PREF_AUTOSPACE_AFTER_SUGGESTION, Defaults.PREF_AUTOSPACE_AFTER_SUGGESTION) { k, t, on ->
            typingScene(k, t, "teri", if (on) "terima " else "terima", true, words = listOf("teri", "terima", "terus"), pick = 1)
        }
        d(Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING, Defaults.PREF_AUTOSPACE_AFTER_GESTURE_TYPING) { k, t, on -> gestureScene(k, t, word = if (on) "halo " else "halo") }
        d(Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING, Defaults.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING) { k, t, on -> gestureScene(k, t, prefix = "kak", word = if (on) " halo" else "halo") }
        d(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, Defaults.PREF_SHIFT_REMOVES_AUTOSPACE) { k, t, on -> typingScene(k, t, "ok ", if (on) "okE" else "ok E", t > 0.5f) }
        d(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS) { k, t, on -> typingScene(k, t, "teri", "terima ", on, words = if (on) listOf("teri", "terima", "terus") else listOf("", "", ""), pick = 1) }
        d(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS) { k, t, on -> typingScene(k, t, "user123", "user123", false, words = if (on) listOf("user", "user123", "users") else listOf("", "", "")) }
        d(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT) { k, t, on -> typingScene(k, t, "scriptgo.id", "scriptgo.id", false, words = if (on) listOf("", "", "") else listOf("script", "scriptgo", "scripts")) }
        d(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS) { k, t, on -> typingScene(k, t, "Scr", "ScriptGo ", on, words = if (on) listOf("Scr", "ScriptGo", "Screen") else listOf("Scr", "Screen", "Scroll"), pick = 1) }
        d(Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS) { k, t, on -> typingScene(k, t, "terima ", "terima kasih ", on, words = if (on) listOf("kasih", "Kak", "ya") else listOf("", "", ""), pick = 0) }
        d(Settings.PREF_SUGGEST_PUNCTUATION, Defaults.PREF_SUGGEST_PUNCTUATION) { k, t, on -> typingScene(k, t, "siap kak ", "siap kak! ", on, words = if (on) listOf(".", "!", "?") else listOf("", "", ""), pick = 1) }
        d(Settings.PREF_PUNCTUATION_SUGGESTIONS) { k, t, _ -> typingScene(k, t, "siap kak ", "siap kak 🙏 ", true, words = listOf(".", "🙏", "?"), pick = 1) }
        d(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, Defaults.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER) { k, t, on -> typingScene(k, t, "kirm", if (on) "kirim " else "kirm ", true, words = listOf("kirm", "kirim", "kira"), pick = if (on) 1 else 0) }
        d(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, Defaults.PREF_SUGGEST_CLIPBOARD_CONTENT) { k, t, on -> typingScene(k, t, "", "JX1234567", on, words = if (on) listOf("", "📋 JX1234567", "") else listOf("", "", ""), pick = 1) }
        d(Settings.PREF_USE_CONTACTS, Defaults.PREF_USE_CONTACTS) { k, t, on -> typingScene(k, t, "halo Ri", "halo Rina ", on, words = if (on) listOf("Ri", "Rina", "Rini") else listOf("Ri", "Rini", "Rio"), pick = 1) }
        d(Settings.PREF_USE_APPS, Defaults.PREF_USE_APPS) { k, t, on -> typingScene(k, t, "cek di Toko", "cek di Tokopedia ", on, words = if (on) listOf("Toko", "Tokopedia", "Tokoh") else listOf("Toko", "Tokoh", "Tokonya"), pick = 1) }
        d(Settings.PREF_SUGGEST_EMOJIS, Defaults.PREF_SUGGEST_EMOJIS) { k, t, on -> typingScene(k, t, "senang", "senang 😊", on, words = if (on) listOf("senang", "😊", "😄") else listOf("senang", "sekali", "banget"), pick = 1) }
        d(Settings.PREF_INLINE_EMOJI_SEARCH, Defaults.PREF_INLINE_EMOJI_SEARCH) { k, t, on -> typingScene(k, t, ":hati", "❤️", on, words = if (on) listOf("❤️", "💖", "💕") else listOf("", "", ""), pick = 0) }
        d(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY) { k, t, on ->
            wordListScene(k, t, if (on) listOf("Rina", "COD", "pashmina") else listOf("Rina", "COD"), newest = on)
        }
        d(Settings.PREF_SPELLCHECK_SUGGEST, Defaults.PREF_SPELLCHECK_SUGGEST) { k, t, on ->
            with(k) {
                val r = fieldRect()
                field(r, "stok mash ada", false, null, t)
                val x0 = r.left + 12f * density + textWidth("stok ", 14f)
                val w = textWidth("mash", 14f)
                val y = r.center.y + 10f * density
                for (i in 0 until (w / (4f * density)).toInt()) {
                    val x = x0 + i * 4f * density
                    drawLine(c.danger, Offset(x, y + if (i % 2 == 0) 0f else 2f * density), Offset(x + 4f * density, y + if (i % 2 == 0) 2f * density else 0f), 1.5f * density)
                }
                if (on && t > 0.4f) strip(Rect(x0 - 20f * density, r.bottom + 8f * density, x0 + 140f * density, r.bottom + 36f * density), listOf("masih", "mas", "mash"), 0)
            }
        }

        // ---- Toolbar ----
        d(Settings.PREF_TOOLBAR_MODE) { k, t, _ -> toolbarScene(k, t, if (t < 0.5f) 1f else 0f) }
        d(Settings.PREF_TOOLBAR_HIDING_GLOBAL, Defaults.PREF_TOOLBAR_HIDING_GLOBAL) { k, t, on -> toolbarScene(k, t, if (on && t > 0.5f) 0f else 1f) }
        d(Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE, Defaults.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE) { k, t, on ->
            val drop = if (on) ease(seg(t, 0.45f, 0.7f)) * (1f - seg(t, 0.9f, 1f)) else 0f
            keyboard(k, Rect(pad, 10f * density + drop * size.height, size.width - pad, size.height - 10f * density + drop * size.height), Kb(toolbar = 1f))
            with(k) {
                val x = size.width / 2f
                if (t in 0.3f..0.7f) {
                    val y = lerp(24f * density, 70f * density, seg(t, 0.35f, 0.65f))
                    drawCircle(finger, 13f * density, Offset(x, y))
                }
            }
        }
        d(Settings.PREF_TOOLBAR_KEYS) { k, t, _ -> toolbarScene(k, t, 1f, icons = if (t < 0.5f) listOf("⎘", "☺", "⚙", "↶", "↷") else listOf("⎘", "☺", "✂", "🔍", "↷", "⚙")) }
        d(Settings.PREF_PINNED_TOOLBAR_KEYS) { k, t, _ -> toolbarScene(k, t, 1f, pinned = if (t > 0.4f) 0 else -1) }
        d(Settings.PREF_CLIPBOARD_TOOLBAR_KEYS) { k, t, _ -> clipboardScene(k, t, true) }
        d(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES) { k, t, _ -> toolbarScene(k, t, 1f, icons = listOf("⎘", "☺", if (t < 0.5f) "⚙" else "★", "↶", "↷"), pinned = 2) }
        d(Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, Defaults.PREF_QUICK_PIN_TOOLBAR_KEYS) { k, t, on ->
            toolbarScene(k, t, 1f, pinned = if (on && t > 0.55f) 1 else -1)
            with(k) { tap(Offset(pad + (size.width - 2 * pad) / 6f * 1.5f, 24f * density), t, 0.2f, 0.6f) }
        }
        d(Settings.PREF_AUTO_SHOW_TOOLBAR, Defaults.PREF_AUTO_SHOW_TOOLBAR) { k, t, on -> toolbarScene(k, t, if (on) ease(seg(t, 0.2f, 0.4f)) else 0f) }
        d(Settings.PREF_AUTO_HIDE_TOOLBAR, Defaults.PREF_AUTO_HIDE_TOOLBAR) { k, t, on -> toolbarScene(k, t, if (on) 1f - ease(seg(t, 0.4f, 0.6f)) else 1f) }
        d(Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, Defaults.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD) { k, _, on ->
            if (on) {
                with(k) {
                    val r = Rect(pad, size.height - 44f * density, size.width - pad, size.height - 12f * density)
                    round(r, kbBg, 10f)
                    listOf("⎘", "☺", "⚙", "↶", "↷").forEachIndexed { i, s -> text(s, Offset(r.left + r.width / 5f * (i + 0.5f), r.center.y), 13f) }
                    text("⌨  keyboard fisik tersambung", Offset(size.width / 2f, 34f * density), 12f, c.muted)
                }
            } else kbFull(k, Kb(toolbar = 1f))
        }
        d(Settings.PREF_VARIABLE_TOOLBAR_DIRECTION, Defaults.PREF_VARIABLE_TOOLBAR_DIRECTION) { k, t, on ->
            toolbarScene(k, t, 1f, icons = if (on && t > 0.5f) listOf("⚙", "↷", "↶", "☺", "⎘") else listOf("⎘", "☺", "↶", "↷", "⚙"))
        }

        // ---- Advanced ----
        d(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE) { k, t, on ->
            typingScene(k, t, "rahasia", "rahasia", false, words = if (on) listOf("", "🕶  tidak diingat", "") else listOf("rahasia", "rahasianya", "rahasiakan"))
        }
        d(Settings.PREF_KEY_LONGPRESS_TIMEOUT) { k, t, _ ->
            val keys = kbLow(k, Kb(pressed = if (t in 0.2f..0.8f) "e" else null), 0.45f)
            keys["e"]?.let { anchor ->
                with(k) {
                    tap(anchor.center, t, 0.15f, 0.85f)
                    val p = seg(t, 0.2f, 0.55f)
                    if (t in 0.2f..0.55f) drawArc(accent, -90f, 360f * p, false, anchor.center - Offset(18f * density, 18f * density), androidx.compose.ui.geometry.Size(36f * density, 36f * density), style = androidx.compose.ui.graphics.drawscope.Stroke(3f * density))
                }
            }
            if (t > 0.55f) popupScene(k, t, "e", listOf("é", "è", "ê", "3"), 0)
        }
        d(Settings.PREF_SPACE_HORIZONTAL_SWIPE) { k, t, _ -> cursorScene(k, t, horizontal = true) }
        d(Settings.PREF_SPACE_VERTICAL_SWIPE) { k, t, _ -> cursorScene(k, t, horizontal = false) }
        d(Settings.PREF_LANGUAGE_SWIPE_DISTANCE) { k, t, _ ->
            val keys = kbFull(k, Kb(spaceLabel = if (t > 0.55f) "English" else "Indonesia"))
            keys["space"]?.let { with(k) { if (t in 0.2f..0.6f) drawCircle(finger, 13f * density, Offset(lerp(it.left + 20f * density, it.right - 20f * density, seg(t, 0.25f, 0.55f)), it.center.y)) } }
        }
        d(Settings.PREF_TOUCHPAD_SENSITIVITY) { k, t, _ -> cursorScene(k, t, horizontal = true, fast = true) }
        d(Settings.PREF_TOUCHPAD_EDGE_SCROLL, Defaults.PREF_TOUCHPAD_EDGE_SCROLL) { k, t, on -> cursorScene(k, t, horizontal = true, edge = on) }
        d(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE) { k, t, on ->
            val sel = if (on) (seg(t, 0.3f, 0.6f) * 9).toInt() else 0
            val text = if (on && t > 0.7f) "halo " else "halo kak Rina"
            with(k) { field(fieldRect(), text, true, if (on && t in 0.3f..0.7f && sel > 0) (text.length - sel) until text.length else null, t) }
            val keys = kbLow(k, Kb(pressed = if (t in 0.25f..0.7f) "delete" else null), 0.4f)
            keys["delete"]?.let { with(k) { if (t in 0.25f..0.7f) drawCircle(finger, 13f * density, Offset(it.center.x - (if (on) seg(t, 0.3f, 0.6f) * 90f * density else 0f), it.center.y)) } }
        }
        d(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG) { k, t, on ->
            val keys = kbFull(k, Kb(spaceLabel = if (on && t > 0.6f) "English" else "Indonesia", pressed = if (t in 0.25f..0.6f) "space" else null))
            keys["space"]?.let { with(k) { tap(it.center, t, 0.2f, 0.65f) } }
        }
        d(Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, Defaults.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD) { k, t, on ->
            if (on && t > 0.5f) numpadScene(k) else {
                val keys = kbFull(k, Kb(pressed = if (t in 0.2f..0.5f) "123" else null))
                keys["123"]?.let { with(k) { tap(it.center, t, 0.15f, 0.5f) } }
            }
        }
        d(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, Defaults.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY) { k, t, on ->
            with(k) {
                val cx = size.width / 2f
                val alt = Rect(cx - 70f * density, 30f * density, cx - 10f * density, 70f * density)
                round(Rect(alt.left, alt.top + 3f * density, alt.right, alt.bottom + 3f * density), func, 10f)
                round(alt, key, 10f)
                text("Alt", alt.center, 14f, keyText, bold = true)
                tap(alt.center, t, 0.2f, 0.6f)
                if (on && t > 0.45f) emojiRow(k, Rect(cx + 4f * density, 30f * density, cx + 110f * density, 70f * density))
            }
        }
        d(Settings.PREF_SHOW_SETUP_WIZARD_ICON, Defaults.PREF_SHOW_SETUP_WIZARD_ICON) { k, t, on ->
            with(k) {
                val icons = listOf("💬", "📷", "🛒", "⌨")
                val w = (size.width - 2 * pad) / icons.size
                icons.forEachIndexed { i, s ->
                    val isOurs = i == icons.lastIndex
                    val a = if (isOurs) (if (on) seg(t, 0.2f, 0.4f) else 0.15f) else 1f
                    val r = Rect(pad + w * i + w / 2f - 22f * density, size.height / 2f - 30f * density, pad + w * i + w / 2f + 22f * density, size.height / 2f + 14f * density)
                    round(r, if (isOurs) c.accentSoft else key, 14f, a)
                    text(s, r.center, 20f, keyText.copy(alpha = a))
                    text(if (isOurs) "Pengaturan" else "", Offset(r.center.x, r.bottom + 12f * density), 10f, c.muted.copy(alpha = a))
                }
            }
        }
        d(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE) { k, t, on -> switchBackScene(k, t, on, "?123") }
        d(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Defaults.PREF_ABC_AFTER_NUMPAD_SPACE) { k, t, on -> switchBackScene(k, t, on, "123") }
        d(Settings.PREF_ABC_AFTER_EMOJI, Defaults.PREF_ABC_AFTER_EMOJI) { k, t, on -> switchBackScene(k, t, on, "☺") }
        d(Settings.PREF_CUSTOM_CURRENCY_KEY) { k, t, _ -> popupScene(k, t, "4", listOf("Rp", "$", "€", "¥"), highlight = 0) }
        d(SettingsWithoutKey.BACKUP_RESTORE) { k, t, _ -> iconScene(k, t, if (t < 0.5f) "⬆" else "⬇", if (t < 0.5f) "Cadangkan ke file" else "Pulihkan dari file") }
        d(Settings.PREF_TIMESTAMP_FORMAT) { k, t, _ -> typingScene(k, t, "", "27/09/2026 14.05", t > 0.3f, fixAt = 0.35f) }
        d(SettingsWithoutKey.DEBUG_SETTINGS) { k, t, _ -> iconScene(k, t, "🛠", "Untuk pengembang") }
        d(Settings.PREF_EMOJI_MAX_SDK) { k, t, _ -> emojiScene(k, 1f, if (t < 0.5f) "😀😂😍🙏" else "😀😂😍🙏🫠🫶") }
        d(Settings.PREF_URL_DETECTION, Defaults.PREF_URL_DETECTION) { k, t, on -> typingScene(k, t, "scriptgo.id/pro ", if (on) "scriptgo.id/pro " else "scriptgo. id/pro ", true, fixAt = 0.5f) }
        d(SettingsWithoutKey.LOAD_GESTURE_LIB) { k, t, _ -> iconScene(k, t, "〰", "Library ketik geser") }

        // ---- Gesture typing ----
        d(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT) { k, t, on -> gestureScene(k, t, word = if (on) "halo" else "", showTrail = on) }
        d(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL) { k, t, on -> gestureScene(k, t, word = "halo", showTrail = on) }
        d(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT) { k, t, on -> gestureScene(k, t, word = "halo", floating = on) }
        d(Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC, Defaults.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC) { k, t, on -> gestureScene(k, t, word = "halo", floating = true, follow = on) }
        d(Settings.PREF_GESTURE_SPACE_AWARE, Defaults.PREF_GESTURE_SPACE_AWARE) { k, t, on -> gestureScene(k, t, word = if (on) "halo kak" else "halo", throughSpace = on) }
        d(Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN) { k, t, _ -> gestureScene(k, t, word = "halo") }
        d(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION) { k, t, _ -> gestureScene(k, t, word = "halo", slowFade = true) }
    }
}

private fun DrawScope.themeCycle(k: DemoKit, t: Float, dark: Boolean) {
    val light = listOf(
        Kb(),
        Kb(bg = Color(0xFFFDE7EF), keyColor = Color.White, funcColor = Color(0xFFF7C7D8)),
        Kb(bg = Color(0xFFE8F0FE), keyColor = Color.White, funcColor = Color(0xFFC7D7F7)),
    )
    val night = listOf(
        Kb(bg = Color(0xFF0B0E12), keyColor = Color(0xFF262D36), funcColor = Color(0xFF1C222A), textColor = Color(0xFFEEF2F6)),
        Kb(bg = Color(0xFF1B1030), keyColor = Color(0xFF2E2150), funcColor = Color(0xFF24183F), textColor = Color(0xFFEDE7F6)),
        Kb(bg = Color(0xFF000000), keyColor = Color(0xFF1A1A1A), funcColor = Color(0xFF111111), textColor = Color.White),
    )
    val list = if (dark) night else light
    kbFull(k, list[(t * list.size).toInt().coerceIn(0, list.lastIndex)])
}

/** After typing on the symbols/emoji/clipboard view, the keyboard returns to letters (or not). */
private fun DrawScope.switchBackScene(k: DemoKit, t: Float, on: Boolean, from: String) {
    val symbolsView = t < 0.5f || !on
    val keys = kbFull(k, Kb(symbols = symbolsView && from != "☺" && from != "⎘", pressed = if (t in 0.35f..0.48f) "space" else null))
    with(k) {
        keys["space"]?.let { tap(it.center, t, 0.3f, 0.55f) }
        if (symbolsView && (from == "☺" || from == "⎘")) {
            val board = keys["board"] ?: return@with
            round(Rect(board.left, board.top, board.right, board.bottom - (keys["space"]?.height ?: 0f) - 8f * density), kbBg, 12f)
            if (from == "☺") emojiRow(k, Rect(board.left, board.top, board.right, board.top + 40f * density))
            else text("⎘  Resi JNE: JX1234567", Offset(board.center.x, board.top + 24f * density), 12f)
        }
    }
}

private fun DrawScope.emojiRow(k: DemoKit, r: Rect) = with(k) {
    val list = splitEmoji("😀😂🙏👍")
    list.forEachIndexed { i, e -> text(e, Offset(r.left + r.width / list.size * (i + 0.5f), r.center.y), 16f) }
}

private fun DrawScope.numpadScene(k: DemoKit) = with(k) {
    val area = Rect(pad, 10f * density, size.width - pad, size.height - 10f * density)
    round(area, kbBg, 12f)
    val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    val w = (area.width - 8f * density) / 3f
    val h = (area.height - 8f * density) / 4f
    labels.forEachIndexed { i, s ->
        val r = Rect(area.left + 4f * density + w * (i % 3), area.top + 4f * density + h * (i / 3), area.left + 4f * density + w * (i % 3 + 1), area.top + 4f * density + h * (i / 3 + 1)).deflate(2f * density)
        round(r, key, 6f)
        text(s, r.center, 13f, keyText, bold = true)
    }
}

/** Words appearing in the personal dictionary. */
private fun DrawScope.wordListScene(k: DemoKit, t: Float, words: List<String>, newest: Boolean = true) = with(k) {
    val panel = Rect(size.width / 2f - 110f * density, 12f * density, size.width / 2f + 110f * density, size.height - 12f * density)
    round(panel, key, 14f)
    outline(panel, c.line, 14f, 1f)
    text("📖  Kamus pribadi", Offset(panel.left + 14f * density, panel.top + 16f * density), 12f, c.muted, alignLeft = true)
    words.forEachIndexed { i, w ->
        val isNew = newest && i == words.lastIndex
        val a = if (isNew) seg(t, 0.3f, 0.45f) else 1f
        val y = panel.top + 38f * density + i * 20f * density
        if (y < panel.bottom - 8f * density) {
            if (isNew && t in 0.3f..0.7f) round(Rect(panel.left + 8f * density, y - 9f * density, panel.right - 8f * density, y + 9f * density), c.accentSoft, 6f, a)
            text(w, Offset(panel.left + 16f * density, y), 13f, keyText.copy(alpha = a), alignLeft = true)
        }
    }
}

/** Cursor moving through text by swiping the space bar (or vertically between lines). */
private fun DrawScope.cursorScene(k: DemoKit, t: Float, horizontal: Boolean, fast: Boolean = false, edge: Boolean = false) = with(k) {
    val value = "halo kak, stok ada"
    val r = fieldRect()
    field(r, value, false, null, t)
    val p = swing(t)
    val reach = if (fast) 1f else 0.6f
    val chars = if (horizontal) (value.length * (1f - p * reach)).toInt().coerceIn(0, value.length) else value.length
    val x = r.left + 12f * density + textWidth(value.take(chars), 14f)
    val y = if (horizontal) r.center.y else lerp(r.center.y, r.center.y - 6f * density, p)
    drawLine(accent, Offset(x + 1f, y - 9f * density), Offset(x + 1f, y + 9f * density), 2f * density)
    if (edge && t in 0.4f..0.6f) drawCircle(c.accentSoft, 10f * density, Offset(r.left + 6f * density, r.center.y))
    val keys = kbLow(k, Kb(pressed = "space"), 0.4f)
    keys["space"]?.let {
        val fx = if (horizontal) lerp(it.right - 16f * density, it.left + 16f * density, p) else it.center.x
        val fy = if (horizontal) it.center.y else lerp(it.center.y, it.center.y - 24f * density, p)
        drawCircle(finger, 13f * density, Offset(fx, fy))
    }
}

/**
 * Gesture typing: a trail over h-a-l-o, then the word appears in the field. Options show the
 * floating preview, a following preview, phrase gestures through the space bar, and slow fading.
 */
private fun DrawScope.gestureScene(
    k: DemoKit,
    t: Float,
    word: String,
    prefix: String = "",
    showTrail: Boolean = true,
    floating: Boolean = false,
    follow: Boolean = false,
    throughSpace: Boolean = false,
    slowFade: Boolean = false,
) = with(k) {
    val keys = kbLow(k, Kb(), 0.36f)
    val path = buildList {
        listOf("h", "a", "l", "o").forEach { keys[it]?.center?.let { add(it) } }
        if (throughSpace) {
            keys["space"]?.center?.let { add(it) }
            listOf("k", "a", "k").forEach { keys[it]?.center?.let { add(it) } }
        }
    }
    val drawn = seg(t, 0.1f, 0.5f)
    val fade = if (slowFade) 1f - seg(t, 0.5f, 0.95f) else 1f - seg(t, 0.5f, 0.6f)
    if (showTrail && path.size > 1) trail(path, drawn, fade)
    if (path.isNotEmpty() && t in 0.1f..0.5f) {
        val idx = ((path.size - 1) * drawn).toInt().coerceAtMost(path.size - 1)
        drawCircle(finger, 13f * density, path[idx])
        if (floating) {
            val partial = word.trim().take(((word.trim().length) * drawn).toInt().coerceAtLeast(1))
            val at = if (follow) path[idx] - Offset(0f, 34f * density) else Offset(size.width / 2f, (keys["board"]?.top ?: 60f) - 14f * density)
            val w = textWidth(partial, 13f, true) + 20f * density
            round(Rect(at.x - w / 2f, at.y - 12f * density, at.x + w / 2f, at.y + 12f * density), c.ink, 12f)
            text(partial, at, 13f, c.onInk, bold = true)
        }
    }
    field(fieldRect(), if (t >= 0.5f) prefix + word else prefix, true, null, t)
}
