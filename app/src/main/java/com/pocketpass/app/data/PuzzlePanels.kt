package com.pocketpass.app.data

/**
 * Predefined puzzle panels for Puzzle Swap.
 * Each panel defines a grid of pieces with rarity assignments.
 */
object PuzzlePanels {

    fun getAll(): List<PuzzlePanel> = listOf(
        createParkPanel(),
        createBeachPanel(),
        createSpacePanel(),
        createCastlePanel()
    )

    fun getById(id: String): PuzzlePanel? = getAll().find { it.id == id }

    // ── Park: 3x3 grid ──
    // Corners are RARE, edges/center are COMMON
    //  [R] [C] [R]
    //  [C] [C] [C]
    //  [R] [C] [R]
    private fun createParkPanel(): PuzzlePanel {
        val id = "park"
        val pieces = mutableListOf<PuzzlePiece>()
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val isCorner = (r == 0 || r == 2) && (c == 0 || c == 2)
                pieces.add(PuzzlePiece(id, r, c, if (isCorner) PieceRarity.RARE else PieceRarity.COMMON))
            }
        }
        return PuzzlePanel(
            id = id,
            name = "Sunny Park",
            description = "A peaceful park with trees and a pond",
            gridSize = 3,
            theme = PuzzleTheme.PARK,
            pieces = pieces
        )
    }

    // ── Beach: 4x4 grid ──
    // Center 4 pieces are RARE, outer ring is COMMON
    //  [C] [C] [C] [C]
    //  [C] [R] [R] [C]
    //  [C] [R] [R] [C]
    //  [C] [C] [C] [C]
    private fun createBeachPanel(): PuzzlePanel {
        val id = "beach"
        val pieces = mutableListOf<PuzzlePiece>()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val isCenter = r in 1..2 && c in 1..2
                pieces.add(PuzzlePiece(id, r, c, if (isCenter) PieceRarity.RARE else PieceRarity.COMMON))
            }
        }
        return PuzzlePanel(
            id = id,
            name = "Tropical Beach",
            description = "Waves, sand, and a colorful umbrella",
            gridSize = 4,
            theme = PuzzleTheme.BEACH,
            pieces = pieces
        )
    }

    // ── Space: 3x3 grid ──
    // Rocket (center), planet (top-right), moon (bottom-left) are RARE
    //  [C] [C] [R]
    //  [C] [R] [C]
    //  [R] [C] [C]
    private fun createSpacePanel(): PuzzlePanel {
        val id = "space"
        val rarePositions = setOf("0_2", "1_1", "2_0")
        val pieces = mutableListOf<PuzzlePiece>()
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val isRare = "${r}_${c}" in rarePositions
                pieces.add(PuzzlePiece(id, r, c, if (isRare) PieceRarity.RARE else PieceRarity.COMMON))
            }
        }
        return PuzzlePanel(
            id = id,
            name = "Outer Space",
            description = "Stars, planets, and a rocket ship",
            gridSize = 3,
            theme = PuzzleTheme.SPACE,
            pieces = pieces
        )
    }

    // ── Castle: 4x4 grid ──
    // Tower tops (row 0, cols 0 and 3) and flags (row 0, cols 1 and 2) are RARE
    //  [R] [R] [R] [R]
    //  [C] [C] [C] [C]
    //  [C] [C] [C] [C]
    //  [C] [C] [C] [C]
    private fun createCastlePanel(): PuzzlePanel {
        val id = "castle"
        val pieces = mutableListOf<PuzzlePiece>()
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val isRare = r == 0
                pieces.add(PuzzlePiece(id, r, c, if (isRare) PieceRarity.RARE else PieceRarity.COMMON))
            }
        }
        return PuzzlePanel(
            id = id,
            name = "Royal Castle",
            description = "A grand castle with towers and a drawbridge",
            gridSize = 4,
            theme = PuzzleTheme.CASTLE,
            pieces = pieces
        )
    }
}
