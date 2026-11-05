package com.theemrock.smoothmedia.lyrics

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Outcome of one lrclib lookup. Retryable covers throttling (429), server errors (5xx) and network failures. */
sealed interface LrclibResult {
    data class Found(val syncedLyrics: String?, val plainLyrics: String?, val instrumental: Boolean) : LrclibResult
    data object NotFound : LrclibResult
    data class Retryable(val reason: String) : LrclibResult
}

/**
 * Spaces requests at least [minIntervalMs] apart across all threads. Each caller reserves its own
 * slot, so parallel workers queue up instead of bursting together.
 */
class RequestGate(
    private val minIntervalMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
) {
    private var nextSlotMs: Long? = null

    fun await() {
        val wait = reserve()
        if (wait > 0) sleepMs(wait)
    }

    @Synchronized
    private fun reserve(): Long {
        val now = nowMs()
        val slot = maxOf(now, nextSlotMs ?: now)
        nextSlotMs = slot + minIntervalMs
        return slot - now
    }
}

/**
 * Minimal client for the public lrclib.net search endpoint (no API key).
 *
 * Uses /api/search with artist and title only, like the approved test script did. The strict
 * /api/get endpoint (which also needs album and exact duration) missed many tracks whose local
 * metadata differs slightly. Results are matched on normalized artist and title; synced lyrics
 * are preferred, then the closest duration.
 *
 * Throttling and transient failures are retried with backoff (0.5 s, 2 s, 5 s).
 */
class LrclibClient(
    private val userAgent: String = USER_AGENT,
    private val openConnection: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    private val requestGate: RequestGate = RequestGate(MIN_REQUEST_INTERVAL_MS),
) {
    fun lookup(trackName: String, artistName: String, albumName: String?, durationMs: Long?): LrclibResult {
        val url = buildSearchUrl(trackName, artistName)
        var last: Fetch = Fetch.Failed("no attempt")
        for (attempt in 0..BACKOFF_MS.size) {
            last = fetchOnce(url)
            if (last !is Fetch.Failed) break
            if (attempt < BACKOFF_MS.size) sleepMs(BACKOFF_MS[attempt])
        }
        return when (val outcome = last) {
            is Fetch.Ok -> pickBest(outcome.records, artistName, trackName, durationMs)
            is Fetch.Failed -> LrclibResult.Retryable(outcome.reason)
        }
    }

    internal fun buildSearchUrl(trackName: String, artistName: String): String {
        val query = listOf(
            "artist_name" to artistName,
            "track_name" to trackName,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return "$BASE_URL/api/search?$query"
    }

    private sealed interface Fetch {
        data class Ok(val records: List<Record>) : Fetch
        data class Failed(val reason: String) : Fetch
    }

    private fun fetchOnce(url: String): Fetch {
        return try {
            requestGate.await()
            val connection = openConnection(url)
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            // The connection is intentionally not disconnected: keep-alive avoids a new TLS handshake per request.
            when (val code = connection.responseCode) {
                200 -> parseSearchResponse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    ?.let { Fetch.Ok(it) } ?: Fetch.Failed("malformed body")
                429, in 500..599 -> Fetch.Failed("http $code")
                else -> Fetch.Failed("unexpected http $code")
            }
        } catch (failure: IOException) {
            Fetch.Failed(failure.javaClass.simpleName)
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net"
        const val USER_AGENT = "SmoothMedia/1014 (Android; https://github.com/theemrock/Smooth-Media)"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        /** Shared spacing between real HTTP requests, across all threads (about 3.3 requests per second at most). */
        const val MIN_REQUEST_INTERVAL_MS = 300L
        /** Retry waits for 503/429/network errors, the sequence used in the approved test (2 s, 5 s, 10 s, 20 s). */
        val BACKOFF_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L)

        /** One record from the search response. Only the fields we use. */
        data class Record(
            val artistName: String,
            val trackName: String,
            val durationSec: Double?,
            val syncedLyrics: String?,
            val plainLyrics: String?,
            val instrumental: Boolean,
        )

        /** Parses a /api/search response (a JSON array). Returns null when the body is not an array. */
        fun parseSearchResponse(body: String): List<Record>? = try {
            val array = JSONArray(body)
            (0 until array.length()).map { i ->
                val json = array.getJSONObject(i)
                Record(
                    artistName = json.optString("artistName", ""),
                    trackName = json.optString("trackName", json.optString("name", "")),
                    durationSec = if (json.has("duration") && !json.isNull("duration")) json.optDouble("duration") else null,
                    syncedLyrics = json.optStringOrNull("syncedLyrics"),
                    plainLyrics = json.optStringOrNull("plainLyrics"),
                    instrumental = json.optBoolean("instrumental", false),
                )
            }
        } catch (_: org.json.JSONException) {
            null
        }

        /**
         * Chooses the best search result: artist and title must match after normalization
         * (exact or one contains the other), then prefer synced lyrics, then the closest duration.
         */
        fun pickBest(records: List<Record>, artistName: String, trackName: String, durationMs: Long?): LrclibResult {
            val wantArtist = normalize(artistName)
            val wantTrack = normalize(trackName)
            val candidates = records.filter { record ->
                val artistOk = normalize(record.artistName).let { it == wantArtist || it.contains(wantArtist) || wantArtist.contains(it) }
                val trackOk = normalize(record.trackName).let { it == wantTrack || it.contains(wantTrack) || wantTrack.contains(it) }
                artistOk && trackOk && wantTrack.isNotEmpty() && wantArtist.isNotEmpty()
            }
            val best = candidates.minWithOrNull(
                compareBy<Record> {
                    // Records with words first: an instrumental version must not hide the sung one.
                    if (it.syncedLyrics.isNullOrBlank() && it.plainLyrics.isNullOrBlank()) 1 else 0
                }
                    // Exact artist+title match first, so short titles do not pick a longer song.
                    .thenBy { if (normalize(it.artistName) == wantArtist && normalize(it.trackName) == wantTrack) 0 else 1 }
                    .thenBy { if (it.syncedLyrics.isNullOrBlank()) 1 else 0 }
                    .thenBy { durationDistance(it.durationSec, durationMs) },
            ) ?: return LrclibResult.NotFound
            return LrclibResult.Found(best.syncedLyrics, best.plainLyrics, best.instrumental)
        }

        private fun durationDistance(recordSec: Double?, wantMs: Long?): Double {
            if (recordSec == null || wantMs == null) return Double.MAX_VALUE / 2
            return kotlin.math.abs(recordSec - wantMs / 1000.0)
        }

        /**
         * Folds case, Turkish letters and accents to ASCII and drops punctuation, so that
         * "Öyle Sarhoş Olsam ki" and "Oyle Sarhos Olsam" compare equal (lrclib's own search is fuzzy in the same way).
         */
        internal fun normalize(value: String): String {
            val folded = value
                .replace('İ', 'I').replace('ı', 'i')
                .replace('ş', 's').replace('Ş', 'S')
                .replace('ğ', 'g').replace('Ğ', 'G')
                .replace('ç', 'c').replace('Ç', 'C')
                .replace('ö', 'o').replace('Ö', 'O')
                .replace('ü', 'u').replace('Ü', 'U')
            val decomposed = java.text.Normalizer.normalize(folded, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            return decomposed.lowercase().filter { it.isLetterOrDigit() }
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() }
        }
    }
}
