package com.theemrock.smoothmedia.metadata

/** Audit decision source used by the disabled-by-default apply coordinator. */
interface MetadataApplyAuditSource {
    fun status(auditSessionId: String): ApplyAuditStatus
    fun decisions(auditSessionId: String): List<ApplyDecisionInput>
}

enum class ApplyAuditStatus {
    RUNNING,
    COMPLETED,
    INVALID,
}

data class ApplyDecisionInput(
    val trackId: String,
    val sourceId: String,
    val locationId: String,
    val field: ControlledMetadataField,
    val expectedOldValue: String?,
    val newValue: String,
    val decision: String,
    val evidence: String,
)

data class MetadataApplyDryRunResult(
    val acceptedCount: Int,
    val skippedCount: Int,
    val batches: List<List<ApplyDecisionInput>>,
    val gateResults: List<ControlledMetadataFieldApplyResult>,
)

/**
 * Bounded policy coordinator. It never writes while applyEnabled is false.
 * Persistence of sessions, leases and change logs is supplied by the Room layer
 * in the next integration step; this class keeps the decision policy testable.
 */
class MetadataApplyCoordinator(
    private val audit: MetadataApplyAuditSource,
    private val gate: ControlledMetadataFieldApplyGate,
    private val applyEnabled: Boolean = false,
    private val batchSize: Int = MAX_BATCH_SIZE,
) {
    fun dryRun(auditSessionId: String): MetadataApplyDryRunResult {
        require(batchSize in 1..MAX_BATCH_SIZE)
        check(audit.status(auditSessionId) == ApplyAuditStatus.COMPLETED) {
            "Apply yalnızca tamamlanmış audit session'ından çalışabilir"
        }
        val all = audit.decisions(auditSessionId)
        val accepted = all.filter { it.decision == AUTO_REPAIR }
        val batches = accepted.chunked(batchSize)
        val gateResults = batches.map { batch -> gate.dryRun(batch.map { it.toReview() }) }
        return MetadataApplyDryRunResult(
            acceptedCount = accepted.size,
            skippedCount = all.size - accepted.size,
            batches = batches,
            gateResults = gateResults,
        )
    }

    fun apply(auditSessionId: String): MetadataApplyDryRunResult {
        val dryRun = dryRun(auditSessionId)
        check(applyEnabled) { "Otonom metadata apply etkin değil" }
        dryRun.batches.forEach { batch ->
            val result = gate.apply(batch.map { it.toReview() })
            check(result.status == ControlledMetadataFieldApplyStatus.APPLIED || result.status == ControlledMetadataFieldApplyStatus.NO_CHANGE) {
                "Metadata apply batch reddedildi veya rollback oldu"
            }
        }
        return dryRun
    }

    private fun ApplyDecisionInput.toReview() = ControlledMetadataFieldReview(
        trackId = trackId,
        sourceId = sourceId,
        locationId = locationId,
        field = field,
        expectedOldValue = expectedOldValue,
        newValue = newValue,
        rawEvidence = evidence,
    )

    companion object {
        const val MAX_BATCH_SIZE = 100
        const val AUTO_REPAIR = "AUTO_REPAIR"
    }
}
