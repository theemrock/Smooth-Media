package com.theemrock.smoothmedia.eq

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Manual 24-band EQ rendered inside AutoEqAudioProcessor, so it works on speaker and Bluetooth alike.
 * Same centers as the song analysis bands. The platform Equalizer effect is not used:
 * on some routes it silences output even when flat.
 */
object ManualEqBands {
    private const val Q = 3.0

    fun bands(levels: Map<Short, Int>): List<EqualizerBand> = EqBands24.centersHz.mapIndexed { index, hz ->
        EqualizerBand(
            id = index.toShort(),
            centerFrequencyHz = hz.roundToInt(),
            minLevelMb = EqualizerLevel.MIN_MILLIBEL,
            maxLevelMb = EqualizerLevel.MAX_MILLIBEL,
            levelMb = EqualizerLevel.clamp(levels[index.toShort()] ?: 0),
        )
    }

    /** Peaking filters for non-zero levels; empty when manual EQ is bypassed. */
    fun peakBands(levels: Map<Short, Int>, enabled: Boolean): List<PeakBand> {
        if (!enabled) return emptyList()
        return EqBands24.centersHz.indices.mapNotNull { index ->
            val level = levels[index.toShort()] ?: 0
            if (level == 0) null else PeakBand(frequencyHz = EqBands24.centersHz[index], gainDb = level / 100.0, q = Q)
        }
    }

    /** Preamp that cancels the largest manual boost so stacked neighbouring bands do not clip. */
    fun preampDb(bands: List<PeakBand>): Double = -max(0.0, bands.maxOfOrNull { it.gainDb } ?: 0.0)
}
