package com.magickeyboard.manager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.magickeyboard.manager.AppTheme
import helium314.keyboard.latin.R

/** ScriptGo colours from design/tokens.json. Material roles cover most UI; the rest is read from here. */
@Immutable
data class ScriptGoColors(
    val accentStart: Color,
    val accentEnd: Color,
    val accentSolid: Color,
    /** Accent for text and icons on light surfaces (the solid accent is too light for text). */
    val accentInk: Color,
    /** Soft accent fill behind accent-ink icons and notes. */
    val accentSoft: Color,
    /** Text on the sky to indigo gradient. */
    val onAccent: Color,
    val ink: Color,
    val onInk: Color,
    val bg: Color,
    val card: Color,
    val line: Color,
    val muted: Color,
    val glow: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    /** The sky to indigo gradient used for the logo, highlights, and big numbers. */
    val accent: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))
}

val LightScriptGoColors = ScriptGoColors(
    accentStart = Color(0xFF0EA5E9),
    accentEnd = Color(0xFF6366F1),
    accentSolid = Color(0xFF0284C7),
    accentInk = Color(0xFF0369A1),
    accentSoft = Color(0xFFE0F2FE),
    onAccent = Color(0xFFFFFFFF),
    ink = Color(0xFF0E1116),
    onInk = Color(0xFFFFFFFF),
    bg = Color(0xFFEEF2F6),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFE3E8EF),
    muted = Color(0xFF5B6675),
    glow = Color(0xFFBAE6FD),
    danger = Color(0xFFC62A2F),
    isDark = false,
)

val DarkScriptGoColors = ScriptGoColors(
    accentStart = Color(0xFF0EA5E9),
    accentEnd = Color(0xFF6366F1),
    accentSolid = Color(0xFF38BDF8),
    accentInk = Color(0xFF7DD3FC),
    accentSoft = Color(0xFF0C3350),
    onAccent = Color(0xFFFFFFFF),
    ink = Color(0xFFEEF2F6),
    onInk = Color(0xFF0E1116),
    bg = Color(0xFF0F1318),
    card = Color(0xFF1A2028),
    line = Color(0xFF2A323D),
    muted = Color(0xFF98A4B3),
    glow = Color(0xFF075985),
    danger = Color(0xFFFF6369),
    isDark = true,
)

val LocalScriptGo = staticCompositionLocalOf { LightScriptGoColors }

object ScriptGo {
    val colors: ScriptGoColors
        @Composable @ReadOnlyComposable get() = LocalScriptGo.current
}

/** Urbanist (OFL) in five weights, from design/fonts. */
val Urbanist = FontFamily(
    Font(R.font.urbanist_regular, FontWeight.Normal),
    Font(R.font.urbanist_medium, FontWeight.Medium),
    Font(R.font.urbanist_semibold, FontWeight.SemiBold),
    Font(R.font.urbanist_bold, FontWeight.Bold),
    Font(R.font.urbanist_extrabold, FontWeight.ExtraBold),
)

private fun colorScheme(c: ScriptGoColors): ColorScheme {
    // Primary is ink, so filled buttons, checkboxes, and focus rings are the black (white in dark
    // mode) pills of the design; the sky accent is kept for highlights.
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.ink,
        onPrimary = c.onInk,
        primaryContainer = c.glow,
        onPrimaryContainer = c.ink,
        inversePrimary = c.accentSolid,
        secondary = c.accentSolid,
        onSecondary = c.onInk,
        secondaryContainer = c.glow,
        onSecondaryContainer = c.ink,
        tertiary = c.accentEnd,
        onTertiary = c.onInk,
        background = c.bg,
        onBackground = c.ink,
        surface = c.card,
        onSurface = c.ink,
        surfaceVariant = c.bg,
        onSurfaceVariant = c.muted,
        surfaceTint = Color.Transparent,
        outline = c.line,
        outlineVariant = c.line,
        error = c.danger,
        onError = Color.White,
        surfaceBright = c.card,
        surfaceDim = c.bg,
        surfaceContainerLowest = c.card,
        surfaceContainerLow = c.card,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.card,
    )
}

private fun TextStyle.urbanist(weight: FontWeight, size: Int? = null, tracking: Double? = null): TextStyle = copy(
    fontFamily = Urbanist,
    fontWeight = weight,
    fontSize = size?.sp ?: fontSize,
    letterSpacing = tracking?.em ?: letterSpacing,
)

/** Urbanist has a small x-height, so body sizes sit one step above Material defaults. */
private val ScriptGoTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.urbanist(FontWeight.ExtraBold, tracking = -0.02),
        displayMedium = base.displayMedium.urbanist(FontWeight.ExtraBold, tracking = -0.02),
        displaySmall = base.displaySmall.urbanist(FontWeight.ExtraBold, tracking = -0.02),
        headlineLarge = base.headlineLarge.urbanist(FontWeight.ExtraBold, size = 32, tracking = -0.02),
        headlineMedium = base.headlineMedium.urbanist(FontWeight.ExtraBold, size = 28, tracking = -0.02),
        headlineSmall = base.headlineSmall.urbanist(FontWeight.ExtraBold, size = 24, tracking = -0.01),
        titleLarge = base.titleLarge.urbanist(FontWeight.ExtraBold, size = 20, tracking = -0.01),
        titleMedium = base.titleMedium.urbanist(FontWeight.Bold, size = 18, tracking = 0.0),
        titleSmall = base.titleSmall.urbanist(FontWeight.Bold),
        bodyLarge = base.bodyLarge.urbanist(FontWeight.Medium, size = 17, tracking = 0.0),
        bodyMedium = base.bodyMedium.urbanist(FontWeight.Medium, size = 16, tracking = 0.0),
        bodySmall = base.bodySmall.urbanist(FontWeight.Medium, size = 14, tracking = 0.0),
        labelLarge = base.labelLarge.urbanist(FontWeight.SemiBold, size = 15, tracking = 0.0),
        labelMedium = base.labelMedium.urbanist(FontWeight.SemiBold, size = 12, tracking = 0.08),
        labelSmall = base.labelSmall.urbanist(FontWeight.SemiBold),
    )
}

private val ScriptGoShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp), // text fields, menus
    small = RoundedCornerShape(14.dp), // chips
    medium = RoundedCornerShape(22.dp), // cards
    large = RoundedCornerShape(28.dp), // sheets
    extraLarge = RoundedCornerShape(28.dp), // dialogs
)

@Composable
fun ScriptGoTheme(dark: Boolean = AppTheme.isDark(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkScriptGoColors else LightScriptGoColors
    // Status and navigation bar icons follow the chosen theme, not the phone's.
    val activity = LocalContext.current.activity()
    if (activity != null) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalScriptGo provides colors) {
        MaterialTheme(
            colorScheme = colorScheme(colors),
            typography = ScriptGoTypography,
            shapes = ScriptGoShapes,
        ) {
            // Text outside a Surface (most of our screens draw their own background) would
            // otherwise fall back to black, unreadable in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

private tailrec fun android.content.Context.activity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.activity()
    else -> null
}
