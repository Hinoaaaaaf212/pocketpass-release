package com.pocketpass.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.EventEffect
import com.pocketpass.app.data.EventEffectType
import com.pocketpass.app.data.SpotPassItemEntity
import com.pocketpass.app.data.SpotPassRepository
import com.pocketpass.app.ui.theme.LocalUserPreferences
import com.pocketpass.app.data.parseEventEffect
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.TokenGold
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.launch

private val SpotPassLedGreen = Color(0xFF00E676)

@Composable
fun SpotPassInboxScreen(
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val repo = remember { SpotPassRepository(context) }
    val userPreferences = LocalUserPreferences.current
    val coroutineScope = rememberCoroutineScope()

    val items by repo.allItems.collectAsState(initial = emptyList())

    // Mark read on open
    LaunchedEffect(Unit) {
        repo.markAllAsRead()
        userPreferences.clearSpotPassUnread()
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
                            text = "WaveLink",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    TextButton(onClick = {
                        coroutineScope.launch { repo.markAllAsRead() }
                    }) {
                        Text("Mark All Read", color = GreenText, fontWeight = FontWeight.Medium)
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\uD83D\uDCE1",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No WaveLink content yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MediumText,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "New content will arrive automatically!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediumText
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            SpotPassItemCard(
                                item = item,
                                onRead = {
                                    coroutineScope.launch { repo.markAsRead(item.id) }
                                },
                                onClaim = {
                                    coroutineScope.launch {
                                        repo.claimPuzzlePanel(item.id)
                                        repo.markAsRead(item.id)
                                        Toast.makeText(context, "New puzzle panel unlocked!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
    }
}

@Composable
private fun SpotPassItemCard(
    item: SpotPassItemEntity,
    onRead: () -> Unit,
    onClaim: () -> Unit
) {
    val isPuzzle = item.type == "puzzle_panel"
    val isEvent = item.type == "event"
    val isExpired = isEvent && item.expiresAt != null && item.expiresAt < System.currentTimeMillis()

    // Mark read
    LaunchedEffect(item.id) {
        if (!item.isRead) onRead()
    }

    AeroCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (!item.isRead) OffWhite else OffWhite.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isPuzzle) PocketPassGreen.copy(alpha = 0.2f)
                        else Color(0xFFFFF3E0)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPuzzle) "\uD83E\uDDE9" else "\uD83D\uDCE2",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!item.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotPassLedGreen)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (item.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Effect badge
                val effect = remember(item.eventEffect) { parseEventEffect(item.eventEffect) }
                if (effect != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Effect pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TokenGold.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = effectLabel(effect),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val now = System.currentTimeMillis()
                        val status = when {
                            item.publishedAt > now -> "Upcoming"
                            isExpired -> "Expired"
                            else -> "Active"
                        }
                        val statusColor = when (status) {
                            "Active" -> GreenText
                            "Upcoming" -> Color(0xFFFFA726)
                            else -> Color(0xFFFF5252)
                        }
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatSpotPassTime(item.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MediumText
                    )

                    when {
                        isPuzzle && item.isClaimed -> {
                            Text(
                                text = "Claimed",
                                style = MaterialTheme.typography.labelSmall,
                                color = GreenText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        isPuzzle && !item.isClaimed -> {
                            AeroButton(
                                onClick = onClaim,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Claim",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        isExpired -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Expired",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        isEvent && item.expiresAt != null -> {
                            Text(
                                text = "Expires ${formatSpotPassTime(item.expiresAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediumText
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun effectLabel(effect: EventEffect): String = when (effect.type) {
    EventEffectType.TOKEN_MULTIPLIER -> "x${effect.value.toInt()} Tokens"
    EventEffectType.PUZZLE_DROP_BOOST -> "${(effect.value * 100).toInt()}% Drop Rate"
    EventEffectType.SHOP_DISCOUNT -> "${effect.value.toInt()}% Off Shop"
    EventEffectType.WALK_BONUS -> "Walk Cap ${effect.value.toInt()}"
    EventEffectType.SPECIAL_GREETING -> effect.message ?: "Special Greeting"
}

private fun formatSpotPassTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs

    if (diff < 0) {
        val ahead = -diff
        val minutes = ahead / 60_000
        val hours = ahead / 3_600_000
        val days = ahead / 86_400_000
        return when {
            minutes < 1 -> "in <1m"
            minutes < 60 -> "in ${minutes}m"
            hours < 24 -> "in ${hours}h"
            days < 7 -> "in ${days}d"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                sdf.format(java.util.Date(epochMs))
            }
        }
    }

    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            sdf.format(java.util.Date(epochMs))
        }
    }
}
