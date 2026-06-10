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

    /** Comma-joined ids of the three currently active missions. */
    var activeMissionIds: String
        get() = prefs.getString(KEY_ACTIVE_MISSIONS, "") ?: ""
        set(value) { prefs.edit().putString(KEY_ACTIVE_MISSIONS, value).apply() }

    /** Index of the next mission to draw from the pool when one completes. */
    var missionPoolCursor: Int
        get() = prefs.getInt(KEY_MISSION_CURSOR, 3)
        set(value) { prefs.edit().putInt(KEY_MISSION_CURSOR, value).apply() }

    /** Total number of missions ever completed (drives mission rewards). */
    var missionsCompletedCount: Int
        get() = prefs.getInt(KEY_MISSION_COUNT, 0)
        set(value) { prefs.edit().putInt(KEY_MISSION_COUNT, value).apply() }

    /** Highest level reachable in "Niveaux" mode (1-based, unlocked sequentially). */
    var maxUnlockedLevel: Int
        get() = prefs.getInt(KEY_MAX_LEVEL, 1)
        set(value) { prefs.edit().putInt(KEY_MAX_LEVEL, value).apply() }

    /** Pearl currency banked across runs, spent in the shop and on revives. */
    var pearlBalance: Int
        get() = prefs.getInt(KEY_PEARLS, 0)
        set(value) { prefs.edit().putInt(KEY_PEARLS, value).apply() }

    /** Enum names of every cosmetic bought with pearls. */
    var purchasedCosmetics: Set<String>
        get() = prefs.getStringSet(KEY_PURCHASED, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_PURCHASED, value).apply() }

    // Lifetime stats feeding the achievements system
    var totalGames: Int
        get() = prefs.getInt(KEY_TOTAL_GAMES, 0)
        set(value) { prefs.edit().putInt(KEY_TOTAL_GAMES, value).apply() }

    var totalPearlsCollected: Int
        get() = prefs.getInt(KEY_TOTAL_PEARLS, 0)
        set(value) { prefs.edit().putInt(KEY_TOTAL_PEARLS, value).apply() }

    /** Ids of permanently unlocked achievements. */
    var unlockedAchievements: Set<String>
        get() = prefs.getStringSet(KEY_ACHIEVEMENTS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_ACHIEVEMENTS, value).apply() }

    private companion object {
        const val KEY_SKIN = "skin"
        const val KEY_ACCESSORY = "accessory"
        const val KEY_NAME = "player_name"
        const val KEY_ACTIVE_MISSIONS = "active_missions"
        const val KEY_MISSION_CURSOR = "mission_pool_cursor"
        const val KEY_MISSION_COUNT = "missions_completed"
        const val KEY_MAX_LEVEL = "max_unlocked_level"
        const val KEY_PEARLS = "pearl_balance"
        const val KEY_PURCHASED = "purchased_cosmetics"
        const val KEY_TOTAL_GAMES = "total_games"
        const val KEY_TOTAL_PEARLS = "total_pearls_collected"
        const val KEY_ACHIEVEMENTS = "unlocked_achievements"
    }
}
