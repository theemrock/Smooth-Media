package com.theemrock.smoothmedia.eq

/** Deterministic, backend-independent EQ state controller; it does not claim acoustic adaptation. */
class AdaptiveEqController(
    initialMode: EqualizerMode = EqualizerMode.ADAPTIVE,
) {
    var mode: EqualizerMode = initialMode
        private set
    val levels: MutableMap<Short, Int> = linkedMapOf()

    fun setMode(next: EqualizerMode) {
        mode = next
    }

    fun updateMeasured(measured: Map<Short, Int>): Boolean {
        if (mode != EqualizerMode.ADAPTIVE || measured.isEmpty()) return false
        levels.clear()
        measured.toSortedMap().forEach { (band, level) -> levels[band] = EqualizerLevel.clamp(level) }
        return true
    }

    fun setManual(band: Short, levelMb: Int): Boolean {
        if (mode != EqualizerMode.MANUAL) return false
        levels[band] = EqualizerLevel.clamp(levelMb)
        return true
    }

    fun reset() {
        levels.clear()
    }
}
