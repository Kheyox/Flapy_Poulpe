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

    /** Day ("yyyy-DDD") the daily mission was last completed, or null. */
    var missionLastCompletedDay: String?
        get() = prefs.getString(KEY_MISSION_DAY, null)
        set(value) { prefs.edit().putString(KEY_MISSION_DAY, value).apply() }

    /** Total number of daily missions ever completed (drives mission rewards). */
    var missionsCompletedCount: Int
        get() = prefs.getInt(KEY_MISSION_COUNT, 0)
        set(value) { prefs.edit().putInt(KEY_MISSION_COUNT, value).apply() }

    /** Highest level reachable in "Niveaux" mode (1-based, unlocked sequentially). */
    var maxUnlockedLevel: Int
        get() = prefs.getInt(KEY_MAX_LEVEL, 1)
        set(value) { prefs.edit().putInt(KEY_MAX_LEVEL, value).apply() }

    private companion object {
        const val KEY_SKIN = "skin"
        const val KEY_ACCESSORY = "accessory"
        const val KEY_NAME = "player_name"
        const val KEY_MISSION_DAY = "mission_last_day"
        const val KEY_MISSION_COUNT = "missions_completed"
        const val KEY_MAX_LEVEL = "max_unlocked_level"
    }
}
