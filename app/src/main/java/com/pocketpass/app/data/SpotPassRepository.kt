package com.pocketpass.app.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SpotPassRepository(context: Context) {

    private val TAG = "SpotPassRepository"
    private val client = SupabaseClient.client
    private val db = PocketPassDatabase.getDatabase(context)
    private val dao = db.spotPassDao()

    val allItems: Flow<List<SpotPassItemEntity>> = dao.getAllItemsFlow()
    val unreadCount: Flow<Int> = dao.getUnreadCountFlow()
    val claimedPanels: Flow<List<SpotPassItemEntity>> = dao.getClaimedPuzzlePanelsFlow()

    /**
     * Full sync: fetch all items from Supabase, upsert into Room, and delete
     * local items that no longer exist on the server.
     * Returns the number of new/updated items.
     */
    suspend fun syncFromServer(): Int = withContext(Dispatchers.IO) {
        try {
            val remoteItems = client.postgrest["spotpass_items"]
                .select()
                .decodeList<SupabaseSpotPassItem>()

            // Build lookup of existing local items to preserve isRead/isClaimed
            val localById = dao.getAllItems().associateBy { it.id }

            // Delete local items that were removed on the server
            val remoteIds = remoteItems.map { it.id }.toSet()
            val deletedIds = localById.keys - remoteIds
            if (deletedIds.isNotEmpty()) {
                dao.deleteByIds(deletedIds.toList())
                Log.d(TAG, "SpotPass sync: removed ${deletedIds.size} deleted items")
            }

            if (remoteItems.isEmpty()) return@withContext 0

            // Upsert remote items, preserving local-only flags
            val entities = remoteItems.map { remote ->
                val entity = remote.toEntity()
                val existing = localById[remote.id]
                if (existing != null) {
                    entity.copy(isRead = existing.isRead, isClaimed = existing.isClaimed)
                } else {
                    entity
                }
            }
            dao.upsertItems(entities)
            Log.d(TAG, "SpotPass sync: ${entities.size} items synced")
            entities.size
        } catch (e: Exception) {
            Log.e(TAG, "SpotPass sync failed: ${e.message}", e)
            0
        }
    }

    suspend fun markAsRead(itemId: String) = dao.markAsRead(itemId)
    suspend fun markAllAsRead() = dao.markAllAsRead()
    suspend fun claimPuzzlePanel(itemId: String) = dao.markAsClaimed(itemId)
    suspend fun getClaimedPuzzlePanels(): List<SpotPassItemEntity> = dao.getClaimedPuzzlePanels()

    /** Returns currently active event effects (published and not expired). */
    suspend fun getActiveEffects(): List<EventEffect> =
        dao.getActiveEventEffects(System.currentTimeMillis())
            .mapNotNull { parseEventEffect(it.eventEffect) }
}
