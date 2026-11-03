package com.theemrock.smoothmedia.library

import com.theemrock.smoothmedia.catalog.database.dao.SmartPlaylistDao
import com.theemrock.smoothmedia.catalog.database.entity.SmartPlaylistEntity
import java.util.Locale
import java.util.UUID

data class SmartPlaylist(val id: String, val name: String, val rule: SmartRule)

/**
 * Saves and loads user-defined smart playlists. Rows whose rule text no longer decodes are
 * skipped instead of failing the whole list.
 */
class SmartPlaylistRepository(
    private val dao: SmartPlaylistDao,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun list(): List<SmartPlaylist> = dao.loadAll().mapNotNull { row ->
        runCatching { SmartPlaylist(row.smartPlaylistId, row.name, SmartRuleCodec.decode(row.ruleText)) }.getOrNull()
    }

    /**
     * Inserts when [id] is null, otherwise updates that playlist. Returns the playlist id.
     * Duplicate names surface as the database constraint exception from the DAO.
     */
    fun save(id: String?, name: String, rule: SmartRule): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name must not be blank" }
        val now = nowEpochMs()
        val row = SmartPlaylistEntity(
            smartPlaylistId = id ?: newId(),
            name = trimmed,
            normalizedName = trimmed.lowercase(Locale.ROOT),
            ruleText = SmartRuleCodec.encode(rule),
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        if (id == null) {
            dao.insert(row)
        } else {
            val created = dao.loadAll().firstOrNull { it.smartPlaylistId == id }?.createdAtEpochMs ?: now
            dao.update(row.copy(createdAtEpochMs = created))
        }
        return row.smartPlaylistId
    }

    fun delete(id: String) {
        dao.delete(id)
    }
}
