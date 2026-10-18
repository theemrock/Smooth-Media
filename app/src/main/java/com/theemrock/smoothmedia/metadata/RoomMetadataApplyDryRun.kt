package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.model.LocationAvailability

enum class RoomMetadataApplyDryRunStatus {
    ELIGIBLE,
    NO_CHANGE,
    SKIPPED_STALE,
    SKIPPED_OVERRIDE,
    READ_ERROR,
    BLOCKED,
}

data class RoomMetadataApplyDryRunSummary(
    val auditSessionId: String,
    val evaluated: Int,
    val eligible: Int,
    val noChange: Int,
    val skippedStale: Int,
    val skippedOverride: Int,
    val readError: Int,
    val blocked: Int,
    val byField: Map<ControlledMetadataField, Map<RoomMetadataApplyDryRunStatus, Int>>,
)

/**
 * Room-backed, read-only apply preflight. It shares the writer gate's
 * identity/availability/override rules but never calls a metadata writer and
 * never inserts an apply session or plan.
 */
class RoomMetadataApplyDryRun(
    private val database: CatalogDatabase,
) {
    fun evaluate(auditSessionId: String): RoomMetadataApplyDryRunSummary {
        val session = database.metadataRecoveryDao().findSession(auditSessionId)
        check(session?.status?.name == "COMPLETED") {
            "Dry-run yalnızca tamamlanmış audit session'ından çalışabilir"
        }

        val counts = linkedMapOf<ControlledMetadataField, MutableMap<RoomMetadataApplyDryRunStatus, Int>>()
        var evaluated = 0
        var eligible = 0
        var noChange = 0
        var stale = 0
        var override = 0
        var readError = 0
        var blocked = 0

        database.metadataRecoveryDao().listDecisions(auditSessionId)
            .filter { it.decision == MetadataApplyCoordinator.AUTO_REPAIR }
            .forEach { decision ->
                evaluated++
                val field = runCatching { ControlledMetadataField.valueOf(decision.fieldName) }.getOrNull()
                val status = if (field == null || decision.newValue == null) {
                    RoomMetadataApplyDryRunStatus.BLOCKED
                } else {
                    evaluateDecision(decision, field)
                }
                field?.let { fieldCounts ->
                    counts.getOrPut(fieldCounts) { linkedMapOf() }[status] =
                        (counts[fieldCounts]?.get(status) ?: 0) + 1
                }
                when (status) {
                    RoomMetadataApplyDryRunStatus.ELIGIBLE -> eligible++
                    RoomMetadataApplyDryRunStatus.NO_CHANGE -> noChange++
                    RoomMetadataApplyDryRunStatus.SKIPPED_STALE -> stale++
                    RoomMetadataApplyDryRunStatus.SKIPPED_OVERRIDE -> override++
                    RoomMetadataApplyDryRunStatus.READ_ERROR -> readError++
                    RoomMetadataApplyDryRunStatus.BLOCKED -> blocked++
                }
            }

        return RoomMetadataApplyDryRunSummary(
            auditSessionId = auditSessionId,
            evaluated = evaluated,
            eligible = eligible,
            noChange = noChange,
            skippedStale = stale,
            skippedOverride = override,
            readError = readError,
            blocked = blocked,
            byField = counts.mapValues { it.value.toMap() },
        )
    }

    /** Returns only decisions that passed the complete read-only gate. */
    fun approvedInputs(auditSessionId: String): List<ApplyDecisionInput> {
        val summary = evaluate(auditSessionId)
        val decisions = database.metadataRecoveryDao().listDecisions(auditSessionId)
            .filter { it.decision == MetadataApplyCoordinator.AUTO_REPAIR }
        check(summary.evaluated == decisions.size && summary.eligible == decisions.size) {
            "Dry-run sonucu apply kapsamına uygun değil"
        }
        return decisions.map { decision ->
            val field = runCatching { ControlledMetadataField.valueOf(decision.fieldName) }.getOrThrow()
            val location = database.trackDao().findLocations(decision.trackId).single()
            ApplyDecisionInput(
                trackId = decision.trackId,
                sourceId = location.sourceId,
                locationId = location.locationId,
                field = field,
                expectedOldValue = decision.oldValue,
                newValue = decision.newValue ?: error("Yeni metadata değeri boş"),
                decision = decision.decision,
                evidence = decision.evidenceType,
            )
        }
    }

    private fun evaluateDecision(
        decision: com.theemrock.smoothmedia.catalog.database.entity.MetadataRecoveryDecisionEntity,
        field: ControlledMetadataField,
    ): RoomMetadataApplyDryRunStatus {
        val locations = database.trackDao().findLocations(decision.trackId)
        if (locations.size != 1) return RoomMetadataApplyDryRunStatus.BLOCKED
        val location = locations.single()
        val source = database.librarySourceDao().find(location.sourceId)
            ?: return RoomMetadataApplyDryRunStatus.READ_ERROR
        val binding = database.locatorBindingDao().findByLocation(location.locationId)
            ?: return RoomMetadataApplyDryRunStatus.READ_ERROR
        if (location.trackId != decision.trackId || binding.sourceId != location.sourceId ||
            location.availability != LocationAvailability.AVAILABLE ||
            source.availability != SourceAvailability.AVAILABLE ||
            source.permissionState !in setOf(SourcePermissionState.GRANTED, SourcePermissionState.NOT_REQUIRED) ||
            source.selectionState != SourceSelectionState.ACTIVE
        ) return RoomMetadataApplyDryRunStatus.READ_ERROR

        val metadata = database.trackMetadataDao().find(decision.trackId)
            ?: return RoomMetadataApplyDryRunStatus.READ_ERROR
        val overrideFields = database.trackMetadataDao().listOverrides(decision.trackId)
            .map { it.fieldName }
        if (field.name in overrideFields) return RoomMetadataApplyDryRunStatus.SKIPPED_OVERRIDE
        val current = when (field) {
            ControlledMetadataField.TITLE -> metadata.normalizedTitle
            ControlledMetadataField.ARTIST -> metadata.normalizedArtist
            ControlledMetadataField.ALBUM -> metadata.normalizedAlbum
            ControlledMetadataField.ALBUM_ARTIST -> metadata.normalizedAlbumArtist
            ControlledMetadataField.GENRE -> metadata.normalizedGenres
        }
        if (current == decision.newValue) return RoomMetadataApplyDryRunStatus.NO_CHANGE
        if (current != decision.oldValue) return RoomMetadataApplyDryRunStatus.SKIPPED_STALE
        return RoomMetadataApplyDryRunStatus.ELIGIBLE
    }
}
