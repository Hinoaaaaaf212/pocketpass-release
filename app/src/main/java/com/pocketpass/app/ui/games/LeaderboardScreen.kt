package com.pocketpass.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.SupabaseLeaderboardEntry
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.ui.MiiAvatarViewer
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.launch

private val Gold = Color(0xFFFFD700)
private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

private enum class LeaderboardCategory(val label: String, val sortKey: String) {
    ENCOUNTERS("Encounters", "total_encounters"),
    PUZZLES("Puzzles", "puzzles_completed"),
    PEOPLE_MET("People Met", "unique_encounters"),
    ACHIEVEMENTS("Achievements", "achievements_unlocked")
}

@Composable
fun LeaderboardScreen(
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncRepo = remember { SyncRepository(context) }
    val authRepo = remember { AuthRepository() }
    val currentUserId = remember { authRepo.currentUserId }

    var selectedCategory by remember { mutableStateOf(LeaderboardCategory.ENCOUNTERS) }
    var entries by remember { mutableStateOf<List<SupabaseLeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            isLoading = true
            // Sync own stats first, then fetch
            try { syncRepo.syncLeaderboard() } catch (_: Exception) {}
            entries = syncRepo.fetchLeaderboard(sortBy = selectedCategory.sortKey)
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory) {
        isLoading = true
        entries = syncRepo.fetchLeaderboard(sortBy = selectedCategory.sortKey)
        isLoading = false
    }

    // Initial sync on first load
    LaunchedEffect(Unit) {
        try { syncRepo.syncLeaderboard() } catch (_: Exception) {}
        entries = syncRepo.fetchLeaderboard(sortBy = selectedCategory.sortKey)
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = "Leaderboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    IconButton(
                        onClick = { soundManager.playSelect(); refresh() }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = DarkText)
                    }
                }

                LeaderboardCategoryTabs(
                    selected = selectedCategory,
                    onSelect = { soundManager.playNavigate(); selectedCategory = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PocketPassGreen)
                        }
                    }
                    entries.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "\uD83C\uDFC6",
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No leaderboard data yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MediumText,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Start meeting people to climb the ranks!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediumText
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(entries) { index, entry ->
                                LeaderboardRow(
                                    rank = index + 1,
                                    entry = entry,
                                    isCurrentUser = entry.userId == currentUserId,
                                    category = selectedCategory
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaderboardCategoryTabs(
    selected: LeaderboardCategory,
    onSelect: (LeaderboardCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeaderboardCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        text = category.label,
                        fontWeight = if (selected == category) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PocketPassGreen,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.gamepadFocusable(
                    shape = RoundedCornerShape(8.dp),
                    onSelect = { onSelect(category) }
                )
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    rank: Int,
    entry: SupabaseLeaderboardEntry,
    isCurrentUser: Boolean,
    category: LeaderboardCategory
) {
    val rankColor = when (rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> MediumText
    }

    val score = when (category) {
        LeaderboardCategory.ENCOUNTERS -> entry.totalEncounters
        LeaderboardCategory.PUZZLES -> entry.puzzlesCompleted
        LeaderboardCategory.PEOPLE_MET -> entry.uniqueEncounters
        LeaderboardCategory.ACHIEVEMENTS -> entry.achievementsUnlocked
    }

    val isDark = LocalDarkMode.current
    val backgroundColor = if (isCurrentUser) {
        if (isDark) Color(0xFF2A4020) else Color(0xFFDFF2D0)
    } else OffWhite

    AeroCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = backgroundColor,
        elevation = if (isCurrentUser) 6.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rank <= 3) {
                    Text(
                        text = when (rank) { 1 -> "\uD83E\uDD47"; 2 -> "\uD83E\uDD48"; else -> "\uD83E\uDD49" },
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = rankColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.isMale) Color(0xFF64B5F6) else Color(0xFFF48FB1)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (entry.avatarHex.isNotBlank()) {
                    MiiAvatarViewer(hexData = entry.avatarHex)
                } else {
                    Text(
                        text = entry.userName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.userName.ifBlank { "Anonymous" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (entry.origin.isNotBlank()) {
                    Text(
                        text = "${RegionFlags.getFlagForRegion(entry.origin)} ${entry.origin}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MediumText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "$score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) rankColor else GreenText
            )
        }
    }
}
