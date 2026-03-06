package com.pocketpass.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pocketpass_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        val AVATAR_HEX = stringPreferencesKey("avatar_hex")
        val SAVED_MIIS_LIST = stringPreferencesKey("saved_miis_list")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AGE = stringPreferencesKey("user_age")
        val USER_HOBBIES = stringPreferencesKey("user_hobbies")
        val USER_ORIGIN = stringPreferencesKey("user_origin")
        val USER_GREETING = stringPreferencesKey("user_greeting")
        val USER_MOOD = stringPreferencesKey("user_mood")
        val CARD_STYLE = stringPreferencesKey("card_style")
        val MUSIC_VOLUME = stringPreferencesKey("music_volume")
        val PROXIMITY_ENABLED = stringPreferencesKey("proximity_enabled")
        val SFX_ENABLED = stringPreferencesKey("sfx_enabled")
        val SFX_VOLUME = stringPreferencesKey("sfx_volume")
        val TOKEN_BALANCE = stringPreferencesKey("token_balance")
        val PUZZLE_PROGRESS = stringPreferencesKey("puzzle_progress")
    }

    private val gson = Gson()

    val avatarHexFlow: Flow<String?> = context.dataStore.data.map { it[AVATAR_HEX] }
    
    val savedMiisFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[SAVED_MIIS_LIST]
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME] }
    val userAgeFlow: Flow<String?> = context.dataStore.data.map { it[USER_AGE] }
    val userHobbiesFlow: Flow<String?> = context.dataStore.data.map { it[USER_HOBBIES] }
    val userOriginFlow: Flow<String?> = context.dataStore.data.map { it[USER_ORIGIN] }
    val userGreetingFlow: Flow<String> = context.dataStore.data.map { it[USER_GREETING] ?: "Hello! Nice to meet you!" }
    val userMoodFlow: Flow<String> = context.dataStore.data.map { it[USER_MOOD] ?: "HAPPY" }  // Default to HAPPY mood type
    val cardStyleFlow: Flow<String> = context.dataStore.data.map { it[CARD_STYLE] ?: "classic" }
    val musicVolumeFlow: Flow<Float> = context.dataStore.data.map { it[MUSIC_VOLUME]?.toFloatOrNull() ?: 0.3f }
    val proximityEnabledFlow: Flow<Boolean> = context.dataStore.data.map { (it[PROXIMITY_ENABLED] ?: "true") == "true" }
    val sfxEnabledFlow: Flow<Boolean> = context.dataStore.data.map { (it[SFX_ENABLED] ?: "true") == "true" }
    val sfxVolumeFlow: Flow<Float> = context.dataStore.data.map { it[SFX_VOLUME]?.toFloatOrNull() ?: 0.5f }

    // ── Token & Puzzle Swap ──

    val tokenBalanceFlow: Flow<Int> = context.dataStore.data.map {
        it[TOKEN_BALANCE]?.toIntOrNull() ?: 0
    }

    val puzzleProgressFlow: Flow<PuzzleProgress> = context.dataStore.data.map { prefs ->
        val json = prefs[PUZZLE_PROGRESS]
        if (json != null) {
            try {
                gson.fromJson(json, PuzzleProgress::class.java) ?: PuzzleProgress()
            } catch (e: Exception) {
                PuzzleProgress()
            }
        } else {
            PuzzleProgress()
        }
    }

    suspend fun saveAvatarHex(hex: String) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_HEX] = hex

            // Also add to saved list if not present (max 3 Miis)
            val currentListJson = preferences[SAVED_MIIS_LIST]
            val type = object : TypeToken<List<String>>() {}.type
            val currentList: MutableList<String> = if (currentListJson != null) {
                try {
                    val parsed: List<String> = gson.fromJson(currentListJson, type)
                    parsed.toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            if (!currentList.contains(hex)) {
                // Enforce max 3 Miis
                if (currentList.size >= 3) {
                    // Remove the oldest Mii (first in list) to make room
                    currentList.removeAt(0)
                }
                currentList.add(hex)
                preferences[SAVED_MIIS_LIST] = gson.toJson(currentList)
            }
        }
    }

    suspend fun deleteMii(hex: String) {
        context.dataStore.edit { preferences ->
            val currentListJson = preferences[SAVED_MIIS_LIST]
            val type = object : TypeToken<List<String>>() {}.type
            val currentList: MutableList<String> = if (currentListJson != null) {
                try {
                    val parsed: List<String> = gson.fromJson(currentListJson, type)
                    parsed.toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            currentList.remove(hex)
            preferences[SAVED_MIIS_LIST] = gson.toJson(currentList)

            // If we deleted the active Mii, switch to another one
            if (preferences[AVATAR_HEX] == hex && currentList.isNotEmpty()) {
                preferences[AVATAR_HEX] = currentList.first()
            } else if (currentList.isEmpty()) {
                preferences.remove(AVATAR_HEX)
            }
        }
    }

    fun getMiiCount(): Flow<Int> = context.dataStore.data.map { preferences ->
        val json = preferences[SAVED_MIIS_LIST]
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(json, type)
                list.size
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    suspend fun setActiveMii(hex: String) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_HEX] = hex
        }
    }

    suspend fun clearProfile() {
        context.dataStore.edit { preferences ->
            preferences.remove(AVATAR_HEX)
            preferences.remove(USER_NAME)
            preferences.remove(USER_AGE)
            preferences.remove(USER_HOBBIES)
            // We can choose to keep or clear SAVED_MIIS_LIST. Let's keep it so they don't lose created Miis
        }
    }

    suspend fun clearAllMiis() {
        context.dataStore.edit { preferences ->
            preferences.remove(SAVED_MIIS_LIST)
            preferences.remove(AVATAR_HEX)
        }
    }

    suspend fun saveUserProfile(name: String, age: String, hobbies: String, origin: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
            preferences[USER_AGE] = age
            preferences[USER_HOBBIES] = hobbies
            preferences[USER_ORIGIN] = origin
        }
    }

    suspend fun saveGreeting(greeting: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_GREETING] = greeting
        }
    }

    suspend fun saveMood(mood: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_MOOD] = mood
        }
    }

    suspend fun saveCardStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[CARD_STYLE] = style
        }
    }

    suspend fun saveMusicVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[MUSIC_VOLUME] = volume.toString()
        }
    }

    suspend fun saveProximityEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PROXIMITY_ENABLED] = enabled.toString()
        }
    }

    suspend fun saveSfxEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SFX_ENABLED] = enabled.toString()
        }
    }

    suspend fun saveSfxVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[SFX_VOLUME] = volume.toString()
        }
    }

    // ── Token System ──

    suspend fun addTokens(amount: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[TOKEN_BALANCE]?.toIntOrNull() ?: 0
            preferences[TOKEN_BALANCE] = (current + amount).toString()
        }
    }

    /** Returns true if tokens were spent, false if insufficient balance. */
    suspend fun spendTokens(amount: Int): Boolean {
        var success = false
        context.dataStore.edit { preferences ->
            val current = preferences[TOKEN_BALANCE]?.toIntOrNull() ?: 0
            if (current >= amount) {
                preferences[TOKEN_BALANCE] = (current - amount).toString()
                success = true
            }
        }
        return success
    }

    // ── Puzzle Swap ──

    suspend fun savePuzzleProgress(progress: PuzzleProgress) {
        context.dataStore.edit { preferences ->
            preferences[PUZZLE_PROGRESS] = gson.toJson(progress)
        }
    }

    suspend fun addPuzzlePiece(piece: PuzzlePiece) {
        context.dataStore.edit { preferences ->
            val json = preferences[PUZZLE_PROGRESS]
            val current = if (json != null) {
                try {
                    gson.fromJson(json, PuzzleProgress::class.java) ?: PuzzleProgress()
                } catch (e: Exception) {
                    PuzzleProgress()
                }
            } else {
                PuzzleProgress()
            }
            val updated = current.withPiece(piece)
            preferences[PUZZLE_PROGRESS] = gson.toJson(updated)
        }
    }
}