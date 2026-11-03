package com.theemrock.smoothmedia.catalog.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQL for the materialized track_folder_scopes table.
 *
 * The SELECT body is the exact folder-scope computation previously repeated inside
 * every library query (recursive per-character scan of the locator). It now runs once
 * per track, at migration time and whenever a location or binding is inserted.
 */
internal object TrackFolderScopeSql {
    const val TABLE = "track_folder_scopes"

    const val CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS track_folder_scopes (" +
            "track_id TEXT NOT NULL, source_id TEXT NOT NULL, folder_prefix TEXT NOT NULL, " +
            "PRIMARY KEY(track_id))"

    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_TABLE_SQL)
        db.execSQL("DELETE FROM $TABLE")
        db.execSQL("INSERT INTO $TABLE (track_id, source_id, folder_prefix) ${selectScopes("1 = 1")}")
        installTriggers(db)
    }

    fun installTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS track_folder_scopes_after_location
            AFTER INSERT ON track_locations
            BEGIN
                INSERT OR REPLACE INTO $TABLE (track_id, source_id, folder_prefix)
                ${selectScopes("t.track_id = NEW.track_id")};
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS track_folder_scopes_after_binding
            AFTER INSERT ON locator_bindings
            BEGIN
                INSERT OR REPLACE INTO $TABLE (track_id, source_id, folder_prefix)
                ${selectScopes("t.track_id = (SELECT tl.track_id FROM track_locations AS tl WHERE tl.location_id = NEW.location_id)")};
            END
            """.trimIndent(),
        )
    }

    /** Returns (trackId, sourceId, folderPrefix) rows for tracks matching [trackFilter]. */
    private fun selectScopes(trackFilter: String): String = """
        WITH RECURSIVE
        track_scope AS (
            SELECT t.track_id AS trackId,
                   COALESCE(MIN(l.source_id), 'NO_SOURCE') AS sourceId,
                   COALESCE(MIN(NULLIF(l.path, '')), MIN(b.locator), 'NO_FOLDER') AS locator
            FROM tracks AS t
            LEFT JOIN track_locations AS l ON l.track_id = t.track_id
            LEFT JOIN locator_bindings AS b ON b.location_id = l.location_id
            WHERE $trackFilter
            GROUP BY t.track_id
        ),
        scope_chars(trackId, sourceId, locator, position, lastSlash) AS (
            SELECT trackId, sourceId, locator, 1, 0 FROM track_scope
            UNION ALL
            SELECT trackId, sourceId, locator, position + 1,
                   CASE WHEN substr(locator, position, 1) = '/' OR substr(locator, position, 3) = '%2F'
                        THEN position ELSE lastSlash END
            FROM scope_chars
            WHERE position <= length(locator)
        )
        SELECT trackId, sourceId,
               CASE WHEN MAX(lastSlash) > 0 THEN substr(locator, 1, MAX(lastSlash)) ELSE locator END
        FROM scope_chars
        GROUP BY trackId, sourceId, locator
    """.trimIndent()
}
