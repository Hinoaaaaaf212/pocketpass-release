package com.pocketpass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Achievement
import com.pocketpass.app.data.AchievementCategory
import com.pocketpass.app.data.Achievements
import com.pocketpass.app.ui.theme.AchievementIcon
import com.pocketpass.app.ui.theme.AchievementIconView
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.LocalEncounters
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import com.pocketpass.app.util.RegionFlags
import com.pocketpass.app.util.WorldMapRegions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import java.text.DateFormat
import java.util.Date

@Composable
fun StatisticsScreen(onBack: () -> Unit, onOpenWorldTourMap: () -> Unit = {}) {
    val soundManager = LocalSoundManager.current
    val encounters = LocalEncounters.current

    // Stats (memoized)
    val totalEncounters = encounters.size
    val uniqueLocations = remember(encounters) { encounters.map { it.origin }.distinct().size }
    val uniquePeople = remember(encounters) { encounters.map { it.otherUserName }.distinct().size }
    val locationCounts = remember(encounters) {
        encounters.filter { it.origin.isNotBlank() }
            .groupingBy { it.origin }.eachCount()
            .entries.sortedByDescending { it.value }
    }

    val firstEncounter = remember(encounters) { encounters.minByOrNull { it.timestamp } }
    val latestEncounter = remember(encounters) { encounters.maxByOrNull { it.timestamp } }
    val allAchievements = remember { Achievements.getAll() }
    val unlockedCount = remember(encounters) { allAchievements.count { it.isUnlocked(encounters) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backFocus = remember { FocusRequester() }
                IconButton(
                    onClick = { soundManager.playBack(); onBack() },
                    modifier = Modifier.gamepadFocusable(
                        focusRequester = backFocus,
                        shape = CircleShape,
                        onSelect = { soundManager.playBack(); onBack() }
                    )
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                }
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = OffWhite
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            StatRow(label = "Total Encounters", value = totalEncounters.toString(), icon = "👥")
                            Spacer(modifier = Modifier.height(12.dp))
                            StatRow(label = "Unique People", value = uniquePeople.toString(), icon = "🙋")
                            Spacer(modifier = Modifier.height(12.dp))
                            StatRow(label = "Locations Visited", value = uniqueLocations.toString(), icon = "🌍")
                        }
                    }
                }

                if (firstEncounter != null && latestEncounter != null) {
                    item {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Timeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "First Encounter",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MediumText
                                        )
                                        Text(
                                            text = DateFormat.getDateInstance().format(Date(firstEncounter.timestamp)),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkText
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Latest Encounter",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MediumText
                                        )
                                        Text(
                                            text = DateFormat.getDateInstance().format(Date(latestEncounter.timestamp)),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (locationCounts.isNotEmpty()) {
                    item {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Top Locations",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                locationCounts.take(5).forEachIndexed { index, entry ->
                                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${index + 1}. ${RegionFlags.getFlagForRegion(entry.key)} ${entry.key}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = DarkText
                                            )
                                            Text(
                                                text = "${entry.value} encounter${if (entry.value != 1) "s" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MediumText
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = entry.value.toFloat() / totalEncounters,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = if (LocalDarkMode.current) GreenText else PocketPassGreen,
                                            trackColor = if (LocalDarkMode.current) androidx.compose.ui.graphics.Color(0xFF404040) else androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val visitedRegions = remember(encounters) {
                        encounters.map { it.origin }.filter { it.isNotBlank() }.distinct()
                    }
                    val visitedCount = visitedRegions.size
                    val totalRegions = WorldMapRegions.TOTAL_REGIONS

                    AeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = OffWhite
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "World Tour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$visitedCount / $totalRegions regions discovered",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediumText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = if (totalRegions > 0) visitedCount.toFloat() / totalRegions else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (LocalDarkMode.current) GreenText else PocketPassGreen,
                                trackColor = if (LocalDarkMode.current) androidx.compose.ui.graphics.Color(0xFF404040) else androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            AeroButton(
                                onClick = { soundManager.playSelect(); onOpenWorldTourMap() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Map")
                            }
                        }
                    }
                }

                item {
                    AeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = OffWhite
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🏆 Achievements",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "$unlockedCount/${allAchievements.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenText
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = unlockedCount.toFloat() / allAchievements.size,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (LocalDarkMode.current) GreenText else PocketPassGreen,
                                trackColor = if (LocalDarkMode.current) androidx.compose.ui.graphics.Color(0xFF404040) else androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                            )
                        }
                    }
                }

                AchievementCategory.values().forEach { category ->
                    val categoryAchievements = allAchievements.filter { it.category == category }

                    item {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = OffWhite
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                val categoryIcon = when (category) {
                                    AchievementCategory.SOCIAL -> "👥"
                                    AchievementCategory.TRAVELER -> "🌍"
                                    AchievementCategory.COLLECTOR -> "⭐"
                                    AchievementCategory.DEDICATED -> "🎯"
                                }
                                val categoryName = when (category) {
                                    AchievementCategory.SOCIAL -> "Social"
                                    AchievementCategory.TRAVELER -> "Traveler"
                                    AchievementCategory.COLLECTOR -> "Collector"
                                    AchievementCategory.DEDICATED -> "Dedicated"
                                }

                                Text(
                                    text = "$categoryIcon $categoryName",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                categoryAchievements.forEach { achievement ->
                                    val unlocked = achievement.isUnlocked(encounters)
                                    val (current, total) = achievement.progress(encounters)

                                    AchievementBadge(
                                        achievement = achievement,
                                        unlocked = unlocked,
                                        current = current,
                                        total = total
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, icon: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MediumText
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = GreenText
        )
    }
}

@Composable
fun AchievementBadge(
    achievement: Achievement,
    unlocked: Boolean,
    current: Int,
    total: Int
) {
    val isDark = LocalDarkMode.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    unlocked -> PocketPassGreen.copy(alpha = 0.15f)
                    isDark -> androidx.compose.ui.graphics.Color(0xFF2A2A2A)
                    else -> androidx.compose.ui.graphics.Color(0xFFF0F0F0)
                }
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        when {
                            unlocked -> PocketPassGreen
                            isDark -> androidx.compose.ui.graphics.Color(0xFF4A4A4A)
                            else -> androidx.compose.ui.graphics.Color(0xFFBDBDBD)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AchievementIconView(
                    icon = if (unlocked) achievement.icon else AchievementIcon.LOCKED,
                    modifier = Modifier.size(32.dp),
                    tint = OffWhite
                )
            }
            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (unlocked) achievement.title else "???",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) DarkText else MediumText
                )
                Text(
                    text = if (unlocked) achievement.description else "Locked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediumText
                )
            }
        }

        if (!unlocked && total > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = current.toFloat() / total,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PocketPassGreen,
                    trackColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF3A3A3A) else androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$current/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
