package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataArtifactVerificationTest {
    @Test
    fun preservesPictureOrderAndPayloadIdentityWhenAudioIsUnchanged() {
        val snapshot = FileTagSnapshot(
            properties = emptyMap(),
            pictures = listOf(
                FileTagPicture("cover-a".toByteArray(), "image/jpeg", "front", "Front"),
                FileTagPicture("cover-b".toByteArray(), "image/png", "back", "Back"),
            ),
        )
        val before = MetadataArtifactVerification.summarize("audio-hash", snapshot)
        assertEquals(FlacVerificationResult.Preserved, MetadataArtifactVerification.compare(before, before.copy()))
        assertEquals(listOf("Front", "Back"), before.pictures.map { it.pictureType })
    }

    @Test
    fun reportsAudioAndPictureChangesSeparately() {
        val snapshot = FileTagSnapshot(emptyMap(), listOf(FileTagPicture("a".toByteArray(), "image/jpeg", "", "Front")))
        val before = MetadataArtifactVerification.summarize("audio-a", snapshot)
        assertEquals(
            FlacVerificationResult.Mismatch(FlacVerificationMismatch.AUDIO_CHANGED),
            MetadataArtifactVerification.compare(before, before.copy(audioSha256 = "audio-b")),
        )
        val changedPicture = MetadataArtifactVerification.summarize(
            "audio-a",
            snapshot.copy(pictures = listOf(FileTagPicture("b".toByteArray(), "image/jpeg", "", "Front"))),
        )
        assertEquals(
            FlacVerificationResult.Mismatch(FlacVerificationMismatch.PICTURES_CHANGED),
            MetadataArtifactVerification.compare(before, changedPicture),
        )
    }
}
