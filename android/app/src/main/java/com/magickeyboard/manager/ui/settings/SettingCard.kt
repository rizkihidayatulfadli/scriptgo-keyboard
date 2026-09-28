package com.magickeyboard.manager.ui.settings

import android.content.SharedPreferences
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/** True when the phone's "remove animations" setting is on; previews then hold one frame. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Current value of a boolean preference, updated when it changes anywhere in the app. */
@Composable
fun rememberPrefBoolean(key: String, default: Boolean): Boolean {
    val prefs = LocalContext.current.prefs()
    fun read(p: SharedPreferences) = runCatching { p.getBoolean(key, default) }.getOrDefault(default)
    var value by remember(key) { mutableStateOf(read(prefs)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, changed -> if (changed == key) value = read(p) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

/** The looping animation for [demo]. Only the draw phase reads the clock, so it never recomposes. */
@Composable
fun DemoStage(demo: Demo, on: Boolean, modifier: Modifier = Modifier) {
    val kit = DemoKit(ScriptGo.colors, rememberTextMeasurer(cacheSize = 32))
    val reduced = rememberReducedMotion()
    val clock = if (reduced) null else rememberInfiniteTransition(label = "demo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(demo.periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val bg = ScriptGo.colors.bg
    Canvas(
        modifier.fillMaxWidth().height(132.dp).background(bg)
            // The animation is decorative; the setting's title and description say the same.
            .clearAndSetSemantics { },
    ) {
        val t = clock?.value ?: 0.62f
        demo.draw(this, kit, t, on)
    }
}

/**
 * Extra content that HeliBoard's `Preference` row draws under its title and description, inside the
 * row, so it lines up with the text and the switch stays centred on the whole block.
 */
val LocalSettingFooter = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * One setting row: HeliBoard's own preference (switch, slider, or list), plain text with no
 * animation. Settings that have an animated explanation get a "Lihat contoh" link inside the row
 * that opens it in a bottom sheet together with the same control, so only one animation ever runs
 * at a time. [divider] draws a thin line above the row to separate it from the one before.
 */
@Composable
fun SettingRow(key: String, title: String, divider: Boolean = false, content: @Composable () -> Unit) {
    val demo = SettingDemos.forKey(key)
    var showing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = ScriptGo.colors.line)
        val footer: (@Composable () -> Unit)? = demo?.let { { DemoLink(onClick = { showing = true }) } }
        CompositionLocalProvider(LocalSettingFooter provides footer) { content() }
        if (demo != null && showing) DemoSheet(key, title, demo, onDismiss = { showing = false }, content = content)
    }
}

/** A single setting on its own ScriptGo card, used for search results. */
@Composable
fun SettingCard(key: String, title: String, content: @Composable () -> Unit) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .then(if (c.isDark) Modifier else Modifier.shadow(6.dp, shape, clip = false, ambientColor = SHADOW, spotColor = SHADOW))
            .clip(shape)
            .background(c.card),
    ) {
        SettingRow(key, title, content = content)
    }
}

/** Compact "Lihat contoh" link under a setting's text, inside its row. */
@Composable
private fun DemoLink(onClick: () -> Unit) {
    val c = ScriptGo.colors
    Row(
        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(ScriptGoIcons.Sparkles, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.sg_setting_demo), style = MaterialTheme.typography.labelMedium, color = c.accentInk)
    }
}

/** Bottom sheet with the setting's animation on top and its control below, to try it out live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoSheet(key: String, title: String, demo: Demo, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = ScriptGo.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            val on = if (demo.boolDefault != null) rememberPrefBoolean(key, demo.boolDefault) else true
            DemoStage(demo, on, Modifier.clip(RoundedCornerShape(20.dp)).height(180.dp))
            // The sheet already is the example: no link inside it.
            CompositionLocalProvider(LocalSettingFooter provides null) { content() }
        }
    }
}

private val SHADOW = Color(0x2E0E1116)

/** Settings menu header (mockup 4a): a live keyboard typing a template shortcut. */
@Composable
fun SettingsHeroPreview(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val demo = remember {
        Demo(periodMs = 4200) { k, t, _ ->
            with(k) {
                val typedText = typed("/ongkir", t, 0.08f, 0.4f)
                val done = t > 0.55f
                field(fieldRect(), if (done) "Halo Kak! Ongkir ke Bandung Rp18.000" else typedText, true, if (done && t < 0.75f) 0..35 else null, t)
                val stripRect = Rect(pad, size.height * 0.37f, size.width - pad, size.height * 0.37f + 30f * density)
                if (!done && typedText.length >= 2) {
                    round(stripRect, kbBg, 10f)
                    val chip = Rect(stripRect.left + 4f * density, stripRect.top + 4f * density, stripRect.left + 150f * density, stripRect.bottom - 4f * density)
                    round(chip, if (t in 0.45f..0.55f) c.accentSoft else key, 8f)
                    outline(chip, c.accentSolid, 8f, 1.5f)
                    text("/ongkir  Info ongkir", Offset(chip.left + 10f * density, chip.center.y), 12f, keyText, alignLeft = true)
                    tap(chip.center, t, 0.42f, 0.58f)
                }
                kbLow(k, Kb(pressed = pressedKey("/ongkir", t, 0.08f, 0.4f)), 0.37f + 34f * density / size.height)
            }
        }
    }
    Column(modifier.fillMaxWidth().clip(shape).background(ScriptGo.colors.card)) {
        DemoStage(demo, true, Modifier.height(180.dp))
    }
}
