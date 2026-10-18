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

    val FROM_7_TO_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_recovery_sessions (session_id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, cursor_track_id TEXT, total_tracks INTEGER NOT NULL, processed_tracks INTEGER NOT NULL, auto_repair_count INTEGER NOT NULL, no_change_count INTEGER NOT NULL, review_required_count INTEGER NOT NULL, unrecoverable_count INTEGER NOT NULL, read_error_count INTEGER NOT NULL, lease_owner TEXT, lease_expires_at_epoch_ms INTEGER, started_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, completed_at_epoch_ms INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_recovery_sessions_status ON metadata_recovery_sessions(status)")
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_recovery_decisions (session_id TEXT NOT NULL, track_id TEXT NOT NULL, field_name TEXT NOT NULL, decision TEXT NOT NULL, evidence_type TEXT NOT NULL, old_value TEXT, new_value TEXT, created_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY(session_id, track_id, field_name), FOREIGN KEY(session_id) REFERENCES metadata_recovery_sessions(session_id) ON DELETE CASCADE, FOREIGN KEY(track_id) REFERENCES tracks(track_id) ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_recovery_decisions_track_id ON metadata_recovery_decisions(track_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_recovery_decisions_decision ON metadata_recovery_decisions(decision)")
        }
    }

    val FROM_8_TO_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_apply_sessions (sessionId TEXT NOT NULL PRIMARY KEY, auditSessionId TEXT NOT NULL, status TEXT NOT NULL, cursorDecisionKey TEXT, totalItems INTEGER NOT NULL, processedItems INTEGER NOT NULL, successCount INTEGER NOT NULL, skippedStaleCount INTEGER NOT NULL, failedCount INTEGER NOT NULL, startedAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, completedAtEpochMs INTEGER, FOREIGN KEY(auditSessionId) REFERENCES metadata_recovery_sessions(session_id) ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_sessions_auditSessionId ON metadata_apply_sessions(auditSessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_sessions_status ON metadata_apply_sessions(status)")
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_apply_items (applySessionId TEXT NOT NULL, decisionSessionId TEXT NOT NULL, trackId TEXT NOT NULL, fieldName TEXT NOT NULL, sourceId TEXT NOT NULL, locationId TEXT NOT NULL, expectedOldValue TEXT, newValue TEXT, evidenceType TEXT NOT NULL, state TEXT NOT NULL, errorMessage TEXT, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(applySessionId, decisionSessionId, trackId, fieldName), FOREIGN KEY(applySessionId) REFERENCES metadata_apply_sessions(sessionId) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_items_applySessionId_state ON metadata_apply_items(applySessionId, state)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_items_trackId ON metadata_apply_items(trackId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_apply_change_log (changeId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, applySessionId TEXT NOT NULL, trackId TEXT NOT NULL, fieldName TEXT NOT NULL, oldValue TEXT, newValue TEXT, evidenceType TEXT NOT NULL, decisionKey TEXT NOT NULL, occurredAtEpochMs INTEGER NOT NULL, result TEXT NOT NULL, FOREIGN KEY(applySessionId) REFERENCES metadata_apply_sessions(sessionId) ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_change_log_applySessionId ON metadata_apply_change_log(applySessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metadata_apply_change_log_trackId_fieldName ON metadata_apply_change_log(trackId, fieldName)")
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_apply_leases (applySessionId TEXT NOT NULL PRIMARY KEY, owner TEXT NOT NULL, expiresAtEpochMs INTEGER NOT NULL, heartbeatAtEpochMs INTEGER NOT NULL, FOREIGN KEY(applySessionId) REFERENCES metadata_apply_sessions(sessionId) ON DELETE CASCADE)")
        }
    }

    val FROM_9_TO_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS artwork_assets (assetId TEXT NOT NULL, contentHash TEXT, sourceType TEXT NOT NULL, sourceLocator TEXT NOT NULL, sourceId TEXT, format TEXT, width INTEGER, height INTEGER, availability TEXT NOT NULL, invalidatedAtEpochMs INTEGER, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(assetId))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_assets_sourceId ON artwork_assets(sourceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_assets_availability ON artwork_assets(availability)")
            db.execSQL("CREATE TABLE IF NOT EXISTS artwork_album_bindings (collectionKey TEXT NOT NULL, assetId TEXT NOT NULL, sourceId TEXT, sourcePriority INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(collectionKey), FOREIGN KEY(assetId) REFERENCES artwork_assets(assetId) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_album_bindings_assetId ON artwork_album_bindings(assetId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_album_bindings_sourceId ON artwork_album_bindings(sourceId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS artwork_overrides (collectionKey TEXT NOT NULL, assetId TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(collectionKey), FOREIGN KEY(assetId) REFERENCES artwork_assets(assetId) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artwork_overrides_assetId ON artwork_overrides(assetId)")
        }
    }

    val ALL: Array<Migration> = arrayOf(FROM_1_TO_2, FROM_2_TO_3, FROM_3_TO_4, FROM_4_TO_5, FROM_5_TO_6, FROM_6_TO_7, FROM_7_TO_8, FROM_8_TO_9, FROM_9_TO_10)
}
