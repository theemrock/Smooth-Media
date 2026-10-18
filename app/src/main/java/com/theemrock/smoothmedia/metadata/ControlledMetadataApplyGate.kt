package com.theemrock.smoothmedia.metadata

/**
 * Fail-closed gate for a reviewed, title-only metadata update.
 * The writer must provide one atomic Room transaction; this class never discovers
 * locations, changes identities, or writes source files.
 */
class ControlledMetadataApplyGate(
    private val read: (String) -> ControlledMetadataCurrent?,
    private val writeAtomically: (List<ControlledMetadataTitleChange>) -> Boolean,
) {
    fun dryRun(reviews: List<ControlledMetadataReview>): ControlledMetadataApplyResult =
        validate(reviews)

    fun apply(reviews: List<ControlledMetadataReview>): ControlledMetadataApplyResult {
        val validated = validate(reviews)
        if (validated.status != ControlledMetadataApplyStatus.READY) return validated
        if (validated.changes.all { it.isNoChange }) {
            return validated.copy(status = ControlledMetadataApplyStatus.NO_CHANGE)
        }
        return if (writeAtomically(validated.changes.filterNot { it.isNoChange })) {
            validated.copy(status = ControlledMetadataApplyStatus.APPLIED, applied = true)
        } else {
            validated.copy(status = ControlledMetadataApplyStatus.ROLLED_BACK)
        }
    }

    private fun validate(reviews: List<ControlledMetadataReview>): ControlledMetadataApplyResult {
        if (reviews.isEmpty()) return rejected("İncelenmiş hedef yok")
        if (reviews.size > MAX_TARGETS) return rejected("Apply en fazla $MAX_TARGETS hedef kabul eder")
        if (reviews.map { it.trackId }.distinct().size != reviews.size) {
            return rejected("Aynı track ID birden fazla kez verildi")
        }

        val changes = mutableListOf<ControlledMetadataTitleChange>()
        for (review in reviews) {
            if (review.rawEvidence.isBlank()) return rejected("Ham tag kanıtı eksik")
            val current = read(review.trackId) ?: return rejected("Track bulunamadı")
            if (!current.available) return rejected("Dosya erişilebilir değil")
            if (current.sourceId != review.sourceId) return rejected("Source ID uyuşmuyor")
            if (current.locationId != review.locationId) return rejected("Location ID uyuşmuyor")
            if (current.titleOverridePresent) return rejected("Kullanıcı title override mevcut")
            if (current.title != review.expectedOldTitle && current.title != review.newTitle) {
                return rejected("Beklenen eski title uyuşmuyor")
            }
            changes += ControlledMetadataTitleChange(
                trackId = review.trackId,
                sourceId = review.sourceId,
                locationId = review.locationId,
                oldTitle = current.title,
                newTitle = review.newTitle,
                isNoChange = current.title == review.newTitle,
            )
        }
        val status = if (changes.all { it.isNoChange }) {
            ControlledMetadataApplyStatus.NO_CHANGE
        } else {
            ControlledMetadataApplyStatus.READY
        }
        return ControlledMetadataApplyResult(status, changes)
    }

    private fun rejected(reason: String) = ControlledMetadataApplyResult(
        status = ControlledMetadataApplyStatus.REJECTED,
        reason = reason,
    )

    companion object {
        const val MAX_TARGETS = 4
    }
}

data class ControlledMetadataReview(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val expectedOldTitle: String,
    val newTitle: String,
    val rawEvidence: String,
)

data class ControlledMetadataCurrent(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val available: Boolean,
    val title: String,
    val titleOverridePresent: Boolean,
)

data class ControlledMetadataTitleChange(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val oldTitle: String,
    val newTitle: String,
    val isNoChange: Boolean,
)

enum class ControlledMetadataApplyStatus {
    READY,
    NO_CHANGE,
    REJECTED,
    APPLIED,
    ROLLED_BACK,
}

data class ControlledMetadataApplyResult(
    val status: ControlledMetadataApplyStatus,
    val changes: List<ControlledMetadataTitleChange> = emptyList(),
    val reason: String? = null,
    val applied: Boolean = false,
)
