package com.example.data

import android.content.Context

/**
 * Lightweight persistence for player cosmetic choices and name.
 * Backed by SharedPreferences so selections survive app restarts.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("poulpe_settings", Context.MODE_PRIVATE)

    var skinName: String?
        get() = prefs.getString(KEY_SKIN, null)
        set(value) { prefs.edit().putString(KEY_SKIN, value).apply() }

    var accessoryName: String?
        get() = prefs.getString(KEY_ACCESSORY, null)
        set(value) { prefs.edit().putString(KEY_ACCESSORY, value).apply() }

    var playerName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) { prefs.edit().putString(KEY_NAME, value).apply() }

    private companion object {
        const val KEY_SKIN = "skin"
        const val KEY_ACCESSORY = "accessory"
        const val KEY_NAME = "player_name"
    }
}
