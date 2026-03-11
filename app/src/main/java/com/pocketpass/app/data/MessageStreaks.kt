package com.pocketpass.app.data

import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class StreakTier(val threshold: Int, val label: String, val color: Long, val reward: Int) {
    BRONZE(3, "Bronze", 0xFFCD7F32, 2),
    SILVER(7, "Silver", 0xFFC0C0C0, 5),
    GOLD(14, "Gold", 0xFFFFD700, 10),
    PLATINUM(30, "Platinum", 0xFF00BCD4, 15),
    RAINBOW(60, "Rainbow", 0xFF9C27B0, 25)
}

fun getCurrentTier(streakDays: Int): StreakTier? {
    return StreakTier.entries.lastOrNull { streakDays >= it.threshold }
}

/**
 * Calculate the messaging streak between two users.
 * A streak day counts when BOTH users sent at least one message on that calendar day.
 * Returns consecutive day count starting from today/yesterday backwards.
 */
fun calculateStreak(messages: List<CachedMessage>, myId: String, friendId: String): Int {
    if (messages.isEmpty()) return 0

    val zone = ZoneId.systemDefault()

    // Group messages by calendar day, tracking which side sent
    data class DayActivity(var mySent: Boolean = false, var friendSent: Boolean = false)

    val dayMap = mutableMapOf<LocalDate, DayActivity>()
    for (msg in messages) {
        val day = Instant.ofEpochMilli(msg.createdAt).atZone(zone).toLocalDate()
        val activity = dayMap.getOrPut(day) { DayActivity() }
        when (msg.senderId) {
            myId -> activity.mySent = true
            friendId -> activity.friendSent = true
        }
    }

    val today = LocalDate.now()
    var streak = 0

    // Check today first
    val todayActivity = dayMap[today]
    val todayCounts = todayActivity != null && todayActivity.mySent && todayActivity.friendSent
    if (todayCounts) streak++

    // Walk backwards from yesterday
    var checkDate = today.minusDays(1)
    while (true) {
        val activity = dayMap[checkDate]
        if (activity != null && activity.mySent && activity.friendSent) {
            streak++
            checkDate = checkDate.minusDays(1)
        } else {
            break
        }
    }

    return streak
}

/**
 * Check if the current user has sent a message today in this conversation.
 */
fun hasSentMessageToday(messages: List<CachedMessage>, myId: String): Boolean {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    return messages.any { msg ->
        msg.senderId == myId &&
            Instant.ofEpochMilli(msg.createdAt).atZone(zone).toLocalDate() == today
    }
}

// ── Supabase Streak Sync ──

object StreakSync {
    private const val TAG = "StreakSync"
    private val client = SupabaseClient.client

    /**
     * Push the locally-calculated streak for a friend to Supabase.
     * Called after streak calculation in MessageRepository.
     */
    suspend fun pushStreak(
        userId: String,
        friendId: String,
        streakDays: Int,
        rewardsClaimed: Set<String>
    ) = withContext(Dispatchers.IO) {
        try {
            val lastMutualDate = if (streakDays > 0) LocalDate.now().toString() else null
            // Filter rewards to only those for this friend
            val friendRewards = rewardsClaimed
                .filter { it.startsWith("${friendId}_") }
                .toList()

            client.postgrest["message_streaks"].upsert(
                SupabaseStreak(
                    userId = userId,
                    friendId = friendId,
                    streakDays = streakDays,
                    lastMutualDate = lastMutualDate,
                    rewardsClaimed = friendRewards
                )
            )
            Log.d(TAG, "Streak pushed: $friendId = $streakDays days")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to push streak: ${e.message}")
        }
    }

    /**
     * Pull all streaks for the current user from Supabase.
     * Used during cloud restore or to sync claimed rewards across devices.
     */
    suspend fun pullStreaks(userId: String): List<SupabaseStreak> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["message_streaks"]
                .select { filter { eq("user_id", userId) } }
                .decodeList<SupabaseStreak>()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pull streaks: ${e.message}")
            emptyList()
        }
    }
}
