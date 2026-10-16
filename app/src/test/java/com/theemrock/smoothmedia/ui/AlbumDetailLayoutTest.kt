package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailLayoutTest {
    @Test
    fun albumWithTracksExposesOnlySupportedPlayAction() {
        assertEquals(listOf("Albümü Çal"), albumDetailActionLabels(hasSongs = true))
        assertEquals(emptyList<String>(), albumDetailActionLabels(hasSongs = false))
    }

    @Test
    fun albumDetailUsesReferenceTabLabels() {
        assertEquals(listOf("Şarkılar", "Detaylar", "Benzer Albümler"), albumDetailTabLabels())
    }
}
