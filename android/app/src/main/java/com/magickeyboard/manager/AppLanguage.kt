package com.magickeyboard.manager

import android.content.Context
import android.content.res.Configuration
import com.magickeyboard.template.TimeSaved
import helium314.keyboard.latin.R
import java.util.Locale

/**
 * Language of ScriptGo's own screens (manager app, template form, template panel, keyboard
 * settings), chosen in Akun → Bahasa. Indonesian by default, whatever the phone's language, since
 * most users are in Indonesia; English, or following the phone, on request.
 */
object AppLanguage {
    const val INDONESIAN = "id"
    const val ENGLISH = "en"
    const val SYSTEM = "system"

    private const val PREFS = "scriptgo_app"
    private const val KEY = "app_language"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it in ALL } ?: INDONESIAN

    fun set(context: Context, language: String) {
        require(language in ALL)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    /** [base] with its resources in the chosen language; use in `attachBaseContext`. */
    @JvmStatic
    fun wrap(base: Context): Context {
        val language = current(base)
        if (language == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(config)
    }

    /** [id] in the chosen language, for code outside an activity (sync, repository, keyboard). */
    fun string(context: Context, id: Int, vararg args: Any): String = wrap(context).getString(id, *args)

    /** "45 detik" / "45 sec": [TimeSaved.format] with unit words in the chosen language. */
    fun duration(context: Context, seconds: Long): String {
        val localized = wrap(context)
        return TimeSaved.format(
            seconds,
            localized.getString(R.string.sg_unit_second),
            localized.getString(R.string.sg_unit_minute),
            localized.getString(R.string.sg_unit_hour),
        )
    }

    private val ALL = setOf(INDONESIAN, ENGLISH, SYSTEM)
}
