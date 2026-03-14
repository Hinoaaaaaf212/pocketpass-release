package com.pocketpass.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Supabase model (for Postgrest) ──

@Serializable
data class SupabaseMessage(
    val id: String = "",
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
)

// ── Insert payload (no id/created_at — server generates them) ──

@Serializable
data class SendMessagePayload(
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String
)

// ── Broadcast payload (sent via Supabase Broadcast channel) ──

@Serializable
data class BroadcastMessagePayload(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val createdAt: Long
)

// ── Local Room entity for offline cache ──

@Entity(
    tableName = "cached_messages",
    indices = [
        Index("senderId"),
        Index("receiverId"),
        Index(value = ["receiverId", "readAt"])
    ]
)
data class CachedMessage(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val createdAt: Long,
    val readAt: Long? = null
)

// ── Conversation summary (for inbox list) ──

data class ConversationSummary(
    val friendId: String,
    val friendName: String,
    val friendAvatarHex: String,
    val lastMessageContent: String,
    val lastMessageTimestamp: Long,
    val lastMessageIsFromMe: Boolean,
    val unreadCount: Int,
    val streakDays: Int = 0
)

// ── Conversion extensions ──

fun SupabaseMessage.toLocal(): CachedMessage = CachedMessage(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    createdAt = try {
        java.time.Instant.parse(createdAt).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    },
    readAt = try {
        readAt?.let { java.time.Instant.parse(it).toEpochMilli() }
    } catch (_: Exception) {
        null
    }
)

fun CachedMessage.toBroadcast(): BroadcastMessagePayload = BroadcastMessagePayload(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    createdAt = createdAt
)

fun BroadcastMessagePayload.toLocal(): CachedMessage = CachedMessage(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    content = content,
    createdAt = createdAt
)
