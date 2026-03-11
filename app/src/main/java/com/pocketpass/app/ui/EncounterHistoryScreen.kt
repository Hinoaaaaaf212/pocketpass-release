package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import com.pocketpass.app.util.RegionFlags
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun EncounterHistoryScreen(onBack: () -> Unit) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val authRepo = remember { com.pocketpass.app.data.AuthRepository() }
    val isLoggedIn by authRepo.isLoggedIn.collectAsState(initial = false)
    val friendRepo = remember { com.pocketpass.app.data.FriendRepository() }
    var selectedEncounter by remember { mutableStateOf<Encounter?>(null) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
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
                    text = "Encounter History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (encounters.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Encounters Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Walk around to meet people!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MediumText
                    )
                }
            } else {
                // Encounter list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "${encounters.size} Encounter${if (encounters.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(encounters, key = { it.encounterId }) { encounter ->
                        EncounterHistoryCard(
                            encounter = encounter,
                            onClick = { soundManager.playSelect(); selectedEncounter = encounter },
                            onDelete = {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    db.encounterDao().deleteEncounter(encounter)
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
        } // AnimatedVisibility

        // Detail Dialog (for friend requests)
        selectedEncounter?.let { encounter ->
            FriendDetailDialog(
                encounter = encounter,
                isLoggedIn = isLoggedIn,
                friendRepo = friendRepo,
                onDismiss = { selectedEncounter = null },
                onDelete = { encounterToDelete ->
                    coroutineScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            db.encounterDao().deleteEncounter(encounterToDelete)
                        }
                    }
                },
                onFriendshipChanged = { }
            )
        }
    }
}

@Composable
fun EncounterHistoryCard(encounter: Encounter, onClick: () -> Unit = {}, onDelete: () -> Unit) {
    val soundManager = LocalSoundManager.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mii thumbnail
            Box(
                modifier = Modifier
                    .size(LocalAppDimensions.current.avatarSmall)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color(0xFFE3F2FD),
                                androidx.compose.ui.graphics.Color(0xFFBBDEFB)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = encounter.otherUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "\"${encounter.greeting.take(40)}${if (encounter.greeting.length > 40) "..." else ""}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediumText,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = RegionFlags.getFlagForRegion(encounter.origin),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = encounter.origin,
                        style = MaterialTheme.typography.labelSmall,
                        color = MediumText
                    )
                }
                if (encounter.meetCount > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤝",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "Met ${encounter.meetCount} times",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(encounter.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText
                )
            }

            // Delete button
            IconButton(
                onClick = { soundManager.playDelete(); onDelete() },
                modifier = Modifier.gamepadFocusable(
                    shape = CircleShape,
                    onSelect = { soundManager.playDelete(); onDelete() }
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Encounter",
                    tint = ErrorText
                )
            }
        }
    }
}
