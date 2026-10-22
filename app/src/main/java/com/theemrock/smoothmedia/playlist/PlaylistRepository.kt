package com.theemrock.smoothmedia.playlist

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntity
import com.theemrock.smoothmedia.catalog.database.entity.PlaylistEntryEntity
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable

data class PlaylistAddResult(val requested: Int, val added: Int, val skippedExisting: Int, val skippedMissing: Int)

class PlaylistRepository(private val database: CatalogDatabase) {
    fun listAll(): List<PlaylistEntity> = database.playlistDao().listAll()

    fun create(name: String): PlaylistEntity {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Playlist adı boş olamaz" }
        val normalized = normalize(clean)
        return database.runInTransaction(Callable {
            check(database.playlistDao().findByNormalizedName(normalized) == null) { "Bu isimde playlist zaten var" }
            val now = System.currentTimeMillis()
            PlaylistEntity(UUID.randomUUID().toString(), clean, normalized, now, now).also(database.playlistDao()::insertPlaylist)
        })
    }

    fun addTracks(playlistId: String, orderedTrackIds: List<String>): PlaylistAddResult = database.runInTransaction(Callable {
        check(database.playlistDao().find(playlistId) != null) { "Playlist bulunamadı" }
        val requestedIds = orderedUniqueTrackIds(orderedTrackIds)
        if (requestedIds.isEmpty()) return@Callable PlaylistAddResult(0, 0, 0, 0)
        val active = database.trackDao().listActiveTrackIds(requestedIds).toSet()
        val existing = database.playlistDao().existingTrackIds(playlistId, requestedIds).toSet()
        val toAdd = requestedIds.filter { it in active && it !in existing }
        val start = database.playlistDao().maxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        database.playlistDao().insertEntries(toAdd.mapIndexed { index, trackId -> PlaylistEntryEntity(playlistId, trackId, start + index, now) })
        PlaylistAddResult(requestedIds.size, toAdd.size, requestedIds.count { it in existing }, requestedIds.count { it !in active })
    })

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
}
