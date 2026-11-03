package com.theemrock.smoothmedia.metadata

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * One-time repair of mojibake already stored in track_metadata (normalized columns only).
 * Files and user overrides are not touched; the same repair already runs on every new read.
 */
object StoredTextRepair {
    const val PREFS_NAME = "smooth_media_repairs"
    const val PREF_KEY = "stored_text_repair_v7"

    /** Returns the number of rows changed. */
    fun runOnce(db: SupportSQLiteDatabase): Int {
        var changed = 0
        db.beginTransaction()
        try {
            db.query(
                SimpleSQLiteQuery(
                    "SELECT trackId, normalizedTitle, normalizedArtist, normalizedAlbum, normalizedAlbumArtist, normalizedGenres FROM track_metadata",
                ),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val trackId = cursor.getString(0)
                    val original = (1..5).map { if (cursor.isNull(it)) null else cursor.getString(it) }
                    val repaired = original.map { it?.let(TagTextDecoder::normalizeText) }
                    if (repaired != original) {
                        db.execSQL(
                            "UPDATE track_metadata SET normalizedTitle = ?, normalizedArtist = ?, normalizedAlbum = ?, " +
                                "normalizedAlbumArtist = ?, normalizedGenres = ? WHERE trackId = ?",
                            arrayOf(repaired[0], repaired[1], repaired[2], repaired[3], repaired[4], trackId),
                        )
                        changed++
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return changed
    }
}
