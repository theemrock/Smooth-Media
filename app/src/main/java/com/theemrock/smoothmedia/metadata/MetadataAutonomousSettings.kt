package com.theemrock.smoothmedia.metadata

import android.content.Context

interface MetadataAutonomousSettings {
    val enabled: Boolean
    fun setEnabled(enabled: Boolean)
}

class SharedPreferencesMetadataAutonomousSettings(context: Context) : MetadataAutonomousSettings {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override val enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFERENCES = "metadata_recovery_settings"
        private const val KEY_ENABLED = "autonomous_apply_enabled"
    }
}
