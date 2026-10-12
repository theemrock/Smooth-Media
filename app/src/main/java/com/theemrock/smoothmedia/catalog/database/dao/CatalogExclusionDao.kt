package com.theemrock.smoothmedia.catalog.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity

@Dao
interface CatalogExclusionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(exclusion: CatalogExclusionEntity)

    @Query(
        """
        SELECT * FROM catalog_exclusions
        WHERE (source_id = :sourceId AND locator = :locator)
           OR (file_hash_algorithm_name = :algorithmName
               AND file_hash_algorithm_version = :algorithmVersion
               AND file_hash_digest = :digest)
        ORDER BY exclusion_id
        """,
    )
    fun findCandidates(
        sourceId: String,
        locator: String,
        algorithmName: String,
        algorithmVersion: Int,
        digest: String,
    ): List<CatalogExclusionEntity>
}
