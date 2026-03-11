package com.pocketpass.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    @Query("""
        SELECT * FROM cached_messages
        WHERE (senderId = :userId AND receiverId = :friendId)
           OR (senderId = :friendId AND receiverId = :userId)
        ORDER BY createdAt ASC
    """)
    fun getConversationFlow(userId: String, friendId: String): Flow<List<CachedMessage>>

    @Query("""
        SELECT * FROM cached_messages
        WHERE (senderId = :userId AND receiverId = :friendId)
           OR (senderId = :friendId AND receiverId = :userId)
        ORDER BY createdAt ASC
    """)
    suspend fun getConversation(userId: String, friendId: String): List<CachedMessage>

    @Query("UPDATE cached_messages SET readAt = :readAt WHERE id = :messageId")
    suspend fun markRead(messageId: String, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE cached_messages SET readAt = :readAt WHERE senderId = :senderId AND receiverId = :receiverId AND readAt IS NULL")
    suspend fun markConversationRead(senderId: String, receiverId: String, readAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE receiverId = :userId AND readAt IS NULL
    """)
    fun getUnreadCountFlow(userId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM cached_messages
        WHERE receiverId = :userId AND readAt IS NULL
    """)
    suspend fun getUnreadCount(userId: String): Int

    @Query("DELETE FROM cached_messages")
    suspend fun deleteAll()
}
