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

    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS lyrics_cache (
                    trackId TEXT NOT NULL PRIMARY KEY,
                    mode TEXT NOT NULL,
                    text TEXT NOT NULL,
                    provenance TEXT NOT NULL,
                    source TEXT,
                    updatedAtEpochMs INTEGER NOT NULL,
                    FOREIGN KEY(trackId) REFERENCES tracks(track_id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    val FROM_4_TO_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS saf_documents (sourceId TEXT NOT NULL, documentId TEXT NOT NULL, parentDocumentId TEXT, displayName TEXT NOT NULL, isDirectory INTEGER NOT NULL, available INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceId, documentId), FOREIGN KEY(sourceId) REFERENCES library_sources(source_id) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saf_documents_sourceId_parentDocumentId ON saf_documents(sourceId, parentDocumentId)")
        }
    }

    val FROM_5_TO_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE saf_documents ADD COLUMN uri TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saf_documents_sourceId_uri ON saf_documents(sourceId, uri)")
        }
    }

    val FROM_6_TO_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS genre_nodes (genreId TEXT NOT NULL, displayName TEXT NOT NULL, normalizedName TEXT NOT NULL, provenance TEXT NOT NULL, confidence REAL, userOverride INTEGER NOT NULL, PRIMARY KEY(genreId))")
            db.execSQL("CREATE TABLE IF NOT EXISTS genre_edges (parentGenreId TEXT NOT NULL, childGenreId TEXT NOT NULL, PRIMARY KEY(parentGenreId, childGenreId), FOREIGN KEY(parentGenreId) REFERENCES genre_nodes(genreId) ON DELETE CASCADE, FOREIGN KEY(childGenreId) REFERENCES genre_nodes(genreId) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_genre_edges_childGenreId ON genre_edges(childGenreId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS track_genre_assignments (trackId TEXT NOT NULL, genreId TEXT NOT NULL, isPrimary INTEGER NOT NULL, provenance TEXT NOT NULL, confidence REAL, userOverride INTEGER NOT NULL, PRIMARY KEY(trackId, genreId), FOREIGN KEY(trackId) REFERENCES tracks(track_id) ON DELETE CASCADE, FOREIGN KEY(genreId) REFERENCES genre_nodes(genreId) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_track_genre_assignments_genreId ON track_genre_assignments(genreId)")
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2, FROM_2_TO_3, FROM_3_TO_4, FROM_4_TO_5, FROM_5_TO_6, FROM_6_TO_7)
}
