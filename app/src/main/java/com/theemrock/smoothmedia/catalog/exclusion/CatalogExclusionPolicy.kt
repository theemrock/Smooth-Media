package com.theemrock.smoothmedia.catalog.exclusion

import com.theemrock.smoothmedia.catalog.model.FileHash

data class ExclusionCandidate(
    val sourceId: String,
    val locator: String,
    val fileHash: FileHash,
)

data class CatalogExclusionRecord(
    val exclusionId: String,
    val sourceId: String,
    val locator: String,
    val fileHash: FileHash,
)

fun interface CatalogExclusionPolicy {
    fun evaluate(
        candidate: ExclusionCandidate,
        exclusions: List<CatalogExclusionRecord>,
    ): ExclusionDecision
}

sealed interface ExclusionDecision {
    data object Allowed : ExclusionDecision

    data class Excluded(val exclusionId: String) : ExclusionDecision

    data class ReviewRequired(val exclusionIds: List<String>) : ExclusionDecision
}

class ConservativeCatalogExclusionPolicy : CatalogExclusionPolicy {
    override fun evaluate(
        candidate: ExclusionCandidate,
        exclusions: List<CatalogExclusionRecord>,
    ): ExclusionDecision {
        val exactMatches = exclusions.filter { exclusion ->
            exclusion.sourceId == candidate.sourceId &&
                exclusion.locator == candidate.locator &&
                exclusion.fileHash == candidate.fileHash
        }
        if (exactMatches.size == 1) {
            return ExclusionDecision.Excluded(exactMatches.single().exclusionId)
        }

        val ambiguousMatches = exclusions.filter { exclusion ->
            (exclusion.sourceId == candidate.sourceId && exclusion.locator == candidate.locator) ||
                exclusion.fileHash == candidate.fileHash
        }
        if (ambiguousMatches.isNotEmpty()) {
            return ExclusionDecision.ReviewRequired(
                ambiguousMatches.map { it.exclusionId }.distinct().sorted(),
            )
        }

        return ExclusionDecision.Allowed
    }
}
