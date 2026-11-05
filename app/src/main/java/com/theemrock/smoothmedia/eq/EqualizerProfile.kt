package com.theemrock.smoothmedia.eq

object EqualizerLevel {
    const val MIN_MILLIBEL = -1500
    const val MAX_MILLIBEL = 1500

    fun clamp(level: Int): Int = level.coerceIn(MIN_MILLIBEL, MAX_MILLIBEL)
}

data class AdaptiveEqState(
    val isAvailable: Boolean,
    val levels: Map<Short, Int>,
) {
    companion object {
        fun fromMeasuredLevels(levels: Map<Short, Int>): AdaptiveEqState =
            AdaptiveEqState(levels.isNotEmpty(), levels.mapValues { EqualizerLevel.clamp(it.value) })
    }
}

enum class EqualizerMode { ADAPTIVE, MANUAL }

data class EqualizerBand(
    val id: Short,
    val centerFrequencyHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val levelMb: Int,
)

