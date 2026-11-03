package com.theemrock.smoothmedia.eq

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One parametric peaking band (AutoEQ / manual). */
data class PeakBand(val frequencyHz: Double, val gainDb: Double, val q: Double)

/** Parsed correction: preamp plus peaking bands, in the common AutoEQ text format. */
data class EqProfile(val preampDb: Double, val bands: List<PeakBand>)

/** Normalized biquad coefficients (a0 = 1). */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    /** Magnitude response in dB at [frequencyHz]. Used for tests and for display. */
    fun magnitudeDb(frequencyHz: Double, sampleRateHz: Double): Double {
        val w = 2 * PI * frequencyHz / sampleRateHz
        val cw = cos(w)
        val c2w = cos(2 * w)
        val sw = sin(w)
        val s2w = sin(2 * w)
        val numRe = b0 + b1 * cw + b2 * c2w
        val numIm = -(b1 * sw + b2 * s2w)
        val denRe = 1 + a1 * cw + a2 * c2w
        val denIm = -(a1 * sw + a2 * s2w)
        val mag = sqrt(numRe * numRe + numIm * numIm) / sqrt(denRe * denRe + denIm * denIm)
        return 20 * log10(mag)
    }
}

object ParametricEq {
    /** Manual limit per band; live adaptive correction uses a separate ±2 dB limit. */
    const val MAX_MANUAL_GAIN_DB = 12.0
    const val LIVE_CORRECTION_LIMIT_DB = 2.0

    /** RBJ cookbook peaking EQ. */
    fun peaking(band: PeakBand, sampleRateHz: Double): Biquad {
        val gain = clamp(band.gainDb, -MAX_MANUAL_GAIN_DB, MAX_MANUAL_GAIN_DB)
        val a = 10.0.pow(gain / 40.0)
        val w0 = 2 * PI * band.frequencyHz / sampleRateHz
        val alpha = sin(w0) / (2 * band.q)
        val cw = cos(w0)
        val a0 = 1 + alpha / a
        return Biquad(
            b0 = (1 + alpha * a) / a0,
            b1 = (-2 * cw) / a0,
            b2 = (1 - alpha * a) / a0,
            a1 = (-2 * cw) / a0,
            a2 = (1 - alpha / a) / a0,
        )
    }

    /** Parses AutoEQ text such as "Preamp: -6.2 dB" and "Filter 1: ON PK Fc 105 Hz Gain -2.1 dB Q 0.7". */
    fun parseAutoEq(text: String): EqProfile {
        var preamp = 0.0
        val bands = mutableListOf<PeakBand>()
        val filter = Regex("""Filter\s*\d*:\s*ON\s+PK\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+(-?[\d.]+)\s*dB\s+Q\s+([\d.]+)""", RegexOption.IGNORE_CASE)
        val preampRe = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
        text.lineSequence().forEach { line ->
            preampRe.find(line)?.let { preamp = it.groupValues[1].toDouble() }
            filter.find(line)?.let {
                bands += PeakBand(
                    frequencyHz = it.groupValues[1].toDouble(),
                    gainDb = it.groupValues[2].toDouble(),
                    q = it.groupValues[3].toDouble(),
                )
            }
        }
        return EqProfile(preampDb = preamp, bands = bands)
    }

    /** Clamps a live correction to ±[LIVE_CORRECTION_LIMIT_DB]. */
    fun clampLive(gainDb: Double): Double = clamp(gainDb, -LIVE_CORRECTION_LIMIT_DB, LIVE_CORRECTION_LIMIT_DB)

    private fun clamp(value: Double, min: Double, max: Double): Double = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    internal fun near(a: Double, b: Double, tolerance: Double): Boolean = abs(a - b) <= tolerance
}
