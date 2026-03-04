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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Achievement
import com.pocketpass.app.data.AchievementCategory
import com.pocketpass.app.data.Achievements
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.ui.theme.AchievementIcon
import com.pocketpass.app.ui.theme.AchievementIconView
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import java.text.DateFormat
import java.util.Date

@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    // Calculate statistics
    val totalEncounters = encounters.size
    val uniqueLocations = encounters.map { it.origin }.distinct().size
    val uniquePeople = encounters.map { it.otherUserName }.distinct().size
    val locationCounts = encounters.groupingBy { it.origin }.eachCount()
        .entries.sortedByDescending { it.value }

    val firstEncounter = encounters.minByOrNull { it.timestamp }
    val latestEncounter = encounters.maxByOrNull { it.timestamp }

    Box(modifier = Modifier.fillMaxSize()) {
        // Checkered background
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
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
                // Overview Stats
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

                // Timeline
                if (firstEncounter != null && latestEncounter != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

                // Top Locations
                if (locationCounts.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                                text = "${index + 1}. ${entry.key}",
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
                                            color = PocketPassGreen,
                                            trackColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Achievements
                val allAchievements = Achievements.getAll()
                val unlockedCount = allAchievements.count { it.isUnlocked(encounters) }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OffWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                                    color = PocketPassGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = unlockedCount.toFloat() / allAchievements.size,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PocketPassGreen,
                                trackColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                            )
                        }
                    }
                }

                // Achievement Categories
                AchievementCategory.values().forEach { category ->
                    val categoryAchievements = allAchievements.filter { it.category == category }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
            color = DarkText
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (unlocked) PocketPassGreen.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color(0xFFF5F5F5)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (unlocked) PocketPassGreen
                        else androidx.compose.ui.graphics.Color(0xFFBDBDBD)
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

            // Info
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

        // Progress bar for locked achievements
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
                    trackColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
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
