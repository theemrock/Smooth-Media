package com.theemrock.smoothmedia.metadata

import java.security.MessageDigest

data class PictureFingerprint(
    val pictureType: String,
    val mimeType: String,
    val description: String,
    val payloadSha256: String,
)

data class FlacVerificationSummary(
    val audioSha256: String,
    val pictures: List<PictureFingerprint>,
)

sealed interface FlacVerificationResult {
    data object Preserved : FlacVerificationResult
    data class Mismatch(val reason: FlacVerificationMismatch) : FlacVerificationResult
}

enum class FlacVerificationMismatch {
    AUDIO_CHANGED,
    PICTURES_CHANGED,
}

/** Independent comparison boundary; audio hash and parsed PICTURE data come from callers. */
object MetadataArtifactVerification {
    fun summarize(audioSha256: String, snapshot: FileTagSnapshot): FlacVerificationSummary =
        FlacVerificationSummary(
            audioSha256 = audioSha256,
            pictures = snapshot.pictures.map { picture ->
                PictureFingerprint(
                    pictureType = picture.pictureType,
                    mimeType = picture.mimeType,
                    description = picture.description,
                    payloadSha256 = sha256(picture.data),
                )
            },
        )

    fun compare(before: FlacVerificationSummary, after: FlacVerificationSummary): FlacVerificationResult {
        if (before.audioSha256 != after.audioSha256) return FlacVerificationResult.Mismatch(FlacVerificationMismatch.AUDIO_CHANGED)
        if (before.pictures != after.pictures) return FlacVerificationResult.Mismatch(FlacVerificationMismatch.PICTURES_CHANGED)
        return FlacVerificationResult.Preserved
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
