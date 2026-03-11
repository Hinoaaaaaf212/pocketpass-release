package com.pocketpass.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive dimension system.
 * Compact = phone portrait (<600dp wide), Medium = phone landscape / small tablet,
 * Expanded = tablet / large screen.
 */
data class AppDimensions(
    /** Large Mii avatar (detail views, settings profile) */
    val avatarLarge: Dp,
    /** Medium Mii avatar (encounter cards, friend cards) */
    val avatarMedium: Dp,
    /** Small Mii avatar (list items, chat) */
    val avatarSmall: Dp,
    /** Friend card height in grid */
    val friendCardHeight: Dp,
    /** Max height for scrollable lists inside dialogs */
    val dialogListHeight: Dp,
    /** Empty state container height */
    val emptyStateHeight: Dp,
    /** Standard content padding */
    val contentPadding: Dp,
    /** Game cover art width */
    val gameCoverWidth: Dp,
    /** Game cover art height */
    val gameCoverHeight: Dp,
    /** Chat message max width */
    val chatBubbleMaxWidth: Dp,
    /** QR code display size */
    val qrCodeSize: Dp,
    /** Mood icon size in detail views */
    val moodIconSize: Dp,
    /** Whether layout is compact (phone portrait) */
    val isCompact: Boolean,
    /** Screen width in dp */
    val screenWidthDp: Int,
    /** Screen height in dp */
    val screenHeightDp: Int,
    /** Number of columns for friend grid */
    val friendGridColumns: Int
)

val LocalAppDimensions = compositionLocalOf { expandedDimensions(800, 600) }

private fun compactDimensions(width: Int, height: Int) = AppDimensions(
    avatarLarge = 100.dp,
    avatarMedium = 64.dp,
    avatarSmall = 40.dp,
    friendCardHeight = 140.dp,
    dialogListHeight = 180.dp,
    emptyStateHeight = 80.dp,
    contentPadding = 12.dp,
    gameCoverWidth = 50.dp,
    gameCoverHeight = 67.dp,
    chatBubbleMaxWidth = (width * 0.75f).dp,
    qrCodeSize = 140.dp,
    moodIconSize = 36.dp,
    isCompact = true,
    screenWidthDp = width,
    screenHeightDp = height,
    friendGridColumns = 2
)

private fun mediumDimensions(width: Int, height: Int) = AppDimensions(
    avatarLarge = 140.dp,
    avatarMedium = 80.dp,
    avatarSmall = 48.dp,
    friendCardHeight = 170.dp,
    dialogListHeight = 240.dp,
    emptyStateHeight = 100.dp,
    contentPadding = 16.dp,
    gameCoverWidth = 60.dp,
    gameCoverHeight = 80.dp,
    chatBubbleMaxWidth = 280.dp,
    qrCodeSize = 160.dp,
    moodIconSize = 48.dp,
    isCompact = false,
    screenWidthDp = width,
    screenHeightDp = height,
    friendGridColumns = 2
)

private fun expandedDimensions(width: Int, height: Int) = AppDimensions(
    avatarLarge = 180.dp,
    avatarMedium = 100.dp,
    avatarSmall = 48.dp,
    friendCardHeight = 200.dp,
    dialogListHeight = 300.dp,
    emptyStateHeight = 120.dp,
    contentPadding = 16.dp,
    gameCoverWidth = 70.dp,
    gameCoverHeight = 94.dp,
    chatBubbleMaxWidth = 280.dp,
    qrCodeSize = 180.dp,
    moodIconSize = 56.dp,
    isCompact = false,
    screenWidthDp = width,
    screenHeightDp = height,
    friendGridColumns = 3
)

@Composable
fun rememberAppDimensions(): AppDimensions {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp
    val height = config.screenHeightDp
    return remember(width, height) {
        when {
            width < 600 -> compactDimensions(width, height)
            width < 840 -> mediumDimensions(width, height)
            else -> expandedDimensions(width, height)
        }
    }
}
