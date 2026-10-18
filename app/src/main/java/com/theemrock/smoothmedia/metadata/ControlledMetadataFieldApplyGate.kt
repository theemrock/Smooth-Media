package com.theemrock.smoothmedia.metadata

/** Supported normalized fields for a future, explicitly enabled metadata apply. */
enum class ControlledMetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
}

data class ControlledMetadataFieldReview(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val field: ControlledMetadataField,
    val expectedOldValue: String?,
    val newValue: String,
    val rawEvidence: String,
)

data class ControlledMetadataFieldCurrent(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val available: Boolean,
    val values: Map<ControlledMetadataField, String?>,
    val overriddenFields: Set<ControlledMetadataField>,
)

data class ControlledMetadataFieldChange(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val field: ControlledMetadataField,
    val oldValue: String?,
    val newValue: String,
    val isNoChange: Boolean,
)

enum class ControlledMetadataFieldApplyStatus {
    READY,
    NO_CHANGE,
    REJECTED,
    APPLIED,
    ROLLED_BACK,
}

data class ControlledMetadataFieldApplyResult(
    val status: ControlledMetadataFieldApplyStatus,
    val changes: List<ControlledMetadataFieldChange> = emptyList(),
    val reason: String? = null,
    val applied: Boolean = false,
)

/** Fail-closed field-level validation; it never discovers files or mutates metadata. */
class ControlledMetadataFieldApplyGate(
    private val read: (String) -> ControlledMetadataFieldCurrent?,
    private val writeAtomically: (List<ControlledMetadataFieldChange>) -> Boolean,
) {
    fun dryRun(reviews: List<ControlledMetadataFieldReview>): ControlledMetadataFieldApplyResult =
        validate(reviews)

    fun apply(reviews: List<ControlledMetadataFieldReview>): ControlledMetadataFieldApplyResult {
        val validated = validate(reviews)
        if (validated.status != ControlledMetadataFieldApplyStatus.READY) return validated
        if (validated.changes.all { it.isNoChange }) {
            return validated.copy(status = ControlledMetadataFieldApplyStatus.NO_CHANGE)
        }
        return if (writeAtomically(validated.changes.filterNot { it.isNoChange })) {
            validated.copy(status = ControlledMetadataFieldApplyStatus.APPLIED, applied = true)
        } else {
            validated.copy(status = ControlledMetadataFieldApplyStatus.ROLLED_BACK)
        }
    }

    private fun validate(reviews: List<ControlledMetadataFieldReview>): ControlledMetadataFieldApplyResult {
        if (reviews.isEmpty()) return rejected("İncelenmiş hedef yok")
        if (reviews.size > MAX_FIELDS) return rejected("Apply en fazla $MAX_FIELDS alan kabul eder")
        if (reviews.map { it.trackId to it.field }.distinct().size != reviews.size) {
            return rejected("Aynı track alanı birden fazla kez verildi")
        }

        val changes = mutableListOf<ControlledMetadataFieldChange>()
        for (review in reviews) {
            if (review.rawEvidence.isBlank()) return rejected("Ham tag kanıtı eksik")
            val current = read(review.trackId) ?: return rejected("Track bulunamadı")
            if (current.trackId != review.trackId) return rejected("Track kimliği uyuşmuyor")
            if (!current.available) return rejected("Dosya erişilebilir değil")
            if (current.sourceId != review.sourceId) return rejected("Source ID uyuşmuyor")
            if (current.locationId != review.locationId) return rejected("Location ID uyuşmuyor")
            if (review.field in current.overriddenFields) return rejected("Kullanıcı ${review.field} override mevcut")
            val oldValue = current.values[review.field]
            if (oldValue != review.expectedOldValue && oldValue != review.newValue) {
                return rejected("Beklenen eski ${review.field} değeri uyuşmuyor")
            }
            changes += ControlledMetadataFieldChange(
                trackId = review.trackId,
                sourceId = review.sourceId,
                locationId = review.locationId,
                field = review.field,
                oldValue = oldValue,
                newValue = review.newValue,
                isNoChange = oldValue == review.newValue,
            )
        }
        return ControlledMetadataFieldApplyResult(
            status = if (changes.all { it.isNoChange }) {
                ControlledMetadataFieldApplyStatus.NO_CHANGE
            } else {
                ControlledMetadataFieldApplyStatus.READY
            },
            changes = changes,
        )
    }

    private fun rejected(reason: String) = ControlledMetadataFieldApplyResult(
        status = ControlledMetadataFieldApplyStatus.REJECTED,
        reason = reason,
    )

    private companion object {
        const val MAX_FIELDS = 100
    }
}
