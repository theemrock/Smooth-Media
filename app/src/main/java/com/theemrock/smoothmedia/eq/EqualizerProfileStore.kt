package com.theemrock.smoothmedia.eq

import android.content.Context

/** Local preference storage only; it never changes audio files or the catalog schema. */
class EqualizerProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("equalizer_profile", Context.MODE_PRIVATE)

    fun readLevels(): Map<Short, Int> = preferences.getString(KEY_LEVELS, "")
        .orEmpty()
        .split(',')
        .mapNotNull { token ->
            val pair = token.split('=')
            if (pair.size != 2) null else runCatching { pair[0].toShort() to pair[1].toInt() }.getOrNull()
        }
        .toMap()

    fun writeLevels(levels: Map<Short, Int>) {
        val encoded = levels.toSortedMap().entries.joinToString(",") { "${it.key}=${EqualizerLevel.clamp(it.value)}" }
        preferences.edit().putString(KEY_LEVELS, encoded).apply()
    }

    fun isManualEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun writeManualEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_LEVELS = "manual_levels"
        private const val KEY_ENABLED = "manual_enabled"
    }
}
