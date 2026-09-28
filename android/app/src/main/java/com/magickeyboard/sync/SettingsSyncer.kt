package com.magickeyboard.sync

import android.content.Context
import android.content.SharedPreferences
import com.magickeyboard.settings.SettingsSync
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Reads and writes the settings that follow the account (table user_settings): the keyboard's
 * settings, the manager's keyboard options (trigger, space hold, ask for a contact), and the app's
 * language and theme. Values are stored typed, `{"b": true}`, `{"f": 1.1}`, `{"ss": ["a"]}`, so
 * they come back exactly as SharedPreferences held them. No network here; [SupabaseSync] uploads.
 */
internal class SettingsSyncer(private val context: Context) {
    private class Group(val name: String, val prefs: SharedPreferences, val keys: (String) -> Boolean)

    private val groups by lazy {
        listOf(
            Group("keyboard", context.prefs(), SettingsSync::syncable),
            Group("manager", context.getSharedPreferences("manager", Context.MODE_PRIVATE)) { it in MANAGER_KEYS },
            Group("app", context.getSharedPreferences("scriptgo_app", Context.MODE_PRIVATE)) { it in APP_KEYS },
        )
    }

    /** This phone's settings as the JSON stored on the server, with keys in a stable order. */
    fun snapshot(): JSONObject {
        val data = JSONObject()
        for (group in groups) {
            val values = SettingsSync.snapshot(group.prefs.all.filterKeys(group.keys))
            val json = JSONObject()
            for (key in values.keys.sorted()) json.put(key, encode(values.getValue(key)))
            data.put(group.name, json)
        }
        return data
    }

    /** A fingerprint of [data], to notice local changes since the last sync. */
    fun fingerprint(data: JSONObject): String =
        MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray()).joinToString("") { "%02x".format(it) }

    /** Makes this phone's settings match [data] from the server. */
    fun apply(data: JSONObject) {
        for (group in groups) {
            val json = data.optJSONObject(group.name) ?: continue
            val remote = buildMap {
                for (key in json.keys()) {
                    if (group.keys(key)) decode(json.optJSONObject(key))?.let { put(key, it) }
                }
            }
            val changes = SettingsSync.apply(group.prefs.all.filterKeys(group.keys), remote)
            if (changes.isEmpty) continue
            val editor = group.prefs.edit()
            changes.remove.forEach { editor.remove(it) }
            for ((key, value) in changes.set) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
        }
    }

    private fun encode(value: Any): JSONObject = when (value) {
        is Boolean -> JSONObject().put("b", value)
        is Int -> JSONObject().put("i", value)
        is Long -> JSONObject().put("l", value)
        is Float -> JSONObject().put("f", value.toDouble())
        is String -> JSONObject().put("s", value)
        is Set<*> -> JSONObject().put("ss", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> error("unsupported setting type")
    }

    private fun decode(json: JSONObject?): Any? = when {
        json == null -> null
        json.has("b") -> json.optBoolean("b")
        json.has("i") -> json.optInt("i")
        json.has("l") -> json.optLong("l")
        json.has("f") -> json.optDouble("f").toFloat()
        json.has("s") -> json.optString("s")
        json.has("ss") -> json.optJSONArray("ss")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() }
        else -> null
    }

    companion object {
        private val MANAGER_KEYS = setOf("trigger_char", "ask_contact", "space_hold_templates", "space_hold_ms")
        private val APP_KEYS = setOf("app_language", "app_theme")
    }
}
