package com.theemrock.smoothmedia.lyrics

import com.theemrock.smoothmedia.catalog.database.dao.LyricsCacheDao
import com.theemrock.smoothmedia.catalog.database.entity.toDomain
import com.theemrock.smoothmedia.catalog.database.entity.toEntity

class RoomLyricsCache(private val dao: LyricsCacheDao) : LyricsCache {
    override fun get(trackId: String): CachedLyrics? = dao.find(trackId)?.toDomain()

    override fun put(value: CachedLyrics) {
        dao.upsert(value.toEntity())
    }
}
