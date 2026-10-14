package com.theemrock.smoothmedia.discovery

import com.theemrock.smoothmedia.catalog.identity.AudioFormat as IdentityAudioFormat
import com.theemrock.smoothmedia.catalog.identity.DiscoveredFileIdentity
import com.theemrock.smoothmedia.catalog.identity.IdentityInput
import com.theemrock.smoothmedia.catalog.identity.KnownTrackLocation
import com.theemrock.smoothmedia.catalog.identity.LogicalTrackCandidate
import com.theemrock.smoothmedia.catalog.identity.LogicalMetadata
import com.theemrock.smoothmedia.catalog.identity.PriorLogicalDecision
import com.theemrock.smoothmedia.catalog.model.FileHash
import com.theemrock.smoothmedia.catalog.model.FileLocator
import com.theemrock.smoothmedia.catalog.model.HashAlgorithmVersion

sealed interface HashIdentityAdapterResult {
    data class Ready(
        val input: IdentityInput.FileDiscovered,
    ) : HashIdentityAdapterResult

    data class HashUnavailable(
        val result: FullFileHashResult,
    ) : HashIdentityAdapterResult

    data class UnsupportedHash(
        val algorithmName: String,
        val algorithmVersion: Int,
    ) : HashIdentityAdapterResult

    data class InvalidInput(
        val field: String,
    ) : HashIdentityAdapterResult
}

/** Converts a successful discovery hash into the existing identity resolver contract. */
class HashIdentityAdapter {
    fun adapt(
        file: DiscoveredAudioFile,
        hashResult: FullFileHashResult,
        knownLocations: List<KnownTrackLocation> = emptyList(),
        logicalCandidates: List<LogicalTrackCandidate> = emptyList(),
        priorDecision: PriorLogicalDecision? = null,
    ): HashIdentityAdapterResult {
        if (file.uri.isBlank()) return HashIdentityAdapterResult.InvalidInput("locator")

        val hash = when (hashResult) {
            is FullFileHashResult.Success -> {
                if (hashResult.algorithmName != SHA256_NAME ||
                    hashResult.algorithmVersion != SHA256_VERSION
                ) {
                    return HashIdentityAdapterResult.UnsupportedHash(
                        hashResult.algorithmName,
                        hashResult.algorithmVersion,
                    )
                }
                if (hashResult.digest.isBlank() || hashResult.bytesRead < 0) {
                    return HashIdentityAdapterResult.InvalidInput("hash")
                }
                FileHash(
                    algorithm = HashAlgorithmVersion(SHA256_NAME, SHA256_VERSION),
                    digest = hashResult.digest,
                )
            }

            else -> return HashIdentityAdapterResult.HashUnavailable(hashResult)
        }

        val metadata = LogicalMetadata(
            title = null,
            artist = null,
            durationMs = null,
            format = file.format.toIdentityFormat(),
            mastering = null,
        )
        return HashIdentityAdapterResult.Ready(
            IdentityInput.FileDiscovered(
                candidate = DiscoveredFileIdentity(
                    locator = FileLocator(file.uri),
                    fileHash = hash,
                    metadata = metadata,
                ),
                knownLocations = knownLocations,
                logicalCandidates = logicalCandidates,
                priorDecision = priorDecision,
            ),
        )
    }

    private fun AudioFormat.toIdentityFormat(): IdentityAudioFormat = when (this) {
        AudioFormat.MP3 -> IdentityAudioFormat.MP3
        AudioFormat.FLAC -> IdentityAudioFormat.FLAC
    }

    private companion object {
        const val SHA256_NAME = "SHA-256"
        const val SHA256_VERSION = 1
    }
}
