package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_genre_assignments",
    primaryKeys = ["trackId", "genreId"],
    foreignKeys = [
        ForeignKey(entity = TrackEntity::class, parentColumns = ["track_id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GenreNodeEntity::class, parentColumns = ["genreId"], childColumns = ["genreId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("genreId")],
)
data class TrackGenreAssignmentEntity(
    val trackId: String,
    val genreId: String,
    val isPrimary: Boolean,
    val provenance: String,
    val confidence: Float?,
    val userOverride: Boolean,
)
