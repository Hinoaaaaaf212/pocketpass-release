package com.pocketpass.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Supabase row — matches the spotpass_items table. */
@Serializable
data class SupabaseSpotPassItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    @SerialName("panel_id") val panelId: String? = null,
    @SerialName("panel_name") val panelName: String? = null,
    @SerialName("panel_description") val panelDescription: String? = null,
    @SerialName("grid_size") val gridSize: Int? = null,
    @SerialName("rare_positions") val rarePositions: String? = null,
    @SerialName("panel_color_hex") val panelColorHex: String? = null,
    @SerialName("panel_image_url") val panelImageUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("event_effect") val eventEffect: String? = null
)

/** Room entity with extra local-only fields. */
@Entity(tableName = "spotpass_items")
data class SpotPassItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    val panelId: String? = null,
    val panelName: String? = null,
    val panelDescription: String? = null,
    val gridSize: Int? = null,
    val rarePositions: String? = null,
    val panelColorHex: String? = null,
    val panelImageUrl: String? = null,
    val expiresAt: Long? = null,
    val publishedAt: Long,
    val createdAt: Long? = null,
    val isRead: Boolean = false,
    val isClaimed: Boolean = false,
    val eventEffect: String? = null
)

/** Convert Supabase model → Room entity. */
fun SupabaseSpotPassItem.toEntity(): SpotPassItemEntity = SpotPassItemEntity(
    id = id,
    type = type,
    title = title,
    body = body,
    panelId = panelId,
    panelName = panelName,
    panelDescription = panelDescription,
    gridSize = gridSize,
    rarePositions = rarePositions,
    panelColorHex = panelColorHex,
    panelImageUrl = panelImageUrl,
    expiresAt = expiresAt?.let { parseIsoToEpochMillis(it) },
    publishedAt = parseIsoToEpochMillis(publishedAt),
    createdAt = createdAt?.let { parseIsoToEpochMillis(it) },
    eventEffect = eventEffect
)

/** Convert a claimed puzzle-panel entity into a PuzzlePanel for the puzzle system. */
fun SpotPassItemEntity.toPuzzlePanel(): PuzzlePanel? {
    if (type != "puzzle_panel" || panelId == null || gridSize == null) return null

    val rareKeys = try {
        rarePositions
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.map { it.trim().removeSurrounding("\"") }
            ?.toSet()
            ?: emptySet()
    } catch (_: Exception) { emptySet() }

    val pieces = mutableListOf<PuzzlePiece>()
    for (r in 0 until gridSize) {
        for (c in 0 until gridSize) {
            val key = "${r}_${c}"
            val rarity = if (key in rareKeys) PieceRarity.RARE else PieceRarity.COMMON
            pieces.add(PuzzlePiece(panelId, r, c, rarity))
        }
    }

    return PuzzlePanel(
        id = panelId,
        name = panelName ?: title,
        description = panelDescription ?: body,
        gridSize = gridSize,
        theme = PuzzleTheme.SPOTPASS,
        pieces = pieces,
        colorHex = panelColorHex,
        imageUrl = panelImageUrl
    )
}

private fun parseIsoToEpochMillis(iso: String): Long = try {
    Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(iso)).toEpochMilli()
} catch (_: Exception) {
    try { Instant.parse(iso).toEpochMilli() } catch (_: Exception) { 0L }
}
