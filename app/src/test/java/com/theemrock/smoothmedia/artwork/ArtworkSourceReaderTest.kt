package com.theemrock.smoothmedia.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkSourceReaderTest {
    @Test
    fun embeddedArtworkReportsFormatAndDimensions() {
        val reader = ArtworkSourceReader(
            backend = FakeArtworkBackend(embedded = mapOf("audio" to byteArrayOf(1, 2))),
            inspector = ArtworkImageInspector { ArtworkImageInfo("image/jpeg", 600, 600) },
        )

        val result = reader.readEmbedded("audio")

        assertEquals(ArtworkSourceType.EMBEDDED_AUDIO, result.sourceType)
        assertEquals(ArtworkAvailability.AVAILABLE, result.availability)
        assertEquals("image/jpeg", result.format)
        assertEquals(600, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun persistedFolderFileCanBeReadAsArtwork() {
        val reader = ArtworkSourceReader(
            backend = FakeArtworkBackend(files = mapOf("cover" to byteArrayOf(1, 2))),
            inspector = ArtworkImageInspector { ArtworkImageInfo("image/jpeg", 320, 320) },
        )

        val result = reader.readFolderFile("cover")

        assertEquals(ArtworkSourceType.FOLDER_IMAGE, result.sourceType)
        assertEquals(ArtworkAvailability.AVAILABLE, result.availability)
        assertEquals("cover", result.locator)
    }

    @Test
    fun folderArtworkPrefersCoverAndIgnoresOtherFiles() {
        val backend = FakeArtworkBackend(
            children = listOf(
                ArtworkFileCandidate("other.png", "other"),
                ArtworkFileCandidate("folder.jpg", "folder"),
                ArtworkFileCandidate("cover.jpg", "cover"),
            ),
            files = mapOf("cover" to byteArrayOf(1)),
        )
        val reader = ArtworkSourceReader(backend, ArtworkImageInspector { ArtworkImageInfo("image/jpeg", 300, 300) })

        val result = reader.readFolderArtwork("folder")

        assertEquals(ArtworkSourceType.FOLDER_IMAGE, result.sourceType)
        assertEquals("cover", result.locator)
        assertEquals(ArtworkAvailability.AVAILABLE, result.availability)
    }

    @Test
    fun folderArtworkSkipsDuplicateAndCorruptCandidate() {
        val backend = FakeArtworkBackend(
            children = listOf(
                ArtworkFileCandidate("cover.jpg", "same"),
                ArtworkFileCandidate("folder.jpg", "same"),
            ),
            files = mapOf("same" to byteArrayOf(1)),
        )
        val reader = ArtworkSourceReader(backend, ArtworkImageInspector { null })

        val result = reader.readFolderArtwork("folder")

        assertEquals(ArtworkAvailability.FAILED, result.availability)
        assertEquals(1, backend.fileReadCount)
    }

    @Test
    fun oversizedImageIsRejectedWithoutDecode() {
        var inspected = false
        val reader = ArtworkSourceReader(
            backend = FakeArtworkBackend(embedded = mapOf("audio" to ByteArray(9))),
            inspector = ArtworkImageInspector { inspected = true; ArtworkImageInfo("image/jpeg", 1, 1) },
            maxEncodedBytes = 8,
        )

        val result = reader.readEmbedded("audio")

        assertEquals(ArtworkFailureReason.TOO_LARGE, result.failureReason)
        assertTrue(!inspected)
    }

    @Test
    fun missingPermissionAndCancellationAreReported() {
        val reader = ArtworkSourceReader(
            backend = FakeArtworkBackend(permissionLost = true),
            inspector = ArtworkImageInspector { ArtworkImageInfo("image/png", 1, 1) },
        )

        assertEquals(ArtworkFailureReason.PERMISSION_LOST, reader.readEmbedded("audio").failureReason)
        assertEquals(ArtworkFailureReason.CANCELLED, reader.readFolderArtwork("folder") { true }.failureReason)
    }

    @Test
    fun disappearingFolderFileIsReportedAsMissing() {
        val reader = ArtworkSourceReader(
            backend = FakeArtworkBackend(children = listOf(ArtworkFileCandidate("cover.jpg", "gone"))),
            inspector = ArtworkImageInspector { ArtworkImageInfo("image/jpeg", 1, 1) },
        )

        val result = reader.readFolderArtwork("folder")

        assertEquals(ArtworkAvailability.MISSING, result.availability)
    }

    private class FakeArtworkBackend(
        private val embedded: Map<String, ByteArray> = emptyMap(),
        private val children: List<ArtworkFileCandidate> = emptyList(),
        private val files: Map<String, ByteArray> = emptyMap(),
        private val permissionLost: Boolean = false,
    ) : ArtworkSourceBackend {
        var fileReadCount: Int = 0

        override fun readEmbedded(locator: String, maxEncodedBytes: Int): ByteArray? {
            if (permissionLost) throw SecurityException("permission")
            return embedded[locator]
        }

        override fun listFolder(folderLocator: String): List<ArtworkFileCandidate> {
            if (permissionLost) throw SecurityException("permission")
            return children
        }

        override fun readFile(locator: String, maxEncodedBytes: Int): ByteArray? {
            fileReadCount++
            if (permissionLost) throw SecurityException("permission")
            return files[locator]
        }
    }
}
