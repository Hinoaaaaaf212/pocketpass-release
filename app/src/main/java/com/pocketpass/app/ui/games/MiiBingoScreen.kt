package com.pocketpass.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpass.app.data.BingoCard
import com.pocketpass.app.data.BingoCell
import com.pocketpass.app.data.BingoChallengeType
import com.pocketpass.app.data.BingoChallenges
import com.pocketpass.app.data.BingoProgress
import com.pocketpass.app.data.EventEffect
import com.pocketpass.app.data.EventEffectManager
import com.pocketpass.app.data.SpotPassRepository
import com.pocketpass.app.data.TokenSystem
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.LocalEncounters
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.TokenGold
import com.pocketpass.app.ui.theme.WarningOrange
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Challenge type colors
private val RegionColor = Color(0xFF42A5F5) // blue
private val HobbyColor = Color(0xFFEF5350) // red
private val SocialColor = Color(0xFFAB47BC) // purple
private val AccessoryColor = Color(0xFFFFA726) // orange
private val GenderColor = Color(0xFF26A69A) // teal
private val FreeColor = Color(0xFFFFC107) // gold
private val CompletedColor = PocketPassGreen

private fun challengeTypeColor(type: BingoChallengeType): Color = when (type) {
    BingoChallengeType.REGION -> RegionColor
    BingoChallengeType.HOBBY -> HobbyColor
    BingoChallengeType.SOCIAL_REPEAT, BingoChallengeType.SOCIAL_TOTAL -> SocialColor
    BingoChallengeType.ACCESSORY_HAT, BingoChallengeType.ACCESSORY_COSTUME -> AccessoryColor
    BingoChallengeType.GENDER -> GenderColor
    BingoChallengeType.FREE -> FreeColor
}

private fun challengeTypeIcon(type: BingoChallengeType): ImageVector = when (type) {
    BingoChallengeType.REGION -> Icons.Filled.LocationOn
    BingoChallengeType.HOBBY -> Icons.Filled.Favorite
    BingoChallengeType.SOCIAL_REPEAT, BingoChallengeType.SOCIAL_TOTAL -> Icons.Filled.Person
    BingoChallengeType.ACCESSORY_HAT, BingoChallengeType.ACCESSORY_COSTUME -> Icons.Filled.Star
    BingoChallengeType.GENDER -> Icons.Filled.Face
    BingoChallengeType.FREE -> Icons.Filled.Star
}

private fun challengeTypeLabel(type: BingoChallengeType): String = when (type) {
    BingoChallengeType.REGION -> "Region"
    BingoChallengeType.HOBBY -> "Hobby"
    BingoChallengeType.SOCIAL_REPEAT -> "Repeat Meet"
    BingoChallengeType.SOCIAL_TOTAL -> "Total Meets"
    BingoChallengeType.ACCESSORY_HAT -> "Accessory"
    BingoChallengeType.ACCESSORY_COSTUME -> "Accessory"
    BingoChallengeType.GENDER -> "Gender"
    BingoChallengeType.FREE -> "Free"
}

@Composable
fun MiiBingoScreen(onBack: () -> Unit) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)
    val bingoProgress by userPreferences.bingoProgressFlow.collectAsState(initial = BingoProgress())
    val encounters = LocalEncounters.current

    var selectedCell by remember { mutableStateOf<BingoCell?>(null) }

    // Event multiplier
    var activeEffects by remember { mutableStateOf<List<EventEffect>>(emptyList()) }
    LaunchedEffect(Unit) {
        activeEffects = withContext(Dispatchers.IO) {
            try { SpotPassRepository(context).getActiveEffects() } catch (_: Exception) { emptyList() }
        }
    }
    val tokenMultiplier = EventEffectManager.getTokenMultiplier(activeEffects)

    // Re-evaluate on new encounters
    LaunchedEffect(encounters, bingoProgress.currentCard) {
        val card = bingoProgress.currentCard ?: return@LaunchedEffect
        if (card.isFullyComplete() && card.fullCardClaimed) return@LaunchedEffect

        val evaluated = BingoChallenges.evaluateCard(card, encounters)
        val newLines = BingoChallenges.findCompletedLines(evaluated)
        val previousLines = card.completedLines
        val freshLines = newLines - previousLines

        var tokensToAward = 0
        if (freshLines.isNotEmpty()) {
            tokensToAward += freshLines.size * TokenSystem.BINGO_LINE_REWARD
        }

        val claimFull = evaluated.isFullyComplete() && !card.fullCardClaimed
        if (claimFull) {
            tokensToAward += TokenSystem.BINGO_FULL_CARD_REWARD
        }

        // Apply multiplier
        tokensToAward *= tokenMultiplier

        if (evaluated != card || freshLines.isNotEmpty() || claimFull) {
            val updated = evaluated.copy(
                completedLines = newLines,
                fullCardClaimed = if (claimFull) true else card.fullCardClaimed
            )
            val newProgress = bingoProgress.copy(
                currentCard = updated,
                totalLinesCompleted = bingoProgress.totalLinesCompleted + freshLines.size,
                totalTokensEarned = bingoProgress.totalTokensEarned + tokensToAward
            )
            if (tokensToAward > 0) {
                userPreferences.addTokens(tokensToAward)
                soundManager.playSuccess()
            }
            userPreferences.saveBingoProgress(newProgress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
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
                            text = "Piip Bingo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    TokenBalanceChip(balance = tokenBalance)
                }

                val card = bingoProgress.currentCard

                if (card == null) {
                    EmptyBingoPrompt(
                        onGenerate = {
                            coroutineScope.launch {
                                val newCard = BingoChallenges.generateCard()
                                val evaluated = BingoChallenges.evaluateCard(newCard, encounters)
                                val lines = BingoChallenges.findCompletedLines(evaluated)
                                val updated = evaluated.copy(completedLines = lines)
                                val newProgress = bingoProgress.copy(currentCard = updated)
                                userPreferences.saveBingoProgress(newProgress)
                                soundManager.playSelect()
                            }
                        }
                    )
                } else {
                    // Progress card
                    val completed = card.completedCount()
                    val total = card.cells.size
                    val linesCompleted = card.completedLines.size

                    AeroCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        cornerRadius = 16.dp,
                        elevation = 4.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "$completed/$total",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = PocketPassGreen
                                    )
                                    Text(
                                        text = "cells complete",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MediumText
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$linesCompleted",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (linesCompleted > 0) TokenGold else MediumText
                                    )
                                    Text(
                                        text = "lines",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MediumText
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${bingoProgress.completedCardCount}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (bingoProgress.completedCardCount > 0) CompletedColor else MediumText
                                    )
                                    Text(
                                        text = "cards",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MediumText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Progress bar
                            val isDarkProgress = LocalDarkMode.current
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isDarkProgress) Color(0xFF404040) else Color(0xFFE8E8E8))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = if (total > 0) completed.toFloat() / total else 0f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(PocketPassGreen, PocketPassGreen.copy(alpha = 0.7f))
                                            )
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val letters = listOf("B", "I", "N", "G")
                        for (letter in letters) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(PocketPassGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Grid
                    BingoGrid(
                        card = card,
                        onCellClick = { cell -> selectedCell = cell; soundManager.playSelect() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rewards
                    RewardsInfoCard(tokenMultiplier = tokenMultiplier)

                    Spacer(modifier = Modifier.height(12.dp))

                    // New card button
                    if (card.isFullyComplete() && card.fullCardClaimed) {
                        AeroButton(
                            onClick = {
                                coroutineScope.launch {
                                    val newCard = BingoChallenges.generateCard()
                                    val evaluated = BingoChallenges.evaluateCard(newCard, encounters)
                                    val lines = BingoChallenges.findCompletedLines(evaluated)
                                    val updated = evaluated.copy(completedLines = lines)
                                    val newProgress = bingoProgress.copy(
                                        currentCard = updated,
                                        completedCardCount = bingoProgress.completedCardCount + 1
                                    )
                                    userPreferences.saveBingoProgress(newProgress)
                                    soundManager.playSuccess()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Bingo Card", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

        // Cell dialog
        if (selectedCell != null) {
            CellDetailDialog(
                cell = selectedCell!!,
                tokenBalance = tokenBalance,
                onDismiss = { selectedCell = null },
                onReroll = {
                    coroutineScope.launch {
                        val cell = selectedCell ?: return@launch
                        if (cell.completed) return@launch
                        if (!userPreferences.spendTokens(TokenSystem.BINGO_REROLL_COST)) return@launch

                        val card = bingoProgress.currentCard ?: return@launch
                        val existingTargets = card.cells.map {
                            "${it.challenge.type}:${it.challenge.targetValue}:${it.challenge.requiredCount}"
                        }.toSet()

                        val newCard = BingoChallenges.generateCard()
                        val replacement = newCard.cells
                            .filter { it.challenge.type != BingoChallengeType.FREE }
                            .map { it.challenge }
                            .firstOrNull { c ->
                                val key = "${c.type}:${c.targetValue}:${c.requiredCount}"
                                key !in existingTargets
                            }

                        if (replacement != null) {
                            val updatedCells = card.cells.map { c ->
                                if (c.row == cell.row && c.col == cell.col) {
                                    c.copy(challenge = replacement, completed = false)
                                } else c
                            }
                            val updatedCard = card.copy(cells = updatedCells)
                            val evaluated = BingoChallenges.evaluateCard(updatedCard, encounters)
                            val lines = BingoChallenges.findCompletedLines(evaluated)
                            val finalCard = evaluated.copy(completedLines = lines)
                            userPreferences.saveBingoProgress(
                                bingoProgress.copy(currentCard = finalCard)
                            )
                            soundManager.playSelect()
                        }
                        selectedCell = null
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyBingoPrompt(onGenerate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(PocketPassGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = PocketPassGreen
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Piip Bingo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Complete challenges by meeting people!\nFill rows, columns, or diagonals to earn tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MediumText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Reward hints
        AeroCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 12.dp,
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RewardHintRow(label = "Complete a line", reward = "${TokenSystem.BINGO_LINE_REWARD}")
                RewardHintRow(label = "Complete the card", reward = "${TokenSystem.BINGO_FULL_CARD_REWARD}")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AeroButton(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Generate Bingo Card",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun RewardHintRow(label: String, reward: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MediumText
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83E\uDE99",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reward,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TokenGold
            )
        }
    }
}

@Composable
private fun RewardsInfoCard(tokenMultiplier: Int) {
    AeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 12.dp,
        elevation = 2.dp,
        containerColor = OffWhite.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line reward
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Line",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83E\uDE99",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${TokenSystem.BINGO_LINE_REWARD * tokenMultiplier}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TokenGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(if (LocalDarkMode.current) Color(0xFF444444) else Color(0xFFE0E0E0))
            )

            // Full card reward
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Full Card",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83E\uDE99",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${TokenSystem.BINGO_FULL_CARD_REWARD * tokenMultiplier}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TokenGold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(if (LocalDarkMode.current) Color(0xFF444444) else Color(0xFFE0E0E0))
            )

            // Reroll cost
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Reroll",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumText
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "\uD83E\uDE99",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${TokenSystem.BINGO_REROLL_COST}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MediumText
                    )
                }
            }

            // Multiplier badge
            if (tokenMultiplier > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (LocalDarkMode.current) Color(0xFF3D2E00) else Color(0xFFFFF3E0))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "x$tokenMultiplier",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = WarningOrange
                    )
                }
            }
        }
    }
}

@Composable
private fun BingoGrid(card: BingoCard, onCellClick: (BingoCell) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (row in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (col in 0..3) {
                    val cell = card.getCell(row, col)
                    if (cell != null) {
                        BingoCellView(
                            cell = cell,
                            modifier = Modifier.weight(1f),
                            onClick = { onCellClick(cell) }
                        )
                    } else {
                        // Fallback: empty spacer to keep grid layout intact
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BingoCellView(
    cell: BingoCell,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isFree = cell.challenge.type == BingoChallengeType.FREE
    val typeColor = challengeTypeColor(cell.challenge.type)

    val isDark = LocalDarkMode.current
    val bgColor = when {
        isFree && cell.completed -> FreeColor.copy(alpha = 0.15f)
        cell.completed -> CompletedColor.copy(alpha = 0.12f)
        else -> if (isDark) Color(0xFF2A2A2A) else Color.White
    }
    val borderColor = when {
        cell.completed -> CompletedColor.copy(alpha = 0.5f)
        else -> if (isDark) Color(0xFF444444) else Color(0xFFE8E8E8)
    }

    AeroCard(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.5.dp, borderColor, RoundedCornerShape(6.dp)),
        cornerRadius = 6.dp,
        elevation = 1.dp,
        containerColor = bgColor,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isFree) {
                // Free space
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Free",
                        tint = FreeColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = FreeColor.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            } else if (cell.completed) {
                // Completed
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = cellShortLabel(cell),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText.copy(alpha = 0.25f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    // Checkmark
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CompletedColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Complete",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // Incomplete
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(typeColor)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cellShortLabel(cell),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

private fun cellShortLabel(cell: BingoCell): String {
    val c = cell.challenge
    return when (c.type) {
        BingoChallengeType.REGION -> c.targetValue
        BingoChallengeType.HOBBY -> c.targetValue
        BingoChallengeType.SOCIAL_REPEAT -> "Meet x${c.requiredCount}"
        BingoChallengeType.SOCIAL_TOTAL -> "${c.requiredCount} people"
        BingoChallengeType.ACCESSORY_HAT -> "Hat"
        BingoChallengeType.ACCESSORY_COSTUME -> "Costume"
        BingoChallengeType.GENDER -> c.targetValue.replaceFirstChar { it.uppercase() }
        BingoChallengeType.FREE -> "FREE"
    }
}

@Composable
private fun CellDetailDialog(
    cell: BingoCell,
    tokenBalance: Int,
    onDismiss: () -> Unit,
    onReroll: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val isFree = cell.challenge.type == BingoChallengeType.FREE
    val canReroll = !cell.completed && !isFree && tokenBalance >= TokenSystem.BINGO_REROLL_COST
    val typeColor = challengeTypeColor(cell.challenge.type)
    val typeIcon = challengeTypeIcon(cell.challenge.type)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AeroCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevation = 8.dp,
            containerColor = OffWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (cell.completed) CompletedColor.copy(alpha = 0.15f)
                            else typeColor.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (cell.completed) {
                        Icon(
                            if (isFree) Icons.Filled.Star else Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (isFree) FreeColor else CompletedColor,
                            modifier = Modifier.size(30.dp)
                        )
                    } else {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(typeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = challengeTypeLabel(cell.challenge.type),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = cell.challenge.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (cell.completed) CompletedColor.copy(alpha = 0.1f)
                            else if (LocalDarkMode.current) Color(0xFF3A3A3A) else Color(0xFFF5F5F5)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (cell.completed) "Completed" else "In progress",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (cell.completed) CompletedColor else MediumText
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { soundManager.playBack(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }

                    if (!isFree && !cell.completed) {
                        AeroButton(
                            onClick = { onReroll() },
                            modifier = Modifier.weight(1f),
                            enabled = canReroll,
                            containerColor = TokenGold,
                            contentColor = DarkText,
                            cornerRadius = 12.dp
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "\uD83E\uDE99 ${TokenSystem.BINGO_REROLL_COST} Reroll",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
