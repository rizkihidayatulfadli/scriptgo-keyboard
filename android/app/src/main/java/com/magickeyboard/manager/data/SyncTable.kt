package com.magickeyboard.manager.data

import android.content.ContentValues
import android.database.Cursor
import org.json.JSONObject

/**
 * Tables that sync with Supabase (supabase/migrations, file sync_tables). Local and server tables
 * share names and most columns; usage counters and timestamps like `last_used_at` stay local.
 */
enum class SyncTable(val local: String, val hasCreatedAt: Boolean) {
    TEMPLATES("templates", hasCreatedAt = true) {
        override fun toServerRow(c: Cursor) = base(c).apply {
            put("title", c.str("title"))
            put("shortcut", c.str("shortcut"))
            put("body", c.str("body"))
            put("tags", c.str("tags"))
        }

        override fun toLocalValues(row: JSONObject) = ContentValues().apply {
            put("title", row.optString("title"))
            put("shortcut", row.optString("shortcut"))
            put("body", row.optString("body"))
            put("tags", row.optString("tags"))
        }
    },
    CONTACTS("contacts", hasCreatedAt = true) {
        override fun toServerRow(c: Cursor) = base(c).apply {
            put("name", c.str("name"))
            put("phones", c.str("phones"))
            put("fields", JSONObject(c.str("fields").ifEmpty { "{}" }))
        }

        override fun toLocalValues(row: JSONObject) = ContentValues().apply {
            put("name", row.optString("name"))
            put("phones", row.optString("phones"))
            put("fields", (row.optJSONObject("fields") ?: JSONObject()).toString())
        }
    },
    FIELD_DEFINITIONS("field_definitions", hasCreatedAt = false) {
        override fun toServerRow(c: Cursor) = base(c).apply {
            put("field_key", c.str("field_key"))
            put("label", c.str("label"))
            put("type", c.str("type"))
            put("options", c.str("options"))
            put("fallback", c.str("fallback"))
            put("icon", c.str("icon"))
            put("position", c.getInt(c.getColumnIndexOrThrow("position")))
        }

        override fun toLocalValues(row: JSONObject) = ContentValues().apply {
            put("field_key", row.optString("field_key"))
            put("label", row.optString("label"))
            put("type", row.optString("type"))
            put("options", row.optString("options"))
            put("fallback", row.optString("fallback"))
            put("icon", row.optString("icon"))
            put("position", row.optInt("position"))
        }
    };

    /** A local row in the server's shape, without `user_id` (the server sets it). */
    abstract fun toServerRow(c: Cursor): JSONObject

    /** Content columns of a server row; the caller adds id, updated_at, deleted, and dirty. */
    abstract fun toLocalValues(row: JSONObject): ContentValues

    internal fun base(c: Cursor) = JSONObject().apply {
        put("id", c.str("id"))
        put("updated_at", c.getLong(c.getColumnIndexOrThrow("updated_at")))
        put("deleted", c.getInt(c.getColumnIndexOrThrow("deleted")) == 1)
    }

    internal fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""
}
