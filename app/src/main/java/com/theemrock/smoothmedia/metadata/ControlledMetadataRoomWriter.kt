package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase

/** Atomic Room writer used only after ControlledMetadataApplyGate validation. */
class ControlledMetadataRoomWriter(
    private val database: CatalogDatabase,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun writeAtomically(changes: List<ControlledMetadataTitleChange>): Boolean {
        if (changes.isEmpty()) return true
        return runCatching {
            database.runInTransaction {
                changes.forEach { change ->
                    val updated = database.trackMetadataDao().updateReviewedTitle(
                        trackId = change.trackId,
                        sourceId = change.sourceId,
                        locationId = change.locationId,
                        oldTitle = change.oldTitle,
                        newTitle = change.newTitle,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    check(updated == 1) { "Controlled metadata update precondition failed" }
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * General field-level writer. The DAO predicates re-check identity scope,
     * source/location availability and user overrides inside the same transaction.
     */
    fun writeFieldsAtomically(changes: List<ControlledMetadataFieldChange>): Boolean {
        if (changes.isEmpty()) return true
        return runCatching {
            database.runInTransaction {
                changes.forEach { change ->
                    val updated = when (change.field) {
                        ControlledMetadataField.TITLE -> database.trackMetadataDao().updateReviewedTitle(
                            trackId = change.trackId,
                            sourceId = change.sourceId,
                            locationId = change.locationId,
                            oldTitle = change.oldValue,
                            newTitle = change.newValue,
                            updatedAtEpochMs = nowEpochMs(),
                        )
                        ControlledMetadataField.ARTIST -> database.trackMetadataDao().updateReviewedArtist(
                            change.trackId, change.sourceId, change.locationId, change.oldValue, change.newValue, nowEpochMs(),
                        )
                        ControlledMetadataField.ALBUM -> database.trackMetadataDao().updateReviewedAlbum(
                            change.trackId, change.sourceId, change.locationId, change.oldValue, change.newValue, nowEpochMs(),
                        )
                        ControlledMetadataField.ALBUM_ARTIST -> database.trackMetadataDao().updateReviewedAlbumArtist(
                            change.trackId, change.sourceId, change.locationId, change.oldValue, change.newValue, nowEpochMs(),
                        )
                        ControlledMetadataField.GENRE -> database.trackMetadataDao().updateReviewedGenre(
                            change.trackId, change.sourceId, change.locationId, change.oldValue, change.newValue, nowEpochMs(),
                        )
                    }
                    check(updated == 1) { "Controlled metadata field update precondition failed" }
                }
            }
            true
        }.getOrDefault(false)
    }
}
