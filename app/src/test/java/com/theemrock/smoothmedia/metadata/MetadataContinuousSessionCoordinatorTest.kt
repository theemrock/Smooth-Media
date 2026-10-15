package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataContinuousSessionCoordinatorTest {
    @Test
    fun processesMultiplePagesAndRequeriesMissingCountAfterEachPage() {
        val remaining = (1..12).map { "track-$it" }.toCollection(linkedSetOf())
        val missingQueries = mutableListOf<Int>()
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { after, limit ->
                    remaining.filter { after == null || it > after }.take(limit)
                },
                batch = MetadataBatchCoordinator { id ->
                    remaining.remove(id)
                    MetadataEnrichmentResult.Updated(id, "location-$id")
                },
            ),
            countMissing = {
                missingQueries += remaining.size
                remaining.size
            },
        )

        val result = coordinator.runSession()

        assertEquals(MetadataSessionStatus.COMPLETED, result.status)
        assertEquals(2, result.progress.pages)
        assertEquals(12, result.progress.processed)
        assertEquals(12, result.progress.succeeded)
        assertEquals(listOf(2, 0), missingQueries)
    }

    @Test
    fun failureOnlyPagesAdvanceAndDoNotRetryFailedTracks() {
        val calls = mutableListOf<String>()
        val ids = (1..12).map { "failed-%02d".format(it) }
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { after, limit ->
                    ids.filter { after == null || it > after }.take(limit)
                },
                batch = MetadataBatchCoordinator { id ->
                    calls += id
                    MetadataEnrichmentResult.ReadFailed(id, listOf(MetadataReadFailure.READ_FAILED))
                },
            ),
            countMissing = { ids.size },
        )

        val result = coordinator.runSession()

        assertEquals(MetadataSessionStatus.COMPLETED, result.status)
        assertEquals(ids, calls)
        assertEquals(12, result.progress.failed)
        assertEquals(2, result.progress.pages)
    }

    @Test
    fun stopAfterPagePreventsStartingAnotherTrack() {
        val started = mutableListOf<String>()
        var stopRequested = false
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { after, limit ->
                    listOf("a", "b", "c").filter { after == null || it > after }.take(limit)
                },
                batch = MetadataBatchCoordinator { id ->
                    started += id
                    MetadataEnrichmentResult.Updated(id, "location-$id")
                },
            ),
            countMissing = { 3 },
        )

        val result = coordinator.runSession(
            cancellationRequested = { stopRequested },
            onPage = { stopRequested = true },
        )

        assertEquals(MetadataSessionStatus.STOPPED, result.status)
        assertEquals(listOf("a", "b", "c"), started)
        assertEquals(1, result.progress.pages)
    }

    @Test
    fun errorStopsSessionAndKeepsCompletedPageCounters() {
        var pageCalls = 0
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { _, _ ->
                    pageCalls++
                    if (pageCalls == 1) listOf("a") else error("db unavailable")
                },
                batch = MetadataBatchCoordinator { id ->
                    MetadataEnrichmentResult.Updated(id, "location-$id")
                },
            ),
            countMissing = { 1 },
        )

        val result = coordinator.runSession()

        assertEquals(MetadataSessionStatus.ERROR, result.status)
        assertEquals(1, result.progress.pages)
        assertEquals(1, result.progress.succeeded)
        assertTrue(result.errorMessage!!.contains("db unavailable"))
    }

    @Test
    fun a_new_session_starts_without_the_previous_session_cursor() {
        val started = mutableListOf<String>()
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { after, _ ->
                    if (after == null) listOf("failed") else emptyList()
                },
                batch = MetadataBatchCoordinator { id ->
                    started += id
                    MetadataEnrichmentResult.ReadFailed(id, listOf(MetadataReadFailure.READ_FAILED))
                },
            ),
            countMissing = { 1 },
        )

        coordinator.runSession()
        coordinator.runSession()

        assertEquals(listOf("failed", "failed"), started)
    }

    @Test
    fun missing_count_error_returns_error_without_losing_page_counters() {
        val coordinator = MetadataContinuousSessionCoordinator(
            page = MetadataMissingTrackPageCoordinator(
                listMissingTrackIds = { _, _ -> listOf("a") },
                batch = MetadataBatchCoordinator { id ->
                    MetadataEnrichmentResult.Updated(id, "location-$id")
                },
            ),
            countMissing = { error("count unavailable") },
        )

        val result = coordinator.runSession()

        assertEquals(MetadataSessionStatus.ERROR, result.status)
        assertEquals(1, result.progress.processed)
        assertTrue(result.errorMessage!!.contains("count unavailable"))
    }
}
