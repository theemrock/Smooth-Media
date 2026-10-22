package com.theemrock.smoothmedia.library

/** Pure selection state keyed only by stable catalog track IDs. */
data class LibrarySongSelection(
    val trackIds: List<String> = emptyList(),
) {
    fun toggle(trackId: String): LibrarySongSelection {
        if (trackId.isBlank()) return this
        return if (trackId in trackIds) {
            copy(trackIds = trackIds - trackId)
        } else {
            copy(trackIds = trackIds + trackId)
        }
    }

    fun clear(): LibrarySongSelection = copy(trackIds = emptyList())

    fun reconcile(knownTrackIds: Set<String>): LibrarySongSelection =
        copy(trackIds = trackIds.filter { it in knownTrackIds })
}

/** Maps the stable selection order to the currently loaded page(s) only. */
fun selectedLibrarySongs(
    songs: List<LibrarySong>,
    selectedTrackIds: List<String>,
): List<LibrarySong> {
    val byId = linkedMapOf<String, LibrarySong>()
    songs.forEach { song -> byId.putIfAbsent(song.trackId, song) }
    return selectedTrackIds.mapNotNull(byId::get)
}
