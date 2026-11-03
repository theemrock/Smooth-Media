package com.theemrock.smoothmedia.library

import com.theemrock.smoothmedia.catalog.database.dao.SmartPlaylistDao
import com.theemrock.smoothmedia.catalog.database.entity.SmartPlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistRepositoryTest {
    private class FakeDao : SmartPlaylistDao {
        val rows = mutableListOf<SmartPlaylistEntity>()

        override fun loadAll(): List<SmartPlaylistEntity> = rows.sortedBy { it.normalizedName }

        override fun insert(entity: SmartPlaylistEntity) {
            check(rows.none { it.normalizedName == entity.normalizedName }) { "duplicate" }
            rows += entity
        }

        override fun update(entity: SmartPlaylistEntity): Int {
            val index = rows.indexOfFirst { it.smartPlaylistId == entity.smartPlaylistId }
            if (index < 0) return 0
            rows[index] = entity
            return 1
        }

        override fun delete(smartPlaylistId: String): Int = rows.removeAll { it.smartPlaylistId == smartPlaylistId }.let { if (it) 1 else 0 }
    }

    @Test
    fun savesTrimmedNameAndRoundTripsRule() {
        val dao = FakeDao()
        val repo = SmartPlaylistRepository(dao, nowEpochMs = { 1L }, newId = { "id-1" })
        val rule = SmartRule.All(listOf(SmartRule.Genre("metal"), SmartRule.ArtistIs("Yol")))

        val id = repo.save(null, "  Ağır  ", rule)

        assertEquals("id-1", id)
        assertEquals("Ağır", dao.rows.single().name)
        assertEquals(listOf(SmartPlaylist("id-1", "Ağır", rule)), repo.list())
    }

    @Test
    fun updateKeepsCreationTime() {
        val dao = FakeDao()
        var now = 10L
        val repo = SmartPlaylistRepository(dao, nowEpochMs = { now }, newId = { "id-1" })
        repo.save(null, "List", SmartRule.NoGenre)

        now = 20L
        repo.save("id-1", "List", SmartRule.NoGenre)

        assertEquals(10L, dao.rows.single().createdAtEpochMs)
        assertEquals(20L, dao.rows.single().updatedAtEpochMs)
    }

    @Test
    fun listSkipsRowsWhoseRuleTextIsBroken() {
        val dao = FakeDao()
        dao.rows += SmartPlaylistEntity("bad", "Broken", "broken", "artist(Yol", 1L, 1L)
        val repo = SmartPlaylistRepository(dao)

        assertEquals(emptyList<SmartPlaylist>(), repo.list())
    }
}
