package com.pocketpass.app.data

/**
 * Play Token economy system for PocketPass mini-games.
 *
 * ## How tokens work
 * - Players earn tokens from BLE/QR encounters (1 token each).
 * - Tokens are spent in mini-games (e.g. Puzzle Swap: 2 tokens per common piece).
 * - Token balance is stored in DataStore via UserPreferences.
 *
 * ## Adding a new game that uses tokens
 * 1. Define a cost constant below (e.g. `MY_GAME_ACTION_COST = 3`).
 * 2. In your game screen, read balance via `userPreferences.tokenBalanceFlow`.
 * 3. Spend tokens via `userPreferences.spendTokens(amount)` (returns false if insufficient).
 * 4. Grant tokens via `userPreferences.addTokens(amount)` when rewarding players.
 */
object TokenSystem {
    /** Tokens earned per new encounter (BLE, QR, or debug). */
    const val TOKENS_PER_NEW_ENCOUNTER = 1

    /** Tokens earned per repeat encounter (same person met again). */
    const val TOKENS_PER_REPEAT_ENCOUNTER = 1

    /** Cost to purchase one random COMMON puzzle piece. */
    const val PUZZLE_SWAP_COMMON_PIECE_COST = 2

    /** Probability (0.0 - 1.0) of granting a puzzle piece per encounter. */
    const val PUZZLE_PIECE_DROP_CHANCE = 0.4f
}
