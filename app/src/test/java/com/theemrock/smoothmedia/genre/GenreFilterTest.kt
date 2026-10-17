package com.theemrock.smoothmedia.genre

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreFilterTest {
    @Test
    fun `and or not filter evaluates multi-parent assignments`() {
        val assignments = mapOf(
            "track-1" to setOf("rock", "metal"),
            "track-2" to setOf("rock"),
            "track-3" to setOf("jazz"),
        )
        val query = GenreQuery.And(
            listOf(GenreQuery.Or(listOf(GenreQuery.Term("rock"), GenreQuery.Term("jazz"))), GenreQuery.Not(GenreQuery.Term("metal"))),
        )
        assertEquals(listOf("track-2", "track-3"), assignments.filterValues { query.matches(it) }.keys.toList())
    }
}
