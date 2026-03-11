package com.pocketpass.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.PieceRarity
import com.pocketpass.app.data.PuzzlePanels
import com.pocketpass.app.data.PuzzleProgress
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.TokenSystem
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.CheckeredBackground
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.launch

@Composable
fun PuzzleSwapScreen(
    onBack: () -> Unit,
    onOpenPuzzleBoard: (panelId: String) -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val tokenBalance by userPreferences.tokenBalanceFlow.collectAsState(initial = 0)
    val progress by userPreferences.puzzleProgressFlow.collectAsState(initial = PuzzleProgress())
    val spotPassRepo = remember { com.pocketpass.app.data.SpotPassRepository(context) }
    val claimedSpotPass by spotPassRepo.claimedPanels.collectAsState(initial = emptyList())
    val panels = remember(claimedSpotPass) { PuzzlePanels.getAllIncludingSpotPass(claimedSpotPass) }

    // Pre-load the Ayn Thor bitmap for the handheld puzzle
    loadHandheldBitmap(context)

    // Pre-load SpotPass panel images
    LaunchedEffect(panels) {
        panels.forEach { panel ->
            val url = panel.imageUrl
            if (url != null) {
                loadSpotPassBitmap(panel.id, url)
            }
        }
    }

    var showTokenShop by remember { mutableStateOf(false) }
    var lastGrantedPiece by remember { mutableStateOf<String?>(null) }

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
                            text = "Puzzle Swap",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Overall progress
                    item {
                        val totalPieces = panels.sumOf { it.totalPieces }
                        val totalCollected = panels.sumOf { progress.collectedCount(it.id) }
                        val completedPanels = panels.count { progress.isPanelComplete(it) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = OffWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Collection Progress",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "$totalCollected/$totalPieces pieces",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = GreenText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = if (totalPieces > 0) totalCollected.toFloat() / totalPieces else 0f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PocketPassGreen,
                                    trackColor = Color(0xFFE0E0E0)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$completedPanels/${panels.size} panels complete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediumText
                                )
                            }
                        }
                    }

                    // Panel cards
                    items(panels) { panel ->
                        val collected = progress.collectedCount(panel.id)
                        val isComplete = progress.isPanelComplete(panel)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .gamepadFocusable(
                                    shape = RoundedCornerShape(16.dp),
                                    onSelect = { soundManager.playNavigate(); onOpenPuzzleBoard(panel.id) }
                                )
                                .clickable { soundManager.playNavigate(); onOpenPuzzleBoard(panel.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isComplete) PocketPassGreen.copy(alpha = 0.15f) else OffWhite
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Mini puzzle preview
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .aspectRatio(1f)
                                    ) {
                                        drawFullPuzzle(panel.theme, panel.gridSize, panel.colorHex, panel.id)
                                    }
                                    // Overlay for incomplete
                                    if (!isComplete) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = panel.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText
                                        )
                                        if (isComplete) {
                                            Text(
                                                text = "COMPLETE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GreenText,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = panel.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediumText
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = progress.completionFraction(panel),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = PocketPassGreen,
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$collected/${panel.totalPieces} pieces",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText
                                        )
                                        Text(
                                            text = "${panel.gridSize}x${panel.gridSize}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Token shop button
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { soundManager.playSelect(); showTokenShop = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .gamepadFocusable(
                                    shape = RoundedCornerShape(12.dp),
                                    onSelect = { soundManager.playSelect(); showTokenShop = true }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFC107),
                                contentColor = DarkText
                            )
                        ) {
                            Text(
                                text = "\uD83E\uDE99 Spend Tokens for Pieces",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        } // AnimatedVisibility

        // Token Shop Dialog
        if (showTokenShop) {
            TokenShopDialog(
                tokenBalance = tokenBalance,
                progress = progress,
                onDismiss = { showTokenShop = false },
                onPurchase = {
                    coroutineScope.launch {
                        val uncollected = progress.uncollectedCommonPieces(panels)
                        if (uncollected.isEmpty()) return@launch
                        if (!userPreferences.spendTokens(TokenSystem.PUZZLE_SWAP_COMMON_PIECE_COST)) return@launch

                        val piece = uncollected.random()
                        userPreferences.addPuzzlePiece(piece)
                        lastGrantedPiece = "${piece.panelId}: (${piece.row},${piece.col})"
                        soundManager.playSuccess()

                        // Sync leaderboard so puzzles_completed updates immediately
                        launch { try { SyncRepository(context).syncLeaderboard() } catch (_: Exception) {} }
                    }
                }
            )
        }
    }
}

@Composable
private fun TokenShopDialog(
    tokenBalance: Int,
    progress: PuzzleProgress,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit
) {
    val panels = remember { PuzzlePanels.getAll() }
    val uncollectedCommon = progress.uncollectedCommonPieces(panels)
    val canPurchase = tokenBalance >= TokenSystem.PUZZLE_SWAP_COMMON_PIECE_COST && uncollectedCommon.isNotEmpty()
    val soundManager = LocalSoundManager.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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
                Text(
                    text = "\uD83E\uDE99 Token Shop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Token balance
                TokenBalanceChip(balance = tokenBalance)

                Spacer(modifier = Modifier.height(16.dp))

                // Purchase option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F0F0))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Random Common Piece",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(PieceRarity.COMMON.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Blue piece from any panel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uncollectedCommon.size} common pieces remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MediumText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "\uD83D\uDCA1 Pink (rare) pieces can only be obtained from encounters!",
                    style = MaterialTheme.typography.labelSmall,
                    color = PieceRarity.RARE.color,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { soundManager.playBack(); onDismiss() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = { onPurchase() },
                        modifier = Modifier.weight(1f),
                        enabled = canPurchase,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFC107),
                            contentColor = DarkText,
                            disabledContainerColor = Color(0xFFE0E0E0),
                            disabledContentColor = MediumText
                        )
                    ) {
                        Text(
                            text = "\uD83E\uDE99 ${TokenSystem.PUZZLE_SWAP_COMMON_PIECE_COST} Buy",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!canPurchase && uncollectedCommon.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All common pieces collected!",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenText,
                        fontWeight = FontWeight.Bold
                    )
                } else if (!canPurchase) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Not enough tokens! Meet people to earn more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText
                    )
                }
            }
        }
    }
}
