package com.magickeyboard.contacts

/** Sync state of one record: when it was last edited (client epoch ms) and whether it awaits upload. */
data class SyncMeta(val updatedAt: Long, val dirty: Boolean)

/**
 * Last-write-wins merge for sync (PRD 6.5): each record keeps the version with the newer
 * `updatedAt`. The server applies the same rule when it receives uploads.
 */
object SyncMerge {
    /** Whether a pulled remote version should replace the local one. */
    fun shouldApplyRemote(local: SyncMeta?, remoteUpdatedAt: Long): Boolean = when {
        local == null -> true
        remoteUpdatedAt > local.updatedAt -> true
        remoteUpdatedAt == local.updatedAt -> !local.dirty
        else -> false
    }

    /**
     * Timestamp for a local edit: now, but always after [previous] so an edit on a device with a
     * slow clock still wins over the version it replaced.
     */
    fun nextUpdatedAt(previous: Long?, now: Long): Long = if (previous != null && now <= previous) previous + 1 else now
}
