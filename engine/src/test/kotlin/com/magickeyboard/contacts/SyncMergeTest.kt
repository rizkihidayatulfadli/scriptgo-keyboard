package com.magickeyboard.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncMergeTest {
    @Test
    fun `remote applies when missing locally or newer`() {
        assertTrue(SyncMerge.shouldApplyRemote(null, 5))
        assertTrue(SyncMerge.shouldApplyRemote(SyncMeta(4, dirty = true), 5))
    }

    @Test
    fun `newer local edits are kept`() {
        assertFalse(SyncMerge.shouldApplyRemote(SyncMeta(6, dirty = true), 5))
        assertFalse(SyncMerge.shouldApplyRemote(SyncMeta(6, dirty = false), 5))
    }

    @Test
    fun `ties keep unsynced local edits`() {
        assertFalse(SyncMerge.shouldApplyRemote(SyncMeta(5, dirty = true), 5))
        assertTrue(SyncMerge.shouldApplyRemote(SyncMeta(5, dirty = false), 5))
    }

    @Test
    fun `edit timestamps always move forward`() {
        assertEquals(100, SyncMerge.nextUpdatedAt(null, 100))
        assertEquals(100, SyncMerge.nextUpdatedAt(50, 100))
        assertEquals(201, SyncMerge.nextUpdatedAt(200, 100))
    }
}
