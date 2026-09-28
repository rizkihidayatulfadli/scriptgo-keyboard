package com.magickeyboard.manager

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

/**
 * Light or dark look of ScriptGo's own screens (manager app, template form, keyboard settings),
 * chosen in Akun → Tema. Follows the phone by default. The keyboard's key colours have their own
 * setting in the keyboard settings.
 */
object AppTheme {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"

    private const val PREFS = "scriptgo_app"
    private const val KEY = "app_theme"
    private val ALL = setOf(LIGHT, DARK, SYSTEM)

    // Compose state, so every open screen switches as soon as the choice changes.
    @Volatile
    private var state: MutableState<String>? = null

    private fun state(context: Context): MutableState<String> = state ?: synchronized(this) {
        state ?: mutableStateOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it in ALL } ?: SYSTEM,
        ).also { state = it }
    }

    fun current(context: Context): String = state(context).value

    fun set(context: Context, theme: String) {
        require(theme in ALL)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme).apply()
        state(context).value = theme
    }

    /**
     * Whether ScriptGo's screens are dark right now. The template form is opened from the keyboard,
     * and there the activity's own configuration can report "light" while the phone is dark, so the
     * phone-wide setting is checked as well.
     */
    @Composable
    fun isDark(): Boolean {
        val system = isSystemInDarkTheme() ||
            Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return when (current(LocalContext.current)) {
            LIGHT -> false
            DARK -> true
            else -> system
        }
    }
}
