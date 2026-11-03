package com.theemrock.smoothmedia.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSuggestionPolicyTest {

    @Test
    fun requestsPackWhenTwoOrFewerUnplayedAndNoneCurrentlyPending() {
        assertFalse(SmartSuggestionPolicy.shouldRequestPack(remainingUnplayed = 3, packPending = false))
        assertTrue(SmartSuggestionPolicy.shouldRequestPack(remainingUnplayed = 2, packPending = false))
        assertTrue(SmartSuggestionPolicy.shouldRequestPack(remainingUnplayed = 0, packPending = false))
    }

    @Test
    fun doesNotRequestTwiceWhilePackIsPending() {
        assertFalse(SmartSuggestionPolicy.shouldRequestPack(remainingUnplayed = 1, packPending = true))
    }

    @Test
    fun packSizeIsFive() {
        assertEquals(5, SmartSuggestionPolicy.PACK_SIZE)
    }

    @Test
    fun accidentalSkipCountsNothing() {
        val signal = ListenScoring.classifySkip(playedMs = 1_000, durationMs = 200_000)
        assertEquals(ListenSignal.ACCIDENTAL_SKIP, signal)
        assertEquals(0, ListenScoring.weight(signal))
    }

    @Test
    fun earlySkipIsNegativeButSmall() {
        val signal = ListenScoring.classifySkip(playedMs = 20_000, durationMs = 200_000)
        assertEquals(ListenSignal.EARLY_SKIP, signal)
        assertEquals(-1, ListenScoring.weight(signal))
    }

    @Test
    fun listeningPastThirtyPercentIsCompleted() {
        val signal = ListenScoring.classifySkip(playedMs = 90_000, durationMs = 200_000)
        assertEquals(ListenSignal.COMPLETED, signal)
    }

    @Test
    fun manualChoiceOutweighsAutomaticCompletion() {
        assertTrue(ListenScoring.weight(ListenSignal.MANUAL_PLAY) > ListenScoring.weight(ListenSignal.COMPLETED))
    }
}
