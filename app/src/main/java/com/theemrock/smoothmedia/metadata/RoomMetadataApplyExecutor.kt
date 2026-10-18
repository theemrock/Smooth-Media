package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.CatalogDatabase
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyChangeLogEntity
import com.theemrock.smoothmedia.catalog.database.entity.MetadataApplyLeaseEntity

enum class MetadataApplyExecutionStatus { DISABLED, RUNNING, COMPLETED, BLOCKED }

data class MetadataApplyExecutionResult(
    val status: MetadataApplyExecutionStatus,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val stale: Int = 0,
    val failed: Int = 0,
)

/**
 * Room-backed, bounded executor. It is deliberately disabled unless both the
 * user-enabled flag and a verified backup are supplied by the caller.
 */
class RoomMetadataApplyExecutor(
    private val database: CatalogDatabase,
    private val writer: ControlledMetadataRoomWriter,
    private val owner: String,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val leaseDurationMs: Long = 60_000L,
) {
    fun runNextBatch(
        applySessionId: String,
        applyEnabled: Boolean = false,
        backupVerified: Boolean = false,
    ): MetadataApplyExecutionResult {
        if (!applyEnabled || !backupVerified) return MetadataApplyExecutionResult(MetadataApplyExecutionStatus.DISABLED)
        val now = nowEpochMs()
        val applyDao = database.metadataApplyDao()
        applyDao.insertLeaseIfAbsent(
            MetadataApplyLeaseEntity(
                applySessionId = applySessionId,
                owner = owner,
                expiresAtEpochMs = now + leaseDurationMs,
                heartbeatAtEpochMs = now,
            ),
        )
        if (applyDao.acquireOrRenewLease(applySessionId, owner, now, now + leaseDurationMs) != 1) {
            return MetadataApplyExecutionResult(MetadataApplyExecutionStatus.BLOCKED)
        }
        val items = applyDao.pendingItems(applySessionId, MAX_BATCH_SIZE)
        if (items.isEmpty()) {
            applyDao.updateSessionProgress(applySessionId, "COMPLETED", null, 0, 0, 0, 0, now, now)
            applyDao.releaseLease(applySessionId, owner)
            return MetadataApplyExecutionResult(MetadataApplyExecutionStatus.COMPLETED)
        }

        var succeeded = 0
        var stale = 0
        var failed = 0
        database.runInTransaction {
            items.forEach { item ->
                val current = current(item.trackId, item.sourceId, item.locationId, item.fieldName)
                val review = ControlledMetadataFieldReview(
                    trackId = item.trackId,
                    sourceId = item.sourceId,
                    locationId = item.locationId,
                    field = runCatching { ControlledMetadataField.valueOf(item.fieldName) }.getOrElse {
                        throw IllegalStateException("Unsupported metadata field")
                    },
                    expectedOldValue = item.expectedOldValue,
                    newValue = item.newValue ?: "",
                    rawEvidence = item.evidenceType,
                )
                val result = if (current == null) {
                    ControlledMetadataFieldApplyResult(ControlledMetadataFieldApplyStatus.REJECTED, reason = "stale catalog relation")
                } else {
                    val gate = ControlledMetadataFieldApplyGate({ current }, writer::writeFieldsAtomically)
                    gate.apply(listOf(review))
                }
                when (result.status) {
                    ControlledMetadataFieldApplyStatus.APPLIED,
                    ControlledMetadataFieldApplyStatus.NO_CHANGE -> {
                        applyDao.markItem(applySessionId, item.decisionSessionId, item.trackId, item.fieldName, "APPLIED", null, now)
                        applyDao.insertChangeLog(
                            MetadataApplyChangeLogEntity(
                                applySessionId = applySessionId,
                                trackId = item.trackId,
                                fieldName = item.fieldName,
                                oldValue = item.expectedOldValue,
                                newValue = item.newValue,
                                evidenceType = item.evidenceType,
                                decisionKey = "${item.decisionSessionId}:${item.trackId}:${item.fieldName}",
                                occurredAtEpochMs = now,
                                result = result.status.name,
                            ),
                        )
                        succeeded++
                    }
                    ControlledMetadataFieldApplyStatus.REJECTED -> {
                        applyDao.markItem(applySessionId, item.decisionSessionId, item.trackId, item.fieldName, "SKIPPED_STALE", result.reason, now)
                        stale++
                    }
                    else -> {
                        applyDao.markItem(applySessionId, item.decisionSessionId, item.trackId, item.fieldName, "FAILED", result.reason, now)
                        failed++
                    }
                }
            }
            applyDao.updateSessionProgress(applySessionId, "RUNNING", items.last().trackId, items.size, succeeded, stale, failed, now, null)
        }
        applyDao.releaseLease(applySessionId, owner)
        return MetadataApplyExecutionResult(MetadataApplyExecutionStatus.RUNNING, items.size, succeeded, stale, failed)
    }

    private fun current(trackId: String, sourceId: String, locationId: String, fieldName: String): ControlledMetadataFieldCurrent? {
        val metadata = database.trackMetadataDao().find(trackId) ?: return null
        val location = database.trackDao().findLocation(locationId) ?: return null
        val source = database.librarySourceDao().find(sourceId) ?: return null
        val binding = database.locatorBindingDao().findByLocation(locationId) ?: return null
        if (location.trackId != trackId || location.sourceId != sourceId || binding.sourceId != sourceId ||
            location.availability.name != "AVAILABLE" || source.availability.name != "AVAILABLE" ||
            source.permissionState.name !in setOf("GRANTED", "NOT_REQUIRED") || source.selectionState.name != "ACTIVE"
        ) return null
        val field = runCatching { ControlledMetadataField.valueOf(fieldName) }.getOrNull() ?: return null
        val values = mapOf(
            ControlledMetadataField.TITLE to metadata.normalizedTitle,
            ControlledMetadataField.ARTIST to metadata.normalizedArtist,
            ControlledMetadataField.ALBUM to metadata.normalizedAlbum,
            ControlledMetadataField.ALBUM_ARTIST to metadata.normalizedAlbumArtist,
            ControlledMetadataField.GENRE to metadata.normalizedGenres,
        )
        val overrides = database.trackMetadataDao().listOverrides(trackId).mapNotNull {
            runCatching { MetadataOverrideField.valueOf(it.fieldName) }.getOrNull()
        }.mapNotNull { field ->
            runCatching { ControlledMetadataField.valueOf(field.name) }.getOrNull()
        }.toSet()
        return ControlledMetadataFieldCurrent(trackId, sourceId, locationId, true, values, overrides)
    }

    companion object { const val MAX_BATCH_SIZE = 100 }
}
