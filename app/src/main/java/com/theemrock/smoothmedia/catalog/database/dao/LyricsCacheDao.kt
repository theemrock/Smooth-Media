package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.LyricsCacheEntity

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    fun find(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(value: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE trackId = :trackId")
    fun delete(trackId: String): Int

    /** Active tracks with no cached lyrics and no lookup result yet ("new" tracks). */
    @Query(
        """
        SELECT t.track_id AS trackId,
               COALESCE(title_o.value, m.normalizedTitle, '') AS title,
               COALESCE(artist_o.value, m.normalizedArtist, '') AS artist,
               COALESCE(album_o.value, m.normalizedAlbum) AS album,
               COALESCE(CAST(dur_o.value AS INTEGER), m.normalizedDurationMs) AS durationMs
        FROM tracks AS t
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        LEFT JOIN track_metadata_overrides AS title_o ON title_o.trackId = t.track_id AND title_o.fieldName = 'TITLE'
        LEFT JOIN track_metadata_overrides AS artist_o ON artist_o.trackId = t.track_id AND artist_o.fieldName = 'ARTIST'
        LEFT JOIN track_metadata_overrides AS album_o ON album_o.trackId = t.track_id AND album_o.fieldName = 'ALBUM'
        LEFT JOIN track_metadata_overrides AS dur_o ON dur_o.trackId = t.track_id AND dur_o.fieldName = 'DURATION_MS'
        LEFT JOIN lyrics_cache AS lc ON lc.trackId = t.track_id
        LEFT JOIN lyrics_misses AS lm ON lm.trackId = t.track_id
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND lc.trackId IS NULL
          AND lm.trackId IS NULL
          AND trim(COALESCE(title_o.value, m.normalizedTitle, '')) <> ''
        ORDER BY t.track_id
        """,
    )
    fun listUntouchedTracks(): List<LyricsCandidateRow>

    /** Active tracks whose last lookup ended with the given kind (NOT_FOUND, INSTRUMENTAL or ERROR). */
    @Query(
        """
        SELECT t.track_id AS trackId,
               COALESCE(title_o.value, m.normalizedTitle, '') AS title,
               COALESCE(artist_o.value, m.normalizedArtist, '') AS artist,
               COALESCE(album_o.value, m.normalizedAlbum) AS album,
               COALESCE(CAST(dur_o.value AS INTEGER), m.normalizedDurationMs) AS durationMs
        FROM tracks AS t
        LEFT JOIN track_metadata AS m ON m.trackId = t.track_id
        LEFT JOIN track_metadata_overrides AS title_o ON title_o.trackId = t.track_id AND title_o.fieldName = 'TITLE'
        LEFT JOIN track_metadata_overrides AS artist_o ON artist_o.trackId = t.track_id AND artist_o.fieldName = 'ARTIST'
        LEFT JOIN track_metadata_overrides AS album_o ON album_o.trackId = t.track_id AND album_o.fieldName = 'ALBUM'
        LEFT JOIN track_metadata_overrides AS dur_o ON dur_o.trackId = t.track_id AND dur_o.fieldName = 'DURATION_MS'
        LEFT JOIN lyrics_cache AS lc ON lc.trackId = t.track_id
        INNER JOIN lyrics_misses AS lm ON lm.trackId = t.track_id
        WHERE t.catalog_lifecycle = 'ACTIVE'
          AND lc.trackId IS NULL
          AND lm.kind = :kind
          AND trim(COALESCE(title_o.value, m.normalizedTitle, '')) <> ''
        ORDER BY t.track_id
        """,
    )
    fun listTracksByMissKind(kind: String): List<LyricsCandidateRow>
}

data class LyricsCandidateRow(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
)
