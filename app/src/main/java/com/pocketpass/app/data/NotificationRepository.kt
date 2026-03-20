package com.pocketpass.app.data

import android.util.Log
import com.pocketpass.app.data.crypto.decryptFields
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationRepository {

    private val TAG = "NotificationRepository"
    private val client = SupabaseClient.client
    private val authRepo = AuthRepository()

    private val _incomingNotifications = MutableSharedFlow<SupabaseNotification>(extraBufferCapacity = 16)
    val incomingNotifications: SharedFlow<SupabaseNotification> = _incomingNotifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private var broadcastChannel: RealtimeChannel? = null
    private var broadcastScope: CoroutineScope? = null
    private var pollingScope: CoroutineScope? = null

    val currentUserId: String?
        get() = authRepo.currentUserId

    // ── Get Notifications ──

    suspend fun getNotifications(): List<SupabaseNotification> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext emptyList()
        try {
            val notifications = client.postgrest["notifications"].select {
                filter {
                    eq("user_id", myId)
                }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(50)
            }.decodeList<SupabaseNotification>()
                .map { it.decryptFields() }

            _unreadCount.value = notifications.count { !it.read }
            notifications
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get notifications: ${e.message}", e)
            emptyList()
        }
    }

    // ── Unread Count ──

    suspend fun getUnreadCount(): Int = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext 0
        try {
            val notifications = client.postgrest["notifications"].select {
                filter {
                    eq("user_id", myId)
                    eq("read", false)
                }
            }.decodeList<SupabaseNotification>()
            val count = notifications.size
            _unreadCount.value = count
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get unread count: ${e.message}", e)
            0
        }
    }

    // ── Mark as Read ──

    suspend fun markAsRead(notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.postgrest["notifications"].update(
                MarkReadPayload(read = true)
            ) {
                filter { eq("id", notificationId) }
            }
            _unreadCount.value = maxOf(0, _unreadCount.value - 1)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark notification as read: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun markAllAsRead(): Result<Unit> = withContext(Dispatchers.IO) {
        val myId = authRepo.currentUserId ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            client.postgrest["notifications"].update(
                MarkReadPayload(read = true)
            ) {
                filter {
                    eq("user_id", myId)
                    eq("read", false)
                }
            }
            _unreadCount.value = 0
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark all as read: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Realtime: Broadcast + Polling ──

    suspend fun subscribeToRealtime() {
        val myId = authRepo.currentUserId ?: return
        try {
            broadcastChannel = client.channel("notif_$myId")

            val validNotifTypes = setOf("friend_request", "friend_accepted", "new_message", "new_encounter")

            broadcastScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            broadcastChannel!!
                .broadcastFlow<BroadcastNotificationPayload>("new_notification")
                .onEach { payload ->
                    if (payload.type !in validNotifTypes) {
                        Log.w(TAG, "Unknown notification type: ${payload.type}")
                        return@onEach
                    }
                    // Convert broadcast payload to a SupabaseNotification for the UI
                    val notif = SupabaseNotification(
                        id = "",
                        userId = myId,
                        type = payload.type,
                        title = payload.title.take(200),
                        body = payload.body.take(500),
                        relatedUserId = payload.relatedUserId,
                        relatedUserName = payload.relatedUserName.take(100),
                        relatedUserAvatarHex = payload.relatedUserAvatarHex.take(512)
                    )
                    _incomingNotifications.emit(notif)
                    _unreadCount.value += 1
                }
                .launchIn(broadcastScope!!)

            broadcastChannel!!.subscribe()
            Log.d(TAG, "Subscribed to notification broadcast channel notif_$myId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to notification broadcast: ${e.message}", e)
        }

        // Start polling as fallback
        pollingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollingScope!!.launch {
            // Initial load
            try {
                getUnreadCount()
            } catch (_: Exception) {}

            while (isActive) {
                delay(60_000) // Poll every 60 seconds (broadcast channel handles real-time delivery)
                try {
                    getUnreadCount()
                } catch (_: Exception) {}
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
        broadcastScope?.cancel()
        broadcastScope = null
        pollingScope?.cancel()
        pollingScope = null
    }
}
