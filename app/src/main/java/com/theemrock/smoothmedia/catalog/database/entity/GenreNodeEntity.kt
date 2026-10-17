package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity

@Entity(tableName = "genre_nodes", primaryKeys = ["genreId"])
data class GenreNodeEntity(
    val genreId: String,
    val displayName: String,
    val normalizedName: String,
    val provenance: String,
    val confidence: Float?,
    val userOverride: Boolean,
)
