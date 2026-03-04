package com.pocketpass.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Nintendo-style mood icons
 * Inspired by Mii facial expressions and Nintendo's friendly design language
 */

enum class MoodType {
    HAPPY,
    EXCITED,
    COOL,
    FRIENDLY,
    SHY,
    SLEEPY,
    PARTY,
    THOUGHTFUL,
    PEACEFUL,
    CHEERFUL
}

@Composable
fun MoodIcon(mood: MoodType, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(48.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension / 2f * 0.85f

        // Draw base circle (face)
        drawCircle(
            color = Color(0xFFFFF8DC),  // Cream color for face
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // Draw outline
        drawCircle(
            color = Color(0xFF5F4B3C),  // Brown outline
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3f)
        )

        when (mood) {
            MoodType.HAPPY -> drawHappyFace(centerX, centerY, radius)
            MoodType.EXCITED -> drawExcitedFace(centerX, centerY, radius)
            MoodType.COOL -> drawCoolFace(centerX, centerY, radius)
            MoodType.FRIENDLY -> drawFriendlyFace(centerX, centerY, radius)
            MoodType.SHY -> drawShyFace(centerX, centerY, radius)
            MoodType.SLEEPY -> drawSleepyFace(centerX, centerY, radius)
            MoodType.PARTY -> drawPartyFace(centerX, centerY, radius)
            MoodType.THOUGHTFUL -> drawThoughtfulFace(centerX, centerY, radius)
            MoodType.PEACEFUL -> drawPeacefulFace(centerX, centerY, radius)
            MoodType.CHEERFUL -> drawCheerfulFace(centerX, centerY, radius)
        }
    }
}

// Happy - Classic smile with open eyes
private fun DrawScope.drawHappyFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Left eye
    drawCircle(
        color = eyeColor,
        radius = radius * 0.12f,
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.2f)
    )

    // Right eye
    drawCircle(
        color = eyeColor,
        radius = radius * 0.12f,
        center = Offset(centerX + radius * 0.3f, centerY - radius * 0.2f)
    )

    // Smile
    val smilePath = Path().apply {
        moveTo(centerX - radius * 0.35f, centerY + radius * 0.1f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.45f,
            centerX + radius * 0.35f, centerY + radius * 0.1f
        )
    }
    drawPath(
        path = smilePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

// Excited - Big smile with sparkle eyes
private fun DrawScope.drawExcitedFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Left sparkle eye
    drawStar(centerX - radius * 0.3f, centerY - radius * 0.2f, radius * 0.15f, eyeColor)

    // Right sparkle eye
    drawStar(centerX + radius * 0.3f, centerY - radius * 0.2f, radius * 0.15f, eyeColor)

    // Wide open smile
    drawOval(
        color = eyeColor,
        topLeft = Offset(centerX - radius * 0.3f, centerY + radius * 0.15f),
        size = Size(radius * 0.6f, radius * 0.25f)
    )

    // Tongue
    drawOval(
        color = Color(0xFFFF6B9D),
        topLeft = Offset(centerX - radius * 0.15f, centerY + radius * 0.25f),
        size = Size(radius * 0.3f, radius * 0.15f)
    )
}

// Cool - Wearing sunglasses
private fun DrawScope.drawCoolFace(centerX: Float, centerY: Float, radius: Float) {
    val glassesColor = Color(0xFF2C2C2C)

    // Sunglasses frame
    val glassesPath = Path().apply {
        // Left lens
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = centerX - radius * 0.45f,
                top = centerY - radius * 0.35f,
                right = centerX - radius * 0.05f,
                bottom = centerY - radius * 0.05f
            )
        )
        // Right lens
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = centerX + radius * 0.05f,
                top = centerY - radius * 0.35f,
                right = centerX + radius * 0.45f,
                bottom = centerY - radius * 0.05f
            )
        )
    }
    drawPath(path = glassesPath, color = glassesColor, style = Fill)

    // Bridge
    drawLine(
        color = glassesColor,
        start = Offset(centerX - radius * 0.05f, centerY - radius * 0.2f),
        end = Offset(centerX + radius * 0.05f, centerY - radius * 0.2f),
        strokeWidth = 3f
    )

    // Slight smirk
    val smirkPath = Path().apply {
        moveTo(centerX - radius * 0.25f, centerY + radius * 0.25f)
        lineTo(centerX + radius * 0.15f, centerY + radius * 0.2f)
    }
    drawPath(
        path = smirkPath,
        color = Color(0xFF2C2C2C),
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

// Friendly - Warm smile with friendly eyes
private fun DrawScope.drawFriendlyFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Happy curved eyes
    val leftEyePath = Path().apply {
        moveTo(centerX - radius * 0.4f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX - radius * 0.25f, centerY - radius * 0.3f,
            centerX - radius * 0.1f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = leftEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    val rightEyePath = Path().apply {
        moveTo(centerX + radius * 0.1f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX + radius * 0.25f, centerY - radius * 0.3f,
            centerX + radius * 0.4f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = rightEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // Gentle smile
    val smilePath = Path().apply {
        moveTo(centerX - radius * 0.3f, centerY + radius * 0.15f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.35f,
            centerX + radius * 0.3f, centerY + radius * 0.15f
        )
    }
    drawPath(
        path = smilePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

// Shy - Blushing with closed eyes
private fun DrawScope.drawShyFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Closed eyes (small arcs)
    val leftEyePath = Path().apply {
        moveTo(centerX - radius * 0.4f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX - radius * 0.25f, centerY - radius * 0.15f,
            centerX - radius * 0.1f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = leftEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    val rightEyePath = Path().apply {
        moveTo(centerX + radius * 0.1f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX + radius * 0.25f, centerY - radius * 0.15f,
            centerX + radius * 0.4f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = rightEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // Blush (pink circles on cheeks)
    drawCircle(
        color = Color(0xFFFF9999),
        radius = radius * 0.15f,
        center = Offset(centerX - radius * 0.5f, centerY + radius * 0.1f),
        alpha = 0.6f
    )
    drawCircle(
        color = Color(0xFFFF9999),
        radius = radius * 0.15f,
        center = Offset(centerX + radius * 0.5f, centerY + radius * 0.1f),
        alpha = 0.6f
    )

    // Small smile
    val smilePath = Path().apply {
        moveTo(centerX - radius * 0.2f, centerY + radius * 0.2f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.3f,
            centerX + radius * 0.2f, centerY + radius * 0.2f
        )
    }
    drawPath(
        path = smilePath,
        color = eyeColor,
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

// Sleepy - Drowsy eyes
private fun DrawScope.drawSleepyFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Half-closed eyes
    drawLine(
        color = eyeColor,
        start = Offset(centerX - radius * 0.4f, centerY - radius * 0.2f),
        end = Offset(centerX - radius * 0.15f, centerY - radius * 0.2f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = eyeColor,
        start = Offset(centerX + radius * 0.15f, centerY - radius * 0.2f),
        end = Offset(centerX + radius * 0.4f, centerY - radius * 0.2f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Sleepy mouth (small open)
    drawOval(
        color = eyeColor,
        topLeft = Offset(centerX - radius * 0.1f, centerY + radius * 0.2f),
        size = Size(radius * 0.2f, radius * 0.15f)
    )

    // Z Z Z (sleep symbols)
    drawSleepZ(centerX + radius * 0.6f, centerY - radius * 0.5f, radius * 0.15f, eyeColor)
}

// Party - Party hat and confetti
private fun DrawScope.drawPartyFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Happy eyes
    drawCircle(
        color = eyeColor,
        radius = radius * 0.12f,
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.15f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.12f,
        center = Offset(centerX + radius * 0.3f, centerY - radius * 0.15f)
    )

    // Big smile
    val smilePath = Path().apply {
        moveTo(centerX - radius * 0.35f, centerY + radius * 0.1f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.45f,
            centerX + radius * 0.35f, centerY + radius * 0.1f
        )
    }
    drawPath(
        path = smilePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // Party hat
    val hatPath = Path().apply {
        moveTo(centerX - radius * 0.3f, centerY - radius * 0.7f)
        lineTo(centerX, centerY - radius * 1.2f)
        lineTo(centerX + radius * 0.3f, centerY - radius * 0.7f)
        close()
    }
    drawPath(path = hatPath, color = Color(0xFFFF6B9D))
    drawPath(
        path = hatPath,
        color = Color(0xFF2C2C2C),
        style = Stroke(width = 2f)
    )

    // Pom-pom on hat
    drawCircle(
        color = Color(0xFFFFC107),
        radius = radius * 0.1f,
        center = Offset(centerX, centerY - radius * 1.2f)
    )
}

// Thoughtful - Hand on chin thinking
private fun DrawScope.drawThoughtfulFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Eyes looking up
    drawCircle(
        color = eyeColor,
        radius = radius * 0.1f,
        center = Offset(centerX - radius * 0.25f, centerY - radius * 0.25f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.1f,
        center = Offset(centerX + radius * 0.25f, centerY - radius * 0.25f)
    )

    // Neutral mouth
    drawLine(
        color = eyeColor,
        start = Offset(centerX - radius * 0.2f, centerY + radius * 0.25f),
        end = Offset(centerX + radius * 0.2f, centerY + radius * 0.25f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Thought bubble
    drawCircle(
        color = Color.White,
        radius = radius * 0.3f,
        center = Offset(centerX + radius * 0.8f, centerY - radius * 0.6f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.3f,
        center = Offset(centerX + radius * 0.8f, centerY - radius * 0.6f),
        style = Stroke(width = 2f)
    )
    drawCircle(
        color = Color.White,
        radius = radius * 0.1f,
        center = Offset(centerX + radius * 0.5f, centerY - radius * 0.3f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.1f,
        center = Offset(centerX + radius * 0.5f, centerY - radius * 0.3f),
        style = Stroke(width = 2f)
    )
}

// Peaceful - Content closed eyes smile
private fun DrawScope.drawPeacefulFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Peaceful closed eyes (arcs)
    val leftEyePath = Path().apply {
        moveTo(centerX - radius * 0.4f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX - radius * 0.25f, centerY - radius * 0.3f,
            centerX - radius * 0.1f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = leftEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    val rightEyePath = Path().apply {
        moveTo(centerX + radius * 0.1f, centerY - radius * 0.2f)
        quadraticBezierTo(
            centerX + radius * 0.25f, centerY - radius * 0.3f,
            centerX + radius * 0.4f, centerY - radius * 0.2f
        )
    }
    drawPath(
        path = rightEyePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // Content smile
    val smilePath = Path().apply {
        moveTo(centerX - radius * 0.3f, centerY + radius * 0.2f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.35f,
            centerX + radius * 0.3f, centerY + radius * 0.2f
        )
    }
    drawPath(
        path = smilePath,
        color = eyeColor,
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

// Cheerful - Wide eyes and big grin
private fun DrawScope.drawCheerfulFace(centerX: Float, centerY: Float, radius: Float) {
    val eyeColor = Color(0xFF2C2C2C)

    // Wide open eyes
    drawCircle(
        color = Color.White,
        radius = radius * 0.18f,
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.2f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.18f,
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.2f),
        style = Stroke(width = 3f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.1f,
        center = Offset(centerX - radius * 0.3f, centerY - radius * 0.2f)
    )

    drawCircle(
        color = Color.White,
        radius = radius * 0.18f,
        center = Offset(centerX + radius * 0.3f, centerY - radius * 0.2f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.18f,
        center = Offset(centerX + radius * 0.3f, centerY - radius * 0.2f),
        style = Stroke(width = 3f)
    )
    drawCircle(
        color = eyeColor,
        radius = radius * 0.1f,
        center = Offset(centerX + radius * 0.3f, centerY - radius * 0.2f)
    )

    // Big grin
    val grinPath = Path().apply {
        moveTo(centerX - radius * 0.4f, centerY + radius * 0.15f)
        quadraticBezierTo(
            centerX, centerY + radius * 0.5f,
            centerX + radius * 0.4f, centerY + radius * 0.15f
        )
    }
    drawPath(
        path = grinPath,
        color = eyeColor,
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
}

// Helper: Draw a star shape
private fun DrawScope.drawStar(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y - size)
        lineTo(x + size * 0.3f, y - size * 0.3f)
        lineTo(x + size, y)
        lineTo(x + size * 0.3f, y + size * 0.3f)
        lineTo(x, y + size)
        lineTo(x - size * 0.3f, y + size * 0.3f)
        lineTo(x - size, y)
        lineTo(x - size * 0.3f, y - size * 0.3f)
        close()
    }
    drawPath(path, color)
}

// Helper: Draw sleep Z symbol
private fun DrawScope.drawSleepZ(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + size, y)
        lineTo(x, y + size)
        lineTo(x + size, y + size)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )
}
