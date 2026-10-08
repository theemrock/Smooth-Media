package com.theemrock.smoothmedia.p01x

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever

class AndroidAudioMetadataReader(
    private val context: Context,
) {
    fun read(audio: DiscoveredAudio): AudioMetadataProbe {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audio.contentUri)
            val technical = readTechnicalMetadata(audio)
            AudioMetadataProbe(
                title = retriever.stringValue(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.stringValue(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.stringValue(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.stringValue(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = retriever.yearValue(),
                trackNumber = retriever.stringValue(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                ),
                discNumber = retriever.stringValue(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                genres = GenreProbe.fromPlatformValue(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                ),
                durationMs = retriever.longValue(MediaMetadataRetriever.METADATA_KEY_DURATION),
                bitrate = technical.bitrate.availableOr(
                    retriever.intValue(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                ),
                sampleRate = technical.sampleRate.availableOr(
                    retriever.intValue(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE),
                ),
                channels = technical.channels,
                artwork = retriever.artworkValue(),
            )
        } catch (error: RuntimeException) {
            unsupportedMetadata(error)
        } finally {
            retriever.close()
        }
    }

    private fun readTechnicalMetadata(audio: DiscoveredAudio): TechnicalMetadata {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, audio.contentUri, null)
            val audioFormat = (0 until extractor.trackCount)
                .map(extractor::getTrackFormat)
                .firstOrNull { format ->
                    format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                ?: return TechnicalMetadata.missing()

            TechnicalMetadata(
                bitrate = audioFormat.intValue(MediaFormat.KEY_BIT_RATE),
                sampleRate = audioFormat.intValue(MediaFormat.KEY_SAMPLE_RATE),
                channels = audioFormat.intValue(MediaFormat.KEY_CHANNEL_COUNT),
            )
        } catch (error: RuntimeException) {
            TechnicalMetadata.unsupported(error)
        } finally {
            extractor.release()
        }
    }

    private fun MediaMetadataRetriever.stringValue(key: Int): ProbeValue<String> {
        val value = extractMetadata(key)?.takeIf(String::isNotBlank)
        return if (value == null) ProbeValue(null, ProbeStatus.MISSING)
        else ProbeValue(value, ProbeStatus.AVAILABLE)
    }

    private fun MediaMetadataRetriever.longValue(key: Int): ProbeValue<Long> {
        val raw = extractMetadata(key) ?: return ProbeValue(null, ProbeStatus.MISSING)
        val value = raw.toLongOrNull()
        return if (value == null) ProbeValue(null, ProbeStatus.INCOMPLETE, raw)
        else ProbeValue(value, ProbeStatus.AVAILABLE)
    }

    private fun MediaMetadataRetriever.intValue(key: Int): ProbeValue<Int> {
        val raw = extractMetadata(key) ?: return ProbeValue(null, ProbeStatus.MISSING)
        val value = raw.toIntOrNull()
        return if (value == null) ProbeValue(null, ProbeStatus.INCOMPLETE, raw)
        else ProbeValue(value, ProbeStatus.AVAILABLE)
    }

    private fun MediaMetadataRetriever.yearValue(): ProbeValue<Int> {
        val raw = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            ?: return ProbeValue(null, ProbeStatus.MISSING)
        val value = YEAR_PATTERN.find(raw)?.value?.toIntOrNull()
        return if (value == null) ProbeValue(null, ProbeStatus.INCOMPLETE, raw)
        else ProbeValue(value, ProbeStatus.AVAILABLE)
    }

    private fun MediaMetadataRetriever.artworkValue(): ArtworkProbe = try {
        val bytes = embeddedPicture?.takeIf { it.isNotEmpty() }
        if (bytes == null) ArtworkProbe(null, ProbeStatus.MISSING)
        else ArtworkProbe(bytes, ProbeStatus.AVAILABLE)
    } catch (error: RuntimeException) {
        ArtworkProbe(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName)
    }

    private fun MediaFormat.intValue(key: String): ProbeValue<Int> =
        if (containsKey(key)) ProbeValue(getInteger(key), ProbeStatus.AVAILABLE)
        else ProbeValue(null, ProbeStatus.MISSING)

    private fun <T> ProbeValue<T>.availableOr(fallback: ProbeValue<T>): ProbeValue<T> =
        if (status == ProbeStatus.AVAILABLE) this else fallback

    private fun unsupportedMetadata(error: RuntimeException): AudioMetadataProbe {
        val unsupportedString = ProbeValue<String>(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName)
        val unsupportedInt = ProbeValue<Int>(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName)
        val unsupportedLong = ProbeValue<Long>(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName)
        return AudioMetadataProbe(
            title = unsupportedString,
            artist = unsupportedString,
            album = unsupportedString,
            albumArtist = unsupportedString,
            year = unsupportedInt,
            trackNumber = unsupportedString,
            discNumber = unsupportedString,
            genres = GenreProbe(emptyList(), ProbeStatus.UNSUPPORTED, error.javaClass.simpleName),
            durationMs = unsupportedLong,
            bitrate = unsupportedInt,
            sampleRate = unsupportedInt,
            channels = unsupportedInt,
            artwork = ArtworkProbe(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName),
        )
    }

    private data class TechnicalMetadata(
        val bitrate: ProbeValue<Int>,
        val sampleRate: ProbeValue<Int>,
        val channels: ProbeValue<Int>,
    ) {
        companion object {
            fun missing() = TechnicalMetadata(
                bitrate = ProbeValue(null, ProbeStatus.MISSING),
                sampleRate = ProbeValue(null, ProbeStatus.MISSING),
                channels = ProbeValue(null, ProbeStatus.MISSING),
            )

            fun unsupported(error: RuntimeException) = TechnicalMetadata(
                bitrate = ProbeValue(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName),
                sampleRate = ProbeValue(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName),
                channels = ProbeValue(null, ProbeStatus.UNSUPPORTED, error.javaClass.simpleName),
            )
        }
    }

    private companion object {
        val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
    }
}
