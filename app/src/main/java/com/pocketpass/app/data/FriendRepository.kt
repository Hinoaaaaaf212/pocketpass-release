package com.pocketpass.app.data

import android.util.Log
import com.pocketpass.app.data.crypto.encryptFields
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FriendRepository {

    private val TAG = "FriendRepository"
    private val client = SupabaseClient.client
    private val authRepo = AuthRepository()

    val currentUserId: String?
        get() = authRepo.currentUserId

    suspend fun lookupByFriendCode(code: String): SupabaseProfile? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["profiles"].select {
                filter { eq("friend_code", code) }
            }.decodeList<SupabaseProfile>().firstOrNull()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup friend code: ${e.message}", e)
            null
        }
    }

    suspend fun sendFriendRequestByCode(code: String): Result<SupabaseProfile> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext Result.failure(Exception("Not logged in"))
        val profile = lookupByFriendCode(code)
            ?: return@withContext Result.failure(Exception("No user found with that friend code"))
        if (profile.id == myId) return@withContext Result.failure(Exception("That's your own friend code!"))
        val result = sendFriendRequest(profile.id)
        result.fold(
            onSuccess = { Result.success(profile) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun getMyFriendCode(): String? = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext null
        try {
            client.postgrest["profiles"].select {
                filter { eq("id", myId) }
            }.decodeList<SupabaseProfile>().firstOrNull()?.friendCode
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get friend code: ${e.message}", e)
            null
        }
    }

    suspend fun sendFriendRequest(toUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext Result.failure(Exception("Not logged in"))
        if (toUserId == myId) return@withContext Result.failure(Exception("You can't add yourself as a friend"))
        if (toUserId.isBlank()) return@withContext Result.failure(Exception("This person doesn't have an account yet"))
        try {
            // Check for existing friendship in either direction
            val existing = getFriendshipWith(toUserId)
            if (existing != null) {
                val msg = when (existing.status) {
                    "accepted" -> "You're already friends with this user"
                    "pending" -> if (existing.requesterId == myId) "Friend request already sent" else "This user already sent you a request — check your pending requests"
                    else -> "A friendship record already exists"
                }
                return@withContext Result.failure(Exception(msg))
            }
            client.postgrest["friendships"].insert(
                SupabaseFriendship(
                    requesterId = myId,
                    addresseeId = toUserId
                )
            )
            Log.d(TAG, "Friend request sent to $toUserId")

            // Create a notification for the recipient with the sender's profile info
            try {
                val myProfile = getRequesterProfile(myId)
                val senderName = myProfile?.userName ?: "Someone"
                // Get recipient's public key for sealed-box encryption
                val recipientProfile = getRequesterProfile(toUserId)
                val recipientPubKey = recipientProfile?.publicKey ?: ""
                val notification = SupabaseNotification(
                    userId = toUserId,
                    type = "friend_request",
                    title = "$senderName sent you a friend request!",
                    body = "Tap to view your pending requests.",
                    relatedUserId = myId,
                    relatedUserName = senderName,
                    relatedUserAvatarHex = myProfile?.avatarHex ?: ""
                ).encryptFields(recipientPubKey)
                client.postgrest["notifications"].insert(notification)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create notification (request still sent): ${e.message}")
            }

            Result.success(Unit)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send friend request: ${e.message}", e)
            val msg = if (e.message?.contains("23503") == true || e.message?.contains("foreign key") == true) {
                "This person doesn't have an account yet"
            } else {
                e.message ?: "Failed to send friend request"
            }
            Result.failure(Exception(msg))
        }
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Fetch the friendship to know who the requester is
            val friendships = client.postgrest["friendships"].select {
                filter { eq("id", friendshipId) }
            }.decodeList<SupabaseFriendship>()
            val friendship = friendships.firstOrNull()

            client.postgrest["friendships"].update(
                mapOf("status" to "accepted")
            ) {
                filter { eq("id", friendshipId) }
            }
            Log.d(TAG, "Friend request accepted: $friendshipId")

            // Notify the original requester that their request was accepted
            if (friendship != null) {
                try {
                    val myId = authRepo.currentUserId
                    val myProfile = if (myId != null) getRequesterProfile(myId) else null
                    val myName = myProfile?.userName ?: "Someone"
                    // Get requester's public key for sealed-box encryption
                    val requesterProfile = getRequesterProfile(friendship.requesterId)
                    val requesterPubKey = requesterProfile?.publicKey ?: ""
                    val notification = SupabaseNotification(
                        userId = friendship.requesterId,
                        type = "friend_accepted",
                        title = "$myName accepted your friend request!",
                        body = "You are now friends.",
                        relatedUserId = myId,
                        relatedUserName = myName,
                        relatedUserAvatarHex = myProfile?.avatarHex ?: ""
                    ).encryptFields(requesterPubKey)
                    client.postgrest["notifications"].insert(notification)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create acceptance notification: ${e.message}")
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept friend request: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(friendshipId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["friendships"].delete {
                filter { eq("id", friendshipId) }
            }
            Log.d(TAG, "Friend request rejected: $friendshipId")
            Result.success(Unit)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject friend request: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendshipId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["friendships"].delete {
                filter { eq("id", friendshipId) }
            }
            Log.d(TAG, "Friend removed: $friendshipId")
            Result.success(Unit)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove friend: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getFriendshipWith(otherUserId: String): SupabaseFriendship? = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext null
        try {
            // Check both directions: I sent to them, or they sent to me
            val results = client.postgrest["friendships"].select {
                filter {
                    or {
                        and {
                            eq("requester_id", myId)
                            eq("addressee_id", otherUserId)
                        }
                        and {
                            eq("requester_id", otherUserId)
                            eq("addressee_id", myId)
                        }
                    }
                }
            }.decodeList<SupabaseFriendship>()
            results.firstOrNull()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check friendship: ${e.message}", e)
            null
        }
    }

    suspend fun getPendingRequests(): List<SupabaseFriendship> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext emptyList()
        try {
            client.postgrest["friendships"].select {
                filter {
                    eq("addressee_id", myId)
                    eq("status", "pending")
                }
            }.decodeList<SupabaseFriendship>()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get pending requests: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getAcceptedFriendIds(): Set<String> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext emptySet()
        try {
            val friendships = client.postgrest["friendships"].select {
                filter {
                    eq("status", "accepted")
                    or {
                        eq("requester_id", myId)
                        eq("addressee_id", myId)
                    }
                }
            }.decodeList<SupabaseFriendship>()
            friendships.map { if (it.requesterId == myId) it.addresseeId else it.requesterId }.toSet()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get friends: ${e.message}", e)
            emptySet()
        }
    }

    suspend fun getRequesterProfile(requesterId: String): SupabaseProfile? = withContext(Dispatchers.IO) {
        try {
            client.postgrest["profiles"].select {
                filter { eq("id", requesterId) }
            }.decodeList<SupabaseProfile>().firstOrNull()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get profile: ${e.message}", e)
            null
        }
    }

    /** Batch-fetch profiles by IDs in a single PostgREST call. */
    suspend fun getProfilesByIds(ids: List<String>): Map<String, SupabaseProfile> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        try {
            client.postgrest["profiles"].select {
                filter { isIn("id", ids) }
            }.decodeList<SupabaseProfile>().associateBy { it.id }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch-fetch profiles: ${e.message}", e)
            emptyMap()
        }
    }
}
