package com.theemrock.smoothmedia.eq

import java.nio.ByteBuffer

/** Compact blob for a song's [SpectrumWindow] list: count, then per window centerMs, valid flag, 24 levels in 0.01 dB. */
object EqProfileCodec {
    /** Bump when the analyzer, band layout or window size changes; stored profiles are then rescanned. */
    const val ANALYZER_VERSION = 1

    fun encode(windows: List<SpectrumWindow>): ByteArray {
        val buf = ByteBuffer.allocate(4 + windows.size * (8 + 1 + EqBands24.COUNT * 2))
        buf.putInt(windows.size)
        for (w in windows) {
            buf.putLong(w.centerMs)
            val db = w.bandDb
            buf.put(if (db == null) 0 else 1)
            for (i in 0 until EqBands24.COUNT) buf.putShort(Math.round((db?.get(i) ?: 0.0) * 100).toInt().coerceIn(-32_768, 32_767).toShort())
        }
        return buf.array()
    }

    /** Returns null for a truncated or foreign blob so the caller can treat the track as unscanned. */
    fun decode(bytes: ByteArray): List<SpectrumWindow>? = runCatching {
        val buf = ByteBuffer.wrap(bytes)
        val count = buf.getInt()
        require(count >= 0 && bytes.size == 4 + count * (8 + 1 + EqBands24.COUNT * 2))
        List(count) {
            val center = buf.getLong()
            val valid = buf.get().toInt() != 0
            val levels = DoubleArray(EqBands24.COUNT) { buf.getShort() / 100.0 }
            SpectrumWindow(center, if (valid) levels else null)
        }
    }.getOrNull()

    fun encodeLevels(levelsMb: List<Int>): String = levelsMb.joinToString(",")

    fun decodeLevels(text: String): List<Int>? =
        text.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == EqBands24.COUNT }
}
