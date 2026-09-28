package com.magickeyboard.settings

/**
 * Which settings follow the account (Akun → sync), so a reinstall or a new phone keeps the
 * keyboard as the user set it up. Values are what SharedPreferences holds: Boolean, Int, Long,
 * Float, String, or Set<String>.
 *
 * Left out: state that only makes sense on this phone or changes on its own (recent emojis, the
 * floating keyboard's position, per-app languages, custom layout files that are not synced), and
 * internal bookkeeping (versions, checksums).
 */
object SettingsSync {
    private val EXCLUDED_KEYS = setOf(
        "recent_emojis",
        "last_shown_emoji_category_id",
        "last_shown_emoji_category_page_id",
        "emoji_max_sdk",
        "version_code",
        "lib_checksum",
        "floating_pos_x",
        "floating_pos_y",
        "dont_show_missing_dict_dialog",
    )
    private val EXCLUDED_PREFIXES = listOf(
        "saved_app_subtype_", // language per app, tied to this phone's apps
        "layout_", // points at custom layout files kept on the phone
    )

    fun syncable(key: String): Boolean =
        key !in EXCLUDED_KEYS && EXCLUDED_PREFIXES.none { key.startsWith(it) }

    private fun supported(value: Any?): Boolean = when (value) {
        is Boolean, is Int, is Long, is Float, is String -> true
        is Set<*> -> value.all { it is String }
        else -> false
    }

    /** The settings of [all] that follow the account. */
    fun snapshot(all: Map<String, Any?>): Map<String, Any> =
        all.filter { (key, value) -> syncable(key) && supported(value) }.mapValues { it.value!! }

    /**
     * What to change on this phone to match [remote]: values to write, and syncable keys to remove
     * because the account no longer has them (the user reset them to the default).
     */
    fun apply(local: Map<String, Any?>, remote: Map<String, Any>): Changes {
        val wanted = snapshot(remote)
        val current = snapshot(local)
        val set = wanted.filter { (key, value) -> current[key] != value }
        val remove = current.keys.filter { it !in wanted }.toSet()
        return Changes(set, remove)
    }

    data class Changes(val set: Map<String, Any>, val remove: Set<String>) {
        val isEmpty: Boolean get() = set.isEmpty() && remove.isEmpty()
    }
}
