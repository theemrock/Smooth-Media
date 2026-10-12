package com.theemrock.smoothmedia.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafTreeIdentityTest {
    @Test
    fun validTreeComponents_produceVersionedDeterministicIdentity() {
        val first = SafTreeIdentityFactory.from(
            authority = "com.android.externalstorage.documents",
            treeDocumentId = "primary:Music",
        )
        val second = SafTreeIdentityFactory.from(
            authority = "com.android.externalstorage.documents",
            treeDocumentId = "primary:Music",
        )

        val expected = SafTreeIdentityResult.Valid(
            SafTreeIdentity(
                sourceId = "c9d1541807a16a6303e82ec34f0444c9cff879e1fd2f57c898cb824db8f72fc4",
                authority = "com.android.externalstorage.documents",
                treeDocumentId = "primary:Music",
                canonicalKey = "saf-tree-v1\u0000com.android.externalstorage.documents\u0000primary:Music",
            ),
        )
        assertEquals(expected, first)
        assertEquals(first, second)
    }

    @Test
    fun differentProviderOrDocument_producesDifferentIdentity() {
        val base = valid("provider.one", "primary:Music")
        val otherProvider = valid("provider.two", "primary:Music")
        val otherDocument = valid("provider.one", "primary:Podcasts")

        assertNotEquals(base.sourceId, otherProvider.sourceId)
        assertNotEquals(base.sourceId, otherDocument.sourceId)
    }

    @Test
    fun blankOrMissingComponents_areRejected() {
        listOf(
            SafTreeIdentityFactory.from(null, "primary:Music"),
            SafTreeIdentityFactory.from("", "primary:Music"),
            SafTreeIdentityFactory.from("provider", null),
            SafTreeIdentityFactory.from("provider", "   "),
        ).forEach { result ->
            assertTrue(result is SafTreeIdentityResult.Invalid)
        }
    }

    private fun valid(authority: String, treeDocumentId: String): SafTreeIdentity =
        (SafTreeIdentityFactory.from(authority, treeDocumentId) as SafTreeIdentityResult.Valid).identity
}
