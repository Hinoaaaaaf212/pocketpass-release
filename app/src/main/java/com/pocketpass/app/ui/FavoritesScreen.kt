package com.pocketpass.app.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.util.RegionFlags
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var selectedEncounter by remember { mutableStateOf<Encounter?>(null) }

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
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "⭐ Favorites",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "${encounters.size} friend${if (encounters.size != 1) "s" else ""} collected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumText
                    )
                }
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
                        text = "✨",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Friends Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Walk around to meet people!\nThey'll appear here when you do.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MediumText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Grid of favorites
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(encounters.sortedByDescending { it.timestamp }) { encounter ->
                        FavoriteCard(
                            encounter = encounter,
                            onClick = { selectedEncounter = encounter }
                        )
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Detail Dialog
        selectedEncounter?.let { encounter ->
            FavoriteDetailDialog(
                encounter = encounter,
                onDismiss = { selectedEncounter = null },
                onDelete = { encounterToDelete ->
                    coroutineScope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            db.encounterDao().deleteEncounter(encounterToDelete)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun FavoriteCard(
    encounter: Encounter,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mii avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = encounter.otherUserName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Location with flag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = RegionFlags.getFlagForRegion(encounter.origin),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = encounter.origin,
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Meet count
            if (encounter.meetCount > 1) {
                Text(
                    text = "Met ${encounter.meetCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = PocketPassGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FavoriteDetailDialog(
    encounter: Encounter,
    onDismiss: () -> Unit,
    onDelete: (Encounter) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = OffWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Mii
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(20.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                Text(
                    text = encounter.otherUserName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Greeting
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFF0F0F0))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "\"${encounter.greeting}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkText,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Details
                if (encounter.age.isNotBlank()) {
                    DetailRow(icon = "🎂", label = "Age", value = encounter.age)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                DetailRow(icon = RegionFlags.getFlagForRegion(encounter.origin), label = "From", value = encounter.origin)

                if (encounter.meetCount > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = "🤝", label = "Met", value = "${encounter.meetCount} times")
                }

                if (encounter.hobbies.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = "💭", label = "Hobbies", value = encounter.hobbies)
                }

                Spacer(modifier = Modifier.height(8.dp))

                val dateStr = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(Date(encounter.timestamp))
                DetailRow(icon = "⏰", label = "Met", value = dateStr)

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Delete button
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onDelete(encounter)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = androidx.compose.ui.graphics.Color(0xFFD32F2F)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            androidx.compose.ui.graphics.Color(0xFFD32F2F)
                        )
                    ) {
                        Text("Delete")
                    }

                    // Close button
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = PocketPassGreen
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MediumText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
            modifier = Modifier.weight(1f)
        )
    }
}
