package com.pocketpass.app.data

import com.pocketpass.app.util.RegionFlags

object BingoChallenges {

    private val hobbyKeywords = listOf(
        "Gaming", "Drawing", "Music", "Sports", "Cooking",
        "Reading", "Photography", "Anime", "Travel", "Coding"
    )

    private fun buildChallengePool(): List<BingoChallenge> {
        val pool = mutableListOf<BingoChallenge>()

        // Region challenges — pick a subset of regions
        val regions = RegionFlags.supportedRegions.shuffled().take(6)
        for (region in regions) {
            pool += BingoChallenge(
                type = BingoChallengeType.REGION,
                description = "Meet someone from $region",
                targetValue = region
            )
        }

        // Hobby challenges
        for (hobby in hobbyKeywords.shuffled().take(5)) {
            pool += BingoChallenge(
                type = BingoChallengeType.HOBBY,
                description = "Meet someone who likes $hobby",
                targetValue = hobby
            )
        }

        // Social challenges
        pool += BingoChallenge(
            type = BingoChallengeType.SOCIAL_REPEAT,
            description = "Meet the same person 2 times",
            requiredCount = 2
        )
        pool += BingoChallenge(
            type = BingoChallengeType.SOCIAL_REPEAT,
            description = "Meet the same person 3 times",
            requiredCount = 3
        )
        pool += BingoChallenge(
            type = BingoChallengeType.SOCIAL_TOTAL,
            description = "Meet 3 unique people",
            requiredCount = 3
        )
        pool += BingoChallenge(
            type = BingoChallengeType.SOCIAL_TOTAL,
            description = "Meet 5 unique people",
            requiredCount = 5
        )
        pool += BingoChallenge(
            type = BingoChallengeType.SOCIAL_TOTAL,
            description = "Meet 10 unique people",
            requiredCount = 10
        )

        // Accessory challenges
        pool += BingoChallenge(
            type = BingoChallengeType.ACCESSORY_HAT,
            description = "Meet someone wearing a hat"
        )
        pool += BingoChallenge(
            type = BingoChallengeType.ACCESSORY_COSTUME,
            description = "Meet someone in a costume"
        )

        // Gender challenge
        pool += BingoChallenge(
            type = BingoChallengeType.GENDER,
            description = "Meet a female Pii",
            targetValue = "female"
        )
        pool += BingoChallenge(
            type = BingoChallengeType.GENDER,
            description = "Meet a male Pii",
            targetValue = "male"
        )

        return pool
    }

    fun generateCard(): BingoCard {
        val pool = buildChallengePool().shuffled()
        val usedTargets = mutableSetOf<String>()
        val usedTypes = mutableMapOf<BingoChallengeType, Int>()
        val picked = mutableListOf<BingoChallenge>()

        for (challenge in pool) {
            if (picked.size >= 15) break
            // Avoid duplicate targetValues
            val key = "${challenge.type}:${challenge.targetValue}:${challenge.requiredCount}"
            if (key in usedTargets) continue
            // Limit per type for diversity
            val typeCount = usedTypes.getOrDefault(challenge.type, 0)
            val maxPerType = when (challenge.type) {
                BingoChallengeType.REGION -> 4
                BingoChallengeType.HOBBY -> 3
                BingoChallengeType.SOCIAL_REPEAT -> 2
                BingoChallengeType.SOCIAL_TOTAL -> 2
                BingoChallengeType.ACCESSORY_HAT -> 1
                BingoChallengeType.ACCESSORY_COSTUME -> 1
                BingoChallengeType.GENDER -> 2
                BingoChallengeType.FREE -> 1
            }
            if (typeCount >= maxPerType) continue
            picked.add(challenge)
            usedTargets.add(key)
            usedTypes[challenge.type] = typeCount + 1
        }

        // Pad with more region/hobby if needed
        while (picked.size < 15) {
            val extra = buildChallengePool().shuffled().firstOrNull { c ->
                val key = "${c.type}:${c.targetValue}:${c.requiredCount}"
                key !in usedTargets
            } ?: break
            val key = "${extra.type}:${extra.targetValue}:${extra.requiredCount}"
            picked.add(extra)
            usedTargets.add(key)
        }

        val freeChallenge = BingoChallenge(
            type = BingoChallengeType.FREE,
            description = "Free Space"
        )

        val cells = mutableListOf<BingoCell>()
        var challengeIndex = 0
        for (row in 0..3) {
            for (col in 0..3) {
                if (row == 1 && col == 1) {
                    cells.add(BingoCell(row, col, freeChallenge, completed = true))
                } else {
                    if (challengeIndex < picked.size) {
                        cells.add(BingoCell(row, col, picked[challengeIndex]))
                        challengeIndex++
                    }
                }
            }
        }

        return BingoCard(cells = cells)
    }

    fun checkCell(cell: BingoCell, encounters: List<Encounter>): Boolean {
        if (cell.challenge.type == BingoChallengeType.FREE) return true
        if (cell.completed) return true

        return when (cell.challenge.type) {
            BingoChallengeType.REGION -> encounters.any {
                it.origin.equals(cell.challenge.targetValue, ignoreCase = true)
            }
            BingoChallengeType.HOBBY -> encounters.any {
                it.hobbies.contains(cell.challenge.targetValue, ignoreCase = true)
            }
            BingoChallengeType.SOCIAL_REPEAT -> {
                encounters.groupBy { it.otherUserName.lowercase() }
                    .any { (_, group) -> group.size >= cell.challenge.requiredCount }
            }
            BingoChallengeType.SOCIAL_TOTAL -> {
                encounters.map { it.otherUserName.lowercase() }
                    .distinct().size >= cell.challenge.requiredCount
            }
            BingoChallengeType.ACCESSORY_HAT -> encounters.any { it.hatId.isNotBlank() }
            BingoChallengeType.ACCESSORY_COSTUME -> encounters.any { it.costumeId.isNotBlank() }
            BingoChallengeType.GENDER -> {
                val wantFemale = cell.challenge.targetValue == "female"
                encounters.any { if (wantFemale) !it.isMale else it.isMale }
            }
            BingoChallengeType.FREE -> true
        }
    }

    fun evaluateCard(card: BingoCard, encounters: List<Encounter>): BingoCard {
        val updatedCells = card.cells.map { cell ->
            if (cell.completed) cell
            else cell.copy(completed = checkCell(cell, encounters))
        }
        return card.copy(cells = updatedCells)
    }

    fun findCompletedLines(card: BingoCard): Set<String> {
        val lines = mutableSetOf<String>()

        // 4 rows
        for (row in 0..3) {
            if ((0..3).all { col -> card.getCell(row, col)?.completed == true }) {
                lines.add("row_$row")
            }
        }

        // 4 columns
        for (col in 0..3) {
            if ((0..3).all { row -> card.getCell(row, col)?.completed == true }) {
                lines.add("col_$col")
            }
        }

        // 2 diagonals
        if ((0..3).all { i -> card.getCell(i, i)?.completed == true }) {
            lines.add("diag_main")
        }
        if ((0..3).all { i -> card.getCell(i, 3 - i)?.completed == true }) {
            lines.add("diag_anti")
        }

        return lines
    }
}
