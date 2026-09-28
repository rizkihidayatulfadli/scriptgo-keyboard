package com.magickeyboard.manager.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** What the mini keyboard shows; fractional values let a demo animate between states. */
data class Kb(
    val numberRow: Float = 0f,
    val numbers: String = "1234567890",
    val numberHints: Float = 0f,
    val toolbar: Float = 0f,
    val toolbarIcons: List<String> = listOf("⎘", "☺", "⚙", "↶", "↷"),
    val split: Float = 0f,
    val gap: Float = 1f,
    val height: Float = 1f,
    val bottomRow: Float = 1f,
    val sidePad: Float = 0f,
    val bottomPad: Float = 0f,
    val borders: Float = 1f,
    val corner: Float = 1f,
    val labels: Boolean = true,
    val fontScale: Float = 1f,
    val hints: Float = 0f,
    val funcHints: Float = 0f,
    val emojiKey: Boolean = false,
    val langKey: Boolean = false,
    val spaceLabel: String = "",
    val pressed: String? = null,
    val symbols: Boolean = false,
    val bg: Color? = null,
    val keyColor: Color? = null,
    val funcColor: Color? = null,
    val textColor: Color? = null,
    val background: Boolean = false,
)

private val LETTERS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val SYMBOLS = listOf("@#Rp_&-+()", "*\"':;!?/", ",.~<>{}")
private const val HINTS = "1234567890"

/**
 * Draws a small keyboard inside [area] and returns each key's rect by label ("q", "space",
 * "shift", "globe", "emoji", "123", "enter", "delete", ",", ".", digits, "tb0".."tb4").
 */
fun DrawScope.keyboard(k: DemoKit, area: Rect, o: Kb): Map<String, Rect> = with(k) {
    val keys = HashMap<String, Rect>()
    val bg = o.bg ?: kbBg
    val keyC = o.keyColor ?: k.key
    val funcC = o.funcColor ?: func
    val textC = o.textColor ?: keyText
    val totalH = area.height * o.height.coerceIn(0.3f, 1f)
    val board = Rect(area.left, area.bottom - totalH, area.right, area.bottom)
    round(board, bg, 12f)
    if (o.background) {
        drawRect(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.accentStart.copy(alpha = 0.55f), c.accentEnd.copy(alpha = 0.35f)), board.topLeft, board.bottomRight),
            board.topLeft, board.size,
        )
    }
    val pad = (4f * density)
    val side = pad + o.sidePad * density
    val bottom = pad + o.bottomPad * density
    val inner = Rect(board.left + side, board.top + pad, board.right - side, board.bottom - bottom)
    val weights = listOf(o.toolbar * 0.9f, o.numberRow, 1f, 1f, 1f, o.bottomRow)
    val unit = inner.height / weights.sum()
    val gapX = (3f * density) * o.gap
    val gapY = (4f * density) * o.gap
    val radius = 6f * o.corner
    var y = inner.top

    fun keyRect(x: Float, w: Float, h: Float) = Rect(x + gapX / 2f, y + gapY / 2f, x + w - gapX / 2f, y + h - gapY / 2f)
    fun drawKey(r: Rect, label: String, name: String, fn: Boolean = false, accent: Boolean = false, hint: String = "", hintAlpha: Float = 0f) {
        keys[name] = r
        val pressed = o.pressed == name
        val fill = when {
            accent -> c.accentSolid
            pressed -> c.accentSoft
            fn -> funcC
            else -> keyC
        }
        val alpha = if (accent || pressed) 1f else lerp(0f, 1f, o.borders)
        if (alpha > 0f) round(r, fill, radius, alpha)
        if (o.labels && r.height > (8f * density)) {
            val size = (r.height / density * 0.42f).coerceIn(6f, 14f) * o.fontScale
            text(label, r.center, size, if (accent) c.onAccent else textC, bold = pressed)
        }
        if (hint.isNotEmpty() && hintAlpha > 0f) {
            text(hint, Offset(r.right - (5f * density), r.top + (6f * density)), 7f * o.fontScale, textC.copy(alpha = 0.6f * hintAlpha))
        }
    }

    // Toolbar
    if (o.toolbar > 0.01f) {
        val h = unit * weights[0]
        val w = inner.width / o.toolbarIcons.size
        o.toolbarIcons.forEachIndexed { i, icon ->
            val r = Rect(inner.left + w * i, y, inner.left + w * (i + 1), y + h)
            keys["tb$i"] = r
            if (o.labels) text(icon, r.center, 12f, textC.copy(alpha = o.toolbar))
        }
        y += h
    }
    // Number row
    if (o.numberRow > 0.01f) {
        val h = unit * weights[1]
        val w = inner.width / 10f
        o.numbers.take(10).forEachIndexed { i, ch ->
            val r = keyRect(inner.left + w * i, w, h)
            drawKey(r, ch.toString(), ch.toString(), hint = "!@#$%^&*()"[i].toString(), hintAlpha = o.numberHints)
        }
        y += h
    }
    // Letter rows (optionally split)
    val rows = if (o.symbols) SYMBOLS else LETTERS
    rows.forEachIndexed { rowIndex, row ->
        val h = unit
        val splitGap = inner.width * o.split
        val usable = inner.width - splitGap
        val keyW = usable / 10f
        val extra = if (rowIndex == 2) 1.5f * keyW else 0f
        val rowWidth = row.length * keyW + extra * 2
        var x = inner.left + (usable - rowWidth) / 2f
        val half = (row.length + 1) / 2
        if (rowIndex == 2) {
            drawKey(keyRect(x, extra, h), "⇧", "shift", fn = true, hint = "", hintAlpha = 0f)
            if (o.funcHints > 0f) text("•", Offset(x + extra - (6f * density), y + (7f * density)), 7f, textC.copy(alpha = o.funcHints))
            x += extra
        }
        row.forEachIndexed { i, ch ->
            if (i == half) x += splitGap
            val hint = if (rowIndex == 0 && !o.symbols) HINTS[i].toString() else ""
            drawKey(keyRect(x, keyW, h), ch.toString(), ch.toString(), hint = hint, hintAlpha = o.hints)
            x += keyW
        }
        if (rowIndex == 2) drawKey(keyRect(x, extra, h), "⌫", "delete", fn = true)
        y += h
    }
    // Bottom row
    run {
        val h = unit * weights[5]
        val parts = buildList {
            add("123" to 1.5f)
            if (o.emojiKey) add("emoji" to 1f)
            if (o.langKey) add("globe" to 1f)
            add("," to 1f)
            add("space" to (if (o.emojiKey && o.langKey) 3f else if (o.emojiKey || o.langKey) 4f else 5f))
            add("." to 1f)
            add("enter" to 1.5f)
        }
        val unitW = inner.width / parts.sumOf { it.second.toDouble() }.toFloat()
        var x = inner.left
        parts.forEach { (name, weight) ->
            val w = unitW * weight
            val r = keyRect(x, w, h)
            when (name) {
                "123" -> drawKey(r, if (o.symbols) "ABC" else "?123", name, fn = true)
                "emoji" -> drawKey(r, "☺", name, fn = true)
                "globe" -> drawKey(r, "🌐", name, fn = true)
                "space" -> drawKey(r, o.spaceLabel, name)
                "enter" -> drawKey(r, "↵", name, accent = true)
                else -> drawKey(r, name, name, fn = true)
            }
            x += w
        }
    }
    keys["board"] = board
    keys
}
