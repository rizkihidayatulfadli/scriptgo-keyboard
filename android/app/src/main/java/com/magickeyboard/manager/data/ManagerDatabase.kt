package com.magickeyboard.manager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local store for templates, contacts, and custom field definitions. Plain SQLite rather than Room:
 * current Room releases need minSdk 23, and HeliBoard targets 21.
 *
 * Contacts keep phones as newline-separated E.164 and field values as a JSON object, so adding a
 * custom field never needs a schema change.
 */
class ManagerDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE templates (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                shortcut TEXT NOT NULL UNIQUE,
                body TEXT NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                usage_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE contacts (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                phones TEXT NOT NULL DEFAULT '',
                fields TEXT NOT NULL DEFAULT '{}',
                last_used_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE field_definitions (
                id TEXT PRIMARY KEY,
                field_key TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL,
                type TEXT NOT NULL,
                options TEXT NOT NULL DEFAULT '',
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // The statements above are the version 1 schema; later versions build on it.
        onUpgrade(db, 1, VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // One step per version; never drop user data.
        if (oldVersion < 2) createStats(db)
        if (oldVersion < 3) addSyncColumns(db)
        // Version 4: a field's fallback, used when a contact has no value for it.
        if (oldVersion < 4) db.execSQL("ALTER TABLE field_definitions ADD COLUMN fallback TEXT NOT NULL DEFAULT ''")
        // Version 5: the icon the user picked for a field; empty means automatic (FieldIcons).
        if (oldVersion < 5) db.execSQL("ALTER TABLE field_definitions ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
    }

    /**
     * Version 3 (sync): `dirty` marks rows not yet uploaded, `deleted` keeps tombstones so
     * deletions reach other devices. The UNIQUE constraints on template shortcuts and field keys
     * are dropped (a tombstone or a row from another device may share them); the app checks
     * uniqueness among live rows instead. SQLite cannot drop a constraint, so those two tables
     * are rebuilt.
     */
    private fun addSyncColumns(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE templates_v3 (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                shortcut TEXT NOT NULL,
                body TEXT NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                usage_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                dirty INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO templates_v3 (id, title, shortcut, body, tags, usage_count, last_used_at, created_at, updated_at) " +
                "SELECT id, title, shortcut, body, tags, usage_count, last_used_at, created_at, updated_at FROM templates"
        )
        db.execSQL("DROP TABLE templates")
        db.execSQL("ALTER TABLE templates_v3 RENAME TO templates")

        db.execSQL(
            """
            CREATE TABLE field_definitions_v3 (
                id TEXT PRIMARY KEY,
                field_key TEXT NOT NULL,
                label TEXT NOT NULL,
                type TEXT NOT NULL,
                options TEXT NOT NULL DEFAULT '',
                position INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0,
                dirty INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            "INSERT INTO field_definitions_v3 (id, field_key, label, type, options, position) " +
                "SELECT id, field_key, label, type, options, position FROM field_definitions"
        )
        db.execSQL("DROP TABLE field_definitions")
        db.execSQL("ALTER TABLE field_definitions_v3 RENAME TO field_definitions")

        db.execSQL("ALTER TABLE contacts ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE contacts ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1")
    }

    /** Usage counters (counts only, never typed text): `uses`, `chars`. */
    private fun createStats(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stats (name TEXT PRIMARY KEY, value INTEGER NOT NULL DEFAULT 0)")
    }

    companion object {
        private const val NAME = "manager.db"
        const val VERSION = 5
    }
}
