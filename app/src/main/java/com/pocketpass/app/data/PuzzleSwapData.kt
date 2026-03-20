package com.pocketpass.app.data

import androidx.compose.ui.graphics.Color

/**
 * Rarity levels for puzzle pieces.
 * COMMON (blue) - obtainable from encounters OR the token shop.
 * RARE (pink) - obtainable ONLY from real encounters (BLE/QR), never purchasable.
 */
enum class PieceRarity(val color: Color, val label: String) {
    COMMON(Color(0xFF4FC3F7), "Common"),
    RARE(Color(0xFFF06292), "Rare")
}

/**
 * Visual theme for a puzzle panel, each drawn with Canvas pixel art.
 */
enum class PuzzleTheme(val displayName: String) {
    PARK("Sunny Park"),
    HANDHELD("Ayn Thor"),
    SPACE("Outer Space"),
    CASTLE("Royal Castle"),
    SPOTPASS("WaveLink")
}

/**
 * A single piece within a puzzle panel.
 */
data class PuzzlePiece(
    val panelId: String,
    val row: Int,
    val col: Int,
    val rarity: PieceRarity
) {
    /** Unique key for this piece, used in storage. */
    val key: String get() = "${row}_${col}"
}

/**
 * Definition of a puzzle panel (the template, not progress).
 */
data class PuzzlePanel(
    val id: String,
    val name: String,
    val description: String,
    val gridSize: Int,
    val theme: PuzzleTheme,
    val pieces: List<PuzzlePiece>,
    val colorHex: String? = null,
    val imageUrl: String? = null
) {
    val totalPieces: Int get() = pieces.size
    val commonCount: Int get() = pieces.count { it.rarity == PieceRarity.COMMON }
    val rareCount: Int get() = pieces.count { it.rarity == PieceRarity.RARE }
}

/**
 * Tracks which pieces the player has collected across all panels.
 * Stored as JSON in DataStore via Gson.
 */
data class PuzzleProgress(
    val collectedPieces: Map<String, List<String>> = emptyMap()
) {
    fun hasPiece(panelId: String, row: Int, col: Int): Boolean {
        return collectedPieces[panelId]?.contains("${row}_${col}") == true
    }

    fun collectedCount(panelId: String): Int {
        return collectedPieces[panelId]?.size ?: 0
    }

    fun isPanelComplete(panel: PuzzlePanel): Boolean {
        return collectedCount(panel.id) >= panel.totalPieces
    }

    fun completionFraction(panel: PuzzlePanel): Float {
        if (panel.totalPieces == 0) return 1f
        return collectedCount(panel.id).toFloat() / panel.totalPieces
    }

    fun withPiece(piece: PuzzlePiece): PuzzleProgress {
        val current = collectedPieces[piece.panelId]?.toMutableList() ?: mutableListOf()
        if (!current.contains(piece.key)) {
            current.add(piece.key)
        }
        return copy(collectedPieces = collectedPieces + (piece.panelId to current))
    }

    fun allUncollectedPieces(panels: List<PuzzlePanel>): List<PuzzlePiece> {
        return panels.flatMap { panel ->
            panel.pieces.filter { !hasPiece(panel.id, it.row, it.col) }
        }
    }

    fun uncollectedCommonPieces(panels: List<PuzzlePanel>): List<PuzzlePiece> {
        return allUncollectedPieces(panels).filter { it.rarity == PieceRarity.COMMON }
    }
}
