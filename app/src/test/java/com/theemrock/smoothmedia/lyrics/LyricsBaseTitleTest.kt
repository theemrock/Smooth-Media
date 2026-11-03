package com.theemrock.smoothmedia.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsBaseTitleTest {
    @Test
    fun removesRemixSuffixInParentheses() {
        assertEquals("Human After All", LyricsFetchUseCase.baseTitle("Human After All (Alter Ego Remix)"))
    }

    @Test
    fun removesBracketedSuffix() {
        assertEquals("Song", LyricsFetchUseCase.baseTitle("Song [Live]"))
    }

    @Test
    fun returnsNullWhenTitleHasNoSuffix() {
        assertNull(LyricsFetchUseCase.baseTitle("Plain Title"))
    }

    @Test
    fun returnsNullWhenOnlySuffixRemains() {
        assertNull(LyricsFetchUseCase.baseTitle("(Intro)"))
    }
}
