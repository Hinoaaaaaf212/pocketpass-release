package com.pocketpass.app.data

import android.content.Context
import android.util.Log
import com.pocketpass.app.data.crypto.CryptoManager
import com.pocketpass.app.data.crypto.decryptContent
import com.pocketpass.app.data.crypto.encryptContent
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageRepository(private val context: Context) {

    private val TAG = "MessageRepository"
    private val client = SupabaseClient.client
    private val authRepo = AuthRepository()
    private val db = PocketPassDatabase.getDatabase(context)
    private val messageDao = db.messageDao()

    private val _incomingMessages = MutableSharedFlow<CachedMessage>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<CachedMessage> = _incomingMessages

    private var broadcastChannel: RealtimeChannel? = null
    private var pollingScope: CoroutineScope? = null

    val currentUserId: String?
        get() = authRepo.currentUserId

    // ── Send Message ──

    suspend fun sendMessage(receiverId: String, content: String): Result<CachedMessage> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            // Get friend's public key for encryption
            val friendPubKey = getFriendPublicKey(receiverId)

            // Encrypt content for the friendship key
            val payload = SendMessagePayload(
                senderId = myId,
                receiverId = receiverId,
                content = content
            ).encryptContent(friendPubKey, myId, receiverId)

            // Insert into Supabase
            val result = client.postgrest["messages"].insert(payload) {
                select()
            }.decodeSingle<SupabaseMessage>()

            // Store locally with plaintext (local DB is already device-encrypted)
            val cached = result.toLocal().copy(content = content)
            messageDao.insertMessage(cached)

            // Broadcast to receiver (encrypted content)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val channel = client.channel("chat_$receiverId")
                    channel.subscribe(blockUntilSubscribed = true)
                    channel.broadcast(
                        event = "new_message",
                        message = result.toLocal().toBroadcast()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Broadcast failed (non-critical): ${e.message}")
                }
            }

            Result.success(cached)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Load Conversation ──

    suspend fun loadConversation(friendId: String): List<CachedMessage> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext emptyList()
        try {
            val friendPubKey = getFriendPublicKey(friendId)

            val messages = client.postgrest["messages"].select {
                filter {
                    or {
                        and {
                            eq("sender_id", myId)
                            eq("receiver_id", friendId)
                        }
                        and {
                            eq("sender_id", friendId)
                            eq("receiver_id", myId)
                        }
                    }
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
            }.decodeList<SupabaseMessage>()

            val cached = messages.map {
                it.decryptContent(friendPubKey, myId, friendId).toLocal()
            }
            messageDao.insertMessages(cached)
            cached
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversation: ${e.message}", e)
            // Fall back to local cache
            messageDao.getConversation(myId, friendId)
        }
    }

    fun getConversationFlow(friendId: String): Flow<List<CachedMessage>> {
        val myId = authRepo.currentUserId ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return messageDao.getConversationFlow(myId, friendId)
    }

    // ── Conversation Summaries (Inbox) ──

    suspend fun getConversationSummaries(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext emptyList()
        try {
            // Fetch all messages for user
            val allMessages = client.postgrest["messages"].select {
                filter {
                    or {
                        eq("sender_id", myId)
                        eq("receiver_id", myId)
                    }
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<SupabaseMessage>()

            // Cache locally
            messageDao.insertMessages(allMessages.map { it.toLocal() })

            // Group by friend
            val grouped = allMessages.groupBy { msg ->
                if (msg.senderId == myId) msg.receiverId else msg.senderId
            }

            // Build summaries
            val friendRepo = FriendRepository()
            val summaries = grouped.mapNotNull { (friendId, messages) ->
                val latest = messages.first() // Already sorted DESC
                val unread = messages.count { it.receiverId == myId && it.readAt == null }
                val profile = friendRepo.getRequesterProfile(friendId)

                // Decrypt the latest message content for display
                val friendPubKey = profile?.publicKey ?: ""
                val decryptedLatest = latest.decryptContent(friendPubKey, myId, friendId)

                // Calculate streak from cached local messages
                val cachedMessages = messages.map {
                    it.decryptContent(friendPubKey, myId, friendId).toLocal()
                }
                val streakDays = calculateStreak(cachedMessages, myId, friendId)

                ConversationSummary(
                    friendId = friendId,
                    friendName = profile?.userName ?: "Unknown",
                    friendAvatarHex = profile?.avatarHex ?: "",
                    lastMessageContent = decryptedLatest.content,
                    lastMessageTimestamp = try {
                        java.time.Instant.parse(decryptedLatest.createdAt).toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    },
                    lastMessageIsFromMe = decryptedLatest.senderId == myId,
                    unreadCount = unread,
                    streakDays = streakDays
                )
            }.sortedByDescending { it.lastMessageTimestamp }

            // Sync streaks to Supabase (fire-and-forget)
            val prefs = UserPreferences(context)
            val claimedRewards = try {
                prefs.claimedStreakRewardsFlow.first()
            } catch (_: Exception) { emptySet<String>() }
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                for (summary in summaries) {
                    StreakSync.pushStreak(myId, summary.friendId, summary.streakDays, claimedRewards)
                }
            }

            summaries
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversation summaries: ${e.message}", e)
            emptyList()
        }
    }

    // ── Mark as Read ──

    suspend fun markConversationRead(friendId: String) = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext
        try {
            // Update on Supabase
            client.postgrest["messages"].update(
                mapOf("read_at" to java.time.Instant.now().toString())
            ) {
                filter {
                    eq("sender_id", friendId)
                    eq("receiver_id", myId)
                    exact("read_at", null)
                }
            }
            // Update local cache in a single query
            messageDao.markConversationRead(senderId = friendId, receiverId = myId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark conversation as read: ${e.message}", e)
        }
    }

    // ── Unread Count ──

    suspend fun getUnreadCount(): Int = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext 0
        try {
            messageDao.getUnreadCount(myId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get unread count: ${e.message}", e)
            0
        }
    }

    fun getUnreadCountFlow(): Flow<Int> {
        val myId = authRepo.currentUserId ?: return kotlinx.coroutines.flow.flowOf(0)
        return messageDao.getUnreadCountFlow(myId)
    }

    // ── Realtime: Broadcast + Polling ──

    suspend fun subscribeToRealtime() {
        val myId = authRepo.currentUserId ?: return
        try {
            // Subscribe to broadcast channel for instant message delivery
            broadcastChannel = client.channel("chat_$myId")

            broadcastChannel!!
                .broadcastFlow<BroadcastMessagePayload>("new_message")
                .onEach { payload ->
                    val friendId = payload.senderId
                    val friendPubKey = getFriendPublicKey(friendId)
                    val decrypted = payload.decryptContent(friendPubKey, myId, friendId)
                    val local = decrypted.toLocal()
                    messageDao.insertMessage(local)
                    _incomingMessages.emit(local)
                }
                .launchIn(CoroutineScope(Dispatchers.IO + SupervisorJob()))

            broadcastChannel!!.subscribe()
            Log.d(TAG, "Subscribed to broadcast channel chat_$myId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to broadcast: ${e.message}", e)
        }

        // Start polling as fallback
        pollingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollingScope!!.launch {
            while (isActive) {
                delay(15_000) // Poll every 15 seconds
                try {
                    pollNewMessages(myId)
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun pollNewMessages(myId: String) {
        // Fetch recent messages from the last 2 minutes to catch anything missed
        val cutoff = java.time.Instant.now().minusSeconds(120).toString()
        val recent = client.postgrest["messages"].select {
            filter {
                eq("receiver_id", myId)
                gte("created_at", cutoff)
            }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<SupabaseMessage>()

        if (recent.isEmpty()) return

        // Decrypt and convert
        val localMessages = recent.map { msg ->
            val friendId = msg.senderId
            val friendPubKey = getFriendPublicKey(friendId)
            msg.decryptContent(friendPubKey, myId, friendId).toLocal()
        }
        val senderIds = localMessages.map { it.senderId }.distinct()
        val existingIds = mutableSetOf<String>()
        for (senderId in senderIds) {
            messageDao.getConversation(myId, senderId).forEach { existingIds.add(it.id) }
        }

        for (local in localMessages) {
            if (local.id !in existingIds) {
                messageDao.insertMessage(local)
                _incomingMessages.emit(local)
            }
        }
    }

    suspend fun unsubscribeFromRealtime() {
        try {
            broadcastChannel?.unsubscribe()
            broadcastChannel = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unsubscribe: ${e.message}")
        }
        pollingScope?.let {
            it.launch { }.cancel()
        }
        pollingScope = null
    }

    /**
     * Get a friend's public key for message encryption/decryption.
     * Uses CryptoManager's LRU cache to avoid repeated network calls.
     */
    private suspend fun getFriendPublicKey(friendId: String): String {
        // Check cache first
        CryptoManager.getCachedFriendPublicKey(friendId)?.let { return it }

        // Fetch from Supabase
        return try {
            val profile = client.postgrest["profiles"].select {
                filter { eq("id", friendId) }
            }.decodeList<SupabaseProfile>().firstOrNull()

            val pubKey = profile?.publicKey ?: ""
            if (pubKey.isNotBlank()) {
                CryptoManager.cacheFriendPublicKey(friendId, pubKey)
            }
            pubKey
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch friend public key: ${e.message}")
            ""
        }
    }
}
