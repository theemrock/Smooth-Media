package com.theemrock.smoothmedia.metadata

import android.os.Bundle
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileNotFoundException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderFileSmokeTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    private val fixtureName = "provider-smoke.bin"
    private val uri = Uri.parse("content://com.theemrock.smoothmedia.test.fixture/$fixtureName")

    @Test
    fun providerOwnsCreateReadHashAndDeleteLifecycle() {
        val expected = byteArrayOf(0x53, 0x4D, 0x2D, 0x50, 0x30, 0x39, 0x00, 0x7F)
        val created = resolver.call(
            uri,
            "create",
            fixtureName,
            Bundle().apply { putByteArray("bytes", expected) },
        ) ?: error("Provider create returned no result")

        val providerPackage = created.getString("package")!!
        val providerUid = InstrumentationRegistry.getInstrumentation().context.packageManager.resolveContentProvider(
            "com.theemrock.smoothmedia.test.fixture",
            0,
        )!!.applicationInfo.uid
        assertEquals("com.theemrock.smoothmedia.p09test.test", providerPackage)
        assertEquals(providerUid, created.getInt("uid"))
        assertEquals(providerPackage, created.getString("process"))

        val actual = resolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        assertEquals(expected.size, actual.size)
        assertArrayEquals(sha256(expected), sha256(actual))

        resolver.call(uri, "delete", fixtureName, null)
        try {
            resolver.openFileDescriptor(uri, "r")
            fail("Deleted provider fixture remained readable")
        } catch (_: FileNotFoundException) {
            // Expected: provider-owned file has been deleted.
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
