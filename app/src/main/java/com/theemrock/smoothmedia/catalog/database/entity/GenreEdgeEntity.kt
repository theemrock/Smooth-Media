package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "genre_edges",
    primaryKeys = ["parentGenreId", "childGenreId"],
    foreignKeys = [
        ForeignKey(entity = GenreNodeEntity::class, parentColumns = ["genreId"], childColumns = ["parentGenreId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GenreNodeEntity::class, parentColumns = ["genreId"], childColumns = ["childGenreId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("childGenreId")],
)
data class GenreEdgeEntity(
    val parentGenreId: String,
    val childGenreId: String,
)
