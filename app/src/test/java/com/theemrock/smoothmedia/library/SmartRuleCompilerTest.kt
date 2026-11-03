package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRuleCompilerTest {

    @Test
    fun genreWithDescendantsUsesRecursiveScopeAndOneArg() {
        val compiled = SmartRuleCompiler.compile(SmartRule.Genre("rock"))
        assertTrue(compiled.sql.contains("WITH RECURSIVE sr_scope"))
        assertTrue(compiled.sql.contains("sr_a.genreId IN ("))
        assertEquals(listOf<Any?>("rock"), compiled.args)
    }

    @Test
    fun genreWithoutDescendantsIsExactMatch() {
        val compiled = SmartRuleCompiler.compile(SmartRule.Genre("rock", includeDescendants = false))
        assertTrue(compiled.sql.contains("sr_a.genreId = ?"))
        assertTrue(!compiled.sql.contains("WITH RECURSIVE"))
        assertEquals(listOf<Any?>("rock"), compiled.args)
    }

    @Test
    fun argsFollowSqlOrderAcrossNestedGroups() {
        val rule = SmartRule.All(
            listOf(
                SmartRule.Any(listOf(SmartRule.Genre("rock"), SmartRule.Genre("jazz"))),
                SmartRule.Not(SmartRule.ArtistIs("Queen")),
                SmartRule.TitleContains("love"),
            ),
        )
        val compiled = SmartRuleCompiler.compile(rule)
        assertEquals(listOf<Any?>("rock", "jazz", "Queen", "love"), compiled.args)
        assertEquals(compiled.args.size, compiled.sql.count { it == '?' })
    }

    @Test
    fun emptyGroupsHaveFixedMeaning() {
        assertEquals("1 = 1", SmartRuleCompiler.compile(SmartRule.All(emptyList())).sql)
        assertEquals("0 = 1", SmartRuleCompiler.compile(SmartRule.Any(emptyList())).sql)
    }

    @Test
    fun noGenreIsNotExistsOverAssignments() {
        val compiled = SmartRuleCompiler.compile(SmartRule.NoGenre)
        assertTrue(compiled.sql.startsWith("NOT EXISTS"))
        assertTrue(compiled.args.isEmpty())
    }
}
