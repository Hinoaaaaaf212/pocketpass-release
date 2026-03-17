package com.pocketpass.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pocketpass.app.data.crypto.CryptoManager
import com.pocketpass.app.data.crypto.decryptFields
import com.pocketpass.app.data.crypto.encryptFields
import com.pocketpass.app.rendering.MiiStudioDecoder
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class SyncRepository(private val context: Context) {

    private val TAG = "SyncRepository"
    private val client = SupabaseClient.client
    private val authRepo = AuthRepository()
    private val gson = Gson()

    // ── Profile Sync ──

    /**
     * Push local profile to Supabase.
     * If the local username is blank but the server has one, pull it down first.
     * @param userNameOverride If non-null, use this value instead of reading from DataStore.
     *   Needed during sign-up where the DataStore write may not have flushed yet.
     */
    suspend fun syncProfile(userNameOverride: String? = null) = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        val prefs = UserPreferences(context)

        try {
            var profile = buildProfileFromLocal(userId, prefs)

            // Apply override if provided (sign-up race condition fix)
            if (!userNameOverride.isNullOrBlank()) {
                profile = profile.copy(userName = userNameOverride)
            }

            // If local username is still blank, pull from server before pushing
            if (profile.userName.isBlank()) {
                try {
                    val serverProfiles = client.postgrest["profiles"].select {
                        filter { eq("id", userId) }
                    }.decodeList<SupabaseProfile>()
                    val serverName = serverProfiles.firstOrNull()?.userName
                    if (!serverName.isNullOrBlank()) {
                        profile = profile.copy(userName = serverName)
                        // Save to local DataStore so UI picks it up
                        prefs.saveUserName(serverName)
                        Log.d(TAG, "Pulled username from server: $serverName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to pull username from server: ${e.message}")
                }
            }

            // Include crypto public key if initialized
            if (CryptoManager.isInitialized) {
                profile = profile.copy(publicKey = CryptoManager.getPublicKeyBase64())
            }

            client.postgrest["profiles"].update(profile) {
                filter { eq("id", userId) }
            }
            Log.d(TAG, "Profile synced to Supabase (userName=${profile.userName})")
        } catch (e: Exception) {
            Log.e(TAG, "Profile sync failed: ${e.message}", e)
        }
    }

    // ── Encounter Sync ──

    suspend fun syncEncounters() = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        val db = PocketPassDatabase.getDatabase(context)
        val dao = db.encounterDao()

        try {
            // 1. Get all local encounters
            val localEncounters = dao.getAllEncountersFlow().first()

            // 2. Get all remote encounters
            val remoteEncounters = client.postgrest["encounters"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabaseEncounter>()

            val remoteMap = remoteEncounters.associateBy { it.encounterId }
            val localMap = localEncounters.associateBy { it.encounterId }

            // 3. Push local encounters not in remote
            val toUpload = localEncounters.filter { it.encounterId !in remoteMap }
            for (encounter in toUpload) {
                val supabaseEncounter = encounter.toSupabase(userId).encryptFields()
                client.postgrest["encounters"].upsert(supabaseEncounter)
                dao.markSynced(encounter.encounterId)
            }

            // 4. Pull remote encounters not in local (excluding soft-deleted)
            val toDownload = remoteEncounters.filter {
                it.encounterId !in localMap && it.deletedAt == null
            }
            for (remote in toDownload) {
                dao.insertEncounter(remote.decryptFields().toLocal())
            }

            // 5. Merge conflicts (same encounterId exists both places)
            for (local in localEncounters) {
                val remote = remoteMap[local.encounterId] ?: continue
                if (remote.deletedAt != null) {
                    // Remote was deleted, delete locally
                    dao.deleteEncounter(local)
                    continue
                }
                // Decrypt remote for comparison
                val decryptedRemote = remote.decryptFields()
                // Take the higher meet_count and latest timestamp
                val mergedMeetCount = maxOf(local.meetCount, decryptedRemote.meetCount)
                val mergedTimestamp = maxOf(local.timestamp, decryptedRemote.timestamp)
                // Decode correct gender from avatar hex
                val mergedIsMale = if (local.otherUserAvatarHex.isNotBlank())
                    MiiStudioDecoder.isMale(local.otherUserAvatarHex) else local.isMale
                if (mergedMeetCount != local.meetCount || mergedTimestamp != local.timestamp || mergedIsMale != local.isMale) {
                    dao.updateEncounter(
                        local.copy(
                            meetCount = mergedMeetCount,
                            timestamp = mergedTimestamp,
                            isMale = mergedIsMale,
                            needsSync = false
                        )
                    )
                }
                // Push merged version back to remote (encrypted)
                val merged = local.copy(
                    meetCount = mergedMeetCount,
                    timestamp = mergedTimestamp,
                    isMale = mergedIsMale
                ).toSupabase(userId).encryptFields()
                client.postgrest["encounters"].upsert(merged)
                dao.markSynced(local.encounterId)
            }

            Log.d(TAG, "Encounters synced: ${toUpload.size} up, ${toDownload.size} down")
        } catch (e: Exception) {
            Log.e(TAG, "Encounter sync failed: ${e.message}", e)
        }
    }

    // ── Immediate Push (called after new encounter) ──

    suspend fun pushEncounter(encounter: Encounter) = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        try {
            client.postgrest["encounters"].upsert(encounter.toSupabase(userId).encryptFields())
            val db = PocketPassDatabase.getDatabase(context)
            db.encounterDao().markSynced(encounter.encounterId)
        } catch (e: Exception) {
            Log.e(TAG, "Push encounter failed: ${e.message}", e)
            // Will be caught on next full sync
        }
    }

    // ── Soft-delete encounter on remote ──

    suspend fun softDeleteEncounter(encounterId: String) = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        try {
            client.postgrest["encounters"].update(
                SoftDeletePayload(deletedAt = java.time.Instant.now().toString())
            ) {
                filter {
                    eq("user_id", userId)
                    eq("encounter_id", encounterId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Soft delete failed: ${e.message}", e)
        }
    }

    // ── Leaderboard Sync ──

    suspend fun syncLeaderboard() = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        val prefs = UserPreferences(context)
        val db = PocketPassDatabase.getDatabase(context)

        try {
            val encounters = db.encounterDao().getAllEncountersFlow().first()
            val snap = prefs.snapshot()
            val puzzleProgressJson = snap[UserPreferences.PUZZLE_PROGRESS]
            val puzzleProgress = if (puzzleProgressJson != null) {
                try { gson.fromJson(puzzleProgressJson, PuzzleProgress::class.java) ?: PuzzleProgress() }
                catch (_: Exception) { PuzzleProgress() }
            } else PuzzleProgress()
            val claimedSpotPass = try { SpotPassRepository(context).getClaimedPuzzlePanels() } catch (_: Exception) { emptyList() }
            val panels = PuzzlePanels.getAllIncludingSpotPass(claimedSpotPass)

            val avatarHex = snap[UserPreferences.AVATAR_HEX] ?: ""
            val entry = SupabaseLeaderboardEntry(
                userId = userId,
                userName = snap[UserPreferences.USER_NAME] ?: "",
                avatarHex = avatarHex,
                origin = snap[UserPreferences.USER_ORIGIN] ?: "",
                totalEncounters = encounters.sumOf { it.meetCount },
                uniqueEncounters = encounters.size,
                uniqueRegions = encounters.map { it.origin }.distinct().size,
                puzzlesCompleted = panels.count { puzzleProgress.isPanelComplete(it) },
                achievementsUnlocked = Achievements.getAll().count { it.isUnlocked(encounters) },
                isMale = if (avatarHex.isNotBlank()) MiiStudioDecoder.isMale(avatarHex) else true
            )
            client.postgrest["leaderboards"].upsert(entry)
            Log.d(TAG, "Leaderboard synced")
        } catch (e: Exception) {
            Log.e(TAG, "Leaderboard sync failed: ${e.message}", e)
        }
    }

    // ── Fetch Leaderboard ──

    suspend fun fetchLeaderboard(
        sortBy: String = "total_encounters",
        limit: Int = 50
    ): List<SupabaseLeaderboardEntry> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["leaderboards"]
                .select {
                    filter { neq("user_name", "") }
                    order(sortBy, Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SupabaseLeaderboardEntry>()
        } catch (e: Exception) {
            Log.e(TAG, "Fetch leaderboard failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Restore from cloud (used during setup when logging into existing account) ──

    suspend fun restoreFromCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext Result.failure(Exception("Not logged in"))
        val prefs = UserPreferences(context)

        try {
            // 1. Pull profile from Supabase
            val profiles = client.postgrest["profiles"].select {
                filter { eq("id", userId) }
            }.decodeList<SupabaseProfile>()

            val profile = profiles.firstOrNull()
                ?: return@withContext Result.failure(Exception("No profile found for this account"))

            // 2. Restore profile data to local DataStore
            if (profile.userName.isNotBlank()) {
                prefs.saveUserProfile(
                    name = profile.userName,
                    age = profile.age,
                    hobbies = profile.hobbies,
                    origin = profile.origin
                )
            }
            if (profile.avatarHex.isNotBlank()) {
                prefs.saveAvatarHex(profile.avatarHex)
            }
            // Restore saved Miis list
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                val savedMiis: List<String> = gson.fromJson(profile.savedMiisList.toString(), type) ?: emptyList()
                for (hex in savedMiis) {
                    if (hex.isNotBlank()) prefs.saveAvatarHex(hex)
                }
                // Set the main avatar as active
                if (profile.avatarHex.isNotBlank()) prefs.setActiveMii(profile.avatarHex)
            } catch (_: Exception) { }

            prefs.saveGreeting(profile.greeting)
            prefs.saveMood(profile.mood)
            prefs.saveCardStyle(profile.cardStyle)
            prefs.saveMusicVolume(profile.musicVolume)
            prefs.saveProximityEnabled(profile.proximityEnabled)
            prefs.saveSfxEnabled(profile.sfxEnabled)
            prefs.saveSfxVolume(profile.sfxVolume)

            // Restore token balance
            if (profile.tokenBalance > 0) prefs.addTokens(profile.tokenBalance)

            // Restore puzzle progress
            try {
                val progress = gson.fromJson(profile.puzzleProgress.toString(), PuzzleProgress::class.java)
                if (progress != null) prefs.savePuzzleProgress(progress)
            } catch (_: Exception) { }

            // Restore selected games
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<IgdbGame>>() {}.type
                val games: List<IgdbGame> = gson.fromJson(profile.selectedGames.toString(), type) ?: emptyList()
                if (games.isNotEmpty()) prefs.saveSelectedGames(games)
            } catch (_: Exception) { }

            // 3. Pull encounters from Supabase into local Room DB
            val db = PocketPassDatabase.getDatabase(context)
            val dao = db.encounterDao()
            val remoteEncounters = client.postgrest["encounters"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<SupabaseEncounter>()

            var restored = 0
            for (remote in remoteEncounters) {
                if (remote.deletedAt != null) continue
                val decrypted = remote.decryptFields()
                val existing = dao.getEncounterByUserName(decrypted.otherUserName)
                if (existing == null) {
                    dao.insertEncounter(decrypted.toLocal())
                    restored++
                }
            }

            // 4. Restore streak rewards from Supabase
            try {
                val remoteStreaks = StreakSync.pullStreaks(userId)
                for (streak in remoteStreaks) {
                    for (reward in streak.rewardsClaimed) {
                        // reward is "friendId_TIER", extract tier
                        val tierName = reward.substringAfterLast("_")
                        val tier = try { StreakTier.valueOf(tierName) } catch (_: Exception) { null }
                        if (tier != null) {
                            prefs.markStreakRewardClaimed(streak.friendId, tier)
                        }
                    }
                }
                Log.d(TAG, "Restored streak rewards from ${remoteStreaks.size} conversations")
            } catch (_: Exception) { }

            Log.d(TAG, "Restore complete: profile + $restored encounters")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore from cloud failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Refresh Friend Encounters ──

    /**
     * For each accepted friend, fetch their latest profile from Supabase
     * and update the local encounter with current avatar, name, isMale, etc.
     * This picks up changes when a friend edits their Mii or profile.
     */
    suspend fun refreshFriendEncounters() = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        val db = PocketPassDatabase.getDatabase(context)
        val dao = db.encounterDao()
        val friendRepo = FriendRepository()

        try {
            val friendIds = friendRepo.getAcceptedFriendIds()
            if (friendIds.isEmpty()) return@withContext

            // Batch-fetch all friend encounters in one query instead of N individual queries
            val encountersByUserId = dao.getEncountersByOtherUserIds(friendIds.toList())
                .associateBy { it.otherUserId }

            // Batch-fetch all friend profiles in one network call
            val profilesById = friendRepo.getProfilesByIds(friendIds.toList())

            for (friendId in friendIds) {
                val encounter = encountersByUserId[friendId] ?: continue
                val profile = profilesById[friendId] ?: continue

                // Determine isMale from their profile
                val friendIsMale = profile.isMale

                // Check if anything changed
                if (encounter.otherUserAvatarHex != profile.avatarHex ||
                    encounter.otherUserName != profile.userName ||
                    encounter.isMale != friendIsMale ||
                    encounter.greeting != profile.greeting ||
                    encounter.origin != profile.origin
                ) {
                    dao.updateEncounter(encounter.copy(
                        otherUserAvatarHex = profile.avatarHex,
                        otherUserName = profile.userName,
                        isMale = friendIsMale,
                        greeting = profile.greeting,
                        origin = profile.origin,
                        age = profile.age,
                        hobbies = profile.hobbies
                    ))
                    Log.d(TAG, "Updated friend encounter for ${profile.userName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh friend encounters: ${e.message}", e)
        }
    }

    // ── Streak Sync ──

    suspend fun syncStreaks() = withContext(Dispatchers.IO) {
        val userId = authRepo.currentUserId ?: return@withContext
        val prefs = UserPreferences(context)
        val messageRepo = MessageRepository(context)

        try {
            // Recalculate all streaks from message history and push to Supabase
            val summaries = messageRepo.getConversationSummaries()
            val claimedRewards = try {
                prefs.claimedStreakRewardsFlow.first()
            } catch (_: Exception) { emptySet<String>() }

            for (summary in summaries) {
                StreakSync.pushStreak(userId, summary.friendId, summary.streakDays, claimedRewards)
            }
            Log.d(TAG, "Streaks synced: ${summaries.size} conversations")
        } catch (e: Exception) {
            Log.e(TAG, "Streak sync failed: ${e.message}", e)
        }
    }

    // ── Full Sync ──

    /**
     * @param isNewAccount If true, skip encounter/friend sync (fresh account, no server data).
     *   Prevents stale local encounters from being pushed to a brand-new account.
     */
    suspend fun fullSync(userNameOverride: String? = null, isNewAccount: Boolean = false) {
        syncProfile(userNameOverride)
        if (!isNewAccount) {
            syncEncounters()
            refreshFriendEncounters()
            syncLeaderboard()
            try { syncStreaks() } catch (_: Exception) {}
        }
        try { SpotPassRepository(context).syncFromServer() } catch (_: Exception) {}
    }

    // ── Build profile from local DataStore ──

    private suspend fun buildProfileFromLocal(
        userId: String,
        prefs: UserPreferences
    ): SupabaseProfile {
        val snap = prefs.snapshot()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        val savedMiisJson = snap[UserPreferences.SAVED_MIIS_LIST]
        val savedMiis: List<String> = if (savedMiisJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                gson.fromJson(savedMiisJson, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val selectedGamesJson = snap[UserPreferences.SELECTED_GAMES]
        val selectedGames: List<IgdbGame> = if (selectedGamesJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<IgdbGame>>() {}.type
                gson.fromJson(selectedGamesJson, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val puzzleProgressJson = snap[UserPreferences.PUZZLE_PROGRESS]
        val puzzleProgress = if (puzzleProgressJson != null) {
            try { gson.fromJson(puzzleProgressJson, PuzzleProgress::class.java) ?: PuzzleProgress() }
            catch (_: Exception) { PuzzleProgress() }
        } else PuzzleProgress()

        val avatarHex = snap[UserPreferences.AVATAR_HEX] ?: ""

        return SupabaseProfile(
            id = userId,
            userName = snap[UserPreferences.USER_NAME] ?: "",
            avatarHex = avatarHex,
            savedMiisList = json.parseToJsonElement(gson.toJson(savedMiis)),
            greeting = snap[UserPreferences.USER_GREETING] ?: "Hello! Nice to meet you!",
            mood = snap[UserPreferences.USER_MOOD] ?: "HAPPY",
            cardStyle = snap[UserPreferences.CARD_STYLE] ?: "classic",
            origin = snap[UserPreferences.USER_ORIGIN] ?: "",
            age = snap[UserPreferences.USER_AGE] ?: "",
            hobbies = snap[UserPreferences.USER_HOBBIES] ?: "",
            selectedGames = json.parseToJsonElement(gson.toJson(selectedGames)),
            tokenBalance = snap[UserPreferences.TOKEN_BALANCE]?.toIntOrNull() ?: 0,
            puzzleProgress = json.parseToJsonElement(gson.toJson(puzzleProgress)),
            musicVolume = snap[UserPreferences.MUSIC_VOLUME]?.toFloatOrNull() ?: 0.3f,
            proximityEnabled = (snap[UserPreferences.PROXIMITY_ENABLED] ?: "true") == "true",
            sfxEnabled = (snap[UserPreferences.SFX_ENABLED] ?: "true") == "true",
            sfxVolume = snap[UserPreferences.SFX_VOLUME]?.toFloatOrNull() ?: 0.5f,
            isMale = if (avatarHex.isNotBlank()) MiiStudioDecoder.isMale(avatarHex) else true
        )
    }
}
