package com.theemrock.smoothmedia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun mainMenu_keepsLibraryOrderAndMovesSourcesBehindSettings() {
        assertEquals(
            listOf(
                MainMenuDestination.DASHBOARD,
                MainMenuDestination.SONGS,
                MainMenuDestination.ALBUMS,
                MainMenuDestination.ARTISTS,
                MainMenuDestination.GENRES,
                MainMenuDestination.FOLDERS,
                MainMenuDestination.SETTINGS,
            ),
            mainMenuItems.map { it.destination },
        )
        assertFalse(mainMenuItems.any { it.label == "Kaynaklar" })
    }

    @Test
    fun settingsPath_leadsToMusicSources() {
        assertEquals(
            SettingsDestination.MUSIC_SOURCES,
            SettingsDestination.LIBRARY_MANAGEMENT.musicSourcesDestination,
        )
    }
}
