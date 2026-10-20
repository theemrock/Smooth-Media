package com.theemrock.smoothmedia.metadata

import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theemrock.smoothmedia.catalog.database.entity.SourceAvailability
import com.theemrock.smoothmedia.catalog.database.entity.SourcePermissionState
import com.theemrock.smoothmedia.catalog.database.entity.SourceSelectionState
import com.theemrock.smoothmedia.catalog.database.entity.StoredFileHash
import com.theemrock.smoothmedia.catalog.identity.AudioFormat
import com.theemrock.smoothmedia.catalog.model.LocationAvailability
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IsolatedMetadataTagLibDeviceTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    private val adapter = KyantTagLibAdapter()
    private val title = "T\u00fcrk\u00e7e Ba\u015fl\u0131k"
    private val artist = "Sentetik Sanat\u00e7\u0131"
    private val unknownId3Payload = byteArrayOf(0x10, 0x00, 0x7F, 0x01, 0x55)

    @Before
    fun createSyntheticFixtures() {
        createFixture("synthetic.mp3", syntheticMp3())
        createFixture("synthetic.flac", syntheticFlac())
        createFixture("readonly-synthetic.mp3", syntheticMp3())
    }

    @After
    fun removeSyntheticFixtures() {
        listOf("synthetic.mp3", "synthetic.flac", "readonly-synthetic.mp3").forEach(::deleteFixture)
        assertEquals(0, providerOpenDescriptors().get())
    }

    @Test
    fun fixtureValidityIsConfirmedBeforeMetadataAssertions() {
        assertEquals(title, read(providerUri("synthetic.mp3")).properties["TITLE"]!!.single())
        assertEquals(title, read(providerUri("synthetic.flac")).properties["TITLE"]!!.single())
    }

    @Test
    fun mp3ReadWritePreservesUnknownGenreArtworkAndClosesDescriptors() {
        val uri = providerUri("synthetic.mp3")
        val before = read(uri)
        assertEquals(title, before.properties["TITLE"]!!.single())
        assertEquals(listOf("Rock", "Anadolu Rock"), before.properties["GENRE"])
        assertEquals(listOf("keep-me"), before.properties["CUSTOM"])
        assertEquals(1, before.pictures.size)
        assertTrue(extractId3FramePayload(readBytes(uri), "ZZZZ").contentEquals(unknownId3Payload))

        val oldHash = hash(uri)
        val result = session().write(
            target(uri, AudioFormat.MP3, oldHash),
            FileTagEditMapper.toChange(before, FileTagEdit(title = "Yeni \u015eark\u0131", genres = listOf("Metal", "Anadolu Rock"))),
        )
        assertTrue(result is MetadataFileWriteOutcome.Written)
        val written = result as MetadataFileWriteOutcome.Written
        assertEquals("Yeni \u015eark\u0131", written.snapshot.properties["TITLE"]!!.single())
        assertEquals(listOf("Metal", "Anadolu Rock"), written.snapshot.properties["GENRE"])
        assertEquals(listOf("keep-me"), written.snapshot.properties["CUSTOM"])
        assertEquals(1, written.snapshot.pictures.size)
        assertNotEquals(oldHash.digest, written.currentHash.digest)
        assertTrue(extractId3FramePayload(readBytes(uri), "ZZZZ").contentEquals(unknownId3Payload))
        assertEquals(0, providerOpenDescriptors().get())
    }

    @Test
    fun flacReadWritePreservesPictureAndSelectedFields() {
        val uri = providerUri("synthetic.flac")
        val before = read(uri)
        assertEquals(title, before.properties["TITLE"]!!.single())
        assertEquals(listOf("Rock", "Anadolu Rock"), before.properties["GENRE"])
        assertEquals(listOf("keep-me"), before.properties["CUSTOM"])
        assertEquals(1, before.pictures.size)

        val result = session().write(
            target(uri, AudioFormat.FLAC, hash(uri)),
            FileTagEditMapper.toChange(before, FileTagEdit(artist = "Yeni Sanat\u00e7\u0131")),
        )
        assertTrue(result is MetadataFileWriteOutcome.Written)
        val written = result as MetadataFileWriteOutcome.Written
        assertEquals("Yeni Sanat\u00e7\u0131", written.snapshot.properties["ARTIST"]!!.single())
        assertEquals(listOf(title), written.snapshot.properties["TITLE"])
        assertEquals(listOf("Rock", "Anadolu Rock"), written.snapshot.properties["GENRE"])
        assertEquals(listOf("keep-me"), written.snapshot.properties["CUSTOM"])
        assertEquals(1, written.snapshot.pictures.size)
    }

    @Test
    fun staleHashAndReadOnlyFailureDoNotWrite() {
        val uri = providerUri("synthetic.mp3")
        val beforeBytes = readBytes(uri)
        val before = read(uri)
        val staleResult = session().write(
            target(uri, AudioFormat.MP3, hash(uri).copy(digest = "stale")),
            FileTagEditMapper.toChange(before, FileTagEdit(title = "Should Not Write")),
        )
        assertEquals(MetadataFileWriteOutcome.Rejected(MetadataFileWriteRejection.STALE_HASH), staleResult)
        assertTrue(beforeBytes.contentEquals(readBytes(uri)))

        val readOnlyUri = providerUri("readonly-synthetic.mp3")
        val readOnly = read(readOnlyUri)
        val failure = session().write(
            target(readOnlyUri, AudioFormat.MP3, hash(readOnlyUri)),
            FileTagEditMapper.toChange(readOnly, FileTagEdit(title = "Should Fail")),
        )
        assertTrue(failure is MetadataFileWriteOutcome.Failed)
        assertEquals(0, providerOpenDescriptors().get())
    }

    private fun providerClass(): Class<*> = Class.forName(
        "com.theemrock.smoothmedia.metadata.SyntheticFixtureProvider",
    )

    private fun providerUri(name: String): Uri = providerClass()
        .getMethod("uri", String::class.java)
        .invoke(null, name) as Uri

    private fun providerOpenDescriptors(): AtomicInteger = providerClass()
        .getField("openDescriptors")
        .get(null) as AtomicInteger

    private fun createFixture(name: String, bytes: ByteArray) {
        resolver.call(
            providerUri(name),
            "create",
            name,
            Bundle().apply { putByteArray("bytes", bytes) },
        ) ?: error("Provider did not create fixture: $name")
    }

    private fun deleteFixture(name: String) {
        resolver.call(providerUri(name), "delete", name, null)
    }

    private fun session() = MetadataFileWriteSession(resolver, adapter) { locator -> hash(Uri.parse(locator)) }
    private fun read(uri: Uri) = resolver.openFileDescriptor(uri, "r")!!.use { adapter.read(it).getOrThrow() }
    private fun readBytes(uri: Uri): ByteArray = resolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
    private fun hash(uri: Uri): StoredFileHash = SafMetadataHashProvider(resolver)(uri.toString())!!

    private fun target(uri: Uri, format: AudioFormat, hash: StoredFileHash) = MetadataFileWriteTarget(
        trackId = "synthetic-track", locationId = "synthetic-location", sourceId = "synthetic-source",
        locator = uri.toString(), format = format, locationAvailability = LocationAvailability.AVAILABLE,
        sourceAvailability = SourceAvailability.AVAILABLE, sourcePermission = SourcePermissionState.GRANTED,
        sourceSelection = SourceSelectionState.ACTIVE, excluded = false, currentHash = hash,
    )

    private fun syntheticMp3(): ByteArray {
        val picture = byteArrayOf(3) + "image/png\u0000".toByteArray() + byteArrayOf(3, 0) + pngBytes()
        val values = listOf(
            textFrame("TIT2", title), textFrame("TPE1", artist), textFrame("TCON", "Rock"),
            textFrame("TCON", "Anadolu Rock"), txxxFrame("CUSTOM", "keep-me"),
            frame("ZZZZ", unknownId3Payload), frame("APIC", picture),
        )
        val frames = ByteArrayOutputStream().apply { values.forEach(::write) }.toByteArray()
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + synchsafe(frames.size) + frames
        val audio = ByteArray(417) { 0 }.apply {
            this[0] = 0xFF.toByte(); this[1] = 0xFB.toByte(); this[2] = 0x90.toByte(); this[3] = 0x64
        }
        return tag + audio + audio
    }

    private fun syntheticFlac(): ByteArray {
        val streamInfo = ByteArray(34)
        ByteBuffer.wrap(streamInfo).order(ByteOrder.BIG_ENDIAN).putShort(4096.toShort()).putShort(4096.toShort())
        val sampleAndChannels = (44100L shl 44) or (15L shl 36)
        for (shift in 7 downTo 0) streamInfo[18 + (7 - shift)] = (sampleAndChannels ushr (shift * 8)).toByte()
        val values = listOf("TITLE=$title", "ARTIST=$artist", "GENRE=Rock", "GENRE=Anadolu Rock", "CUSTOM=keep-me")
        val comments = ByteArrayOutputStream().apply { values.forEach { write(littleString(it)) } }.toByteArray()
        val vorbis = littleString("Smooth Synthetic") + littleInt(values.size) + comments
        val picture = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(3).array() + bigString("image/png") +
            bigString("synthetic") + ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(1)
                .putInt(24).putInt(0).putInt(pngBytes().size).array() + pngBytes()
        return "fLaC".toByteArray() + block(false, 0, streamInfo) + block(false, 4, vorbis) + block(true, 6, picture)
    }

    private fun frame(id: String, payload: ByteArray) = id.toByteArray() + synchsafe(payload.size) + byteArrayOf(0, 0) + payload
    private fun textFrame(id: String, value: String) = frame(id, byteArrayOf(3) + value.toByteArray())
    private fun txxxFrame(description: String, value: String) =
        frame("TXXX", byteArrayOf(3) + description.toByteArray() + byteArrayOf(0) + value.toByteArray())

    private fun extractId3FramePayload(bytes: ByteArray, id: String): ByteArray {
        require(bytes.copyOfRange(0, 3).contentEquals("ID3".toByteArray()))
        var offset = 10
        val tagSize = synchsafeValue(bytes, 6)
        val end = offset + tagSize
        while (offset + 10 <= end) {
            val frameId = bytes.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val size = synchsafeValue(bytes, offset + 4)
            val payloadStart = offset + 10
            val payloadEnd = payloadStart + size
            if (frameId == id) return bytes.copyOfRange(payloadStart, payloadEnd)
            offset = payloadEnd
        }
        error("ID3 frame not found: $id")
    }

    private fun synchsafeValue(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    private fun synchsafe(value: Int) = byteArrayOf((value ushr 21).toByte(), (value ushr 14 and 0x7F).toByte(), (value ushr 7 and 0x7F).toByte(), (value and 0x7F).toByte())
    private fun littleInt(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun littleString(value: String) = littleInt(value.toByteArray().size) + value.toByteArray()
    private fun bigString(value: String) = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toByteArray().size).array() + value.toByteArray()
    private fun block(last: Boolean, type: Int, payload: ByteArray) = byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (payload.size ushr 16).toByte(), (payload.size ushr 8).toByte(), payload.size.toByte()) + payload
    private fun pngBytes() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
