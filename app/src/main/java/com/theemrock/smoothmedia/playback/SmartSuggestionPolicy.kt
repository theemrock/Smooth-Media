package com.theemrock.smoothmedia.playback

/**
 * Autoplay suggestion timing (decided: 5-track packs; request the next pack when two
 * unplayed tracks remain in the queue). Pure logic, no Android dependencies.
 */
object SmartSuggestionPolicy {
    const val PACK_SIZE = 5
    const val TRIGGER_REMAINING = 2

    /** True once per queue state: the remaining unplayed count reached the trigger and no pack is pending. */
    fun shouldRequestPack(remainingUnplayed: Int, packPending: Boolean): Boolean =
        !packPending && remainingUnplayed <= TRIGGER_REMAINING
}

/** How the user reacted to a track. Manual choices outweigh automatic skips. */
enum class ListenSignal { MANUAL_PLAY, MANUAL_REPEAT, COMPLETED, EARLY_SKIP, ACCIDENTAL_SKIP }

object ListenScoring {
    /** Accidental skips (under 3 s) count for nothing; a single skip never blacklists a track. */
    const val ACCIDENTAL_SKIP_MS = 3_000L

    /** Early skip = stopped before 30% of the track. */
    const val EARLY_SKIP_FRACTION = 0.3

    fun classifySkip(playedMs: Long, durationMs: Long?): ListenSignal {
        if (playedMs < ACCIDENTAL_SKIP_MS) return ListenSignal.ACCIDENTAL_SKIP
        val duration = durationMs ?: return ListenSignal.EARLY_SKIP
        return if (duration > 0 && playedMs.toDouble() / duration < EARLY_SKIP_FRACTION) {
            ListenSignal.EARLY_SKIP
        } else {
            ListenSignal.COMPLETED
        }
    }

    fun weight(signal: ListenSignal): Int = when (signal) {
        ListenSignal.MANUAL_PLAY -> 3
        ListenSignal.MANUAL_REPEAT -> 2
        ListenSignal.COMPLETED -> 1
        ListenSignal.EARLY_SKIP -> -1
        ListenSignal.ACCIDENTAL_SKIP -> 0
    }
}
