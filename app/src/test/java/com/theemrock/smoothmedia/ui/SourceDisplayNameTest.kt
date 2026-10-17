package com.theemrock.smoothmedia.ui

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SourceDisplayNameTest {
    @Test
    fun `provider display name is preferred over technical uri`() {
        val source = source(volumeName = "Music")
        assertEquals("Music", sourceDisplayName(source))
        assertFalse(sourceDisplayName(source).contains("content://"))
    }

    @Test
    fun `missing provider name uses readable identity fallback`() {
        val source = source(volumeName = null)
        assertEquals("SAF kaynağı · ce-123", sourceDisplayName(source))
        assertFalse(sourceDisplayName(source).contains("content://"))
    }

    private fun source(volumeName: String?) = LibrarySourceEntity(
        sourceId = "source-123",
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://provider/tree/primary%3AMusic",
        volumeName = volumeName,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        selectionState = SourceSelectionState.ACTIVE,
    )
}
