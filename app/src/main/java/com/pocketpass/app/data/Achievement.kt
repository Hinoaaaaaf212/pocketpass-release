package com.pocketpass.app.data

import com.pocketpass.app.ui.theme.AchievementIcon

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: AchievementIcon,
    val category: AchievementCategory,
    val requirement: Int,
    val isUnlocked: (encounters: List<Encounter>) -> Boolean,
    val progress: (encounters: List<Encounter>) -> Pair<Int, Int> // current / total
)

enum class AchievementCategory {
    SOCIAL,      // Meeting people
    TRAVELER,    // Visiting locations
    COLLECTOR,   // Collection milestones
    DEDICATED    // Time/streak based
}

object Achievements {
    fun getAll(): List<Achievement> = listOf(
        // SOCIAL Achievements
        Achievement(
            id = "first_steps",
            title = "First Steps",
            description = "Met your first person",
            icon = AchievementIcon.FIRST_STEPS,
            category = AchievementCategory.SOCIAL,
            requirement = 1,
            isUnlocked = { it.size >= 1 },
            progress = { Pair(minOf(it.size, 1), 1) }
        ),
        Achievement(
            id = "social_butterfly",
            title = "Social Butterfly",
            description = "Met 5 different people",
            icon = AchievementIcon.SOCIAL_BUTTERFLY,
            category = AchievementCategory.SOCIAL,
            requirement = 5,
            isUnlocked = { it.size >= 5 },
            progress = { Pair(minOf(it.size, 5), 5) }
        ),
        Achievement(
            id = "popular",
            title = "Popular",
            description = "Met 10 different people",
            icon = AchievementIcon.POPULAR,
            category = AchievementCategory.SOCIAL,
            requirement = 10,
            isUnlocked = { it.size >= 10 },
            progress = { Pair(minOf(it.size, 10), 10) }
        ),
        Achievement(
            id = "celebrity",
            title = "Celebrity",
            description = "Met 25 different people",
            icon = AchievementIcon.CELEBRITY,
            category = AchievementCategory.SOCIAL,
            requirement = 25,
            isUnlocked = { it.size >= 25 },
            progress = { Pair(minOf(it.size, 25), 25) }
        ),
        Achievement(
            id = "legend",
            title = "Legend",
            description = "Met 50 different people",
            icon = AchievementIcon.LEGEND,
            category = AchievementCategory.SOCIAL,
            requirement = 50,
            isUnlocked = { it.size >= 50 },
            progress = { Pair(minOf(it.size, 50), 50) }
        ),

        // TRAVELER Achievements
        Achievement(
            id = "tourist",
            title = "Tourist",
            description = "Met people from 3 different regions",
            icon = AchievementIcon.TOURIST,
            category = AchievementCategory.TRAVELER,
            requirement = 3,
            isUnlocked = { encounters ->
                encounters.map { it.origin }.distinct().size >= 3
            },
            progress = { encounters ->
                val count = encounters.map { it.origin }.distinct().size
                Pair(minOf(count, 3), 3)
            }
        ),
        Achievement(
            id = "explorer",
            title = "Explorer",
            description = "Met people from 5 different regions",
            icon = AchievementIcon.EXPLORER,
            category = AchievementCategory.TRAVELER,
            requirement = 5,
            isUnlocked = { encounters ->
                encounters.map { it.origin }.distinct().size >= 5
            },
            progress = { encounters ->
                val count = encounters.map { it.origin }.distinct().size
                Pair(minOf(count, 5), 5)
            }
        ),
        Achievement(
            id = "world_traveler",
            title = "World Traveler",
            description = "Met people from 10 different regions",
            icon = AchievementIcon.WORLD_TRAVELER,
            category = AchievementCategory.TRAVELER,
            requirement = 10,
            isUnlocked = { encounters ->
                encounters.map { it.origin }.distinct().size >= 10
            },
            progress = { encounters ->
                val count = encounters.map { it.origin }.distinct().size
                Pair(minOf(count, 10), 10)
            }
        ),
        Achievement(
            id = "globe_trotter",
            title = "Globe Trotter",
            description = "Met people from 15 different regions",
            icon = AchievementIcon.GLOBE_TROTTER,
            category = AchievementCategory.TRAVELER,
            requirement = 15,
            isUnlocked = { encounters ->
                encounters.map { it.origin }.distinct().size >= 15
            },
            progress = { encounters ->
                val count = encounters.map { it.origin }.distinct().size
                Pair(minOf(count, 15), 15)
            }
        ),

        // COLLECTOR Achievements
        Achievement(
            id = "reunion",
            title = "Reunion",
            description = "Met the same person 3 times",
            icon = AchievementIcon.REUNION,
            category = AchievementCategory.COLLECTOR,
            requirement = 3,
            isUnlocked = { encounters ->
                encounters.any { it.meetCount >= 3 }
            },
            progress = { encounters ->
                val max = encounters.maxOfOrNull { it.meetCount } ?: 0
                Pair(minOf(max, 3), 3)
            }
        ),
        Achievement(
            id = "best_friends",
            title = "Best Friends",
            description = "Met the same person 5 times",
            icon = AchievementIcon.BEST_FRIENDS,
            category = AchievementCategory.COLLECTOR,
            requirement = 5,
            isUnlocked = { encounters ->
                encounters.any { it.meetCount >= 5 }
            },
            progress = { encounters ->
                val max = encounters.maxOfOrNull { it.meetCount } ?: 0
                Pair(minOf(max, 5), 5)
            }
        ),
        Achievement(
            id = "inseparable",
            title = "Inseparable",
            description = "Met the same person 10 times",
            icon = AchievementIcon.INSEPARABLE,
            category = AchievementCategory.COLLECTOR,
            requirement = 10,
            isUnlocked = { encounters ->
                encounters.any { it.meetCount >= 10 }
            },
            progress = { encounters ->
                val max = encounters.maxOfOrNull { it.meetCount } ?: 0
                Pair(minOf(max, 10), 10)
            }
        ),

        // DEDICATED Achievements
        Achievement(
            id = "collector",
            title = "Collector",
            description = "Total encounters: 20",
            icon = AchievementIcon.COLLECTOR,
            category = AchievementCategory.DEDICATED,
            requirement = 20,
            isUnlocked = { encounters ->
                encounters.map { it.meetCount }.sum() >= 20
            },
            progress = { encounters ->
                val total = encounters.map { it.meetCount }.sum()
                Pair(minOf(total, 20), 20)
            }
        ),
        Achievement(
            id = "enthusiast",
            title = "Enthusiast",
            description = "Total encounters: 50",
            icon = AchievementIcon.ENTHUSIAST,
            category = AchievementCategory.DEDICATED,
            requirement = 50,
            isUnlocked = { encounters ->
                encounters.map { it.meetCount }.sum() >= 50
            },
            progress = { encounters ->
                val total = encounters.map { it.meetCount }.sum()
                Pair(minOf(total, 50), 50)
            }
        ),
        Achievement(
            id = "master",
            title = "Master",
            description = "Total encounters: 100",
            icon = AchievementIcon.MASTER,
            category = AchievementCategory.DEDICATED,
            requirement = 100,
            isUnlocked = { encounters ->
                encounters.map { it.meetCount }.sum() >= 100
            },
            progress = { encounters ->
                val total = encounters.map { it.meetCount }.sum()
                Pair(minOf(total, 100), 100)
            }
        )
    )
}
