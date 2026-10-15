package com.theemrock.smoothmedia.catalog.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CatalogMigrations {
    val FROM_1_TO_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE library_sources " +
                    "ADD COLUMN selection_state TEXT NOT NULL DEFAULT 'ACTIVE'",
            )
        }
    }

    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS track_metadata (
                    trackId TEXT NOT NULL PRIMARY KEY,
                    rawTitle TEXT,
                    rawArtist TEXT,
                    rawAlbum TEXT,
                    rawAlbumArtist TEXT,
                    rawGenre TEXT,
                    rawTrackNumber TEXT,
                    rawDiscNumber TEXT,
                    rawYear TEXT,
                    rawDurationMs TEXT,
                    normalizedTitle TEXT,
                    normalizedTitleStatus TEXT NOT NULL,
                    normalizedArtist TEXT,
                    normalizedArtistStatus TEXT NOT NULL,
                    normalizedAlbum TEXT,
                    normalizedAlbumStatus TEXT NOT NULL,
                    normalizedAlbumArtist TEXT,
                    normalizedAlbumArtistStatus TEXT NOT NULL,
                    normalizedGenres TEXT,
                    normalizedGenresStatus TEXT NOT NULL,
                    normalizedTrackNumber INTEGER,
                    normalizedTrackNumberStatus TEXT NOT NULL,
                    normalizedDiscNumber INTEGER,
                    normalizedDiscNumberStatus TEXT NOT NULL,
                    normalizedYear INTEGER,
                    normalizedYearStatus TEXT NOT NULL,
                    normalizedDurationMs INTEGER,
                    normalizedDurationMsStatus TEXT NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    FOREIGN KEY(trackId) REFERENCES tracks(track_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS track_metadata_overrides (
                    trackId TEXT NOT NULL,
                    fieldName TEXT NOT NULL,
                    value TEXT NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(trackId, fieldName),
                    FOREIGN KEY(trackId) REFERENCES tracks(track_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2, FROM_2_TO_3)
}
