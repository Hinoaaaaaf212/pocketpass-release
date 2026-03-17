package com.pocketpass.app.data

import android.content.Context
import com.pocketpass.app.data.crypto.decryptFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class EncounterRepository(private val context: Context) {
    private val db = PocketPassDatabase.getDatabase(context)
    private val dao = db.encounterDao()
    private val syncRepo = SyncRepository(context)
    private val authRepo = AuthRepository()

    /** Observable list of all encounters (from local Room DB), decrypted for display */
    val allEncounters: Flow<List<Encounter>> = dao.getAllEncountersFlow()
        .map { list -> list.map { it.decryptFields() } }

    /** Insert or update an encounter locally, then push to cloud if authenticated */
    suspend fun saveEncounter(
        userName: String,
        avatarHex: String,
        greeting: String,
        origin: String,
        age: String,
        hobbies: String,
        games: String = ""
    ): Encounter {
        val existing = dao.getEncounterByUserName(userName)
        val encounter: Encounter

        if (existing != null) {
            encounter = existing.copy(
                timestamp = System.currentTimeMillis(),
                meetCount = existing.meetCount + 1,
                needsSync = true
            )
            dao.updateEncounter(encounter)
        } else {
            encounter = Encounter(
                encounterId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                otherUserAvatarHex = avatarHex,
                otherUserName = userName,
                greeting = greeting,
                origin = origin,
                age = age,
                hobbies = hobbies,
                games = games,
                needsSync = true
            )
            dao.insertEncounter(encounter)
        }

        // Push to cloud if authenticated
        if (authRepo.currentUserId != null) {
            syncRepo.pushEncounter(encounter)
        }

        return encounter
    }

    /** Insert a pre-built encounter (e.g. from QR/BLE) */
    suspend fun insertEncounter(encounter: Encounter) {
        dao.insertEncounter(encounter.copy(needsSync = true))
        if (authRepo.currentUserId != null) {
            syncRepo.pushEncounter(encounter)
        }
    }

    /** Delete an encounter locally and soft-delete on remote */
    suspend fun deleteEncounter(encounter: Encounter) {
        dao.deleteEncounter(encounter)
        if (authRepo.currentUserId != null) {
            syncRepo.softDeleteEncounter(encounter.encounterId)
        }
    }

    /** Get encounter by username */
    suspend fun getEncounterByUserName(userName: String): Encounter? {
        return dao.getEncounterByUserName(userName)
    }

    /** Delete all encounters */
    suspend fun deleteAllEncounters() {
        dao.deleteAllEncounters()
    }
}
