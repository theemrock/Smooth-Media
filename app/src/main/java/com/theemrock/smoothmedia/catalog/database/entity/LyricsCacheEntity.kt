package com.theemrock.smoothmedia.catalog.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import com.theemrock.smoothmedia.lyrics.CachedLyrics
import com.theemrock.smoothmedia.lyrics.LyricsMode
import com.theemrock.smoothmedia.lyrics.LyricsProvenance

@Entity(
    tableName = "lyrics_cache",
    primaryKeys = ["trackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsCacheEntity(
    val trackId: String,
    val mode: String,
    val text: String,
    val provenance: String,
    val source: String?,
    val updatedAtEpochMs: Long,
)

fun LyricsCacheEntity.toDomain(): CachedLyrics? = runCatching {
    CachedLyrics(trackId, LyricsMode.valueOf(mode), text, LyricsProvenance.valueOf(provenance), source, updatedAtEpochMs)
}.getOrNull()

fun CachedLyrics.toEntity(): LyricsCacheEntity = LyricsCacheEntity(
    trackId = trackId,
    mode = mode.name,
    text = text,
    provenance = provenance.name,
    source = source,
    updatedAtEpochMs = updatedAtEpochMs,
)
