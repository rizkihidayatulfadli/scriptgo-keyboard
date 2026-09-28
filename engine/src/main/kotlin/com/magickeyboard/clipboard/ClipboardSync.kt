package com.magickeyboard.clipboard

/**
 * Plans the sync of the keyboard's text clipboard history with the account, when the user keeps
 * it without a time limit. Items travel encrypted; this planner only sees decrypted clips and
 * opaque ids (an HMAC of the text under the user's key, so the server never learns the text).
 *
 * Last write wins per clip by [Clip.updatedAt]. A clip deleted on one phone is a tombstone on the
 * server and is deleted on the others; a clip that was synced before and is gone from this phone
 * was deleted here.
 */
object ClipboardSync {
    data class Clip(val id: String, val text: String, val pinned: Boolean, val updatedAt: Long)

    data class Remote(val id: String, val clip: Clip?, val deleted: Boolean, val updatedAt: Long)

    data class Plan(
        /** Clips to upload (new or changed here). */
        val upload: List<Clip>,
        /** Ids to mark deleted on the server (deleted on this phone). */
        val deleteRemote: Set<String>,
        /** Clips to add or update on this phone. */
        val saveLocal: List<Clip>,
        /** Ids to delete on this phone (deleted on another phone). */
        val deleteLocal: Set<String>,
    )

    /**
     * [local] is this phone's history; [remote] the server rows (decrypted when not deleted);
     * [synced] the ids that were on both sides after the last sync.
     */
    fun plan(local: List<Clip>, remote: List<Remote>, synced: Set<String>): Plan {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val upload = mutableListOf<Clip>()
        val deleteRemote = mutableSetOf<String>()
        val saveLocal = mutableListOf<Clip>()
        val deleteLocal = mutableSetOf<String>()

        for (clip in local) {
            val server = remoteById[clip.id]
            when {
                server == null -> upload += clip
                server.deleted -> if (server.updatedAt >= clip.updatedAt) deleteLocal += clip.id else upload += clip
                server.clip == null -> upload += clip // could not be read here; rewrite with this phone's copy
                clip.updatedAt > server.updatedAt && clip != server.clip -> upload += clip
                server.updatedAt > clip.updatedAt && server.clip != clip -> saveLocal += server.clip
            }
        }
        for (server in remote) {
            if (server.id in localById || server.deleted) continue
            if (server.id in synced) deleteRemote += server.id else server.clip?.let { saveLocal += it }
        }
        return Plan(upload, deleteRemote, saveLocal, deleteLocal)
    }

    /** The ids on both sides once [plan] is carried out. */
    fun syncedAfter(local: List<Clip>, plan: Plan): Set<String> =
        (local.map { it.id } + plan.saveLocal.map { it.id }).toSet() - plan.deleteLocal - plan.deleteRemote
}
