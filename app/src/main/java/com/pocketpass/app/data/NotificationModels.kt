package com.pocketpass.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String,
    val type: String, // "friend_request", "friend_accepted", "new_message", "new_encounter"
    val title: String,
    val body: String = "",
    @SerialName("related_user_id") val relatedUserId: String? = null,
    @SerialName("related_user_name") val relatedUserName: String = "",
    @SerialName("related_user_avatar_hex") val relatedUserAvatarHex: String = "",
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MarkReadPayload(
    val read: Boolean = true
)

// ── Broadcast payload for instant notification delivery ──

@Serializable
data class BroadcastNotificationPayload(
    val type: String,
    val title: String,
    val body: String = "",
    val relatedUserId: String? = null,
    val relatedUserName: String = "",
    val relatedUserAvatarHex: String = ""
)
