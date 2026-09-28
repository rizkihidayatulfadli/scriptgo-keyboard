package com.magickeyboard.manager.ui.settings

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.magickeyboard.manager.ui.theme.ScriptGoColors
import com.magickeyboard.manager.ui.theme.Urbanist
import kotlin.math.PI
import kotlin.math.sin

/**
 * Drawing helpers for the animated setting previews. Everything is drawn on a Canvas from a single
 * loop progress `t` (0..1), so a screen full of previews only redraws, never recomposes.
 */
class DemoKit(val c: ScriptGoColors, private val measurer: TextMeasurer) {
    val kbBg = if (c.isDark) Color(0xFF0B0E12) else Color(0xFFE2E8EF)
    val key = if (c.isDark) Color(0xFF262D36) else Color.White
    val func = if (c.isDark) Color(0xFF1C222A) else Color(0xFFCBD4DF)
    val keyText = c.ink
    val accent = c.accentSolid
    val finger = c.ink.copy(alpha = if (c.isDark) 0.3f else 0.2f)

    fun DrawScope.text(
        s: String,
        center: Offset,
        size: Float = 13f,
        color: Color = keyText,
        bold: Boolean = false,
        alignLeft: Boolean = false,
    ) {
        if (s.isEmpty()) return
        val layout = measurer.measure(
            s,
            TextStyle(fontFamily = Urbanist, fontSize = size.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, color = color),
        )
        val x = if (alignLeft) center.x else center.x - layout.size.width / 2f
        drawText(layout, topLeft = Offset(x, center.y - layout.size.height / 2f))
    }

    fun DrawScope.textWidth(s: String, size: Float = 13f, bold: Boolean = false): Float =
        if (s.isEmpty()) 0f else measurer.measure(
            s,
            TextStyle(fontFamily = Urbanist, fontSize = size.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold),
        ).size.width.toFloat()

    fun DrawScope.round(rect: Rect, color: Color, radius: Float = 8f, alpha: Float = 1f) {
        drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(radius.dp2px(this)), alpha = alpha)
    }

    fun DrawScope.outline(rect: Rect, color: Color, radius: Float = 8f, width: Float = 1.5f) {
        drawRoundRect(color, rect.topLeft, rect.size, CornerRadius(radius.dp2px(this)), style = Stroke(width.dp2px(this)))
    }

    /** A pressing finger: fades in, presses (smaller), and fades out within [from, to] of the loop. */
    fun DrawScope.tap(at: Offset, t: Float, from: Float, to: Float) {
        if (t < from || t > to) return
        val p = (t - from) / (to - from)
        val alpha = when {
            p < 0.2f -> p / 0.2f
            p > 0.8f -> (1f - p) / 0.2f
            else -> 1f
        }
        val scale = if (p in 0.35f..0.65f) 0.8f else 1f
        drawCircle(finger, radius = 13f.dp2px(this) * scale, center = at, alpha = alpha)
    }

    /** Text field with optional caret; [highlight] tints a character range (a fix just applied). */
    fun DrawScope.field(rect: Rect, value: String, caret: Boolean = true, highlight: IntRange? = null, t: Float = 0f, placeholder: String = "") {
        round(rect, c.card, 12f)
        outline(rect, c.line, 12f, 1f)
        val size = 14f
        var x = rect.left + 12f.dp2px(this)
        val y = rect.center.y
        if (value.isEmpty() && placeholder.isNotEmpty()) text(placeholder, Offset(x, y), size, c.muted, alignLeft = true)
        if (highlight != null && value.isNotEmpty()) {
            val start = highlight.first.coerceIn(0, value.length)
            val end = (highlight.last + 1).coerceIn(start, value.length)
            val left = x + textWidth(value.substring(0, start), size)
            val width = textWidth(value.substring(start, end), size)
            drawRoundRect(c.accentSoft, Offset(left - 2f, y - 11f.dp2px(this)), Size(width + 4f, 22f.dp2px(this)), CornerRadius(4f.dp2px(this)))
        }
        text(value, Offset(x, y), size, c.ink, alignLeft = true)
        x += textWidth(value, size)
        if (caret && (t * 6f).toInt() % 2 == 0) {
            drawLine(c.accentSolid, Offset(x + 2f, y - 9f.dp2px(this)), Offset(x + 2f, y + 9f.dp2px(this)), 2f.dp2px(this))
        }
    }

    /** Suggestion strip with three words; [pick] highlights one (-1 none). */
    fun DrawScope.strip(rect: Rect, words: List<String>, pick: Int = -1, alpha: Float = 1f) {
        round(rect, kbBg, 10f, alpha)
        val w = rect.width / 3f
        for (i in 0 until 3) {
            val word = words.getOrNull(i) ?: ""
            val cell = Rect(rect.left + w * i, rect.top, rect.left + w * (i + 1), rect.bottom)
            if (i == pick) round(cell.deflate(3f.dp2px(this)), c.accentSoft, 8f, alpha)
            if (i > 0) drawLine(func, Offset(cell.left, rect.top + 8f.dp2px(this)), Offset(cell.left, rect.bottom - 8f.dp2px(this)), 1f.dp2px(this), alpha = alpha)
            text(word, cell.center, 13f, (if (i == pick) c.accentInk else keyText).copy(alpha = alpha), bold = i == pick)
        }
    }

    /** Popup bubble above [key] showing [label] (key preview or long-press popup). */
    fun DrawScope.bubble(anchor: Rect, label: String, alpha: Float = 1f, wide: Float = 1f) {
        if (alpha <= 0f) return
        val w = anchor.width * 1.4f * wide
        val h = anchor.height * 1.5f
        val r = Rect(anchor.center.x - w / 2f, anchor.top - h - 2f.dp2px(this), anchor.center.x + w / 2f, anchor.top - 2f.dp2px(this))
        drawRoundRect(Color.Black, r.topLeft + Offset(0f, 3f), r.size, CornerRadius(10f.dp2px(this)), alpha = 0.12f * alpha)
        round(r, key, 10f, alpha)
        text(label, r.center, 18f, keyText.copy(alpha = alpha), bold = true)
    }

    /** Small phone outline used by vibration and system demos. */
    fun DrawScope.phone(center: Offset, shake: Float = 0f, dark: Boolean = false): Rect {
        val w = 64f.dp2px(this)
        val h = 96f.dp2px(this)
        val r = Rect(center.x - w / 2f + shake, center.y - h / 2f, center.x + w / 2f + shake, center.y + h / 2f)
        round(r, if (dark) Color(0xFF1A2028) else c.card, 14f)
        outline(r, c.ink, 14f, 2f)
        return r
    }

    fun DrawScope.wave(center: Offset, radius: Float, alpha: Float, color: Color = accent) {
        if (alpha <= 0f) return
        drawArc(color, -45f, 90f, false, center - Offset(radius, radius), Size(radius * 2, radius * 2), alpha = alpha, style = Stroke(3f.dp2px(this), cap = StrokeCap.Round))
    }

    fun DrawScope.trail(points: List<Offset>, progress: Float, alpha: Float = 1f) {
        if (points.size < 2 || progress <= 0f) return
        val path = Path()
        val count = ((points.size - 1) * progress).coerceAtLeast(0.01f)
        path.moveTo(points[0].x, points[0].y)
        val whole = count.toInt()
        for (i in 1..whole.coerceAtMost(points.size - 1)) path.lineTo(points[i].x, points[i].y)
        if (whole < points.size - 1) {
            val a = points[whole]
            val b = points[whole + 1]
            val f = count - whole
            path.lineTo(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        }
        drawPath(path, accent, alpha = alpha, style = Stroke(5f.dp2px(this), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

fun Float.dp2px(scope: DrawScope): Float = this * scope.density

/** Ease in-out on 0..1. */
fun ease(x: Float): Float = (0.5f - 0.5f * kotlin.math.cos(PI * x.coerceIn(0f, 1f))).toFloat()

/** 0..1 progress of [t] inside [from, to], clamped. */
fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** A value that swings 0 → 1 → 0 once per loop, smoothly. */
fun swing(t: Float): Float = (0.5f - 0.5f * kotlin.math.cos(2 * PI * t)).toFloat()

fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

/** Shake offset for the vibration demos: a short burst around [at]. */
fun shake(t: Float, at: Float, amp: Float, duration: Float = 0.12f): Float {
    if (t < at || t > at + duration) return 0f
    val p = (t - at) / duration
    return (sin(p * PI * 8) * amp * (1f - p)).toFloat()
}
