package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMissingTrackPageCoordinatorTest {
    @Test
    fun selectsStableBoundedPageAndReturnsExplicitCursor() {
        val calls = mutableListOf<Pair<String?, Int>>()
        val coordinator = coordinator { after, limit ->
            calls += after to limit
            listOf("track-a", "track-b", "track-c").filter { after == null || it > after }.take(limit)
        }

        val result = coordinator.runPage(pageSize = 2)

        assertEquals(listOf("track-a", "track-b"), result.batch.outcomes.map { it.trackId })
        assertEquals("track-b", result.nextCursor)
        assertEquals(listOf(null to 2), calls)
    }

    @Test
    fun cancellationDoesNotStartNewTrackAndDoesNotAutoResume() {
        val started = mutableListOf<String>()
        val coordinator = MetadataMissingTrackPageCoordinator(
            listMissingTrackIds = { _, _ -> listOf("track-a", "track-b") },
            batch = MetadataBatchCoordinator { id ->
                started += id
                MetadataEnrichmentResult.Updated(id, "location-$id")
            },
        )

        val result = coordinator.runPage(cancellationRequested = { true })

        assertTrue(result.batch.progress.cancelled)
        assertTrue(started.isEmpty())
        assertEquals(null, result.nextCursor)
    }

    @Test
    fun pageAfterSuccessfulWriteSkipsPreviouslyEnrichedTracks() {
        val remaining = linkedSetOf("track-a", "track-b", "track-c")
        val coordinator = MetadataMissingTrackPageCoordinator(
            listMissingTrackIds = { after, limit ->
                remaining.filter { after == null || it > after }.take(limit)
            },
            batch = MetadataBatchCoordinator { id ->
                remaining.remove(id)
                MetadataEnrichmentResult.Updated(id, "location-$id")
            },
        )

        val first = coordinator.runPage(pageSize = 2)
        val second = coordinator.runPage(afterTrackId = first.nextCursor, pageSize = 2)

        assertEquals(listOf("track-a", "track-b"), first.batch.outcomes.map { it.trackId })
        assertEquals(listOf("track-c"), second.batch.outcomes.map { it.trackId })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPageLargerThanBatchLimit() {
        coordinator { _, _ -> emptyList() }.runPage(pageSize = 11)
    }

    private fun coordinator(
        list: (String?, Int) -> List<String>,
    ) = MetadataMissingTrackPageCoordinator(
        listMissingTrackIds = list,
        batch = MetadataBatchCoordinator { id ->
            MetadataEnrichmentResult.NoAccessibleLocation(id)
        },
    )
}
