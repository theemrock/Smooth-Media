package com.theemrock.smoothmedia.eq

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for song-synced EQ: stored band levels in, per-band gains out.
 * Levels are stored, not gains, so strength can change without rescanning.
 */
object DynamicEq {
    const val MAX_GAIN_DB = 6.0
    const val DEFAULT_STRENGTH = 0.5
    const val MAX_SLEW_DB_PER_SEC = 2.0
    private const val BAND_Q = 3.0

    /** shortcut: placeholder tilt, not calibrated; measure the library median spectrum, freeze it here and bump the analyzer version. */
    private const val TARGET_TILT_DB_PER_OCTAVE = -1.5

    private val targetDb = DoubleArray(EqBands24.COUNT) {
        TARGET_TILT_DB_PER_OCTAVE * ln(EqBands24.centersHz[it] / 1000.0) / ln(2.0)
    }

    /** Gain per band that moves [levelDb] toward the target shape; overall loudness is ignored. */
    fun gainsFor(levelDb: DoubleArray, strength: Double = DEFAULT_STRENGTH): DoubleArray {
        val levelMean = levelDb.average()
        val targetMean = targetDb.average()
        return DoubleArray(EqBands24.COUNT) {
            val wanted = (targetDb[it] - targetMean) - (levelDb[it] - levelMean)
            (strength * wanted).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)
        }
    }

    /** Constant preamp for the whole song: cancels the loudest boost so peaks do not clip. */
    fun songPreampDb(windows: List<SpectrumWindow>, strength: Double = DEFAULT_STRENGTH): Double {
        val maxBoost = windows.mapNotNull { it.bandDb }.maxOfOrNull { gainsFor(it, strength).max() } ?: 0.0
        return -max(0.0, maxBoost)
    }

    /** Band levels at [positionMs], linearly blended between the nearest valid windows. Null if none is valid. */
    fun levelsAt(windows: List<SpectrumWindow>, positionMs: Long): DoubleArray? {
        val valid = windows.filter { it.bandDb != null }
        if (valid.isEmpty()) return null
        val next = valid.indexOfFirst { it.centerMs >= positionMs }
        if (next == 0) return valid.first().bandDb
        if (next < 0) return valid.last().bandDb
        val a = valid[next - 1]
        val b = valid[next]
        val t = (positionMs - a.centerMs).toDouble() / (b.centerMs - a.centerMs)
        return DoubleArray(EqBands24.COUNT) { a.bandDb!![it] + t * (b.bandDb!![it] - a.bandDb[it]) }
    }

    fun gainsAt(windows: List<SpectrumWindow>, positionMs: Long, strength: Double = DEFAULT_STRENGTH): DoubleArray =
        levelsAt(windows, positionMs)?.let { gainsFor(it, strength) } ?: DoubleArray(EqBands24.COUNT)

    /** Moves [previous] toward [target] by at most [MAX_SLEW_DB_PER_SEC] over [dtSeconds], so the EQ never pumps. */
    fun slew(previous: DoubleArray, target: DoubleArray, dtSeconds: Double): DoubleArray {
        val step = MAX_SLEW_DB_PER_SEC * dtSeconds
        return DoubleArray(EqBands24.COUNT) {
            val delta = target[it] - previous[it]
            previous[it] + if (abs(delta) <= step) delta else if (delta > 0) step else -step
        }
    }

    /**
     * shortcut: each band is its own peaking filter, no least-squares fit for overlap between neighbours;
     * upgrade if measured response drifts from the wanted gains.
     */
    fun toProfile(gains: DoubleArray, preampDb: Double): EqProfile = EqProfile(
        preampDb = min(0.0, preampDb),
        bands = gains.indices.map { PeakBand(EqBands24.centersHz[it], gains[it], BAND_Q) },
    )
}
