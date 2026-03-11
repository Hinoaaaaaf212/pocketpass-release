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
        val SELECTED_GAMES = stringPreferencesKey("selected_games")
        val UNSEEN_ENCOUNTERS = stringPreferencesKey("unseen_encounters")
        val DUAL_SCREEN_MODE = stringPreferencesKey("dual_screen_mode")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val SELECTED_HAT = stringPreferencesKey("selected_hat")
        val SELECTED_COSTUME = stringPreferencesKey("selected_costume")
        val ENABLE_3D_MIIS = stringPreferencesKey("enable_3d_miis")
        val OWNED_SHOP_ITEMS = stringPreferencesKey("owned_shop_items")
        val STEP_COUNTER_BASELINE = stringPreferencesKey("step_counter_baseline")
        val STEP_TOKENS_TODAY = stringPreferencesKey("step_tokens_today")
        val STEP_TOKENS_DATE = stringPreferencesKey("step_tokens_date")
        val BINGO_PROGRESS = stringPreferencesKey("bingo_progress")
        val SPOTPASS_UNREAD = stringPreferencesKey("spotpass_unread")
        val STREAK_REWARDS_CLAIMED = stringPreferencesKey("streak_rewards_claimed")
    }

    private val gson = Gson()

    /** Combined profile data to reduce recomposition triggers in PlazaScreen */
    data class ProfileData(
        val avatarHex: String? = null,
        val userName: String? = null,
        val userAge: String? = null,
        val userHobbies: String? = null,
        val userOrigin: String? = null,
        val userGreeting: String = "Hello! Nice to meet you!",
        val userMood: String = "HAPPY",
        val cardStyle: String = "classic",
        val selectedGames: List<IgdbGame> = emptyList()
    )

    val profileDataFlow: Flow<ProfileData> = context.dataStore.data.map { prefs ->
        val gamesJson = prefs[SELECTED_GAMES]
        val games = if (gamesJson != null) {
            try {
                val type = object : TypeToken<List<IgdbGame>>() {}.type
                gson.fromJson<List<IgdbGame>>(gamesJson, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else emptyList()

        ProfileData(
            avatarHex = prefs[AVATAR_HEX],
            userName = prefs[USER_NAME],
            userAge = prefs[USER_AGE],
            userHobbies = prefs[USER_HOBBIES],
            userOrigin = prefs[USER_ORIGIN],
            userGreeting = prefs[USER_GREETING] ?: "Hello! Nice to meet you!",
            userMood = prefs[USER_MOOD] ?: "HAPPY",
            cardStyle = prefs[CARD_STYLE] ?: "classic",
            selectedGames = games
        )
    }

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
    val dualScreenModeFlow: Flow<Boolean> = context.dataStore.data.map { (it[DUAL_SCREEN_MODE] ?: "true") == "true" }
    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { (it[DARK_MODE] ?: "false") == "true" }
    val selectedHatFlow: Flow<String?> = context.dataStore.data.map { it[SELECTED_HAT] }
    val selectedCostumeFlow: Flow<String?> = context.dataStore.data.map { it[SELECTED_COSTUME] }
    val enable3dMiisFlow: Flow<Boolean> = context.dataStore.data.map { (it[ENABLE_3D_MIIS] ?: "true") == "true" }

    // ── Unseen Encounters (for LED notification) ──

    val unseenEncountersFlow: Flow<Int> = context.dataStore.data.map {
        it[UNSEEN_ENCOUNTERS]?.toIntOrNull() ?: 0
    }

    suspend fun incrementUnseenEncounters() {
        context.dataStore.edit { preferences ->
            val current = preferences[UNSEEN_ENCOUNTERS]?.toIntOrNull() ?: 0
            preferences[UNSEEN_ENCOUNTERS] = (current + 1).toString()
        }
    }

    suspend fun clearUnseenEncounters() {
        context.dataStore.edit { preferences ->
            preferences[UNSEEN_ENCOUNTERS] = "0"
        }
    }

    // ── SpotPass Unread (lightweight counter for LED indicator) ──

    val spotPassUnreadFlow: Flow<Int> = context.dataStore.data.map {
        it[SPOTPASS_UNREAD]?.toIntOrNull() ?: 0
    }

    suspend fun setSpotPassUnread(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[SPOTPASS_UNREAD] = count.toString()
        }
    }

    suspend fun clearSpotPassUnread() {
        context.dataStore.edit { preferences ->
            preferences[SPOTPASS_UNREAD] = "0"
        }
    }

    // ── Favourite Games ──

    val selectedGamesFlow: Flow<List<IgdbGame>> = context.dataStore.data.map { prefs ->
        val json = prefs[SELECTED_GAMES]
        if (json != null) {
            try {
                val type = object : TypeToken<List<IgdbGame>>() {}.type
                gson.fromJson<List<IgdbGame>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun saveSelectedGames(games: List<IgdbGame>) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_GAMES] = gson.toJson(games)
        }
    }

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

    /** Wipe all DataStore preferences. Used when deleting the account. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
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

    suspend fun saveAge(age: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_AGE] = age
        }
    }

    suspend fun saveHobbies(hobbies: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_HOBBIES] = hobbies
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

    suspend fun saveDualScreenMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DUAL_SCREEN_MODE] = enabled.toString()
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled.toString()
        }
    }

    suspend fun saveSelectedHat(hatFileName: String?) {
        context.dataStore.edit { preferences ->
            if (hatFileName != null) {
                preferences[SELECTED_HAT] = hatFileName
            } else {
                preferences.remove(SELECTED_HAT)
            }
        }
    }

    suspend fun saveSelectedCostume(costumeFileName: String?) {
        context.dataStore.edit { preferences ->
            if (costumeFileName != null) {
                preferences[SELECTED_COSTUME] = costumeFileName
            } else {
                preferences.remove(SELECTED_COSTUME)
            }
        }
    }

    suspend fun saveEnable3dMiis(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_3D_MIIS] = enabled.toString()
        }
    }

    // ── Bingo ──

    val bingoProgressFlow: Flow<BingoProgress> = context.dataStore.data.map { prefs ->
        val json = prefs[BINGO_PROGRESS]
        if (json != null) {
            try {
                gson.fromJson(json, BingoProgress::class.java) ?: BingoProgress()
            } catch (e: Exception) {
                BingoProgress()
            }
        } else {
            BingoProgress()
        }
    }

    suspend fun saveBingoProgress(progress: BingoProgress) {
        context.dataStore.edit { preferences ->
            preferences[BINGO_PROGRESS] = gson.toJson(progress)
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

    // ── Step Tokens ──

    /**
     * Process step sensor event. Compares against a stored baseline to count
     * steps taken since tracking started. Awards 1 token per [TokenSystem.STEPS_PER_TOKEN]
     * steps, up to [TokenSystem.MAX_STEP_TOKENS_PER_DAY] tokens per day.
     *
     * @param totalStepsSinceBoot the raw TYPE_STEP_COUNTER value
     * @return number of tokens awarded in this call (0 if cap reached or not enough steps)
     */
    suspend fun processSteps(totalStepsSinceBoot: Int, maxStepTokensOverride: Int? = null): Int {
        var awarded = 0
        val dailyCap = maxStepTokensOverride ?: TokenSystem.MAX_STEP_TOKENS_PER_DAY
        context.dataStore.edit { prefs ->
            val today = java.time.LocalDate.now().toString()
            val savedDate = prefs[STEP_TOKENS_DATE] ?: ""

            // Reset daily counter if the date has changed
            var tokensToday = if (savedDate == today) {
                prefs[STEP_TOKENS_TODAY]?.toIntOrNull() ?: 0
            } else {
                0
            }

            if (tokensToday >= dailyCap) return@edit

            val baseline = prefs[STEP_COUNTER_BASELINE]?.toIntOrNull()
            if (baseline == null) {
                // First time — set baseline, no tokens yet
                prefs[STEP_COUNTER_BASELINE] = totalStepsSinceBoot.toString()
                prefs[STEP_TOKENS_DATE] = today
                prefs[STEP_TOKENS_TODAY] = tokensToday.toString()
                return@edit
            }

            val stepsSinceBaseline = totalStepsSinceBoot - baseline
            if (stepsSinceBaseline < TokenSystem.STEPS_PER_TOKEN) return@edit

            // How many tokens can we award from steps taken?
            val earnableTokens = stepsSinceBaseline / TokenSystem.STEPS_PER_TOKEN
            val remaining = dailyCap - tokensToday
            awarded = minOf(earnableTokens, remaining)

            if (awarded > 0) {
                val current = prefs[TOKEN_BALANCE]?.toIntOrNull() ?: 0
                prefs[TOKEN_BALANCE] = (current + awarded).toString()
                tokensToday += awarded
                // Advance baseline by the steps that were "consumed" for tokens
                prefs[STEP_COUNTER_BASELINE] = (baseline + awarded * TokenSystem.STEPS_PER_TOKEN).toString()
            }

            prefs[STEP_TOKENS_DATE] = today
            prefs[STEP_TOKENS_TODAY] = tokensToday.toString()
        }
        return awarded
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

    // ── Shop Items ──

    val ownedShopItemsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[OWNED_SHOP_ITEMS]
        val stored = if (json != null) {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson<Set<String>>(json, type) ?: emptySet()
            } catch (e: Exception) { emptySet() }
        } else emptySet()
        // Free items (price == 0) are always considered owned
        val freeIds = ShopItems.getAll().filter { it.price == 0 }.map { it.id }.toSet()
        stored + freeIds
    }

    // ── Streak Rewards ──

    val claimedStreakRewardsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[STREAK_REWARDS_CLAIMED]
        if (json != null) {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson<Set<String>>(json, type) ?: emptySet()
            } catch (e: Exception) { emptySet() }
        } else emptySet()
    }

    suspend fun claimStreakReward(friendId: String, tier: StreakTier) {
        context.dataStore.edit { preferences ->
            val json = preferences[STREAK_REWARDS_CLAIMED]
            val current: MutableSet<String> = if (json != null) {
                try {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val parsed: Set<String> = gson.fromJson(json, type) ?: emptySet()
                    parsed.toMutableSet()
                } catch (e: Exception) { mutableSetOf() }
            } else { mutableSetOf() }
            current.add("${friendId}_${tier.name}")
            preferences[STREAK_REWARDS_CLAIMED] = gson.toJson(current)

            // Award tokens
            val currentTokens = preferences[TOKEN_BALANCE]?.toIntOrNull() ?: 0
            preferences[TOKEN_BALANCE] = (currentTokens + tier.reward).toString()
        }
    }

    /** Marks a streak reward as claimed WITHOUT awarding tokens. Used during cloud restore. */
    suspend fun markStreakRewardClaimed(friendId: String, tier: StreakTier) {
        context.dataStore.edit { preferences ->
            val json = preferences[STREAK_REWARDS_CLAIMED]
            val current: MutableSet<String> = if (json != null) {
                try {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val parsed: Set<String> = gson.fromJson(json, type) ?: emptySet()
                    parsed.toMutableSet()
                } catch (e: Exception) { mutableSetOf() }
            } else { mutableSetOf() }
            current.add("${friendId}_${tier.name}")
            preferences[STREAK_REWARDS_CLAIMED] = gson.toJson(current)
        }
    }

    /** Adds item to owned set. Does NOT spend tokens — caller handles that. */
    suspend fun purchaseShopItem(itemId: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[OWNED_SHOP_ITEMS]
            val current: MutableSet<String> = if (json != null) {
                try {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val parsed: Set<String> = gson.fromJson(json, type) ?: emptySet()
                    parsed.toMutableSet()
                } catch (e: Exception) { mutableSetOf() }
            } else { mutableSetOf() }
            current.add(itemId)
            preferences[OWNED_SHOP_ITEMS] = gson.toJson(current)
        }
    }
}