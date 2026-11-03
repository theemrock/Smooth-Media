package com.theemrock.smoothmedia.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmartRuleCodecTest {
    @Test
    fun roundTripsNestedRule() {
        val rule = SmartRule.All(
            listOf(
                SmartRule.Genre("rock", includeDescendants = false),
                SmartRule.Not(SmartRule.ArtistIs("Yol")),
                SmartRule.Any(listOf(SmartRule.NoGenre, SmartRule.AlbumIs("Live"))),
                SmartRule.TitleContains("a"),
            ),
        )
        assertEquals(rule, SmartRuleCodec.decode(SmartRuleCodec.encode(rule)))
    }

    @Test
    fun escapesSpecialCharactersInText() {
        val rule = SmartRule.ArtistIs("a,b(c)d\\e")
        assertEquals(rule, SmartRuleCodec.decode(SmartRuleCodec.encode(rule)))
    }

    @Test
    fun emptyGroupsRoundTrip() {
        val rule = SmartRule.Any(listOf(SmartRule.All(emptyList())))
        assertEquals(rule, SmartRuleCodec.decode(SmartRuleCodec.encode(rule)))
    }

    @Test
    fun rejectsMalformedText() {
        assertThrows(IllegalArgumentException::class.java) { SmartRuleCodec.decode("artist(Yol") }
        assertThrows(IllegalArgumentException::class.java) { SmartRuleCodec.decode("bogus()") }
        assertThrows(IllegalArgumentException::class.java) { SmartRuleCodec.decode("genre(rock,2)") }
        assertThrows(IllegalArgumentException::class.java) { SmartRuleCodec.decode("noGenre()x") }
    }
}
