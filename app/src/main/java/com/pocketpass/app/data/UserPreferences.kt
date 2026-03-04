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
}