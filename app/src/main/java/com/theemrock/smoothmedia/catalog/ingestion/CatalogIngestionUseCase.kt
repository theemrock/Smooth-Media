package com.theemrock.smoothmedia.catalog.ingestion

import com.theemrock.smoothmedia.catalog.database.entity.CatalogLifecycle
import com.theemrock.smoothmedia.catalog.database.entity.LocatorBindingEntity
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.database.entity.TrackEntity
import com.theemrock.smoothmedia.catalog.database.entity.TrackLocationEntity
import com.theemrock.smoothmedia.catalog.identity.IdentityDecision
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchAssessment
import com.theemrock.smoothmedia.catalog.identity.LogicalMatchPolicy
import com.theemrock.smoothmedia.catalog.identity.KnownTrackLocation
import com.theemrock.smoothmedia.catalog.identity.TrackIdentityResolver
import com.theemrock.smoothmedia.catalog.model.TrackId
import com.theemrock.smoothmedia.catalog.model.TrackLocationId
import com.theemrock.smoothmedia.catalog.repository.CatalogIdentityWriter
import com.theemrock.smoothmedia.catalog.repository.CatalogRepository
import com.theemrock.smoothmedia.discovery.AudioFormat
import com.theemrock.smoothmedia.discovery.DiscoveredAudioFile
import com.theemrock.smoothmedia.discovery.FullFileHashResult
import com.theemrock.smoothmedia.discovery.HashIdentityAdapter

interface CatalogIdFactory {
    fun newTrackId(): TrackId

    fun newLocationId(): TrackLocationId
}

sealed interface CatalogIngestionResult {
    data class Reused(val decision: IdentityDecision.ReuseLocation) : CatalogIngestionResult

    data class Written(
        val decision: IdentityDecision,
        val trackId: TrackId,
        val locationId: TrackLocationId,
    ) : CatalogIngestionResult

    data class NeedsReview(val decision: IdentityDecision.ReviewCandidate) : CatalogIngestionResult

    data class HashUnavailable(val result: FullFileHashResult) : CatalogIngestionResult

    data class UnsupportedHash(val algorithmName: String, val algorithmVersion: Int) : CatalogIngestionResult

    data class InvalidInput(val field: String) : CatalogIngestionResult

    data class WriteBlocked(val reason: String) : CatalogIngestionResult

    data object StorageFailed : CatalogIngestionResult
}

class CatalogIngestionUseCase(
    private val catalog: CatalogRepository,
    private val writer: CatalogIdentityWriter,
    private val hashIdentityAdapter: HashIdentityAdapter,
    private val idFactory: CatalogIdFactory,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val resolver: TrackIdentityResolver = TrackIdentityResolver(
        LogicalMatchPolicy { _, _ -> LogicalMatchAssessment.NoMatch },
    ),
    private val knownLocationsCache: MutableList<KnownTrackLocation>? = null,
) {
    fun ingest(
        file: DiscoveredAudioFile,
        hashResult: FullFileHashResult,
    ): CatalogIngestionResult {
        if (file.sizeBytes == null) return CatalogIngestionResult.InvalidInput("sizeBytes")
        if (file.modifiedAtEpochMs == null) return CatalogIngestionResult.InvalidInput("modifiedAtEpochMs")

        val knownLocations = try {
            knownLocationsCache ?: catalog.listKnownTrackLocations()
        } catch (_: Exception) {
            return CatalogIngestionResult.StorageFailed
        }
        val adapted = hashIdentityAdapter.adapt(
            file = file,
            hashResult = hashResult,
            knownLocations = knownLocations,
        )
        val input = when (adapted) {
            is com.theemrock.smoothmedia.discovery.HashIdentityAdapterResult.Ready -> adapted.input
            is com.theemrock.smoothmedia.discovery.HashIdentityAdapterResult.HashUnavailable ->
                return CatalogIngestionResult.HashUnavailable(adapted.result)
            is com.theemrock.smoothmedia.discovery.HashIdentityAdapterResult.UnsupportedHash ->
                return CatalogIngestionResult.UnsupportedHash(adapted.algorithmName, adapted.algorithmVersion)
            is com.theemrock.smoothmedia.discovery.HashIdentityAdapterResult.InvalidInput ->
                return CatalogIngestionResult.InvalidInput(adapted.field)
        }

        return when (val decision = resolver.resolve(input)) {
            is IdentityDecision.ReuseLocation -> CatalogIngestionResult.Reused(decision)
            is IdentityDecision.ReviewCandidate -> CatalogIngestionResult.NeedsReview(decision)
            is IdentityDecision.CreateTrackLocation -> writeLocation(
                file = file,
                hash = input.candidate.fileHash,
                decision = decision,
                trackId = decision.trackId,
                knownLocations = knownLocations,
            )
            is IdentityDecision.CreateNewTrack -> writeLocation(
                file = file,
                hash = input.candidate.fileHash,
                decision = decision,
                trackId = idFactory.newTrackId(),
                knownLocations = knownLocations,
            )
            is IdentityDecision.PreserveLocation -> CatalogIngestionResult.WriteBlocked("unsupported-decision")
        }
    }

    private fun writeLocation(
        file: DiscoveredAudioFile,
        hash: com.theemrock.smoothmedia.catalog.model.FileHash,
        decision: IdentityDecision,
        trackId: TrackId,
        knownLocations: List<KnownTrackLocation>,
    ): CatalogIngestionResult {
        val locatorAlreadyBound = if (knownLocationsCache != null) {
            knownLocations.any { it.locator.value == file.uri }
        } else {
            try {
                catalog.findBindingByLocator(file.uri) != null
            } catch (_: Exception) {
                return CatalogIngestionResult.StorageFailed
            }
        }
        if (locatorAlreadyBound) {
            return CatalogIngestionResult.WriteBlocked("locator-already-bound")
        }

        val locationId = idFactory.newLocationId()
        val now = nowEpochMs()
        val location = TrackLocationEntity(
            locationId = locationId.value,
            trackId = trackId.value,
            sourceId = file.sourceId,
            originalFileHash = hash.toStoredHash(),
            currentFileHash = hash.toStoredHash(),
            format = file.format.toCatalogFormat(),
            availability = com.theemrock.smoothmedia.catalog.model.LocationAvailability.AVAILABLE,
            path = null,
            mediaStoreId = null,
            fileSizeBytes = file.sizeBytes!!,
            modifiedAtEpochMs = file.modifiedAtEpochMs!!,
            durationMs = null,
            bitrate = null,
            sampleRateHz = null,
            channelCount = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val binding = LocatorBindingEntity(
            locator = file.uri,
            locationId = locationId.value,
            sourceId = file.sourceId,
            boundAtEpochMs = now,
        )

        return try {
            if (decision is IdentityDecision.CreateNewTrack) {
                writer.createTrackWithLocation(
                    track = TrackEntity(
                        trackId = trackId.value,
                        catalogLifecycle = CatalogLifecycle.ACTIVE,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now,
                    ),
                    location = location,
                    binding = binding,
                )
            } else {
                writer.attachLocation(location, binding)
            }
            knownLocationsCache?.add(
                KnownTrackLocation(
                    trackId = trackId,
                    locationId = locationId,
                    locator = com.theemrock.smoothmedia.catalog.model.FileLocator(file.uri),
                    originalFileHash = hash,
                    currentFileHash = hash,
                ),
            )
            CatalogIngestionResult.Written(decision, trackId, locationId)
        } catch (_: Exception) {
            CatalogIngestionResult.StorageFailed
        }
    }

    private fun com.theemrock.smoothmedia.catalog.model.FileHash.toStoredHash() = StoredFileHash(
        algorithmName = algorithm.name,
        algorithmVersion = algorithm.version,
        digest = digest,
    )

    private fun AudioFormat.toCatalogFormat() = when (this) {
        AudioFormat.MP3 -> com.theemrock.smoothmedia.catalog.identity.AudioFormat.MP3
        AudioFormat.FLAC -> com.theemrock.smoothmedia.catalog.identity.AudioFormat.FLAC
    }
}
