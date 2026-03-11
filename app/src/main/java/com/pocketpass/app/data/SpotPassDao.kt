package com.pocketpass.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotPassDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<SpotPassItemEntity>)

    @Query("SELECT * FROM spotpass_items ORDER BY publishedAt DESC")
    fun getAllItemsFlow(): Flow<List<SpotPassItemEntity>>

    @Query("SELECT COUNT(*) FROM spotpass_items WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM spotpass_items WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("UPDATE spotpass_items SET isRead = 1 WHERE id = :itemId")
    suspend fun markAsRead(itemId: String)

    @Query("UPDATE spotpass_items SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE spotpass_items SET isClaimed = 1 WHERE id = :itemId")
    suspend fun markAsClaimed(itemId: String)

    @Query("SELECT * FROM spotpass_items WHERE type = 'puzzle_panel' AND isClaimed = 1")
    suspend fun getClaimedPuzzlePanels(): List<SpotPassItemEntity>

    @Query("SELECT * FROM spotpass_items WHERE type = 'puzzle_panel' AND isClaimed = 1")
    fun getClaimedPuzzlePanelsFlow(): Flow<List<SpotPassItemEntity>>

    @Query("SELECT MAX(publishedAt) FROM spotpass_items")
    suspend fun getLatestPublishedAt(): Long?

    @Query("SELECT * FROM spotpass_items")
    suspend fun getAllItems(): List<SpotPassItemEntity>

    @Query("SELECT id FROM spotpass_items")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM spotpass_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<SpotPassItemEntity>)

    @Query("SELECT * FROM spotpass_items WHERE type = 'event' AND eventEffect IS NOT NULL AND publishedAt <= :nowMs AND (expiresAt IS NULL OR expiresAt > :nowMs)")
    suspend fun getActiveEventEffects(nowMs: Long): List<SpotPassItemEntity>
}
