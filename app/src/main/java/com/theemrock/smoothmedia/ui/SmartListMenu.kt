package com.theemrock.smoothmedia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntity
import com.theemrock.smoothmedia.library.AutoGenreList

/** Key prefix for user smart playlists; their ids follow the prefix. */
const val SMART_KEY_PREFIX = "smart:"

/**
 * Smart playlists shown in the hamburger menu. MainActivity fills [lists] and the callbacks;
 * the menu only reads them. Process-wide because the menu lives in many screens.
 */
internal object SmartListMenu {
    var lists by mutableStateOf<List<AutoGenreList>>(emptyList())
    var onOpen: (AutoGenreList) -> Unit = {}
    var onCreate: () -> Unit = {}
    var onEdit: (AutoGenreList) -> Unit = {}
}

/** Manual playlists shown under "Playlistler" in the hamburger menu. */
internal object PlaylistMenu {
    var playlists by mutableStateOf<List<PlaylistEntity>>(emptyList())
    var onCreate: () -> Unit = {}
}
