package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.theemrock.smoothmedia.catalog.database.entity.GenreEdgeEntity
import com.theemrock.smoothmedia.catalog.database.entity.GenreNodeEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackGenreAssignmentEntity

@Dao
interface GenreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertNode(node: GenreNodeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEdge(edge: GenreEdgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAssignment(assignment: TrackGenreAssignmentEntity)

    @Query("SELECT * FROM genre_nodes ORDER BY normalizedName, genreId")
    fun allNodes(): List<GenreNodeEntity>

    @Query("SELECT * FROM genre_nodes WHERE genreId = :genreId")
    fun findNode(genreId: String): GenreNodeEntity?

    @Query("SELECT * FROM genre_edges ORDER BY parentGenreId, childGenreId")
    fun allEdges(): List<GenreEdgeEntity>

    @Query("DELETE FROM genre_edges")
    fun deleteAllEdges()

    @Query("DELETE FROM genre_edges WHERE childGenreId = :childGenreId")
    fun deleteEdgesForChild(childGenreId: String)

    @Query("SELECT * FROM genre_edges WHERE parentGenreId = :parent ORDER BY childGenreId")
    fun childrenOf(parent: String): List<GenreEdgeEntity>

    @Query(
        """
        SELECT n.* FROM genre_nodes n
        INNER JOIN genre_edges e ON e.childGenreId = n.genreId
        WHERE e.parentGenreId = :parent
        ORDER BY n.normalizedName, n.genreId
        """,
    )
    fun childNodesOf(parent: String): List<GenreNodeEntity>

    @Query("SELECT * FROM genre_edges WHERE childGenreId = :child ORDER BY parentGenreId")
    fun parentsOf(child: String): List<GenreEdgeEntity>

    @Query("SELECT * FROM track_genre_assignments WHERE trackId = :trackId ORDER BY isPrimary DESC, genreId")
    fun assignmentsForTrack(trackId: String): List<TrackGenreAssignmentEntity>

    @Query("UPDATE track_genre_assignments SET isPrimary = :isPrimary WHERE trackId = :trackId AND genreId = :genreId")
    fun updatePrimary(trackId: String, genreId: String, isPrimary: Boolean): Int

    @Query("SELECT DISTINCT trackId FROM track_genre_assignments WHERE genreId = :genreId")
    fun trackIdsForGenre(genreId: String): List<String>

    @RawQuery
    fun queryTrackIds(query: SupportSQLiteQuery): List<String>
}
