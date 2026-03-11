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
    val isMale: Boolean = true
) {
    companion object {
        const val MAX_PAYLOAD_SIZE_BYTES = 16 * 1024

        private const val MAX_STRING_LENGTH = 500
        private const val MAX_AVATAR_HEX_LENGTH = 1024
        private const val MAX_GAMES_LENGTH = 4096

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
