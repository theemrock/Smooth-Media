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
               (SELECT COUNT(*) FROM saf_documents f
                  WHERE f.sourceId = d.sourceId
                    AND f.parentDocumentId = d.documentId
                    AND f.isDirectory = 0
                    AND f.available = 1) AS trackCount,
               0 AS albumCount, 0 AS mp3Count, 0 AS flacCount
        FROM saf_documents d
        WHERE d.isDirectory = 1 AND d.available = 1
        ORDER BY d.displayName COLLATE NOCASE, `key`
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadDirectoryRows(offset: Int, limit: Int): List<LibraryCollectionRow>

    @Query("SELECT COUNT(*) FROM saf_documents WHERE isDirectory = 1 AND available = 1")
    fun countDirectories(): Int
}
