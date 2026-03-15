package com.pocketpass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Achievement
import com.pocketpass.app.data.Achievements
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.util.RegionFlags
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

// ── Plaza secondary screen: recent encounters summary ──

@Composable
fun PlazaSecondaryScreen() {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Encounters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (encounters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OffWhite)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No encounters yet. Walk around to meet people!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText
                        )
                    }
                }
            } else {
                val recentEncounters = encounters.sortedByDescending { it.timestamp }.take(10)
                items(recentEncounters, key = { it.encounterId }) { encounter ->
                    SecondaryEncounterRow(encounter)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SecondaryEncounterRow(encounter: Encounter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OffWhite)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = encounter.otherUserName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${RegionFlags.getFlagForRegion(encounter.origin)} ${encounter.origin}",
                style = MaterialTheme.typography.bodySmall,
                color = MediumText,
                maxLines = 1
            )
        }
        Text(
            text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(encounter.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MediumText
        )
    }
}

// ── History secondary screen: stats overview ──

@Composable
fun HistorySecondaryScreen() {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    val totalEncounters = encounters.size
    val uniquePeople = remember(encounters) { encounters.map { it.otherUserName }.distinct().size }
    val topRegions = remember(encounters) {
        encounters.groupingBy { it.origin }.eachCount()
            .entries.sortedByDescending { it.value }.take(5)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Encounter Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("Total", "$totalEncounters", Modifier.weight(1f))
                    StatCard("Unique", "$uniquePeople", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (topRegions.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Regions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(topRegions, key = { it.key }) { (region, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OffWhite)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${RegionFlags.getFlagForRegion(region)} $region",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkText
                        )
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenText
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Friends secondary screen: friend details ──

@Composable
fun FriendsSecondaryScreen() {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    val friendsByMeetCount = remember(encounters) {
        encounters.sortedByDescending { it.meetCount }.take(10)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Most Met",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (friendsByMeetCount.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OffWhite)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends yet!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText
                        )
                    }
                }
            } else {
                items(friendsByMeetCount, key = { it.encounterId }) { encounter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OffWhite)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = encounter.otherUserName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Met ${encounter.meetCount} time${if (encounter.meetCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Animated Plaza secondary screen: Mii list with count ──

@Composable
fun AnimatedPlazaSecondaryScreen() {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    val displayedMiis = remember(encounters) {
        encounters.sortedByDescending { it.timestamp }.take(30)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Plaza Visitors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${displayedMiis.size} Miis in your plaza",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (displayedMiis.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OffWhite)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your plaza is empty. Meet some people!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText
                        )
                    }
                }
            } else {
                items(displayedMiis, key = { it.encounterId }) { encounter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(OffWhite)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = encounter.otherUserName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${RegionFlags.getFlagForRegion(encounter.origin)} ${encounter.origin}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText,
                                maxLines = 1
                            )
                        }
                        if (encounter.greeting.isNotBlank()) {
                            Text(
                                text = "\"${encounter.greeting}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediumText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Messages secondary screen: recent conversations ──

@Composable
fun MessagesSecondaryScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a conversation on the main screen to chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumText
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Statistics secondary screen: achievements ──

@Composable
fun StatisticsSecondaryScreen() {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

    val allAchievements = remember { Achievements.getAll() }
    val unlockedAchievements = remember(encounters) {
        allAchievements.filter { it.isUnlocked(encounters) }
    }
    val lockedAchievements = remember(encounters) {
        allAchievements.filter { !it.isUnlocked(encounters) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Achievements (${unlockedAchievements.size}/${allAchievements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(unlockedAchievements, key = { it.id }) { achievement ->
                AchievementSecondaryRow(achievement, encounters, isUnlocked = true)
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (lockedAchievements.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MediumText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(lockedAchievements.take(5), key = { it.id }) { achievement ->
                    AchievementSecondaryRow(achievement, encounters, isUnlocked = false)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AchievementSecondaryRow(
    achievement: Achievement,
    encounters: List<Encounter>,
    isUnlocked: Boolean
) {
    val (current, total) = achievement.progress(encounters)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isUnlocked) OffWhite else OffWhite.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.pocketpass.app.ui.theme.AchievementIconView(
            icon = if (isUnlocked) achievement.icon
                   else com.pocketpass.app.ui.theme.AchievementIcon.LOCKED,
            modifier = Modifier.size(32.dp),
            tint = if (isUnlocked) GreenText else MediumText
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnlocked) DarkText else MediumText
            )
            Text(
                text = if (isUnlocked) achievement.description else "$current / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MediumText
            )
        }
    }
}

// ── Games Hub secondary screen: token balance & puzzle progress ──

@Composable
fun GamesHubSecondaryScreen() {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)
    val puzzleProgress by userPreferences.puzzleProgressFlow.collectAsState(
        initial = com.pocketpass.app.data.PuzzleProgress()
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Activity Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Play Coins",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$tokenBalance coins",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Earn coins by meeting people!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Puzzle Swap",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val completedPanels = puzzleProgress.collectedPieces.count { (_, pieces) ->
                            pieces.size >= 15
                        }
                        val totalPieces = puzzleProgress.collectedPieces.values.sumOf { it.size }

                        Text(
                            text = "$totalPieces pieces collected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkText
                        )
                        Text(
                            text = "$completedPanels panels completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Settings secondary screen (moved from SettingsScreen.kt) ──

@Composable
fun SettingsSecondaryScreenContent(
    onCreateNewMii: () -> Unit,
    onOpenProfileSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val soundManager = com.pocketpass.app.util.LocalSoundManager.current

    val savedMiis by userPreferences.savedMiisFlow.collectAsState(initial = emptyList())
    val activeMiiHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val miiCount by userPreferences.getMiiCount().collectAsState(initial = 0)

    val maxMiis = 3
    val canCreateNewMii = miiCount < maxMiis

    // Auth state
    val authRepo = remember { AuthRepository() }
    val isLoggedIn by authRepo.isLoggedIn.collectAsState(initial = false)
    val syncRepo = remember { SyncRepository(context) }
    var syncStatus by remember { mutableStateOf("idle") }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            syncStatus = "syncing"
            try {
                // Use NonCancellable so the network request finishes
                // even if the user navigates away mid-sync
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    syncRepo.fullSync()
                }
                syncStatus = "synced"
            } catch (_: Exception) {
                syncStatus = "failed"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Auth section
            item {
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoggedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (syncStatus) {
                            "syncing" -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = PocketPassGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", style = MaterialTheme.typography.bodySmall, color = MediumText, fontWeight = FontWeight.SemiBold)
                            }
                            "synced" -> {
                                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(14.dp), tint = GreenText)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Synced to cloud", style = MaterialTheme.typography.bodySmall, color = GreenText, fontWeight = FontWeight.SemiBold)
                            }
                            "failed" -> {
                                Text("Sync failed", style = MaterialTheme.typography.bodySmall, color = ErrorText, fontWeight = FontWeight.SemiBold)
                            }
                            else -> {
                                Text("Signed in", style = MaterialTheme.typography.bodySmall, color = GreenText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AeroButton(
                        onClick = { soundManager.playTap(); showSignOutConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ) {
                        Text("Sign Out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                } else {
                    AeroButton(
                        onClick = { soundManager.playNavigate(); onOpenAuth() },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp
                    ) {
                        Text("Sign In to Sync", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AeroButton(
                    onClick = { soundManager.playNavigate(); onOpenProfileSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Text("Profile Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                AeroButton(
                    onClick = { soundManager.playNavigate(); onOpenAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    containerColor = if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF4A4A4A) else Color(0xFF6B6B6B)
                ) {
                    Text("App Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Miis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "$miiCount / $maxMiis",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (miiCount >= maxMiis) ErrorText else DarkText
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(savedMiis, key = { it }) { miiHex ->
                val isActive = miiHex == activeMiiHex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) PocketPassGreen.copy(alpha = 0.2f) else OffWhite
                        )
                        .clickable { coroutineScope.launch { userPreferences.setActiveMii(miiHex); try { SyncRepository(context).syncProfile() } catch (_: Exception) { } } }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MiiAvatarViewer(hexData = miiHex)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isActive) "Active" else "Tap to use",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) GreenText else MediumText
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                AeroButton(
                    onClick = { if (canCreateNewMii) onCreateNewMii() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canCreateNewMii,
                    cornerRadius = 12.dp,
                    containerColor = if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF4A4A4A) else Color(0xFF6B6B6B),
                    contentColor = OffWhite
                ) {
                    Text(
                        text = if (canCreateNewMii) "Create New Pii" else "Max Piis (3/3)",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out? Your data is saved locally but won't sync until you sign back in.") },
            confirmButton = {
                AeroButton(
                    onClick = {
                        showSignOutConfirm = false
                        coroutineScope.launch { authRepo.signOut() }
                    },
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Shared composable ──

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(OffWhite)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = GreenText
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MediumText
            )
        }
    }
}
