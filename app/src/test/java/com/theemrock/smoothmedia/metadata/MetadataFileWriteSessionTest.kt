package com.theemrock.smoothmedia.metadata

import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataFileWriteSessionTest {
    private val hash = StoredFileHash("SHA-256", 1, "abc")

    @Test
    fun staleHashIsRejectedBeforeAnyWrite() {
        val target = target()

        val result = MetadataFileWritePolicy.validate(
            target = target,
            observedHash = hash.copy(digest = "different"),
        )

        assertEquals(
            MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.STALE_HASH),
            result,
        )
    }

    @Test
    fun excludedLocationIsRejectedEvenWhenHashMatches() {
        val result = MetadataFileWritePolicy.validate(
            target = target().copy(excluded = true),
            observedHash = hash,
        )

        assertEquals(
            MetadataFileWriteDecision.Rejected(MetadataFileWriteRejection.EXCLUDED),
            result,
        )
    }

    @Test
    fun supportedSelectedAvailableTargetIsAllowed() {
        assertEquals(
            MetadataFileWriteDecision.Allowed,
            MetadataFileWritePolicy.validate(target(), hash),
        )
    }

    private fun target() = MetadataFileWriteTarget(
        trackId = "track",
        locationId = "location",
        sourceId = "source",
        locator = "content://music/track",
        format = AudioFormat.MP3,
        locationAvailability = LocationAvailability.AVAILABLE,
        sourceAvailability = SourceAvailability.AVAILABLE,
        sourcePermission = SourcePermissionState.GRANTED,
        sourceSelection = SourceSelectionState.ACTIVE,
        excluded = false,
        currentHash = hash,
    )
}
