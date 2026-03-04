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
}