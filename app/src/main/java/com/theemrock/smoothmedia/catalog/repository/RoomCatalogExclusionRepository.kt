package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.dao.CatalogExclusionDao
import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.exclusion.CatalogExclusionPolicy
import com.theemrock.smoothmedia.catalog.exclusion.CatalogExclusionRecord
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionCandidate
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionDecision
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion

class RoomCatalogExclusionRepository(
    private val dao: CatalogExclusionDao,
    private val policy: CatalogExclusionPolicy,
) : CatalogExclusionRepository {
    override fun record(exclusion: CatalogExclusionEntity) = dao.insert(exclusion)

    override fun evaluate(candidate: ExclusionCandidate): ExclusionDecision {
        val matches = dao.findCandidates(
            sourceId = candidate.sourceId,
            locator = candidate.locator,
            algorithmName = candidate.fileHash.algorithm.name,
            algorithmVersion = candidate.fileHash.algorithm.version,
            digest = candidate.fileHash.digest,
        ).map { entity ->
            CatalogExclusionRecord(
                exclusionId = entity.exclusionId,
                sourceId = entity.sourceId,
                locator = entity.locator,
                fileHash = FileHash(
                    algorithm = HashAlgorithmVersion(
                        entity.fileHash.algorithmName,
                        entity.fileHash.algorithmVersion,
                    ),
                    digest = entity.fileHash.digest,
                ),
            )
        }
        return policy.evaluate(candidate, matches)
    }
}
