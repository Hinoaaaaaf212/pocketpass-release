package com.pocketpass.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class SupabaseProfile(
    val id: String,
    @SerialName("user_name") val userName: String = "",
    @SerialName("avatar_hex") val avatarHex: String = "",
    @SerialName("saved_miis_list") val savedMiisList: JsonElement = JsonArray(emptyList()),
    val greeting: String = "Hello! Nice to meet you!",
    val mood: String = "HAPPY",
    @SerialName("card_style") val cardStyle: String = "classic",
    val origin: String = "",
    val age: String = "",
    val hobbies: String = "",
    @SerialName("selected_games") val selectedGames: JsonElement = JsonArray(emptyList()),
    @SerialName("token_balance") val tokenBalance: Int = 0,
    @SerialName("puzzle_progress") val puzzleProgress: JsonElement = JsonObject(emptyMap()),
    @SerialName("music_volume") val musicVolume: Float = 0.3f,
    @SerialName("proximity_enabled") val proximityEnabled: Boolean = true,
    @SerialName("sfx_enabled") val sfxEnabled: Boolean = true,
    @SerialName("sfx_volume") val sfxVolume: Float = 0.5f,
    @SerialName("is_male") val isMale: Boolean = true,
    @SerialName("friend_code") val friendCode: String = "",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class SupabaseEncounter(
    @SerialName("encounter_id") val encounterId: String,
    @SerialName("user_id") val userId: String,
    val timestamp: Long,
    @SerialName("other_user_avatar_hex") val otherUserAvatarHex: String = "",
    @SerialName("other_user_name") val otherUserName: String,
    val greeting: String = "",
    val origin: String = "",
    val age: String = "",
    val hobbies: String = "",
    @SerialName("meet_count") val meetCount: Int = 1,
    val games: String = "",
    @SerialName("other_user_id") val otherUserId: String? = null,
    @SerialName("is_male") val isMale: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class SupabaseLeaderboardEntry(
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String = "",
    @SerialName("avatar_hex") val avatarHex: String = "",
    val origin: String = "",
    @SerialName("total_encounters") val totalEncounters: Int = 0,
    @SerialName("unique_encounters") val uniqueEncounters: Int = 0,
    @SerialName("unique_regions") val uniqueRegions: Int = 0,
    @SerialName("puzzles_completed") val puzzlesCompleted: Int = 0,
    @SerialName("achievements_unlocked") val achievementsUnlocked: Int = 0,
    @SerialName("is_male") val isMale: Boolean = true
)

@Serializable
data class SupabaseFriendship(
    val id: String = "",
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SoftDeletePayload(
    @SerialName("deleted_at") val deletedAt: String
)

// Extension functions for model conversion

fun Encounter.toSupabase(userId: String) = SupabaseEncounter(
    encounterId = encounterId,
    userId = userId,
    timestamp = timestamp,
    otherUserAvatarHex = otherUserAvatarHex,
    otherUserName = otherUserName,
    greeting = greeting,
    origin = origin,
    age = age,
    hobbies = hobbies,
    meetCount = meetCount,
    games = games,
    otherUserId = otherUserId.ifEmpty { null },
    isMale = isMale
)

@Serializable
data class AppVersion(
    val id: Int = 0,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("download_url") val downloadUrl: String,
    val changelog: String = "",
    @SerialName("min_version_code") val minVersionCode: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SupabaseStreak(
    @SerialName("user_id") val userId: String,
    @SerialName("friend_id") val friendId: String,
    @SerialName("streak_days") val streakDays: Int = 0,
    @SerialName("last_mutual_date") val lastMutualDate: String? = null,
    @SerialName("rewards_claimed") val rewardsClaimed: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null
)

fun SupabaseEncounter.toLocal() = Encounter(
    encounterId = encounterId,
    timestamp = timestamp,
    otherUserAvatarHex = otherUserAvatarHex,
    otherUserName = otherUserName,
    greeting = greeting,
    origin = origin,
    age = age,
    hobbies = hobbies,
    meetCount = meetCount,
    games = games,
    needsSync = false,
    otherUserId = otherUserId ?: "",
    isMale = isMale
)
