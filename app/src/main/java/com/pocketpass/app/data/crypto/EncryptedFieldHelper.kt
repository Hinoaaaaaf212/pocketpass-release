package com.pocketpass.app.data.crypto

import android.util.Log
import com.pocketpass.app.data.SupabaseEncounter
import com.pocketpass.app.data.SupabaseMessage
import com.pocketpass.app.data.SupabaseNotification
import com.pocketpass.app.data.SendMessagePayload
import com.pocketpass.app.data.CachedMessage
import com.pocketpass.app.data.BroadcastMessagePayload

private const val TAG = "EncryptedFieldHelper"

// ── Encounter encryption (per-user symmetric key) ──

fun SupabaseEncounter.encryptFields(): SupabaseEncounter {
    if (!CryptoManager.isInitialized) return this
    return try {
        copy(
            otherUserName = CryptoManager.encryptForSelf(otherUserName),
            otherUserAvatarHex = CryptoManager.encryptForSelf(otherUserAvatarHex),
            greeting = CryptoManager.encryptForSelf(greeting),
            origin = CryptoManager.encryptForSelf(origin),
            age = CryptoManager.encryptForSelf(age),
            hobbies = CryptoManager.encryptForSelf(hobbies),
            games = CryptoManager.encryptForSelf(games)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encrypt encounter fields", e)
        this
    }
}

fun SupabaseEncounter.decryptFields(): SupabaseEncounter {
    if (!CryptoManager.isInitialized) return this
    return try {
        copy(
            otherUserName = CryptoManager.decryptForSelf(otherUserName),
            otherUserAvatarHex = CryptoManager.decryptForSelf(otherUserAvatarHex),
            greeting = CryptoManager.decryptForSelf(greeting),
            origin = CryptoManager.decryptForSelf(origin),
            age = CryptoManager.decryptForSelf(age),
            hobbies = CryptoManager.decryptForSelf(hobbies),
            games = CryptoManager.decryptForSelf(games)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt encounter fields", e)
        this
    }
}

// ── Message encryption (per-friendship shared key via ECDH) ──

fun SendMessagePayload.encryptContent(
    friendPublicKeyBase64: String,
    myUserId: String,
    friendId: String
): SendMessagePayload {
    if (!CryptoManager.isInitialized || friendPublicKeyBase64.isBlank()) return this
    return try {
        copy(content = CryptoManager.encryptForFriend(friendId, friendPublicKeyBase64, myUserId, content))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encrypt message content", e)
        this
    }
}

fun SupabaseMessage.decryptContent(
    friendPublicKeyBase64: String,
    myUserId: String,
    friendId: String
): SupabaseMessage {
    if (!CryptoManager.isInitialized || friendPublicKeyBase64.isBlank()) return this
    if (!content.startsWith("enc:1:")) return this
    return try {
        copy(content = CryptoManager.decryptFromFriend(friendId, friendPublicKeyBase64, myUserId, content))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt message content", e)
        this
    }
}

fun CachedMessage.decryptContent(
    friendPublicKeyBase64: String,
    myUserId: String,
    friendId: String
): CachedMessage {
    if (!CryptoManager.isInitialized || friendPublicKeyBase64.isBlank()) return this
    if (!content.startsWith("enc:1:")) return this
    return try {
        copy(content = CryptoManager.decryptFromFriend(friendId, friendPublicKeyBase64, myUserId, content))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt cached message content", e)
        this
    }
}

fun BroadcastMessagePayload.decryptContent(
    friendPublicKeyBase64: String,
    myUserId: String,
    friendId: String
): BroadcastMessagePayload {
    if (!CryptoManager.isInitialized || friendPublicKeyBase64.isBlank()) return this
    if (!content.startsWith("enc:1:")) return this
    return try {
        copy(content = CryptoManager.decryptFromFriend(friendId, friendPublicKeyBase64, myUserId, content))
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt broadcast message content", e)
        this
    }
}

// ── Notification encryption (sealed-box: sender writes, receiver reads) ──

fun SupabaseNotification.encryptFields(receiverPublicKeyBase64: String): SupabaseNotification {
    if (!CryptoManager.isInitialized || receiverPublicKeyBase64.isBlank()) return this
    return try {
        copy(
            title = CryptoManager.sealForReceiver(receiverPublicKeyBase64, title),
            body = CryptoManager.sealForReceiver(receiverPublicKeyBase64, body),
            relatedUserName = CryptoManager.sealForReceiver(receiverPublicKeyBase64, relatedUserName),
            relatedUserAvatarHex = CryptoManager.sealForReceiver(receiverPublicKeyBase64, relatedUserAvatarHex)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to encrypt notification fields", e)
        this
    }
}

fun SupabaseNotification.decryptFields(): SupabaseNotification {
    if (!CryptoManager.isInitialized) return this
    if (!title.startsWith("enc:1:")) return this // Plaintext notification
    return try {
        copy(
            title = CryptoManager.unseal(title),
            body = CryptoManager.unseal(body),
            relatedUserName = CryptoManager.unseal(relatedUserName),
            relatedUserAvatarHex = CryptoManager.unseal(relatedUserAvatarHex)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt notification fields", e)
        this
    }
}
