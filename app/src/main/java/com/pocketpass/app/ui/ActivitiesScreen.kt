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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.R
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.games.TokenBalanceChip
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.aeroGloss
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable

@Composable
fun ActivitiesScreen(
    onBack: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenShop: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenSpotPass: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
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
                            text = "Activities",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    TokenBalanceChip(balance = tokenBalance)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Games category
                    item {
                        ActivityCategoryCard(
                            iconRes = R.drawable.ic_puzzle_swap,
                            title = "Games",
                            description = "Play mini-games and collect puzzle pieces!",
                            subtitle = "Puzzle Swap, Piip Bingo",
                            onClick = { soundManager.playNavigate(); onOpenGames() }
                        )
                    }

                    // Shop
                    item {
                        ActivityCategoryCard(
                            iconRes = R.drawable.ic_shop,
                            title = "Shop",
                            description = "Spend tokens on card themes, hats, and more!",
                            subtitle = "Browse items",
                            onClick = { soundManager.playNavigate(); onOpenShop() }
                        )
                    }

                    // Leaderboard
                    item {
                        ActivityCategoryCard(
                            iconRes = R.drawable.ic_leaderboard,
                            title = "Leaderboard",
                            description = "See how you rank against other PocketPass users!",
                            subtitle = "Global rankings",
                            onClick = { soundManager.playNavigate(); onOpenLeaderboard() }
                        )
                    }

                    // SpotPass category
                    item {
                        val spotPassRepo = remember { com.pocketpass.app.data.SpotPassRepository(context) }
                        val spotPassUnread by spotPassRepo.unreadCount.collectAsState(initial = 0)

                        ActivityCategoryCard(
                            iconRes = R.drawable.ic_puzzle_swap,
                            title = "WaveLink",
                            description = "Receive new puzzle panels and event announcements delivered to your device!",
                            subtitle = if (spotPassUnread > 0) "$spotPassUnread new item${if (spotPassUnread > 1) "s" else ""}" else "Server-delivered content",
                            onClick = { soundManager.playNavigate(); onOpenSpotPass() }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
    }
}

@Composable
private fun ActivityCategoryCard(
    iconRes: Int,
    title: String,
    description: String,
    subtitle: String,
    onClick: () -> Unit
) {
    AeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .gamepadFocusable(
                shape = RoundedCornerShape(16.dp),
                onSelect = onClick
            ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PocketPassGreen.copy(alpha = 0.2f))
                    .aeroGloss(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    modifier = Modifier.size(44.dp),
                    tint = Color.Unspecified
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenText,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Open",
                tint = GreenText,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
