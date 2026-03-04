package com.pocketpass.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encounters")
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
    val meetCount: Int = 1
)