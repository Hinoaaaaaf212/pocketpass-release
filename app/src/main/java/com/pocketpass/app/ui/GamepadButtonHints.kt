package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalGamepadState

@Composable
fun GamepadButtonHints() {
    val gamepadState = LocalGamepadState.current
    if (!gamepadState.isAynThor) return

    AnimatedVisibility(
        visible = gamepadState.isGamepadActive,
        enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HintItem(icon = { AButtonIcon() }, action = "Select")
                VerticalDivider()
                HintItem(icon = { BButtonIcon() }, action = "Back")
                VerticalDivider()
                HintItem(icon = { DpadIcon() }, action = "Navigate")
                VerticalDivider()
                HintItem(icon = { ShoulderButtonsIcon() }, action = "Switch")
            }
        }
    }
}

@Composable
private fun HintItem(icon: @Composable () -> Unit, action: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MediumText,
            textAlign = TextAlign.Center
        )
    }
}

// ── A Button: green circle with "A" ──
@Composable
private fun AButtonIcon() {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Canvas(modifier = Modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 2f
        // Shadow
        drawCircle(color = Color(0xFF4A9E20), center = center + Offset(0f, 2f), radius = radius)
        // Main circle
        drawCircle(color = PocketPassGreen, center = center, radius = radius)
        // Highlight
        drawCircle(color = Color.White.copy(alpha = 0.25f), center = center - Offset(0f, radius * 0.25f), radius = radius * 0.55f)
        // Letter
        val textLayout = textMeasurer.measure("A", textStyle)
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                (size.width - textLayout.size.width) / 2f,
                (size.height - textLayout.size.height) / 2f
            )
        )
    }
}

// ── B Button: red circle with "B" ──
@Composable
private fun BButtonIcon() {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    val buttonColor = Color(0xFFE54040)
    Canvas(modifier = Modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 2f
        // Shadow
        drawCircle(color = Color(0xFFB82020), center = center + Offset(0f, 2f), radius = radius)
        // Main circle
        drawCircle(color = buttonColor, center = center, radius = radius)
        // Highlight
        drawCircle(color = Color.White.copy(alpha = 0.25f), center = center - Offset(0f, radius * 0.25f), radius = radius * 0.55f)
        // Letter
        val textLayout = textMeasurer.measure("B", textStyle)
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                (size.width - textLayout.size.width) / 2f,
                (size.height - textLayout.size.height) / 2f
            )
        )
    }
}

// ── D-Pad: cross shape ──
@Composable
private fun DpadIcon() {
    Canvas(modifier = Modifier.size(40.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val armWidth = size.width * 0.32f
        val armLength = size.width * 0.42f
        val cornerR = armWidth * 0.2f
        val padColor = Color(0xFF555555)
        val shadowColor = Color(0xFF3A3A3A)

        // Shadow
        drawDpadCross(cx, cy + 2f, armWidth, armLength, cornerR, shadowColor)
        // Main cross
        drawDpadCross(cx, cy, armWidth, armLength, cornerR, padColor)

        // Center circle indent
        drawCircle(color = Color(0xFF444444), center = Offset(cx, cy), radius = armWidth * 0.28f)
        drawCircle(color = Color(0xFF666666), center = Offset(cx, cy), radius = armWidth * 0.18f)

        // Arrow triangles on each arm
        val arrowColor = Color.White.copy(alpha = 0.6f)
        val arrowSize = armWidth * 0.25f
        val arrowDist = armLength * 0.65f

        // Up arrow
        drawArrow(cx, cy - arrowDist, arrowSize, 0f, arrowColor)
        // Down arrow
        drawArrow(cx, cy + arrowDist, arrowSize, 180f, arrowColor)
        // Left arrow
        drawArrow(cx - arrowDist, cy, arrowSize, 270f, arrowColor)
        // Right arrow
        drawArrow(cx + arrowDist, cy, arrowSize, 90f, arrowColor)
    }
}

private fun DrawScope.drawDpadCross(
    cx: Float, cy: Float, armWidth: Float, armLength: Float, cornerR: Float, color: Color
) {
    // Horizontal bar
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - armLength, cy - armWidth / 2f),
        size = Size(armLength * 2f, armWidth),
        cornerRadius = CornerRadius(cornerR, cornerR)
    )
    // Vertical bar
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - armWidth / 2f, cy - armLength),
        size = Size(armWidth, armLength * 2f),
        cornerRadius = CornerRadius(cornerR, cornerR)
    )
}

private fun DrawScope.drawArrow(cx: Float, cy: Float, arrowSize: Float, rotation: Float, color: Color) {
    val path = Path().apply {
        // Triangle pointing up by default
        moveTo(0f, -arrowSize)
        lineTo(-arrowSize * 0.7f, arrowSize * 0.3f)
        lineTo(arrowSize * 0.7f, arrowSize * 0.3f)
        close()
    }
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    val cos = kotlin.math.cos(radians)
    val sin = kotlin.math.sin(radians)
    val transformedPath = Path().apply {
        val points = listOf(
            Pair(0f, -arrowSize),
            Pair(-arrowSize * 0.7f, arrowSize * 0.3f),
            Pair(arrowSize * 0.7f, arrowSize * 0.3f)
        )
        points.forEachIndexed { i, (px, py) ->
            val rx = px * cos - py * sin + cx
            val ry = px * sin + py * cos + cy
            if (i == 0) moveTo(rx, ry) else lineTo(rx, ry)
        }
        close()
    }
    drawPath(transformedPath, color, style = Fill)
}

// ── L/R Shoulder Buttons ──
@Composable
private fun ShoulderButtonsIcon() {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Canvas(modifier = Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val bumperColor = Color(0xFF666666)
        val shadowColor = Color(0xFF444444)
        val bumperWidth = w * 0.40f
        val bumperHeight = h * 0.65f
        val gap = w * 0.06f
        val cornerR = CornerRadius(6f, 6f)

        // Left bumper
        val lx = w / 2f - gap / 2f - bumperWidth
        val ly = (h - bumperHeight) / 2f
        drawRoundRect(color = shadowColor, topLeft = Offset(lx, ly + 2f), size = Size(bumperWidth, bumperHeight), cornerRadius = cornerR)
        drawRoundRect(color = bumperColor, topLeft = Offset(lx, ly), size = Size(bumperWidth, bumperHeight), cornerRadius = cornerR)

        // Right bumper
        val rx = w / 2f + gap / 2f
        drawRoundRect(color = shadowColor, topLeft = Offset(rx, ly + 2f), size = Size(bumperWidth, bumperHeight), cornerRadius = cornerR)
        drawRoundRect(color = bumperColor, topLeft = Offset(rx, ly), size = Size(bumperWidth, bumperHeight), cornerRadius = cornerR)

        // Letters
        val lText = textMeasurer.measure("L", textStyle)
        drawText(lText, topLeft = Offset(lx + (bumperWidth - lText.size.width) / 2f, ly + (bumperHeight - lText.size.height) / 2f))
        val rText = textMeasurer.measure("R", textStyle)
        drawText(rText, topLeft = Offset(rx + (bumperWidth - rText.size.width) / 2f, ly + (bumperHeight - rText.size.height) / 2f))
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(Color(0xFFDDDDDD))
    )
}
