package com.magickeyboard.manager.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Reusable scenes built from [DemoKit] and [keyboard]; each takes the loop progress `t`. */

val DrawScope.pad: Float get() = 14f * density

/** Keyboard filling the stage. */
fun DrawScope.kbFull(k: DemoKit, o: Kb): Map<String, Rect> =
    keyboard(k, Rect(pad, 10f * density, size.width - pad, size.height - 10f * density), o)

/** Keyboard in the lower part of the stage, leaving room above for a field or popup. */
fun DrawScope.kbLow(k: DemoKit, o: Kb, topFraction: Float = 0.42f): Map<String, Rect> =
    keyboard(k, Rect(pad, size.height * topFraction, size.width - pad, size.height - 8f * density), o)

fun DrawScope.fieldRect(): Rect = Rect(pad, 10f * density, size.width - pad, 44f * density)

/** Characters of [s] typed between [from] and [to]. */
fun typed(s: String, t: Float, from: Float = 0.05f, to: Float = 0.45f): String =
    s.take((s.length * seg(t, from, to)).toInt().coerceAtMost(s.length))

/** The key being pressed while typing [s] (null when done), for highlighting on the mini keyboard. */
fun pressedKey(s: String, t: Float, from: Float = 0.05f, to: Float = 0.45f): String? {
    if (t < from || t > to) return null
    val i = (s.length * seg(t, from, to)).toInt().coerceAtMost(s.length - 1)
    val ch = s[i].lowercaseChar()
    return when (ch) {
        ' ' -> "space"
        else -> ch.toString()
    }
}

/**
 * Typing demo: [input] is typed; at [fixAt] it becomes [output] when [applies] (with the change
 * highlighted), otherwise it stays. Optional suggestion strip above the keyboard.
 */
fun DrawScope.typingScene(
    k: DemoKit,
    t: Float,
    input: String,
    output: String,
    applies: Boolean,
    fixAt: Float = 0.55f,
    words: List<String>? = null,
    pick: Int = -1,
    showKeyboard: Boolean = true,
) = with(k) {
    val done = t >= fixAt && applies
    val value = if (done) output else typed(input, t)
    val changed = if (done && t < fixAt + 0.25f) changedRange(input, output) else null
    field(fieldRect(), value, true, changed, t)
    var top = 0.40f
    if (words != null) {
        val r = Rect(pad, size.height * 0.37f, size.width - pad, size.height * 0.37f + 30f * density)
        strip(r, words, if (t in (fixAt - 0.12f)..fixAt) pick else -1)
        top = 0.37f + 34f * density / size.height
    }
    if (showKeyboard) kbLow(k, Kb(labels = true, pressed = pressedKey(input, t)), top)
}

/** Range in [b] that differs from [a] (for highlighting a correction). */
fun changedRange(a: String, b: String): IntRange {
    var start = 0
    while (start < a.length && start < b.length && a[start] == b[start]) start++
    var endA = a.length - 1
    var endB = b.length - 1
    while (endA >= start && endB >= start && a[endA] == b[endB]) { endA--; endB-- }
    return start..maxOf(start, endB)
}

/** Phone that shakes when a key is tapped, with vibration marks at the sides. */
fun DrawScope.vibrateScene(k: DemoKit, t: Float, on: Boolean, amp: Float = 4f, dnd: Boolean = false) = with(k) {
    val s = if (on) shake(t, 0.4f, amp * density, 0.14f) else 0f
    val p = phone(Offset(size.width / 2f, size.height / 2f), s)
    keyboard(k, Rect(p.left + 5f * density, p.bottom - 38f * density, p.right - 5f * density, p.bottom - 5f * density), Kb(labels = false, pressed = if (t in 0.38f..0.55f) "g" else null))
    tap(Offset(p.center.x, p.bottom - 22f * density), t, 0.3f, 0.6f)
    if (on && t in 0.4f..0.56f) {
        val a = 1f - seg(t, 0.4f, 0.56f)
        for (i in 0..1) {
            val dx = (10f + i * 8f) * density
            drawLine(accent, Offset(p.left - dx, p.center.y - 12f * density), Offset(p.left - dx, p.center.y + 12f * density), 3f * density, alpha = a)
            drawLine(accent, Offset(p.right + dx, p.center.y - 12f * density), Offset(p.right + dx, p.center.y + 12f * density), 3f * density, alpha = a)
        }
    }
    if (dnd) {
        drawCircle(c.ink, 11f * density, Offset(p.right - 2f * density, p.top + 2f * density))
        text("☾", Offset(p.right - 2f * density, p.top + 2f * density), 11f, c.onInk, bold = true)
    }
}

/** A key being pressed with sound waves. */
fun DrawScope.soundScene(k: DemoKit, t: Float, on: Boolean, volume: Float = 1f) = with(k) {
    val cx = size.width / 2f - 30f * density
    val cy = size.height / 2f
    val pressed = t in 0.3f..0.45f
    val keyR = Rect(cx - 26f * density, cy - 26f * density + if (pressed) 2f * density else 0f, cx + 26f * density, cy + 26f * density + if (pressed) 2f * density else 0f)
    round(Rect(keyR.left, keyR.top + 3f * density, keyR.right, keyR.bottom + 3f * density), func, 12f)
    round(keyR, key, 12f)
    text("a", keyR.center, 22f, keyText, bold = true)
    if (on) {
        for (i in 0..2) {
            val start = 0.32f + i * 0.04f
            val a = if (t in start..start + 0.3f) (1f - seg(t, start, start + 0.3f)) * volume else 0f
            wave(Offset(keyR.right + 6f * density, cy), (14f + i * 12f) * density, a)
        }
    } else {
        text("🔇", Offset(keyR.right + 34f * density, cy), 20f)
    }
    tap(keyR.center, t, 0.2f, 0.55f)
}

/** Clipboard history panel: items slide in; optional pinned item on top and an image item. */
fun DrawScope.clipboardScene(k: DemoKit, t: Float, on: Boolean, pinnedFirst: Boolean = false, files: Boolean = false, expire: Boolean = false) = with(k) {
    val panel = Rect(pad, 10f * density, size.width - pad, size.height - 10f * density)
    round(panel, kbBg, 12f)
    if (!on) {
        text("Riwayat clipboard mati", panel.center, 13f, c.muted)
        return@with
    }
    val items = mutableListOf("BCA 123-456-7890 a.n. Toko Rina", "Jl. Merdeka No. 5, Bandung", "Resi JNE: JX1234567")
    if (files) items.add(1, "🖼  foto-produk.jpg")
    val rowH = 24f * density
    val newest = seg(t, 0.1f, 0.3f)
    var y = panel.top + 8f * density
    val ordered = if (pinnedFirst) listOf("📌  " + items.last()) + items.dropLast(1) else items
    ordered.forEachIndexed { i, s ->
        val alpha = if (i == (if (pinnedFirst) 1 else 0)) newest else 1f
        val fade = if (expire && i == ordered.lastIndex) 1f - seg(t, 0.55f, 0.8f) else 1f
        if (y + rowH <= panel.bottom) {
            val r = Rect(panel.left + 8f * density, y, panel.right - 8f * density, y + rowH - 4f * density)
            round(r, key, 8f, alpha * fade)
            text(s, Offset(r.left + 8f * density, r.center.y), 11f, keyText.copy(alpha = alpha * fade), alignLeft = true)
        }
        y += rowH
    }
}

/** Toolbar above the keyboard; [visible] animates it in and out. */
fun DrawScope.toolbarScene(k: DemoKit, t: Float, visible: Float, icons: List<String> = listOf("⎘", "☺", "✂", "↶", "↷", "⚙"), pinned: Int = -1) = with(k) {
    val keys = kbFull(k, Kb(toolbar = visible, toolbarIcons = icons, labels = true))
    if (pinned >= 0) keys["tb$pinned"]?.let { outline(it.deflate(2f * density), accent, 8f, 2f) }
}

/** An emoji row, scaled by [scale]. */
fun DrawScope.emojiScene(k: DemoKit, scale: Float, emojis: String = "😀😂😍🙏👍🎉") = with(k) {
    val panel = Rect(pad, 10f * density, size.width - pad, size.height - 10f * density)
    round(panel, kbBg, 12f)
    val list = splitEmoji(emojis)
    val w = panel.width / list.size
    list.forEachIndexed { i, e -> text(e, Offset(panel.left + w * (i + 0.5f), panel.center.y), 20f * scale) }
}

/** A long-press popup above a key listing [options]; [highlight] shows which one is chosen. */
fun DrawScope.popupScene(k: DemoKit, t: Float, keyName: String, options: List<String>, highlight: Int = 0, show: Boolean = true) = with(k) {
    val keys = kbLow(k, Kb(pressed = if (t in 0.2f..0.8f) keyName else null), 0.45f)
    val anchor = keys[keyName] ?: return@with
    tap(anchor.center, t, 0.15f, 0.85f)
    if (!show || t !in 0.3f..0.8f) return@with
    val a = seg(t, 0.3f, 0.36f) * (1f - seg(t, 0.74f, 0.8f))
    val cellW = 26f * density
    val w = cellW * options.size + 8f * density
    val left = (anchor.center.x - w / 2f).coerceIn(pad, size.width - pad - w)
    val r = Rect(left, anchor.top - 44f * density, left + w, anchor.top - 6f * density)
    round(r, key, 10f, a)
    outline(r, c.line, 10f, 1f)
    options.forEachIndexed { i, o ->
        val cell = Rect(r.left + 4f * density + cellW * i, r.top + 4f * density, r.left + 4f * density + cellW * (i + 1), r.bottom - 4f * density)
        if (i == highlight) round(cell, c.accentSoft, 7f, a)
        text(o, cell.center, 13f, (if (i == highlight) c.accentInk else keyText).copy(alpha = a), bold = i == highlight)
    }
}

/** Big icon tile with a gentle pulse, for settings without a visual effect on the keyboard. */
fun DrawScope.iconScene(k: DemoKit, t: Float, icon: String, caption: String = "") = with(k) {
    val s = 1f + 0.06f * swing(t)
    val r = 30f * density * s
    drawCircle(c.accentSoft, r, Offset(size.width / 2f, size.height / 2f - if (caption.isEmpty()) 0f else 10f * density))
    text(icon, Offset(size.width / 2f, size.height / 2f - if (caption.isEmpty()) 0f else 10f * density), 24f)
    if (caption.isNotEmpty()) text(caption, Offset(size.width / 2f, size.height - 22f * density), 12f, c.muted)
}

/** Splits a string of emoji into code points (no CharSequence.codePoints: it needs API 24). */
fun splitEmoji(s: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < s.length) {
        val n = Character.charCount(s.codePointAt(i))
        out.add(s.substring(i, i + n))
        i += n
    }
    return out
}
