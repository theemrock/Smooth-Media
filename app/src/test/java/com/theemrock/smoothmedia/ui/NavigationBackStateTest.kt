package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationBackStateTest {
    @Test
    fun overlayBackClosesOnlyOverlayAndPreservesLibraryState() {
        val state = MainNavigationState(showLibrary = true, showNowPlaying = true, showSearch = true)
        assertEquals(MainNavigationState(showLibrary = true, showSearch = true), MainNavigationBack.handle(state))
    }

    @Test
    fun backOrderIsQueueThenEqualizerThenNowPlayingThenSearch() {
        val state = MainNavigationState(showLibrary = true, showSearch = true, showNowPlaying = true, showEqualizer = true, showQueue = true)
        val afterQueue = MainNavigationBack.handle(state)!!
        val afterEqualizer = MainNavigationBack.handle(afterQueue)!!
        val afterNowPlaying = MainNavigationBack.handle(afterEqualizer)!!
        val afterSearch = MainNavigationBack.handle(afterNowPlaying)!!
        assertEquals(false, afterQueue.showQueue)
        assertEquals(false, afterEqualizer.showEqualizer)
        assertEquals(false, afterNowPlaying.showNowPlaying)
        assertEquals(MainNavigationState(showLibrary = true), afterSearch)
    }

    @Test
    fun rootBackIsUnhandled() {
        assertNull(MainNavigationBack.handle(MainNavigationState()))
    }
}
