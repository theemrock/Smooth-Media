package com.theemrock.smoothmedia.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkIdentityPolicyTest {
    @Test
    fun oneAssetMayBeReferencedByDifferentAlbumIdentities() {
        val asset = ArtworkAssetRef("asset-cover-1", "content://cover")
        val bindings = listOf(
            ArtworkBinding("album-source-a", asset, ArtworkBindingOrigin.DISCOVERED),
            ArtworkBinding("album-source-b", asset, ArtworkBindingOrigin.DISCOVERED),
        )

        assertEquals(asset, ArtworkSelectionPolicy.resolve("album-source-a", bindings, emptyList()))
        assertEquals(asset, ArtworkSelectionPolicy.resolve("album-source-b", bindings, emptyList()))
    }

    @Test
    fun userOverrideWinsOverDiscoveredBinding() {
        val discovered = ArtworkAssetRef("embedded", "content://embedded")
        val override = ArtworkAssetRef("manual", "content://manual")
        val bindings = listOf(ArtworkBinding("album-1", discovered, ArtworkBindingOrigin.DISCOVERED))

        assertEquals(override, ArtworkSelectionPolicy.resolve("album-1", bindings, listOf(ArtworkOverride("album-1", override))))
    }

    @Test
    fun missingOverrideFallsBackToDiscoveredBinding() {
        val discovered = ArtworkAssetRef("embedded", "content://embedded")
        val bindings = listOf(ArtworkBinding("album-1", discovered, ArtworkBindingOrigin.DISCOVERED))

        assertEquals(discovered, ArtworkSelectionPolicy.resolve("album-1", bindings, emptyList()))
    }

    @Test
    fun absentBindingResolvesToPlaceholderSignal() {
        assertNull(ArtworkSelectionPolicy.resolve("missing", emptyList(), emptyList()))
    }

    @Test
    fun duplicateDiscoveredBindingsUseStableAssetTieBreaker() {
        val bindings = listOf(
            ArtworkBinding("album-1", ArtworkAssetRef("z", "content://z"), ArtworkBindingOrigin.DISCOVERED),
            ArtworkBinding("album-1", ArtworkAssetRef("a", "content://a"), ArtworkBindingOrigin.DISCOVERED),
        )

        assertEquals("a", ArtworkSelectionPolicy.resolve("album-1", bindings, emptyList())?.assetId)
    }

    @Test
    fun identicalArtworkBytesShareAssetIdentityWithoutMergingCollections() {
        val first = ArtworkAssetIdentity.fromEncodedBytes(byteArrayOf(1, 2, 3), "content://one")
        val second = ArtworkAssetIdentity.fromEncodedBytes(byteArrayOf(1, 2, 3), "content://two")

        assertEquals(first.assetId, second.assetId)
        assertNotEquals(first.locator, second.locator)
    }
}
