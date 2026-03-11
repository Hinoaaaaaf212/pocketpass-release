package com.pocketpass.app.data

/**
 * Play Token economy system for PocketPass mini-games.
 *
 * ## How tokens work
 * - Players earn tokens from BLE/QR encounters (1 token each).
 * - Tokens are spent in mini-games (e.g. Puzzle Swap: 2 tokens per common piece).
 * - Token balance is stored in DataStore via UserPreferences.
 *
 * ## Shop
 * The shop uses per-item pricing defined in ShopItem.price (see ShopItems registry).
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

    /** Steps required to earn 1 token. */
    const val STEPS_PER_TOKEN = 150

    /** Maximum tokens earnable from walking per day. */
    const val MAX_STEP_TOKENS_PER_DAY = 10

    /** Tokens awarded per completed bingo line (row/col/diagonal). */
    const val BINGO_LINE_REWARD = 3

    /** Tokens awarded for completing an entire bingo card. */
    const val BINGO_FULL_CARD_REWARD = 10

    /** Cost to reroll one uncompleted bingo cell. */
    const val BINGO_REROLL_COST = 1
}
