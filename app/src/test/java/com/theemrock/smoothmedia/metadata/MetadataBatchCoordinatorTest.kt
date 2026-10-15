package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataBatchCoordinatorTest {
    @Test
    fun limitsBatchToTenDistinctTracks() {
        val coordinator = MetadataBatchCoordinator { id ->
            MetadataEnrichmentResult.NoAccessibleLocation(id)
        }

        val result = coordinator.run((1..10).map { "track-$it" } + "track-1")

        assertEquals(10, result.progress.total)
        assertEquals(10, result.progress.processed)
        assertEquals(10, result.progress.skipped)
        assertEquals(10, result.outcomes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanTenDistinctTracks() {
        MetadataBatchCoordinator { id -> MetadataEnrichmentResult.NoAccessibleLocation(id) }
            .run((1..11).map { "track-$it" })
    }

    @Test
    fun isolatesFailureAndContinuesFollowingTracks() {
        val coordinator = MetadataBatchCoordinator { id ->
            when (id) {
                "failed" -> MetadataEnrichmentResult.ReadFailed(
                    id,
                    listOf(MetadataReadFailure.READ_FAILED),
                )

                "updated" -> MetadataEnrichmentResult.Updated(id, "location-1")
                else -> MetadataEnrichmentResult.Excluded(id)
            }
        }

        val result = coordinator.run(listOf("failed", "updated", "skipped"))

        assertEquals(
            listOf(
                MetadataBatchStatus.FAILED,
                MetadataBatchStatus.SUCCESS,
                MetadataBatchStatus.SKIPPED,
            ),
            result.outcomes.map { it.status },
        )
        assertEquals(1, result.progress.failed)
        assertEquals(1, result.progress.succeeded)
        assertEquals(1, result.progress.skipped)
    }

    @Test
    fun cancellationStopsBeforeStartingNextTrack() {
        val started = mutableListOf<String>()
        val coordinator = MetadataBatchCoordinator { id ->
            started += id
            MetadataEnrichmentResult.Updated(id, "location-$id")
        }
        var checks = 0

        val result = coordinator.run(listOf("one", "two", "three"), cancellationRequested = {
            checks++
            checks > 1
        })

        assertEquals(listOf("one"), started)
        assertEquals(3, result.progress.total)
        assertEquals(1, result.progress.processed)
        assertTrue(result.progress.cancelled)
    }

    @Test
    fun repeatedBatchUsesSingleTrackContractAndRemainsDeterministic() {
        val calls = mutableListOf<String>()
        val coordinator = MetadataBatchCoordinator { id ->
            calls += id
            MetadataEnrichmentResult.Updated(id, "location-$id")
        }

        val first = coordinator.run(listOf("one", "two"))
        val second = coordinator.run(listOf("one", "two"))

        assertEquals(first.progress.copy(cancelled = false), second.progress)
        assertEquals(2, calls.count { it == "one" })
        assertEquals(2, calls.count { it == "two" })
    }
}
