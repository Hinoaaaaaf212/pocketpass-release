package com.pocketpass.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncounter(encounter: Encounter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncounters(encounters: List<Encounter>)

    @Update
    suspend fun updateEncounter(encounter: Encounter)

    @Query("SELECT * FROM encounters ORDER BY timestamp DESC")
    fun getAllEncountersFlow(): Flow<List<Encounter>>

    @Query("SELECT * FROM encounters WHERE otherUserName = :userName LIMIT 1")
    suspend fun getEncounterByUserName(userName: String): Encounter?

    @Delete
    suspend fun deleteEncounter(encounter: Encounter)

    @Query("DELETE FROM encounters")
    suspend fun deleteAllEncounters()

    @Query("SELECT * FROM encounters WHERE needsSync = 1")
    suspend fun getUnsyncedEncounters(): List<Encounter>

    @Query("UPDATE encounters SET needsSync = 0 WHERE encounterId = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM encounters WHERE otherUserId = :userId LIMIT 1")
    suspend fun getEncounterByOtherUserId(userId: String): Encounter?

    @Query("SELECT * FROM encounters WHERE otherUserId IN (:userIds)")
    suspend fun getEncountersByOtherUserIds(userIds: List<String>): List<Encounter>
}