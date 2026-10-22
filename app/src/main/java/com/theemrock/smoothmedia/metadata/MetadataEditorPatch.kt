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
    val touched: Set<MetadataEditorField> = emptySet(),
    val mixed: Set<MetadataEditorField> = emptySet(),
    val missing: Set<MetadataEditorField> = emptySet(),
) {
    fun set(field: MetadataEditorField, value: String): MetadataEditorState = copy(
        values = values + (field to value),
        cleared = cleared - field,
        touched = touched + field,
        mixed = mixed - field,
        missing = missing - field,
    )

    fun clear(field: MetadataEditorField): MetadataEditorState = copy(
        values = values - field,
        cleared = cleared + field,
        touched = touched + field,
        mixed = mixed - field,
        missing = missing - field,
    )

    /** Displays only fields present in the source snapshot; this never marks fields dirty. */
    fun prefill(snapshot: FileTagSnapshot): MetadataEditorState = prefill(listOf(snapshot))

    /** Displays common values and explicit mixed/missing states without making them dirty. */
    fun prefill(snapshots: List<FileTagSnapshot>): MetadataEditorState {
        if (snapshots.isEmpty()) return this
        val snapshotValues = linkedMapOf<MetadataEditorField, String>()
        val snapshotMixed = linkedSetOf<MetadataEditorField>()
        val snapshotMissing = linkedSetOf<MetadataEditorField>()
        MetadataEditorField.values().forEach { field ->
            val fieldValues = snapshots.map { snapshot ->
                if (field == MetadataEditorField.GENRE) {
                    snapshot.properties[field.propertyKey]?.joinToString(", ")
                } else {
                    snapshot.properties[field.propertyKey]?.firstOrNull()
                }
            }
            val distinct = fieldValues.distinct()
            when {
                fieldValues.all { it == null } -> snapshotMissing += field
                fieldValues.size == 1 || (fieldValues.all { it != null } && distinct.size == 1) ->
                    snapshotValues[field] = fieldValues.first().orEmpty()
                else -> snapshotMixed += field
            }
        }
        return copy(
            values = snapshotValues + values.filterKeys { it in touched },
            cleared = cleared.intersect(touched),
            mixed = snapshotMixed - touched,
            missing = snapshotMissing - touched,
        )
    }

    val isDirty: Boolean
        get() = touched.isNotEmpty()

    fun toFileTagEdit(): FileTagEdit {
        fun value(field: MetadataEditorField): String? = values[field].takeIf { field in touched }
        return FileTagEdit(
            title = value(MetadataEditorField.TITLE),
            artist = value(MetadataEditorField.ARTIST),
            album = value(MetadataEditorField.ALBUM),
            albumArtist = value(MetadataEditorField.ALBUM_ARTIST),
            genres = value(MetadataEditorField.GENRE)?.split(',')?.map(String::trim),
            trackNumber = value(MetadataEditorField.TRACK_NUMBER),
            discNumber = value(MetadataEditorField.DISC_NUMBER),
            year = value(MetadataEditorField.YEAR),
            comment = value(MetadataEditorField.COMMENT),
            clearFields = cleared.mapTo(linkedSetOf()) { it.propertyKey },
        )
    }
}
