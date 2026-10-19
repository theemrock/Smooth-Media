package com.theemrock.smoothmedia.genre

import com.theemrock.smoothmedia.catalog.database.dao.GenreDao
import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.GenreEdgeEntity
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackGenreAssignmentEntity
import java.util.concurrent.Callable
import androidx.sqlite.db.SimpleSQLiteQuery

/** Persists only validated, user/metadata-backed genre relationships. */
class RoomGenreRepository(private val database: CatalogDatabase) {
    private val dao: GenreDao = database.genreDao()

    data class AssignmentResult(val accepted: Boolean, val reason: String? = null) {
        val isRejected: Boolean get() = !accepted
    }
    data class ChildGenre(
        val id: String,
        val displayName: String,
        val directTrackCount: Int,
    )
    data class AssignmentDisplay(
        val primary: String?,
        val secondary: List<String>,
    )

    fun saveHierarchy(nodes: List<GenreNode>): GenreBuildResult {
        return saveFullHierarchySnapshot(nodes)
    }

    fun saveFullHierarchySnapshot(nodes: List<GenreNode>): GenreBuildResult {
        val result = GenreHierarchy.build(nodes)
        if (result !is GenreBuildResult.Valid) return result
        inTransaction {
            upsertNodes(nodes)
            dao.deleteAllEdges()
            nodes.forEach { node ->
                node.parentIds.forEach { parent -> dao.insertEdge(GenreEdgeEntity(parent.trim(), node.id.trim())) }
            }
        }
        return result
    }

    fun applyPartialHierarchyUpdate(nodes: List<GenreNode>): GenreBuildResult {
        val candidate = currentGraph().toMutableMap()
        nodes.forEach { candidate[it.id.trim()] = it.copy(id = it.id.trim(), parentIds = it.parentIds.map(String::trim).toSet()) }
        val result = GenreHierarchy.build(candidate.values.toList())
        if (result !is GenreBuildResult.Valid) return result
        inTransaction {
            upsertNodes(nodes)
            nodes.forEach { node ->
                val id = node.id.trim()
                dao.deleteEdgesForChild(id)
                node.parentIds.forEach { parent -> dao.insertEdge(GenreEdgeEntity(parent.trim(), id)) }
            }
        }
        return result
    }

    fun assignGenre(
        trackId: String,
        genreId: String,
        isPrimary: Boolean,
        userOverride: Boolean,
        provenance: String = if (userOverride) "USER" else "LOCAL_METADATA",
        confidence: Float? = null,
    ): AssignmentResult = inTransaction {
        val existing = dao.assignmentsForTrack(trackId)
        val normalizedGenreId = genreId.trim()
        val otherPrimary = existing.firstOrNull { it.isPrimary && it.genreId != normalizedGenreId }
        if (isPrimary && otherPrimary != null) {
            return@inTransaction AssignmentResult(false, "İkinci Primary Genre reddedildi")
        }
        val current = existing.firstOrNull { it.genreId == normalizedGenreId }
        if (current?.userOverride == true && !userOverride) return@inTransaction AssignmentResult(true)
        dao.upsertAssignment(
            TrackGenreAssignmentEntity(trackId, normalizedGenreId, isPrimary, provenance, confidence, userOverride),
        )
        AssignmentResult(true)
    }

    fun changePrimaryGenre(trackId: String, genreId: String, userOverride: Boolean): AssignmentResult = inTransaction {
        val normalizedGenreId = genreId.trim()
        val existing = dao.assignmentsForTrack(trackId)
        val current = existing.firstOrNull { it.genreId == normalizedGenreId }
        if (current?.userOverride == true && !userOverride) return@inTransaction AssignmentResult(true)
        existing.filter { it.isPrimary && it.genreId != normalizedGenreId }
            .forEach { dao.updatePrimary(trackId, it.genreId, false) }
        dao.upsertAssignment(
            TrackGenreAssignmentEntity(trackId, normalizedGenreId, true, if (userOverride) "USER" else "LOCAL_METADATA", null, userOverride),
        )
        AssignmentResult(true)
    }

    fun clearPrimaryGenre(trackId: String, genreId: String): AssignmentResult = inTransaction {
        val updated = dao.updatePrimary(trackId, genreId.trim(), false)
        if (updated == 0) AssignmentResult(false, "Primary Genre assignment bulunamadı") else AssignmentResult(true)
    }

    fun assignGenresBatch(assignments: List<TrackGenreAssignmentEntity>): AssignmentResult = inTransaction {
        val grouped = assignments.groupBy { it.trackId }
        grouped.forEach { (trackId, incoming) ->
            val primaryCount = incoming.count { it.isPrimary } + dao.assignmentsForTrack(trackId).count { it.isPrimary && incoming.none { item -> item.genreId == it.genreId } }
            if (primaryCount > 1) return@inTransaction AssignmentResult(false, "İkinci Primary Genre reddedildi")
        }
        assignments.forEach { dao.upsertAssignment(it) }
        AssignmentResult(true)
    }

    /** Materializes embedded metadata genres without removing user-owned assignments. */
    fun syncMetadataGenres(trackId: String, genres: List<String>) = inTransaction {
        val normalized = genres.mapNotNull { raw ->
            val displayName = raw.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            displayName.lowercase() to displayName
        }.distinctBy { it.first }
        if (normalized.isEmpty()) return@inTransaction

        val existing = dao.assignmentsForTrack(trackId)
        val hasPrimary = existing.any { it.isPrimary }
        normalized.forEachIndexed { index, (genreId, displayName) ->
            val node = dao.findNode(genreId)
            if (node == null) {
                dao.upsertNode(
                    GenreNodeEntity(
                        genreId = genreId,
                        displayName = displayName,
                        normalizedName = genreId,
                        provenance = "LOCAL_METADATA",
                        confidence = null,
                        userOverride = false,
                    ),
                )
            }
            val current = existing.firstOrNull { it.genreId == genreId }
            if (current?.userOverride != true) {
                dao.upsertAssignment(
                    TrackGenreAssignmentEntity(
                        trackId = trackId,
                        genreId = genreId,
                        isPrimary = index == 0 && !hasPrimary,
                        provenance = "LOCAL_METADATA",
                        confidence = null,
                        userOverride = false,
                    ),
                )
            }
        }
    }

    fun childrenOf(parentId: String): List<String> = dao.childrenOf(parentId).map { it.childGenreId }
    fun childGenresOf(parentId: String): List<ChildGenre> = dao.childNodesOf(parentId).map { node ->
        ChildGenre(node.genreId, node.displayName, dao.trackIdsForGenre(node.genreId).size)
    }
    fun parentsOf(childId: String): List<String> = dao.parentsOf(childId).map { it.parentGenreId }
    fun trackIdsForGenre(genreId: String): List<String> = dao.trackIdsForGenre(genreId)

    fun assignmentDisplayForTracks(trackIds: Collection<String>): Map<String, AssignmentDisplay> {
        val names = dao.allNodes().associate { it.genreId to it.displayName }
        return trackIds.associateWith { trackId ->
            val assignments = dao.assignmentsForTrack(trackId)
            AssignmentDisplay(
                primary = assignments.firstOrNull { it.isPrimary }?.genreId?.let(names::get),
                secondary = assignments.filterNot { it.isPrimary }.mapNotNull { names[it.genreId] },
            )
        }
    }

    fun queryTrackIds(query: GenreQuery, limit: Int, offset: Int): List<String> {
        val compiled = GenreQueryCompiler.compile(query, limit, offset)
        val args = buildList<Any?> {
            addAll(compiled.arguments)
            add(compiled.limit)
            add(compiled.offset)
        }.toTypedArray()
        return dao.queryTrackIds(SimpleSQLiteQuery(compiled.sql, args))
    }

    private fun upsertNodes(nodes: List<GenreNode>) {
        nodes.forEach { node ->
            val id = node.id.trim()
            val existing = dao.findNode(id)
            if (existing?.userOverride == true) return@forEach
            dao.upsertNode(GenreNodeEntity(id, id, id.lowercase(), "LOCAL_METADATA", null, false))
        }
    }

    private fun currentGraph(): Map<String, GenreNode> {
        val parentsByChild = dao.allEdges().groupBy { it.childGenreId }.mapValues { (_, edges) -> edges.map { it.parentGenreId }.toSet() }
        return dao.allNodes().associate { it.genreId to GenreNode(it.genreId, parentsByChild[it.genreId].orEmpty()) }
    }

    private fun <T> inTransaction(block: () -> T): T = database.runInTransaction(Callable { block() })
}
