package com.theemrock.smoothmedia.genre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun emptyAndMatchesAndEmptyOrDoesNotMatch() {
        assertEquals(true, GenreQuery.And(emptyList()).matches(emptySet()))
        assertEquals(false, GenreQuery.Or(emptyList()).matches(setOf("rock")))
    }

    @Test
    fun unknownTermIsFalseAndNotUnknownIsTrue() {
        val unknown = GenreQuery.Term("missing")
        assertEquals(false, unknown.matches(setOf("rock")))
        assertEquals(true, GenreQuery.Not(unknown).matches(setOf("rock")))
    }

    @Test
    fun compilesNestedQueryToParameterizedSqlOverActiveTrackUniverse() {
        val compiled = GenreQueryCompiler.compile(
            GenreQuery.And(
                listOf(
                    GenreQuery.Term("rock", includeDescendants = true),
                    GenreQuery.Not(GenreQuery.Term("metal")),
                    GenreQuery.Or(listOf(GenreQuery.Term("jazz"), GenreQuery.Term("blues"))),
                ),
            ),
            limit = 50,
            offset = 10,
        )

        assertEquals(50, compiled.limit)
        assertEquals(10, compiled.offset)
        assertTrue(compiled.sql.contains("catalog_lifecycle = 'ACTIVE'"))
        assertTrue(compiled.sql.contains("WITH RECURSIVE"))
        assertTrue(compiled.sql.contains("NOT EXISTS"))
        assertTrue(compiled.sql.contains("SELECT DISTINCT t.track_id"))
        assertEquals(listOf("rock", "rock", "rock", "metal", "jazz", "blues"), compiled.arguments)
    }
}
