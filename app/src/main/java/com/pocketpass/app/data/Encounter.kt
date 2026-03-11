package com.pocketpass.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encounters",
    indices = [
        Index("otherUserName"),
        Index("otherUserId"),
        Index("needsSync")
    ]
)
data class Encounter(
    @PrimaryKey
    val encounterId: String,
    val timestamp: Long,
    val otherUserAvatarHex: String,
    val otherUserName: String,
    val greeting: String,
    val origin: String,
    val age: String,
    val hobbies: String,
    val meetCount: Int = 1,
    val games: String = "",
    val needsSync: Boolean = true,
    val otherUserId: String = "",
    val hatId: String = "",
    val costumeId: String = "",
    val isMale: Boolean = true
)