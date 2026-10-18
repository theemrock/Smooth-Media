package com.theemrock.smoothmedia.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class MetadataRecoveryEngineTest {
    @Test
    fun evaluatesHighConfidenceTextRepairPerField() {
        val engine = engine(
            readResult = MetadataReadOnlyResult.Success(
                "track-1",
                "location-1",
                RawAudioMetadata(title = "AÅk YangÄ±nÄ±", artist = "Özlem Tekin"),
                MetadataNormalizer().normalize(RawAudioMetadata(title = "AÅk YangÄ±nÄ±", artist = "Özlem Tekin")),
            ),
        )

        val result = engine.evaluate(listOf("track-1"))
        val fields = result.tracks.single().fields.associateBy { it.field }

        assertEquals(RecoveryDecision.AUTO_REPAIR, fields[RecoveryField.TITLE]!!.decision)
        assertEquals(RecoveryDecision.NO_CHANGE, fields[RecoveryField.ARTIST]!!.decision)
    }

    @Test
    fun preservesOverrideAndDoesNotProposeErasure() {
        val current = snapshot(overrides = mapOf(MetadataOverrideField.TITLE to "Kullanıcı başlığı"))
        val engine = engine(current = current, readResult = success(RawAudioMetadata(title = "AÅk YangÄ±nÄ±")))

        val fields = engine.evaluate(listOf("track-1")).tracks.single().fields.associateBy { it.field }

        assertEquals(RecoveryDecision.NO_CHANGE, fields[RecoveryField.TITLE]!!.decision)
        assertEquals(RecoveryDecision.NO_CHANGE, fields[RecoveryField.ALBUM]!!.decision)
    }

    @Test
    fun marksReplacementCharacterAsUnrecoverable() {
        val engine = engine(readResult = success(RawAudioMetadata(title = "A�k")))

        val title = engine.evaluate(listOf("track-1")).tracks.single().fields
            .single { it.field == RecoveryField.TITLE }

        assertEquals(RecoveryDecision.UNRECOVERABLE, title.decision)
    }

    @Test
    fun readFailureIsReportedForAllFields() {
        val engine = engine(readResult = MetadataReadOnlyResult.NoAccessibleLocation)

        val fields = engine.evaluate(listOf("track-1")).tracks.single().fields

        assertTrue(fields.isNotEmpty())
        assertTrue(fields.all { it.decision == RecoveryDecision.READ_ERROR })
    }

    @Test
    fun ambiguousTextRequiresReviewAndCorrectUnicodeIsNoChange() {
        val engine = engine(
            current = snapshot(artist = "Björk"),
            readResult = success(RawAudioMetadata(title = "Åland", artist = "Björk")),
        )

        val fields = engine.evaluate(listOf("track-1")).tracks.single().fields.associateBy { it.field }

        assertEquals(RecoveryDecision.REVIEW_REQUIRED, fields[RecoveryField.TITLE]!!.decision)
        assertEquals(RecoveryDecision.NO_CHANGE, fields[RecoveryField.ARTIST]!!.decision)
    }

    @Test
    fun isBoundedAndResumableByCursorWithoutWriting() {
        val engine = engine(readResult = success(RawAudioMetadata(title = "AÅk YangÄ±nÄ±")))

        val first = engine.evaluate(List(5) { "track-$it" }, limit = 2)
        val resumed = engine.evaluate(List(5) { "track-$it" }, afterTrackId = first.lastProcessedTrackId, limit = 2)

        assertEquals(2, first.tracks.size)
        assertEquals(2, resumed.tracks.size)
        assertEquals("track-2", resumed.tracks.first().trackId)
    }

    @Test
    fun rejectsConcurrentWorkerWhileFirstEvaluationIsActive() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = MetadataRecoveryEngine(
            read = {
                entered.countDown()
                release.await()
                success(RawAudioMetadata(title = "AÅk YangÄ±nÄ±"))
            },
            current = { snapshot() },
        )

        val first = thread { engine.evaluate(listOf("track-1")) }
        entered.await()
        val second = engine.evaluate(listOf("track-2"))
        release.countDown()
        first.join()

        assertTrue(second.busy)
    }

    private fun engine(
        current: TrackMetadataSnapshot = snapshot(),
        readResult: MetadataReadOnlyResult,
    ) = MetadataRecoveryEngine(
        read = { readResult },
        current = { current },
    )

    private fun success(raw: RawAudioMetadata) = MetadataReadOnlyResult.Success(
        trackId = "track-1",
        locationId = "location-1",
        raw = raw,
        metadata = MetadataNormalizer().normalize(raw),
    )

    private fun snapshot(
        overrides: Map<MetadataOverrideField, String> = emptyMap(),
        artist: String = "Özlem Tekin",
    ) = TrackMetadataSnapshot(
        trackId = "track-1",
        raw = RawAudioMetadata(title = "AÅk YangÄ±nÄ±", artist = artist),
        fileMetadata = NormalizedAudioMetadata(
            title = MetadataField("AÅk YangÄ±nÄ±", MetadataFieldStatus.AVAILABLE, "AÅk YangÄ±nÄ±"),
            artist = MetadataField(artist, MetadataFieldStatus.AVAILABLE),
            album = MetadataField(null, MetadataFieldStatus.MISSING),
            albumArtist = MetadataField(null, MetadataFieldStatus.MISSING),
            genres = MetadataField(null, MetadataFieldStatus.MISSING),
            trackNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            discNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            year = MetadataField(null, MetadataFieldStatus.MISSING),
            durationMs = MetadataField(null, MetadataFieldStatus.MISSING),
        ),
        overrides = overrides,
        effective = NormalizedAudioMetadata(
            title = MetadataField(overrides[MetadataOverrideField.TITLE] ?: "AÅk YangÄ±nÄ±", MetadataFieldStatus.AVAILABLE),
            artist = MetadataField(artist, MetadataFieldStatus.AVAILABLE),
            album = MetadataField(null, MetadataFieldStatus.MISSING),
            albumArtist = MetadataField(null, MetadataFieldStatus.MISSING),
            genres = MetadataField(null, MetadataFieldStatus.MISSING),
            trackNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            discNumber = MetadataField(null, MetadataFieldStatus.MISSING),
            year = MetadataField(null, MetadataFieldStatus.MISSING),
            durationMs = MetadataField(null, MetadataFieldStatus.MISSING),
        ),
    )
}
