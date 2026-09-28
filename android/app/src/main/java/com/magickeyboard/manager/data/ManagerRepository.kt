package com.magickeyboard.manager.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.magickeyboard.contacts.Channels
import com.magickeyboard.contacts.ContactData
import com.magickeyboard.contacts.ExistingContact
import com.magickeyboard.contacts.ImportAction
import com.magickeyboard.contacts.ImportPlan
import com.magickeyboard.contacts.SyncMerge
import com.magickeyboard.contacts.TemplateCopy
import com.magickeyboard.contacts.TemplateDuplicates
import com.magickeyboard.contacts.SyncMeta
import com.magickeyboard.keyboard.SpaceHold
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.plan.FreeLimits
import com.magickeyboard.template.FieldType
import helium314.keyboard.latin.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

/**
 * Reads and writes the manager's data. Calls are blocking; run them off the main thread.
 * [changes] increments after every write so screens know to reload.
 */
class ManagerRepository private constructor(context: Context) {
    private val db = ManagerDatabase(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("manager", Context.MODE_PRIVATE)
    internal val appContext: Context = context.applicationContext

    /** A user-facing message in the app language. */
    private fun text(id: Int, vararg args: Any): String = AppLanguage.string(appContext, id, *args)
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes

    private fun changed() {
        _changes.value++
    }

    // region Templates

    fun templates(query: String = ""): List<Template> {
        val q = query.trim()
        val cursor = if (q.isEmpty()) {
            db.readableDatabase.rawQuery("SELECT * FROM templates WHERE deleted = 0 ORDER BY usage_count DESC, title COLLATE NOCASE", null)
        } else {
            val like = "%${q.escapeLike()}%"
            db.readableDatabase.rawQuery(
                "SELECT * FROM templates WHERE deleted = 0 AND (title LIKE ? ESCAPE '\\' OR shortcut LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') " +
                    "ORDER BY usage_count DESC, title COLLATE NOCASE",
                arrayOf(like, like, like),
            )
        }
        return cursor.use { c -> c.map { it.toTemplate() } }
    }

    fun template(id: String): Template? =
        db.readableDatabase.rawQuery("SELECT * FROM templates WHERE id = ? AND deleted = 0", arrayOf(id))
            .use { c -> c.map { it.toTemplate() }.firstOrNull() }

    /**
     * Saves [template] (insert when its id is new). Returns an error message, or null on success.
     * [limited]: the free plan's limits apply, so a new template past [FreeLimits.TEMPLATES] is refused.
     */
    fun saveTemplate(template: Template, limited: Boolean = false): String? {
        val shortcut = template.shortcut.trim()
        if (template.title.isBlank()) return text(R.string.sg_err_title_required)
        if (!SHORTCUT.matches(shortcut)) return text(R.string.sg_err_shortcut_chars)
        val clash = db.readableDatabase.rawQuery(
            "SELECT id FROM templates WHERE shortcut = ? AND id != ? AND deleted = 0", arrayOf(shortcut, template.id),
        ).use { it.moveToFirst() }
        if (clash) return text(R.string.sg_err_shortcut_taken, "/$shortcut")
        if (limited && template(template.id) == null && !FreeLimits.canAddTemplate(true, liveTemplateCount())) {
            return text(R.string.sg_limit_templates, FreeLimits.TEMPLATES)
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", template.title.trim())
            put("shortcut", shortcut)
            put("body", template.body)
            put("tags", template.tags.joinToString(","))
            put("updated_at", editTime("templates", template.id))
            put("deleted", 0)
            put("dirty", 1)
        }
        val updated = db.writableDatabase.update("templates", values, "id = ?", arrayOf(template.id))
        if (updated == 0) {
            values.put("id", template.id)
            values.put("created_at", now)
            db.writableDatabase.insertOrThrow("templates", null, values)
        }
        changed()
        return null
    }

    fun deleteTemplate(id: String) {
        markDeleted("templates", id)
        changed()
    }

    private fun liveTemplateCount(): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM templates WHERE deleted = 0", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Template uses counted while the free limits applied (never reset, PRD §9). */
    fun freeUses(): Long = db.readableDatabase.rawQuery("SELECT value FROM stats WHERE name = ?", arrayOf(STAT_FREE_USES))
        .use { if (it.moveToFirst()) it.getLong(0) else 0L }

    /**
     * Counts one insertion of [templateId] that put [chars] characters into a text field; [limited]
     * also counts it against the free plan's uses.
     */
    fun recordUsage(templateId: String, chars: Int, limited: Boolean = false) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.execSQL(
                "UPDATE templates SET usage_count = usage_count + 1, last_used_at = ? WHERE id = ?",
                arrayOf<Any>(System.currentTimeMillis(), templateId),
            )
            addToStat(w, STAT_USES, 1)
            addToStat(w, STAT_CHARS, chars.toLong())
            if (limited) addToStat(w, STAT_FREE_USES, 1)
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        changed()
    }

    fun usageTotals(): UsageTotals {
        val stats = db.readableDatabase.rawQuery("SELECT name, value FROM stats", null).use { c ->
            buildMap<String, Long> { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }
        return UsageTotals(uses = stats[STAT_USES] ?: 0, chars = stats[STAT_CHARS] ?: 0)
    }

    private fun addToStat(w: SQLiteDatabase, name: String, amount: Long) {
        w.execSQL("INSERT OR IGNORE INTO stats (name, value) VALUES (?, 0)", arrayOf<Any>(name))
        w.execSQL("UPDATE stats SET value = value + ? WHERE name = ?", arrayOf<Any>(amount, name))
    }

    // endregion

    // region Settings

    /** Character that starts a template shortcut in the keyboard, `/` by default. */
    fun triggerChar(): Char = prefs.getString(PREF_TRIGGER, null)?.firstOrNull() ?: DEFAULT_TRIGGER

    fun setTriggerChar(trigger: Char) {
        prefs.edit().putString(PREF_TRIGGER, trigger.toString()).apply()
        changed()
    }

    /** Whether the first-run guide was finished or skipped. */
    fun onboardingDone(): Boolean = prefs.getBoolean(PREF_ONBOARDING_DONE, false)

    fun setOnboardingDone() {
        prefs.edit().putBoolean(PREF_ONBOARDING_DONE, true).apply()
    }

    /**
     * Adds [STARTER_TEMPLATES] once, and only when the user has no templates yet. Each starter has
     * a fixed id (`starter-salam`) and the oldest possible `updated_at`, so a reinstall adds no
     * second copy on the server, and the account's own version (edited or deleted) always wins.
     */
    fun seedStarterTemplates() {
        if (prefs.getBoolean(PREF_STARTERS_SEEDED, false)) return
        prefs.edit().putBoolean(PREF_STARTERS_SEEDED, true).apply()
        if (templates().isNotEmpty()) return
        val w = db.writableDatabase
        STARTER_TEMPLATES.forEach { (shortcut, title, body) ->
            val values = ContentValues().apply {
                put("id", TemplateDuplicates.STARTER_PREFIX + shortcut)
                put("title", title)
                put("shortcut", shortcut)
                put("body", body)
                put("tags", "")
                put("created_at", System.currentTimeMillis())
                put("updated_at", 1L)
                put("deleted", 0)
                put("dirty", 1)
            }
            w.insertWithOnConflict("templates", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
        changed()
    }

    /**
     * Deletes extra copies of identical templates (see [TemplateDuplicates]), e.g. starters added
     * again by earlier installs. Returns how many were deleted; they upload on the next push.
     */
    fun removeDuplicateTemplates(): Int {
        val copies = db.readableDatabase.rawQuery("SELECT id, shortcut, title, body FROM templates WHERE deleted = 0", null).use { c ->
            c.map { TemplateCopy(it.getString(0), it.getString(1), it.getString(2), it.getString(3)) }
        }
        val extra = TemplateDuplicates.toDelete(copies)
        extra.forEach { markDeleted("templates", it) }
        if (extra.isNotEmpty()) changed()
        return extra.size
    }

    /** Whether the keyboard asks "Untuk siapa?" before a template's variable form. */
    fun askContact(): Boolean = prefs.getBoolean(PREF_ASK_CONTACT, true)

    fun setAskContact(ask: Boolean) {
        prefs.edit().putBoolean(PREF_ASK_CONTACT, ask).apply()
        changed()
    }

    /** Whether holding the space bar opens the template panel in the keyboard. */
    fun spaceHoldEnabled(): Boolean = prefs.getBoolean(PREF_SPACE_HOLD, true)

    /** How long the space bar must be held, in milliseconds (0.5 to 3 seconds, 1 by default). */
    fun spaceHoldMs(): Int = prefs.getInt(PREF_SPACE_HOLD_MS, SpaceHold.DEFAULT_MS).coerceIn(SpaceHold.MIN_MS, SpaceHold.MAX_MS)

    fun setSpaceHold(enabled: Boolean, millis: Int) {
        prefs.edit()
            .putBoolean(PREF_SPACE_HOLD, enabled)
            .putInt(PREF_SPACE_HOLD_MS, millis.coerceIn(SpaceHold.MIN_MS, SpaceHold.MAX_MS))
            .apply()
        changed()
    }

    // endregion

    // region Contacts

    fun contacts(query: String = ""): List<Contact> {
        val q = query.trim()
        val cursor = if (q.isEmpty()) {
            db.readableDatabase.rawQuery("SELECT * FROM contacts WHERE deleted = 0 ORDER BY name COLLATE NOCASE", null)
        } else {
            // Phones are stored as E.164; also match a local "08…" search against "+628…".
            val digits = q.filter { it.isDigit() }.removePrefix("0")
            val like = "%${q.escapeLike()}%"
            val phoneLike = if (digits.isEmpty()) like else "%${digits.escapeLike()}%"
            db.readableDatabase.rawQuery(
                "SELECT * FROM contacts WHERE deleted = 0 AND (name LIKE ? ESCAPE '\\' OR phones LIKE ? ESCAPE '\\' OR fields LIKE ? ESCAPE '\\') " +
                    "ORDER BY name COLLATE NOCASE",
                arrayOf(like, phoneLike, like),
            )
        }
        return cursor.use { c -> c.map { it.toContact() } }
    }

    /** Contacts for the keyboard's picker: most recently used first, then by name. */
    fun recentContacts(query: String, limit: Int = 50): List<Contact> =
        contacts(query).sortedWith(compareByDescending<Contact> { it.lastUsedAt ?: 0L }.thenBy { it.name.lowercase() }).take(limit)

    /** Marks [id] as just used from the keyboard and merges [changedFields] into its values. */
    fun useContact(id: String, changedFields: Map<String, String>) {
        val contact = contact(id) ?: return
        val values = ContentValues().apply {
            put("last_used_at", System.currentTimeMillis())
            if (changedFields.isNotEmpty()) {
                put("fields", JSONObject((contact.fields + changedFields).filterValues { it.isNotEmpty() }).toString())
                put("updated_at", editTime("contacts", id))
                put("dirty", 1)
            }
        }
        db.writableDatabase.update("contacts", values, "id = ?", arrayOf(id))
        changed()
    }

    fun contact(id: String): Contact? =
        db.readableDatabase.rawQuery("SELECT * FROM contacts WHERE id = ? AND deleted = 0", arrayOf(id))
            .use { c -> c.map { it.toContact() }.firstOrNull() }

    /** Saves [contact]; its phones must already be E.164. Returns an error message, or null on success. */
    fun saveContact(contact: Contact): String? {
        if (contact.name.isBlank()) return text(R.string.sg_err_name_required)
        upsertContact(db.writableDatabase, contact.id, ContactData(contact.name.trim(), contact.phones, contact.fields))
        changed()
        return null
    }

    fun deleteContact(id: String) {
        markDeleted("contacts", id)
        changed()
    }

    fun existingContacts(): List<ExistingContact> =
        contacts().map { ExistingContact(it.id, ContactData(it.name, it.phones, it.fields)) }

    /** Applies an import plan in one transaction and returns how many contacts were written. */
    fun applyImport(plan: ImportPlan): Int {
        val w = db.writableDatabase
        var written = 0
        w.beginTransaction()
        try {
            for (action in plan.actions) {
                when (action) {
                    is ImportAction.Create -> upsertContact(w, newId(), action.contact)
                    is ImportAction.Update -> upsertContact(w, action.id, action.contact)
                    is ImportAction.Skip -> continue
                }
                written++
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        changed()
        return written
    }

    private fun upsertContact(w: SQLiteDatabase, id: String, data: ContactData) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", data.name)
            put("phones", data.phones.joinToString("\n"))
            put("fields", JSONObject(data.fields.filterValues { it.isNotEmpty() }).toString())
            put("updated_at", editTime("contacts", id, w))
            put("deleted", 0)
            put("dirty", 1)
        }
        if (w.update("contacts", values, "id = ?", arrayOf(id)) == 0) {
            values.put("id", id)
            values.put("created_at", now)
            w.insertOrThrow("contacts", null, values)
        }
    }

    // endregion

    // region Field definitions

    fun fieldDefinitions(): List<FieldDefinition> {
        ensureChannelField()
        return db.readableDatabase.rawQuery("SELECT * FROM field_definitions WHERE deleted = 0 ORDER BY position, label COLLATE NOCASE", null)
            .use { c -> c.map { it.toFieldDefinition() } }
    }

    /**
     * Creates the built-in Channel field (see [Channels]) when there is no field with its key; a
     * `channel` field the user made earlier is used as it is. The fixed id makes copies created on
     * several devices one field after sync, and the oldest possible edit time lets any real edit
     * from another device win.
     */
    private fun ensureChannelField() {
        val exists = db.readableDatabase
            .rawQuery("SELECT 1 FROM field_definitions WHERE field_key = ? AND deleted = 0 LIMIT 1", arrayOf(Channels.KEY))
            .use { it.moveToFirst() }
        if (exists) return
        val values = ContentValues().apply {
            put("id", Channels.FIELD_ID)
            put("field_key", Channels.KEY)
            put("label", "Channel")
            put("type", FieldType.CHOICE.key)
            put("options", Channels.DEFAULT_OPTIONS.joinToString("\n"))
            put("fallback", "")
            put("position", -1)
            put("updated_at", 1L)
            put("deleted", 0)
            put("dirty", 1)
        }
        db.writableDatabase.insertWithOnConflict("field_definitions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Saves [field]. Its key must already be unique; see [com.magickeyboard.contacts.FieldKeys]. */
    fun saveField(field: FieldDefinition): String? {
        if (field.label.isBlank()) return text(R.string.sg_err_field_name_required)
        if (field.type == FieldType.CHOICE && field.options.isEmpty()) return text(R.string.sg_err_need_option)
        val values = ContentValues().apply {
            put("label", field.label.trim())
            put("type", field.type.key)
            put("options", field.options.joinToString("\n"))
            put("fallback", field.fallback.trim())
            put("icon", field.icon)
            put("position", field.position)
            put("updated_at", editTime("field_definitions", field.id))
            put("deleted", 0)
            put("dirty", 1)
        }
        val w = db.writableDatabase
        if (w.update("field_definitions", values, "id = ?", arrayOf(field.id)) == 0) {
            values.put("id", field.id)
            values.put("field_key", field.key)
            w.insertOrThrow("field_definitions", null, values)
        }
        changed()
        return null
    }

    /** Deletes the definition only; values stay on contacts, so re-adding the key restores them. */
    fun deleteField(id: String) {
        markDeleted("field_definitions", id)
        changed()
    }

    // endregion

    // region Sync support

    /** Edit time for a row: now, but always after the row's previous `updated_at` (see SyncMerge). */
    private fun editTime(table: String, id: String, w: SQLiteDatabase = db.writableDatabase): Long {
        val previous = w.rawQuery("SELECT updated_at FROM $table WHERE id = ?", arrayOf(id))
            .use { if (it.moveToFirst()) it.getLong(0) else null }
        return SyncMerge.nextUpdatedAt(previous, System.currentTimeMillis())
    }

    private fun markDeleted(table: String, id: String) {
        val values = ContentValues().apply {
            put("deleted", 1)
            put("dirty", 1)
            put("updated_at", editTime(table, id))
        }
        db.writableDatabase.update(table, values, "id = ?", arrayOf(id))
    }

    /** Rows of [table] changed since the last upload, shaped like the server's table. */
    fun dirtyRows(table: SyncTable): List<JSONObject> =
        db.readableDatabase.rawQuery("SELECT * FROM ${table.local} WHERE dirty = 1", null).use { c ->
            c.map { table.toServerRow(it) }
        }

    /** Marks a row uploaded, unless it was edited again after [updatedAt] was read. */
    fun markClean(table: SyncTable, id: String, updatedAt: Long) {
        db.writableDatabase.execSQL(
            "UPDATE ${table.local} SET dirty = 0 WHERE id = ? AND updated_at = ?",
            arrayOf<Any>(id, updatedAt),
        )
    }

    /** Applies pulled server rows (last write wins). Returns how many replaced local data. */
    fun applyRemote(table: SyncTable, rows: List<JSONObject>): Int {
        val w = db.writableDatabase
        var applied = 0
        w.beginTransaction()
        try {
            for (row in rows) {
                val id = row.getString("id")
                val remoteUpdatedAt = row.getLong("updated_at")
                val local = w.rawQuery("SELECT updated_at, dirty FROM ${table.local} WHERE id = ?", arrayOf(id))
                    .use { if (it.moveToFirst()) SyncMeta(it.getLong(0), it.getInt(1) == 1) else null }
                if (!SyncMerge.shouldApplyRemote(local, remoteUpdatedAt)) continue
                val values = table.toLocalValues(row).apply {
                    put("updated_at", remoteUpdatedAt)
                    put("deleted", if (row.optBoolean("deleted")) 1 else 0)
                    put("dirty", 0)
                }
                if (w.update(table.local, values, "id = ?", arrayOf(id)) == 0) {
                    values.put("id", id)
                    if (table.hasCreatedAt) values.put("created_at", remoteUpdatedAt)
                    w.insertOrThrow(table.local, null, values)
                }
                applied++
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        if (applied > 0) changed()
        return applied
    }

    /** Server `synced_at` of the last pulled row of [table], or null before the first pull. */
    fun pullCursor(table: SyncTable): String? = prefs.getString("sync_cursor_${table.local}", null)

    fun setPullCursor(table: SyncTable, cursor: String) {
        prefs.edit().putString("sync_cursor_${table.local}", cursor).apply()
    }

    /** Forgets pull progress, e.g. after signing in to another account, so everything is pulled again. */
    fun resetSync() {
        val editor = prefs.edit()
        SyncTable.entries.forEach { editor.remove("sync_cursor_${it.local}") }
        editor.apply()
        val w = db.writableDatabase
        SyncTable.entries.forEach { w.execSQL("UPDATE ${it.local} SET dirty = 1") }
    }

    // endregion

    private fun Cursor.toTemplate() = Template(
        id = string("id"),
        title = string("title"),
        shortcut = string("shortcut"),
        body = string("body"),
        tags = string("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
        usageCount = getInt(getColumnIndexOrThrow("usage_count")),
        lastUsedAt = longOrNull("last_used_at"),
    )

    private fun Cursor.toContact(): Contact {
        val json = JSONObject(string("fields"))
        val fields = json.keys().asSequence().associateWith { json.optString(it) }
        return Contact(
            id = string("id"),
            name = string("name"),
            phones = string("phones").split('\n').filter { it.isNotEmpty() },
            fields = fields,
            lastUsedAt = longOrNull("last_used_at"),
        )
    }

    private fun Cursor.toFieldDefinition() = FieldDefinition(
        id = string("id"),
        key = string("field_key"),
        label = string("label"),
        type = FieldType.fromKey(string("type")) ?: FieldType.TEXT,
        options = string("options").split('\n').filter { it.isNotEmpty() },
        position = getInt(getColumnIndexOrThrow("position")),
        fallback = string("fallback"),
        icon = string("icon"),
    )

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
        val result = ArrayList<T>(count)
        while (moveToNext()) result += transform(this)
        return result
    }

    private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        val SHORTCUT = Regex("[a-z0-9_-]+")
        const val DEFAULT_TRIGGER = '/'
        private const val PREF_TRIGGER = "trigger_char"
        private const val PREF_ASK_CONTACT = "ask_contact"
        private const val PREF_SPACE_HOLD = "space_hold_templates"
        private const val PREF_SPACE_HOLD_MS = "space_hold_ms"
        private const val PREF_ONBOARDING_DONE = "onboarding_done"
        private const val PREF_STARTERS_SEEDED = "starter_templates_seeded"

        /** Examples for sellers and CS, added on first run: shortcut, title, body. */
        val STARTER_TEMPLATES = listOf(
            Triple(
                "salam", "Salam pembuka",
                "Halo {nama|Kak}, terima kasih sudah menghubungi kami 😊 Ada yang bisa kami bantu?",
            ),
            Triple(
                "ongkir", "Info ongkir",
                "Ongkir ke {kota} via {kurir:JNE,J&T,SiCepat} Rp{ongkir} ya {nama|Kak}. Total jadi Rp{total}.",
            ),
            Triple(
                "rekening", "Info pembayaran",
                "Pembayaran bisa ditransfer ke:\nBCA 1234567890 a.n. {nama_toko|Nama Toko}\nMohon kirim bukti transfer ya {nama|Kak} 🙏",
            ),
            Triple(
                "makasih", "Terima kasih",
                "Terima kasih sudah berbelanja, {nama|Kak}! Pesanan dikirim hari {@hari} ini. Ditunggu order berikutnya 🙏",
            ),
            Triple(
                "followup", "Follow up pesanan",
                "Halo {nama|Kak}, mau follow up pesanan kemarin. Apakah jadi diproses? {@kursor}",
            ),
        )
        private const val STAT_USES = "uses"
        private const val STAT_CHARS = "chars"
        private const val STAT_FREE_USES = "free_uses"

        fun newId(): String = UUID.randomUUID().toString()

        @Volatile
        private var instance: ManagerRepository? = null

        fun get(context: Context): ManagerRepository =
            instance ?: synchronized(this) { instance ?: ManagerRepository(context).also { instance = it } }
    }
}
