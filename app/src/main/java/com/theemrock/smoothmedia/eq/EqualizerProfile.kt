package com.theemrock.smoothmedia.eq

import android.media.audiofx.Equalizer

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

/** Applies real manual levels to Android's current Media3 audio session when supported. */
class AndroidEqualizerBackend : AutoCloseable {
    private var effect: Equalizer? = null

    fun attach(audioSessionId: Int): Boolean {
        close()
        if (audioSessionId <= 0) return false
        return runCatching {
            Equalizer(0, audioSessionId).also { it.enabled = true }.also { effect = it }
        }.isSuccess
    }

    fun applyManual(levels: Map<Short, Int>): Boolean {
        val equalizer = effect ?: return false
        val range = equalizer.bandLevelRange
        val low = range[0].toInt().coerceAtLeast(EqualizerLevel.MIN_MILLIBEL)
        val high = range[1].toInt().coerceAtMost(EqualizerLevel.MAX_MILLIBEL)
        return runCatching {
            levels.forEach { (band, level) -> equalizer.setBandLevel(band, level.coerceIn(low, high).toShort()) }
        }.isSuccess
    }

    fun bands(): List<EqualizerBand> {
        val equalizer = effect ?: return emptyList()
        val range = equalizer.bandLevelRange
        val low = range[0].toInt()
        val high = range[1].toInt()
        return (0 until equalizer.numberOfBands.toInt()).map { index ->
            val id = index.toShort()
            EqualizerBand(
                id = id,
                centerFrequencyHz = equalizer.getCenterFreq(id) / 1000,
                minLevelMb = low,
                maxLevelMb = high,
                levelMb = equalizer.getBandLevel(id).toInt(),
            )
        }
    }

    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        effect?.enabled = enabled
        effect != null
    }.getOrDefault(false)

    fun isEnabled(): Boolean = effect?.enabled == true

    fun resetFlat(): Boolean = applyManual(bands().associate { it.id to 0 })

    fun bandCount(): Int = effect?.numberOfBands?.toInt() ?: 0

    override fun close() {
        effect?.release()
        effect = null
    }
}
