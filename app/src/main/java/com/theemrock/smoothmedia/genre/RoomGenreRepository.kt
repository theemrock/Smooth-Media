package com.theemrock.smoothmedia.genre

import com.theemrock.smoothmedia.catalog.database.dao.GenreDao
import com.theemrock.smoothmedia.catalog.database.entity.GenreEdgeEntity
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity

/** Persists only validated, user/metadata-backed genre relationships. */
class RoomGenreRepository(private val dao: GenreDao) {
    fun saveHierarchy(nodes: List<GenreNode>): GenreBuildResult {
        val result = GenreHierarchy.build(nodes)
        if (result !is GenreBuildResult.Valid) return result
        nodes.forEach { node ->
            val id = node.id.trim()
            dao.upsertNode(GenreNodeEntity(id, id, id.lowercase(), "LOCAL_METADATA", null, false))
            node.parentIds.forEach { parent ->
                dao.insertEdge(GenreEdgeEntity(parent.trim(), id))
            }
        }
        return result
    }

    fun childrenOf(parentId: String): List<String> = dao.childrenOf(parentId).map { it.childGenreId }
    fun parentsOf(childId: String): List<String> = dao.parentsOf(childId).map { it.parentGenreId }
    fun trackIdsForGenre(genreId: String): List<String> = dao.trackIdsForGenre(genreId)
}
