package com.theemrock.smoothmedia.eq

/**
 * Manual 5-band EQ rendered inside AutoEqAudioProcessor, so it works on speaker and Bluetooth alike.
 * The platform Equalizer effect is not used: on some routes it silences output even when flat.
 */
object ManualEqBands {
    private val centersHz = listOf(60, 230, 910, 3600, 14000)
    private const val Q = 1.0

    fun bands(levels: Map<Short, Int>): List<EqualizerBand> = centersHz.mapIndexed { index, hz ->
        EqualizerBand(
            id = index.toShort(),
            centerFrequencyHz = hz,
            minLevelMb = EqualizerLevel.MIN_MILLIBEL,
            maxLevelMb = EqualizerLevel.MAX_MILLIBEL,
            levelMb = EqualizerLevel.clamp(levels[index.toShort()] ?: 0),
        )
    }

    /** Peaking filters for non-zero levels; empty when manual EQ is bypassed. */
    fun peakBands(levels: Map<Short, Int>, enabled: Boolean): List<PeakBand> {
        if (!enabled) return emptyList()
        return centersHz.mapIndexedNotNull { index, hz ->
            val level = levels[index.toShort()] ?: 0
            if (level == 0) null else PeakBand(frequencyHz = hz.toDouble(), gainDb = level / 100.0, q = Q)
        }
    }
}
