package com.pocketpass.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Color blending utility ──

/**
 * Blends this color toward [other] by [ratio] (0.0 = this, 1.0 = other).
 */
fun Color.blendWith(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = this.red * (1 - r) + other.red * r,
        green = this.green * (1 - r) + other.green * r,
        blue = this.blue * (1 - r) + other.blue * r,
        alpha = this.alpha * (1 - r) + other.alpha * r
    )
}

// ── AeroCard ──

/**
 * A glossy glass card with a white gradient highlight overlay and luminous border.
 * Drop-in replacement for Material3 Card with Frutiger Aero aesthetics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AeroCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    containerColor: Color = OffWhite,
    shape: Shape? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = CardBorder
    val highlightColor = GlassHighlight
    val highlightMidColor = GlassHighlightMid
    val resolvedShape = shape ?: RoundedCornerShape(cornerRadius)

    val cardContent: @Composable ColumnScope.() -> Unit = {
        Box {
            Column(content = content)
            // Glass highlight overlay — top 45% white gradient fade
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to highlightColor,
                                0.35f to highlightMidColor,
                                0.45f to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    if (onClick != null) {
        Card(
            modifier = modifier,
            shape = resolvedShape,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(0.75.dp, borderColor),
            onClick = onClick,
            content = cardContent
        )
    } else {
        Card(
            modifier = modifier,
            shape = resolvedShape,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(0.75.dp, borderColor),
            content = cardContent
        )
    }
}

// ── AeroButton ──

/**
 * A gel-style button with a 3-stop vertical gradient and subtle elevation.
 * Inspired by Windows Vista/7 Aero and early iOS glossy buttons.
 */
@Composable
fun AeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = PocketPassGreen,
    contentColor: Color = Color.White,
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation = if (isPressed) 1.dp else 3.dp
    val shape = RoundedCornerShape(cornerRadius)

    val effectiveColor = if (enabled) containerColor else Color(0xFF9E9E9E)
    val topColor = effectiveColor.blendWith(Color.White, 0.30f)
    val midColor = effectiveColor
    val bottomColor = effectiveColor.blendWith(Color.Black, 0.08f)

    Box(
        modifier = modifier
            .shadow(elevation, shape)
            .background(
                Brush.verticalGradient(listOf(topColor, midColor, bottomColor)),
                shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

// ── AeroTopBar ──

/**
 * Frosted-glass top bar with semi-transparent gradient background.
 * Consolidates the duplicated top-bar pattern across 15+ screens.
 */
@Composable
fun AeroTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isDark = LocalDarkMode.current
    val bgGradient = if (isDark)
        Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.40f),
                Color.Black.copy(alpha = 0.15f),
                Color.Transparent
            )
        )
    else
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.20f),
                Color.Transparent
            )
        )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgGradient)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkText
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.weight(1f)
            )

            actions()
        }
    }
}

// ── Modifier.aeroGloss() ──

/**
 * Glass-effect extension for non-card surfaces (icon containers, badges, chat bubbles).
 * Draws a subtle white gradient overlay on top of existing content.
 */
fun Modifier.aeroGloss(isDark: Boolean = false): Modifier = this.drawWithContent {
    drawContent()
    val highlight = if (isDark) 0.08f else 0.30f
    val mid = if (isDark) 0.02f else 0.08f
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = highlight),
                0.4f to Color.White.copy(alpha = mid),
                0.5f to Color.Transparent,
                1.0f to Color.Transparent
            )
        )
    )
}
