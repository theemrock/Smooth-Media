package com.theemrock.smoothmedia.metadata

enum class MetadataEditorField(val propertyKey: String) {
    TITLE("TITLE"),
    ARTIST("ARTIST"),
    ALBUM("ALBUM"),
    ALBUM_ARTIST("ALBUMARTIST"),
    GENRE("GENRE"),
    TRACK_NUMBER("TRACKNUMBER"),
    DISC_NUMBER("DISCNUMBER"),
    YEAR("DATE"),
    COMMENT("COMMENT"),
}

data class MetadataEditorState(
    val selectedTrackIds: List<String>,
    val values: Map<MetadataEditorField, String> = emptyMap(),
    val cleared: Set<MetadataEditorField> = emptySet(),
) {
    fun set(field: MetadataEditorField, value: String): MetadataEditorState = copy(
        values = values + (field to value),
        cleared = cleared - field,
    )

    fun clear(field: MetadataEditorField): MetadataEditorState = copy(
        values = values - field,
        cleared = cleared + field,
    )

    fun toFileTagEdit(): FileTagEdit = FileTagEdit(
        title = values[MetadataEditorField.TITLE],
        artist = values[MetadataEditorField.ARTIST],
        album = values[MetadataEditorField.ALBUM],
        albumArtist = values[MetadataEditorField.ALBUM_ARTIST],
        genres = values[MetadataEditorField.GENRE]?.split(',')?.map(String::trim),
        trackNumber = values[MetadataEditorField.TRACK_NUMBER],
        discNumber = values[MetadataEditorField.DISC_NUMBER],
        year = values[MetadataEditorField.YEAR],
        comment = values[MetadataEditorField.COMMENT],
        clearFields = cleared.mapTo(linkedSetOf()) { it.propertyKey },
    )
}
