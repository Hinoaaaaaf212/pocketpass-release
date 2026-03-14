package com.pocketpass.app.data

import com.pocketpass.app.util.Continent
import com.pocketpass.app.util.WorldMapRegions

enum class WorldTourMilestone(val threshold: Int, val label: String, val reward: Int) {
    FIRST_REGION(1, "First Contact", 2),
    FIVE_REGIONS(5, "Explorer", 5),
    TEN_REGIONS(10, "Globetrotter", 10),
    TWENTY_FIVE_REGIONS(25, "World Traveler", 20),
    FIFTY_REGIONS(50, "International", 35),
    SEVENTY_FIVE_REGIONS(75, "Ambassador", 50),
    ALL_CONTINENTS(0, "Every Continent", 15),  // special: 1+ region per continent
    HUNDRED_REGIONS(100, "Centurion", 75),
    ALL_REGIONS(WorldMapRegions.TOTAL_REGIONS, "Completionist", 200);

    fun isUnlocked(visitedRegions: Set<String>): Boolean {
        if (this == ALL_CONTINENTS) {
            val visitedContinents = WorldMapRegions.regions
                .filter { it.name in visitedRegions }
                .map { it.continent }
                .toSet()
            return visitedContinents.size == Continent.entries.size
        }
        return visitedRegions.size >= threshold
    }
}
