package com.pocketpass.app.service

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

@Keep
data class ExchangePayload(
    val userId: String,
    val userName: String,
    val avatarHex: String,
    val greeting: String,
    val origin: String,
    val age: String,
    val hobbies: String,
    val games: String = "",
    val hatId: String = "",
    val costumeId: String = "",
    val isMale: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val MAX_PAYLOAD_SIZE_BYTES = 16 * 1024

        private const val MAX_STRING_LENGTH = 500
        private const val MAX_AVATAR_HEX_LENGTH = 1024
        private const val MAX_GAMES_LENGTH = 4096

        // UUID v4 pattern: 8-4-4-4-12 hex digits
        private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        // Only hex chars (Mii avatar data is hex-encoded)
        private val HEX_REGEX = Regex("^[0-9a-fA-F]*$")

        /**
         * Parse JSON into an ExchangePayload with size and field validation.
         * Returns null if the data is invalid or too large.
         */
        fun fromJsonSafe(json: String, gson: Gson): ExchangePayload? {
            if (json.length > MAX_PAYLOAD_SIZE_BYTES) return null
            return try {
                val payload = gson.fromJson(json, ExchangePayload::class.java) ?: return null
                payload.validateAndSanitize()
            } catch (_: JsonSyntaxException) {
                null
            } catch (_: Exception) {
                null
            }
        }

        private fun ExchangePayload.validateAndSanitize(): ExchangePayload? {
            if (userId.isBlank() || userName.isBlank()) return null

            // Validate userId is a valid UUID
            if (!UUID_REGEX.matches(userId)) return null

            // Validate avatarHex contains only hex characters
            if (avatarHex.isNotEmpty() && !HEX_REGEX.matches(avatarHex)) return null

            // Validate age is empty or a reasonable number
            if (age.isNotEmpty()) {
                val ageNum = age.toIntOrNull()
                if (ageNum != null && (ageNum < 0 || ageNum > 150)) return null
            }

            // Validate games is empty or valid JSON array
            if (games.isNotEmpty()) {
                if (!games.trimStart().startsWith("[") || !games.trimEnd().endsWith("]")) return null
            }

            // Reject stale timestamps (older than 5 minutes)
            val now = System.currentTimeMillis()
            if (kotlin.math.abs(now - timestamp) > BleCryptoHandshake.MAX_TIMESTAMP_DRIFT_MS) return null

            return copy(
                userId = userId.take(MAX_STRING_LENGTH),
                userName = userName.take(MAX_STRING_LENGTH),
                avatarHex = avatarHex.take(MAX_AVATAR_HEX_LENGTH),
                greeting = greeting.take(MAX_STRING_LENGTH),
                origin = origin.take(MAX_STRING_LENGTH),
                age = age.take(MAX_STRING_LENGTH),
                hobbies = hobbies.take(MAX_STRING_LENGTH),
                games = games.take(MAX_GAMES_LENGTH),
                hatId = hatId.take(MAX_STRING_LENGTH),
                costumeId = costumeId.take(MAX_STRING_LENGTH)
            )
        }
    }
}
