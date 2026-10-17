package com.theemrock.smoothmedia.genre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreHierarchyTest {
    @Test
    fun supportsMultipleParentsAndDeterministicDescendants() {
        val result = GenreHierarchy.build(listOf(
            GenreNode("rock"),
            GenreNode("metal", setOf("rock")),
            GenreNode("prog-metal", setOf("metal", "progressive")),
            GenreNode("progressive"),
        ))

        val graph = (result as GenreBuildResult.Valid).value
        assertEquals(listOf("metal", "prog-metal"), graph.descendantsOf("rock"))
        assertEquals(listOf("metal", "progressive"), graph.parentsOf("prog-metal"))
    }

    @Test
    fun rejectsCyclesAndMissingParents() {
        val cycle = GenreHierarchy.build(listOf(GenreNode("a", setOf("b")), GenreNode("b", setOf("a"))))
        val missing = GenreHierarchy.build(listOf(GenreNode("child", setOf("missing"))))

        assertTrue(cycle is GenreBuildResult.Invalid)
        assertTrue(missing is GenreBuildResult.Invalid)
    }
}
