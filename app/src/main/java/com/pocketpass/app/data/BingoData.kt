package com.pocketpass.app.data

enum class BingoChallengeType {
    REGION,
    HOBBY,
    SOCIAL_REPEAT,
    SOCIAL_TOTAL,
    ACCESSORY_HAT,
    ACCESSORY_COSTUME,
    GENDER,
    FREE
}

data class BingoChallenge(
    val type: BingoChallengeType,
    val description: String,
    val targetValue: String = "",
    val requiredCount: Int = 1
)

data class BingoCell(
    val row: Int,
    val col: Int,
    val challenge: BingoChallenge,
    val completed: Boolean = false
)

data class BingoCard(
    val id: Long = System.currentTimeMillis(),
    val cells: List<BingoCell> = emptyList(),
    val completedLines: Set<String> = emptySet(),
    val fullCardClaimed: Boolean = false
) {
    fun getCell(row: Int, col: Int): BingoCell? =
        cells.find { it.row == row && it.col == col }

    fun isFullyComplete(): Boolean =
        cells.all { it.completed }

    fun completedCount(): Int =
        cells.count { it.completed }
}

data class BingoProgress(
    val currentCard: BingoCard? = null,
    val completedCardCount: Int = 0,
    val totalLinesCompleted: Int = 0,
    val totalTokensEarned: Int = 0
)
