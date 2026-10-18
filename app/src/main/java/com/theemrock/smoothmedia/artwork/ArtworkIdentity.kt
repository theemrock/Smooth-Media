package com.theemrock.smoothmedia.artwork

import java.security.MessageDigest

/** Opaque canonical album/collection key supplied by the existing catalog identity flow. */
data class ArtworkAssetRef(
    val assetId: String,
    val locator: String,
)

object ArtworkAssetIdentity {
    fun fromEncodedBytes(bytes: ByteArray, locator: String): ArtworkAssetRef {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return ArtworkAssetRef("sha256:$digest", locator)
    }
}

enum class ArtworkBindingOrigin {
    DISCOVERED,
    USER_OVERRIDE,
}

data class ArtworkBinding(
    val collectionKey: String,
    val asset: ArtworkAssetRef,
    val origin: ArtworkBindingOrigin,
    val priority: Int = 0,
)

data class ArtworkOverride(
    val collectionKey: String,
    val asset: ArtworkAssetRef,
)

/**
 * Selection is deliberately independent from track IDs and does not derive identity from names.
 * The caller supplies the already-canonical collection key from the catalog identity pipeline.
 */
object ArtworkSelectionPolicy {
    fun resolve(
        collectionKey: String,
        bindings: List<ArtworkBinding>,
        overrides: List<ArtworkOverride>,
    ): ArtworkAssetRef? {
        overrides.firstOrNull { it.collectionKey == collectionKey }?.let { return it.asset }
        return bindings
            .asSequence()
            .filter { it.collectionKey == collectionKey && it.origin == ArtworkBindingOrigin.DISCOVERED }
            .sortedWith(compareBy<ArtworkBinding> { it.priority }.thenBy { it.asset.assetId })
            .map { it.asset }
            .firstOrNull()
    }
}
