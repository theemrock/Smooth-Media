package com.theemrock.smoothmedia.catalog.exclusion

import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogExclusionPolicyTest {
    private val policy = ConservativeCatalogExclusionPolicy()

    @Test
    fun exactSourceLocatorAndHash_isExcluded() {
        val decision = policy.evaluate(
            candidate = candidate(),
            exclusions = listOf(exclusion()),
        )

        assertEquals(ExclusionDecision.Excluded("exclusion-1"), decision)
    }

    @Test
    fun sameHashAtDifferentLocator_requiresReview() {
        val decision = policy.evaluate(
            candidate = candidate(locator = "content://music/copy"),
            exclusions = listOf(exclusion()),
        )

        assertEquals(ExclusionDecision.ReviewRequired(listOf("exclusion-1")), decision)
    }

    @Test
    fun sameLocatorWithDifferentHash_requiresReview() {
        val decision = policy.evaluate(
            candidate = candidate(hash = hash("changed")),
            exclusions = listOf(exclusion()),
        )

        assertEquals(ExclusionDecision.ReviewRequired(listOf("exclusion-1")), decision)
    }

    @Test
    fun sameHashAcrossSources_requiresReviewInsteadOfGlobalExclusion() {
        val decision = policy.evaluate(
            candidate = candidate(sourceId = "source-2", locator = "content://other/copy"),
            exclusions = listOf(exclusion()),
        )

        assertEquals(ExclusionDecision.ReviewRequired(listOf("exclusion-1")), decision)
    }

    @Test
    fun unrelatedCandidate_isAllowed() {
        val decision = policy.evaluate(
            candidate = candidate(locator = "content://music/other", hash = hash("other")),
            exclusions = listOf(exclusion()),
        )

        assertEquals(ExclusionDecision.Allowed, decision)
    }

    private fun candidate(
        sourceId: String = "source-1",
        locator: String = "content://music/1",
        hash: FileHash = hash("hash-1"),
    ) = ExclusionCandidate(sourceId, locator, hash)

    private fun exclusion() = CatalogExclusionRecord(
        exclusionId = "exclusion-1",
        sourceId = "source-1",
        locator = "content://music/1",
        fileHash = hash("hash-1"),
    )

    private fun hash(digest: String) = FileHash(
        algorithm = HashAlgorithmVersion("SHA-256", 1),
        digest = digest,
    )
}
