package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartRuleDraftTest {
    @Test
    fun blankRowsAreIgnoredAndGenreIsLowercased() {
        val rule = SmartRuleDrafts.toRule(
            listOf(
                SmartRuleDraft(SmartRuleKind.GENRE, "  Metal "),
                SmartRuleDraft(SmartRuleKind.ARTIST, "   "),
                SmartRuleDraft(SmartRuleKind.TITLE, "live"),
            ),
        )
        assertEquals(SmartRule.All(listOf(SmartRule.Genre("metal"), SmartRule.TitleContains("live"))), rule)
    }

    @Test
    fun allBlankRowsGiveNoRule() {
        assertNull(SmartRuleDrafts.toRule(listOf(SmartRuleDraft(SmartRuleKind.ALBUM, " "))))
    }

    @Test
    fun flatRuleRoundTripsThroughDrafts() {
        val drafts = listOf(
            SmartRuleDraft(SmartRuleKind.ARTIST, "Yol"),
            SmartRuleDraft(SmartRuleKind.ALBUM, "Live"),
        )
        assertEquals(drafts, SmartRuleDrafts.fromRule(SmartRuleDrafts.toRule(drafts)!!))
    }

    @Test
    fun nestedRuleIsNotEditable() {
        assertNull(SmartRuleDrafts.fromRule(SmartRule.Not(SmartRule.ArtistIs("Yol"))))
        assertNull(SmartRuleDrafts.fromRule(SmartRule.All(listOf(SmartRule.Any(emptyList())))))
    }
}
