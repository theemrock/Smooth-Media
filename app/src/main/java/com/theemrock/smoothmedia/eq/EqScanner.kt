package com.theemrock.smoothmedia.eq

import com.theemrock.smoothmedia.catalog.database.dao.EqDao
import com.theemrock.smoothmedia.catalog.database.entity.EqProfileEntity

/** Decodes a track to mono float chunks: sink(samples, count, sampleRate). */
fun interface PcmDecoder {
    fun decode(uri: String, sink: (FloatArray, Int, Int) -> Unit)
}

interface EqProfileStore {
    fun pendingTrackIds(analyzerVersion: Int): List<String>
    fun save(trackId: String, analyzerVersion: Int, signature: String, windows: ByteArray)
}

class RoomEqProfileStore(private val dao: EqDao, private val now: () -> Long = System::currentTimeMillis) : EqProfileStore {
    override fun pendingTrackIds(analyzerVersion: Int) = dao.trackIdsToScan(analyzerVersion)

    override fun save(trackId: String, analyzerVersion: Int, signature: String, windows: ByteArray) =
        dao.upsertProfile(EqProfileEntity(trackId, analyzerVersion, signature, windows, now()))
}

/**
 * Scans tracks into stored band profiles.
 * shortcut: plain in-process loop, no WorkManager; the scan stops when the app process dies and resumes from
 * the pending list on the next start. Wrap [scanPending] in a worker if background scanning must survive that.
 */
class EqScanner(
    private val decoder: PcmDecoder,
    private val store: EqProfileStore,
    private val uriFor: (String) -> String?,
    private val signatureFor: (String) -> String = { "" },
    /** Called per track with total and spectrum-analysis milliseconds; the rest of the total is file read plus decode. */
    private val onTiming: (String, Long, Long) -> Unit = { _, _, _ -> },
) {
    /** Returns true when a usable profile was stored; a failed track gets an empty blob so it is not retried until the version changes. */
    fun scanTrack(trackId: String): Boolean {
        val started = System.nanoTime()
        val analyzeNanos = LongArray(1)
        val uri = uriFor(trackId)
        val windows = if (uri == null) null else runCatching { analyze(uri, analyzeNanos) }.getOrNull()?.takeIf { it.isNotEmpty() }
        store.save(trackId, EqProfileCodec.ANALYZER_VERSION, signatureFor(trackId), EqProfileCodec.encode(windows.orEmpty()))
        onTiming(trackId, (System.nanoTime() - started) / 1_000_000, analyzeNanos[0] / 1_000_000)
        return windows != null
    }

    /** Scans [priority] tracks first (current and next in queue), then the rest; checks [shouldStop] between tracks. */
    fun scanPending(priority: List<String> = emptyList(), shouldStop: () -> Boolean = { false }): Int {
        val pending = store.pendingTrackIds(EqProfileCodec.ANALYZER_VERSION)
        val ordered = priority.filter { it in pending } + pending.filter { it !in priority }
        var done = 0
        for (id in ordered) {
            if (shouldStop()) break
            if (scanTrack(id)) done++
        }
        return done
    }

    private fun analyze(uri: String, analyzeNanos: LongArray): List<SpectrumWindow> {
        var analyzer: SpectrumAnalyzer? = null
        decoder.decode(uri) { samples, count, rate ->
            val a = analyzer ?: SpectrumAnalyzer(rate).also { analyzer = it }
            val t = System.nanoTime()
            a.feed(samples, count)
            analyzeNanos[0] += System.nanoTime() - t
        }
        return analyzer?.finish().orEmpty()
    }
}
