package com.theemrock.smoothmedia.catalog.repository

import com.theemrock.smoothmedia.catalog.database.entity.CatalogExclusionEntity
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionCandidate
import com.theemrock.smoothmedia.catalog.exclusion.ExclusionDecision

interface CatalogExclusionRepository {
    fun record(exclusion: CatalogExclusionEntity)

    fun evaluate(candidate: ExclusionCandidate): ExclusionDecision
}
