package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "saf_documents",
    primaryKeys = ["sourceId", "documentId"],
    indices = [
        Index(value = ["sourceId", "parentDocumentId"]),
        Index(value = ["sourceId", "uri"]),
    ],
    foreignKeys = [
        ForeignKey(entity = LibrarySourceEntity::class, parentColumns = ["source_id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SafDocumentEntity(
    val sourceId: String,
    val documentId: String,
    @ColumnInfo(defaultValue = "''")
    val uri: String,
    val parentDocumentId: String?,
    val displayName: String,
    val isDirectory: Boolean,
    val available: Boolean,
    val updatedAtEpochMs: Long,
)
