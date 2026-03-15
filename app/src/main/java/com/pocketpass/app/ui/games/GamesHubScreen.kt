package com.pocketpass.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pocketpass.app.ui.CheckeredBackground
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable

@Composable
fun GamesHubScreen(
    onBack: () -> Unit,
    onOpenPuzzleSwap: () -> Unit,
    onOpenBingo: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)

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
                            text = "Games",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Token balance chip
                    TokenBalanceChip(balance = tokenBalance)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Puzzle Swap game card
                    item {
                        GameCard(
                            iconRes = R.drawable.ic_puzzle_swap,
                            title = "Puzzle Swap",
                            description = "Collect pieces from encounters to complete pixel art panels!",
                            subtitle = "4 panels to complete",
                            onClick = { soundManager.playNavigate(); onOpenPuzzleSwap() }
                        )
                    }

                    // Mii Bingo card
                    item {
                        GameCard(
                            iconRes = R.drawable.ic_bingo,
                            title = "Pal Bingo",
                            description = "Complete encounter challenges to fill your bingo card!",
                            subtitle = "Earn tokens for lines",
                            onClick = { soundManager.playNavigate(); onOpenBingo() }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        } // AnimatedVisibility
    }
}

@Composable
fun TokenBalanceChip(balance: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFC107))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83E\uDE99", // coin emoji
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$balance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
        }
    }
}

@Composable
internal fun GameCard(
    icon: String? = null,
    iconRes: Int? = null,
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
            // Game icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PocketPassGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        modifier = Modifier.size(44.dp),
                        tint = Color.Unspecified
                    )
                } else if (icon != null) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Game info
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
                contentDescription = "Play",
                tint = GreenText,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
