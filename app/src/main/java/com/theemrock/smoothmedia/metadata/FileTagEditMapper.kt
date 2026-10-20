package com.theemrock.smoothmedia.metadata

data class FileTagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genres: List<String>? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val year: String? = null,
    val comment: String? = null,
    /** Explicitly removes the named tag; a blank string remains a present blank value. */
    val clearFields: Set<String> = emptySet(),
)

object FileTagEditMapper {
    fun toChange(
        current: FileTagSnapshot,
        edit: FileTagEdit,
        pictures: List<FileTagPicture>? = null,
    ): FileTagChange {
        val replacements = buildMap {
            edit.title?.let { put("TITLE", listOf(it)) }
            edit.artist?.let { put("ARTIST", listOf(it)) }
            edit.album?.let { put("ALBUM", listOf(it)) }
            edit.albumArtist?.let { put("ALBUMARTIST", listOf(it)) }
            edit.genres?.let { put("GENRE", it) }
            edit.trackNumber?.let { put("TRACKNUMBER", listOf(it)) }
            edit.discNumber?.let { put("DISCNUMBER", listOf(it)) }
            edit.year?.let { put("DATE", listOf(it)) }
            edit.comment?.let { put("COMMENT", listOf(it)) }
        }
        val patched = current.properties + replacements
        return FileTagChange(
            properties = patched - edit.clearFields,
            pictures = pictures,
        )
    }
}
