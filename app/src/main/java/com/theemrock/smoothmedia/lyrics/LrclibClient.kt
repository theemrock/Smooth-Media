package com.theemrock.smoothmedia.lyrics

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Outcome of one lrclib lookup. Retryable covers throttling (429), server errors (5xx) and network failures. */
sealed interface LrclibResult {
    data class Found(val syncedLyrics: String?, val plainLyrics: String?, val instrumental: Boolean) : LrclibResult
    data object NotFound : LrclibResult
    data class Retryable(val reason: String) : LrclibResult
}

/**
 * Minimal client for the public lrclib.net lookup endpoint. No API key is required.
 * Requests are made one at a time by the caller; this class only handles backoff for
 * throttling and transient failures (0.5 s, 2 s, 5 s), as agreed in the design notes.
 */
class LrclibClient(
    private val userAgent: String = USER_AGENT,
    private val openConnection: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun lookup(trackName: String, artistName: String, albumName: String?, durationMs: Long?): LrclibResult {
        val url = buildUrl(trackName, artistName, albumName, durationMs)
        var last: LrclibResult = LrclibResult.Retryable("no attempt")
        for (attempt in 0..BACKOFF_MS.size) {
            last = fetchOnce(url)
            if (last !is LrclibResult.Retryable) return last
            if (attempt < BACKOFF_MS.size) sleepMs(BACKOFF_MS[attempt])
        }
        return last
    }

    internal fun buildUrl(trackName: String, artistName: String, albumName: String?, durationMs: Long?): String {
        val params = linkedMapOf(
            "track_name" to trackName,
            "artist_name" to artistName,
        )
        if (!albumName.isNullOrBlank()) params["album_name"] = albumName
        durationMs?.let { if (it > 0L) params["duration"] = (it / 1000L).coerceIn(1L, 3600L).toString() }
        val query = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$BASE_URL/api/get?$query"
    }

    private fun fetchOnce(url: String): LrclibResult {
        return try {
            val connection = openConnection(url)
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            // The connection is intentionally not disconnected: keep-alive avoids a new TLS handshake per request.
            when (val code = connection.responseCode) {
                200 -> parseGetResponse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                404 -> LrclibResult.NotFound
                429, in 500..599 -> LrclibResult.Retryable("http $code")
                else -> LrclibResult.Retryable("unexpected http $code")
            }
        } catch (failure: IOException) {
            LrclibResult.Retryable(failure.javaClass.simpleName)
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net"
        const val USER_AGENT = "SmoothMedia/1013 (Android; https://github.com/theemrock/Smooth-Media)"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        val BACKOFF_MS = longArrayOf(500L, 2_000L, 5_000L)

        /** Parses a /api/get response body. Pure function, no I/O. */
        fun parseGetResponse(body: String): LrclibResult = try {
            val json = JSONObject(body)
            LrclibResult.Found(
                syncedLyrics = json.optStringOrNull("syncedLyrics"),
                plainLyrics = json.optStringOrNull("plainLyrics"),
                instrumental = json.optBoolean("instrumental", false),
            )
        } catch (_: org.json.JSONException) {
            LrclibResult.Retryable("malformed body")
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() }
        }
    }
}
