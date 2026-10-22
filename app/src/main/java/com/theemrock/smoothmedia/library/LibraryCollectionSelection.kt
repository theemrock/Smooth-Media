package com.theemrock.smoothmedia.library

fun toggleCollectionSelection(selectedKeys: Set<String>, key: String): Set<String> =
    if (key in selectedKeys) selectedKeys - key else selectedKeys + key

fun selectedCollectionRows(
    rows: List<LibraryCollectionRow>,
    selectedKeys: Set<String>,
): List<LibraryCollectionRow> = rows.filter { it.key in selectedKeys }

fun deduplicateLibrarySongs(pages: Iterable<List<LibrarySong>>): List<LibrarySong> {
    val songsById = linkedMapOf<String, LibrarySong>()
    pages.forEach { page -> page.forEach { song -> songsById.putIfAbsent(song.trackId, song) } }
    return songsById.values.toList()
}
