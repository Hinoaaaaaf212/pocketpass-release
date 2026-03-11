package com.pocketpass.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.PieceRarity
import com.pocketpass.app.data.PuzzlePanels
import com.pocketpass.app.data.PuzzleProgress
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.CheckeredBackground
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable

@Composable
fun PuzzleBoardScreen(
    panelId: String,
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }
    val progress by userPreferences.puzzleProgressFlow.collectAsState(initial = PuzzleProgress())
    val spotPassRepo = remember { com.pocketpass.app.data.SpotPassRepository(context) }
    val claimedSpotPass by spotPassRepo.claimedPanels.collectAsState(initial = emptyList())
    val panel = remember(claimedSpotPass) { PuzzlePanels.getByIdIncludingSpotPass(panelId, claimedSpotPass) }

    // Pre-load the Ayn Thor bitmap if this panel uses it
    loadHandheldBitmap(context)

    // Pre-load SpotPass panel image if available
    LaunchedEffect(panel?.id, panel?.imageUrl) {
        val url = panel?.imageUrl
        val id = panel?.id
        if (url != null && id != null) {
            loadSpotPassBitmap(id, url)
        }
    }

    if (panel == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Panel not found", color = ErrorText)
        }
        return
    }

    val isComplete = progress.isPanelComplete(panel)
    val collectedCount = progress.collectedCount(panel.id)
    val collectedCommon = panel.pieces.count { it.rarity == PieceRarity.COMMON && progress.hasPiece(panel.id, it.row, it.col) }
    val collectedRare = panel.pieces.count { it.rarity == PieceRarity.RARE && progress.hasPiece(panel.id, it.row, it.col) }

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
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = panel.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = "$collectedCount/${panel.totalPieces} pieces collected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                    }
                }

                // Puzzle Grid
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OffWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            val gridSize = panel.gridSize
                            val cellSize = size.width / gridSize

                            for (row in 0 until gridSize) {
                                for (col in 0 until gridSize) {
                                    val cx = col * cellSize
                                    val cy = row * cellSize
                                    val hasPiece = progress.hasPiece(panel.id, row, col)

                                    if (hasPiece) {
                                        // Draw the actual pixel art for this piece
                                        drawPuzzlePiece(panel.theme, row, col, cx, cy, cellSize, panel.colorHex, panel.id, panel.gridSize)
                                    } else {
                                        // Draw rarity-colored placeholder
                                        val piece = panel.pieces.find { it.row == row && it.col == col }
                                        val rarityColor = piece?.rarity?.color ?: Color.Gray
                                        drawRect(
                                            rarityColor.copy(alpha = 0.2f),
                                            Offset(cx, cy),
                                            Size(cellSize, cellSize)
                                        )
                                        // Question mark / lock indicator
                                        drawCircle(
                                            rarityColor.copy(alpha = 0.4f),
                                            cellSize * 0.2f,
                                            Offset(cx + cellSize / 2, cy + cellSize / 2)
                                        )
                                    }

                                    // Grid border
                                    drawRect(
                                        Color.White.copy(alpha = 0.8f),
                                        Offset(cx, cy),
                                        Size(cellSize, cellSize),
                                        style = Stroke(2f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Piece Legend
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OffWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Piece Legend",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Common
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(PieceRarity.COMMON.color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Common: $collectedCommon/${panel.commonCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkText,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Rare
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(PieceRarity.RARE.color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Rare: $collectedRare/${panel.rareCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isComplete) {
                                "Puzzle complete! All pieces collected."
                            } else {
                                "Blue pieces: tokens or encounters. Pink pieces: encounters only!"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isComplete) GreenText else MediumText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Completion message
                if (isComplete) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PocketPassGreen.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "\uD83C\uDF89",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Congratulations!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenText
                            )
                            Text(
                                text = "You completed the ${panel.name} puzzle!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        } // AnimatedVisibility
    }
}
