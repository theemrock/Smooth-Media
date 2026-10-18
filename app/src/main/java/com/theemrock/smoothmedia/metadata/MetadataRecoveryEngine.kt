package com.theemrock.smoothmedia.metadata

import java.util.concurrent.atomic.AtomicBoolean

/** Read-only, bounded decision engine. It never calls a metadata writer. */
class MetadataRecoveryEngine(
    private val read: (String) -> MetadataReadOnlyResult,
    private val current: (String) -> TrackMetadataSnapshot?,
) {
    private val running = AtomicBoolean(false)

    fun evaluate(
        trackIds: List<String>,
        afterTrackId: String? = null,
        limit: Int = DEFAULT_BATCH_SIZE,
    ): MetadataRecoveryBatchResult {
        require(limit in 1..MAX_BATCH_SIZE) { "Metadata recovery batch is limited to $MAX_BATCH_SIZE Tracks" }
        if (!running.compareAndSet(false, true)) {
            return MetadataRecoveryBatchResult(emptyList(), null, busy = true)
        }
        return try {
            val selected = trackIds.asSequence()
                .distinct()
                .sorted()
                .filter { afterTrackId == null || it > afterTrackId }
                .take(limit)
                .map(::evaluateTrack)
                .toList()
            MetadataRecoveryBatchResult(
                tracks = selected,
                lastProcessedTrackId = selected.lastOrNull()?.trackId,
            )
        } finally {
            running.set(false)
        }
    }

    private fun evaluateTrack(trackId: String): MetadataRecoveryTrackResult {
        val readResult = read(trackId)
        val readError = when (readResult) {
            is MetadataReadOnlyResult.Success -> null
            is MetadataReadOnlyResult.Failed -> "metadata read failed: ${readResult.failures.joinToString()}"
            MetadataReadOnlyResult.NoAccessibleLocation -> "no accessible location"
            MetadataReadOnlyResult.Excluded -> "catalog exclusion"
            MetadataReadOnlyResult.TrackNotFound -> "track not found"
        }
        if (readError != null) {
            return MetadataRecoveryTrackResult(trackId, null, allFields(RecoveryDecision.READ_ERROR, readError))
        }

        val success = readResult as MetadataReadOnlyResult.Success
        val stored = current(trackId)
            ?: return MetadataRecoveryTrackResult(trackId, success.locationId, allFields(RecoveryDecision.REVIEW_REQUIRED, "stored metadata unavailable"))
        val fresh = success.metadata
        val fields = listOf(
            decide(RecoveryField.TITLE, stored.effective.title, fresh.title, stored.overrides.containsKey(MetadataOverrideField.TITLE)),
            decide(RecoveryField.ARTIST, stored.effective.artist, fresh.artist, stored.overrides.containsKey(MetadataOverrideField.ARTIST)),
            decide(RecoveryField.ALBUM, stored.effective.album, fresh.album, stored.overrides.containsKey(MetadataOverrideField.ALBUM)),
            decide(RecoveryField.ALBUM_ARTIST, stored.effective.albumArtist, fresh.albumArtist, stored.overrides.containsKey(MetadataOverrideField.ALBUM_ARTIST)),
            decide(RecoveryField.GENRE, stored.effective.genres.asText(), fresh.genres.asText(), stored.overrides.containsKey(MetadataOverrideField.GENRE)),
            decide(RecoveryField.TRACK_NUMBER, stored.effective.trackNumber, fresh.trackNumber, stored.overrides.containsKey(MetadataOverrideField.TRACK_NUMBER)),
            decide(RecoveryField.DISC_NUMBER, stored.effective.discNumber, fresh.discNumber, stored.overrides.containsKey(MetadataOverrideField.DISC_NUMBER)),
            decide(RecoveryField.YEAR, stored.effective.year, fresh.year, stored.overrides.containsKey(MetadataOverrideField.YEAR)),
            decide(RecoveryField.DURATION_MS, stored.effective.durationMs, fresh.durationMs, stored.overrides.containsKey(MetadataOverrideField.DURATION_MS)),
        )
        return MetadataRecoveryTrackResult(trackId, success.locationId, fields)
    }

    private fun <T> decide(
        field: RecoveryField,
        stored: MetadataField<T>,
        fresh: MetadataField<T>,
        overridePresent: Boolean,
    ): MetadataRecoveryFieldResult {
        val currentText = stored.value?.toString()
        val proposedText = fresh.value?.toString()
        val rawText = fresh.rawValue
        if (overridePresent) return MetadataRecoveryFieldResult(field, RecoveryDecision.NO_CHANGE, currentText, proposedText, "user override")
        if (currentText == proposedText) return MetadataRecoveryFieldResult(field, RecoveryDecision.NO_CHANGE, currentText, proposedText, "already equivalent")
        if (rawText?.contains('\uFFFD') == true || proposedText?.contains('\uFFFD') == true) {
            return MetadataRecoveryFieldResult(field, RecoveryDecision.UNRECOVERABLE, currentText, proposedText, "replacement character present")
        }
        if (fresh.status != MetadataFieldStatus.AVAILABLE || proposedText == null) {
            return MetadataRecoveryFieldResult(field, RecoveryDecision.REVIEW_REQUIRED, currentText, proposedText, "field is not a complete available value")
        }
        val highConfidence = field.isText && rawText != null && rawText != proposedText && rawText.containsMojibakeMarker()
        return MetadataRecoveryFieldResult(
            field = field,
            decision = if (highConfidence) RecoveryDecision.AUTO_REPAIR else RecoveryDecision.REVIEW_REQUIRED,
            current = currentText,
            proposed = proposedText,
            reason = if (highConfidence) "bounded UTF-8 round-trip repair" else "new value lacks bounded repair evidence",
        )
    }

    private fun allFields(decision: RecoveryDecision, reason: String) =
        RecoveryField.entries.map { MetadataRecoveryFieldResult(it, decision, null, null, reason) }

    private fun MetadataField<List<String>>.asText(): MetadataField<String> =
        MetadataField(value?.joinToString(";"), status, rawValue)

    private fun String.containsMojibakeMarker() = any { it in MOJIBAKE_MARKERS }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
        const val MAX_BATCH_SIZE = 100
        private val MOJIBAKE_MARKERS = setOf('Ã', 'Â', 'â', 'ð', 'Å', 'Ä', '\uFFFD')
    }
}

enum class RecoveryField(val isText: Boolean) {
    TITLE(true),
    ARTIST(true),
    ALBUM(true),
    ALBUM_ARTIST(true),
    GENRE(true),
    TRACK_NUMBER(false),
    DISC_NUMBER(false),
    YEAR(false),
    DURATION_MS(false),
}

enum class RecoveryDecision {
    AUTO_REPAIR,
    NO_CHANGE,
    REVIEW_REQUIRED,
    UNRECOVERABLE,
    READ_ERROR,
}

data class MetadataRecoveryFieldResult(
    val field: RecoveryField,
    val decision: RecoveryDecision,
    val current: String?,
    val proposed: String?,
    val reason: String,
)

data class MetadataRecoveryTrackResult(
    val trackId: String,
    val locationId: String?,
    val fields: List<MetadataRecoveryFieldResult>,
)

data class MetadataRecoveryBatchResult(
    val tracks: List<MetadataRecoveryTrackResult>,
    val lastProcessedTrackId: String?,
    val busy: Boolean = false,
)
