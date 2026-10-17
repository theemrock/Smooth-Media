package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.SafDocumentEntity
import com.theemrock.smoothmedia.library.LibraryCollectionRow

@Dao
interface SafDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(document: SafDocumentEntity)

    @Query("SELECT * FROM saf_documents WHERE sourceId = :sourceId AND parentDocumentId IS :parentDocumentId ORDER BY isDirectory DESC, displayName COLLATE NOCASE")
    fun children(sourceId: String, parentDocumentId: String?): List<SafDocumentEntity>

    @Query("SELECT * FROM saf_documents WHERE sourceId = :sourceId AND documentId = :documentId")
    fun find(sourceId: String, documentId: String): SafDocumentEntity?

    @Query("UPDATE saf_documents SET available = 0, updatedAtEpochMs = :updatedAtEpochMs WHERE sourceId = :sourceId")
    fun markSourceUnavailable(sourceId: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        SELECT sourceId || char(30) || documentId AS `key`,
               displayName AS title,
               parentDocumentId AS subtitle,
               (SELECT COUNT(DISTINCT l.track_id) FROM saf_documents f
                  INNER JOIN locator_bindings b
                    ON b.source_id = f.sourceId AND b.locator = f.uri
                  INNER JOIN track_locations l
                    ON l.location_id = b.location_id AND l.source_id = f.sourceId
                  WHERE f.sourceId = d.sourceId
                    AND f.parentDocumentId = d.documentId
                    AND f.isDirectory = 0
                    AND f.available = 1) AS trackCount,
               0 AS albumCount, 0 AS mp3Count, 0 AS flacCount
        FROM saf_documents d
        WHERE d.isDirectory = 1 AND d.available = 1
          AND NOT EXISTS (
              SELECT 1 FROM saf_documents p
              WHERE p.sourceId = d.sourceId
                AND p.documentId = d.parentDocumentId
          )
        ORDER BY d.displayName COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadDirectoryRows(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query(
        """
        SELECT COUNT(*) FROM saf_documents d
        WHERE d.isDirectory = 1 AND d.available = 1
          AND NOT EXISTS (
              SELECT 1 FROM saf_documents p
              WHERE p.sourceId = d.sourceId
                AND p.documentId = d.parentDocumentId
          )
        """,
    )
    fun countDirectories(): Int

    @Query(
        """
        SELECT COUNT(DISTINCT l.track_id)
        FROM saf_documents f
        INNER JOIN locator_bindings b
          ON b.source_id = f.sourceId AND b.locator = f.uri
        INNER JOIN track_locations l
          ON l.location_id = b.location_id AND l.source_id = f.sourceId
        WHERE f.sourceId = :sourceId
          AND f.parentDocumentId = :documentId
          AND f.isDirectory = 0
          AND f.available = 1
        """,
    )
    fun countDirectCatalogTracks(sourceId: String, documentId: String): Int

    @Query(
        """
        WITH RECURSIVE folder_scope(documentId) AS (
            SELECT :documentId
            UNION
            SELECT child.documentId
            FROM saf_documents child
            INNER JOIN folder_scope parent ON parent.documentId = child.parentDocumentId
            WHERE child.sourceId = :sourceId
              AND child.isDirectory = 1
              AND child.available = 1
        )
        SELECT COUNT(DISTINCT l.track_id)
        FROM saf_documents f
        INNER JOIN folder_scope scope ON scope.documentId = f.parentDocumentId
        INNER JOIN locator_bindings b
          ON b.source_id = f.sourceId AND b.locator = f.uri
        INNER JOIN track_locations l
          ON l.location_id = b.location_id AND l.source_id = f.sourceId
        WHERE f.sourceId = :sourceId
          AND f.isDirectory = 0
          AND f.available = 1
        """,
    )
    fun countRecursiveCatalogTracks(sourceId: String, documentId: String): Int
}
