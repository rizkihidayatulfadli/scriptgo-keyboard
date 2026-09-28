package com.magickeyboard.clipboard

import com.magickeyboard.clipboard.ClipboardSync.Clip
import com.magickeyboard.clipboard.ClipboardSync.Remote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClipboardSyncTest {
    private fun clip(id: String, at: Long, pinned: Boolean = false) = Clip(id, "text $id", pinned, at)
    private fun remote(c: Clip) = Remote(c.id, c, deleted = false, updatedAt = c.updatedAt)

    @Test
    fun `a new phone downloads everything, an old one uploads what the server lacks`() {
        val a = clip("a", 10)
        val b = clip("b", 20)
        val fresh = ClipboardSync.plan(local = emptyList(), remote = listOf(remote(a), remote(b)), synced = emptySet())
        assertEquals(listOf(a, b), fresh.saveLocal)
        assertEquals(emptyList<Clip>(), fresh.upload)

        val first = ClipboardSync.plan(local = listOf(a, b), remote = emptyList(), synced = emptySet())
        assertEquals(listOf(a, b), first.upload)
    }

    @Test
    fun `newer edits win on either side`() {
        val pinnedHere = clip("a", 30, pinned = true)
        val plan = ClipboardSync.plan(listOf(pinnedHere), listOf(remote(clip("a", 10))), synced = setOf("a"))
        assertEquals(listOf(pinnedHere), plan.upload)

        val pinnedThere = clip("a", 30, pinned = true)
        val other = ClipboardSync.plan(listOf(clip("a", 10)), listOf(remote(pinnedThere)), synced = setOf("a"))
        assertEquals(listOf(pinnedThere), other.saveLocal)
    }

    @Test
    fun `deletions travel both ways`() {
        // Deleted on this phone: it was synced, the server still has it.
        val deletedHere = ClipboardSync.plan(emptyList(), listOf(remote(clip("a", 10))), synced = setOf("a"))
        assertEquals(setOf("a"), deletedHere.deleteRemote)
        assertEquals(emptySet<String>(), ClipboardSync.syncedAfter(emptyList(), deletedHere))

        // Deleted on another phone: a tombstone newer than the local copy.
        val a = clip("a", 10)
        val deletedThere = ClipboardSync.plan(listOf(a), listOf(Remote("a", null, deleted = true, updatedAt = 50)), synced = setOf("a"))
        assertEquals(setOf("a"), deletedThere.deleteLocal)

        // Copied again after the deletion: the local copy is newer and comes back.
        val copiedAgain = ClipboardSync.plan(listOf(clip("a", 60)), listOf(Remote("a", null, deleted = true, updatedAt = 50)), synced = emptySet())
        assertEquals(listOf(clip("a", 60)), copiedAgain.upload)
    }
}
