package com.theemrock.smoothmedia.ui

data class MainNavigationState(
    val showLibrary: Boolean = false,
    val showCollections: Boolean = false,
    val showSearch: Boolean = false,
    val showNowPlaying: Boolean = false,
    val showEqualizer: Boolean = false,
    val showQueue: Boolean = false,
    val filteredCollection: Boolean = false,
)

object MainNavigationBack {
    fun handle(state: MainNavigationState): MainNavigationState? = when {
        state.showQueue -> state.copy(showQueue = false)
        state.showEqualizer -> state.copy(showEqualizer = false)
        state.showNowPlaying -> state.copy(showNowPlaying = false)
        state.showSearch -> state.copy(showSearch = false)
        state.filteredCollection -> state.copy(showLibrary = false, showCollections = true, filteredCollection = false)
        state.showCollections -> state.copy(showCollections = false)
        state.showLibrary -> state.copy(showLibrary = false)
        else -> null
    }
}
