package com.fondabec.battage.data

import android.content.Context

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
    }

    companion object {
        private const val PREFS = "fondabec_settings"
        private const val KEY_DARK_MODE = "dark_mode"
    }
}
