package com.theemrock.smoothmedia.library

import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceEntity
import com.theemrock.smoothmedia.catalog.database.entity.LibrarySourceType
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceTrackCountSnapshotTest {
    @Test
    fun refresh_keepsSourceCountsSeparateFromUniqueTrackCount() {
        val sourceA = source("source-a")
        val sourceB = source("source-b")

        val snapshot = SourceTrackCountSnapshot.load(
            sources = listOf(sourceA, sourceB),
            countTracksForSource = { sourceId ->
                when (sourceId) {
                    "source-a" -> 2
                    "source-b" -> 1
                    else -> error("unexpected source")
                }
            },
            countUniqueActiveTracks = { 2 },
        )

        assertEquals(mapOf("source-a" to 2, "source-b" to 1), snapshot.bySource)
        assertEquals(2, snapshot.uniqueActiveTrackCount)
    }

    private fun source(id: String) = LibrarySourceEntity(
        sourceId = id,
        sourceType = LibrarySourceType.SAF_TREE,
        rootUri = "content://tree/$id",
        volumeName = null,
        persistedPermissionFlags = 1,
        permissionState = SourcePermissionState.GRANTED,
        availability = SourceAvailability.AVAILABLE,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
        selectionState = SourceSelectionState.ACTIVE,
    )
}
