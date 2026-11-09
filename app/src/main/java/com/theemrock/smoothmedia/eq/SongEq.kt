package com.theemrock.smoothmedia.eq

import android.content.Context
import kotlin.math.max

/** Global default for songs without their own manual override. */
enum class EqMode { AUTO, MANUAL }

/** What is stored for one track: the user's manual override (24 millibel levels) and the scanned windows. */
class SongEqData(val overrideMb: List<Int>?, val windows: List<SpectrumWindow>?)

fun interface SongEqSource {
    fun load(trackId: String): SongEqData
}

sealed interface SongEqPlan {
    /** Pre-existing behaviour: headphone AutoEQ profile plus the manual bands. */
    data object Legacy : SongEqPlan

    class Override(val profile: EqProfile) : SongEqPlan

    class Dynamic(val windows: List<SpectrumWindow>, val preampDb: Double, val strength: Double) : SongEqPlan
}

object SongEqResolver {
    /** Order: the song's manual override, then the scanned profile in AUTO mode, then legacy. */
    fun resolve(mode: EqMode, data: SongEqData, strength: Double = DynamicEq.DEFAULT_STRENGTH): SongEqPlan {
        data.overrideMb?.takeIf { it.size == EqBands24.COUNT }?.let { return SongEqPlan.Override(overrideProfile(it)) }
        val windows = data.windows
        if (mode == EqMode.AUTO && windows != null && windows.any { it.bandDb != null }) {
            return SongEqPlan.Dynamic(windows, DynamicEq.songPreampDb(windows, strength), strength)
        }
        return SongEqPlan.Legacy
    }

    private fun overrideProfile(levelsMb: List<Int>): EqProfile {
        val gains = DoubleArray(EqBands24.COUNT) { (levelsMb[it] / 100.0).coerceIn(-ParametricEq.MAX_MANUAL_GAIN_DB, ParametricEq.MAX_MANUAL_GAIN_DB) }
        return DynamicEq.toProfile(gains, -max(0.0, gains.max()))
    }
}

/** Playback-time state for a [SongEqPlan.Dynamic]: blends windows by position and limits how fast gains move. */
class DynamicSession(private val plan: SongEqPlan.Dynamic) {
    private var previous: DoubleArray? = null

    fun profileAt(positionMs: Long, dtSeconds: Double): EqProfile {
        val target = DynamicEq.gainsAt(plan.windows, positionMs, plan.strength)
        val next = previous?.let { DynamicEq.slew(it, target, dtSeconds) } ?: target
        previous = next
        return DynamicEq.toProfile(next, plan.preampDb)
    }

    /** After a seek the old gains are meaningless; snap to the new position. */
    fun reset() {
        previous = null
    }
}

/**
 * Global EQ mode. Until the first full library scan has finished the default is MANUAL; afterwards AUTO.
 * An explicit choice from settings always wins.
 */
class EqModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("song_eq", Context.MODE_PRIVATE)

    fun mode(): EqMode {
        prefs.getString(KEY_MODE, null)?.let { saved -> EqMode.values().firstOrNull { it.name == saved }?.let { return it } }
        return if (prefs.getBoolean(KEY_FIRST_SCAN_DONE, false)) EqMode.AUTO else EqMode.MANUAL
    }

    fun setMode(mode: EqMode) = prefs.edit().putString(KEY_MODE, mode.name).apply()

    fun markFirstScanComplete() = prefs.edit().putBoolean(KEY_FIRST_SCAN_DONE, true).apply()

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_FIRST_SCAN_DONE = "first_scan_done"
    }
}
