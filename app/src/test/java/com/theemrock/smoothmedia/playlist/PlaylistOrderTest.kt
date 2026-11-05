package com.theemrock.smoothmedia.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {
    @Test
    fun requestedOrderComesFirstAndTheRestKeepsItsOrder() {
        assertEquals(
            listOf("c", "a", "b", "d"),
            mergePlaylistOrder(existing = listOf("a", "b", "c", "d"), requested = listOf("c", "a")),
        )
    }

    @Test
    fun unknownAndDuplicateIdsAreIgnored() {
        assertEquals(
            listOf("b", "a"),
            mergePlaylistOrder(existing = listOf("a", "b"), requested = listOf("x", "b", "b", "a")),
        )
    }

    @Test
    fun emptyRequestKeepsTheExistingOrder() {
        assertEquals(listOf("a", "b"), mergePlaylistOrder(listOf("a", "b"), emptyList()))
    }
}
