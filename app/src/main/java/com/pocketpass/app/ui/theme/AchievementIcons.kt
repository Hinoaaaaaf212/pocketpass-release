package com.pocketpass.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class AchievementIcon {
    FIRST_STEPS,
    SOCIAL_BUTTERFLY,
    POPULAR,
    CELEBRITY,
    LEGEND,
    TOURIST,
    EXPLORER,
    WORLD_TRAVELER,
    GLOBE_TROTTER,
    REUNION,
    BEST_FRIENDS,
    INSEPARABLE,
    COLLECTOR,
    ENTHUSIAST,
    MASTER,
    LOCKED
}

@Composable
fun AchievementIconView(
    icon: AchievementIcon,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val iconSize = size.minDimension
        val centerX = size.width / 2
        val centerY = size.height / 2

        when (icon) {
            AchievementIcon.FIRST_STEPS -> drawFirstSteps(centerX, centerY, iconSize, tint)
            AchievementIcon.SOCIAL_BUTTERFLY -> drawSocialButterfly(centerX, centerY, iconSize, tint)
            AchievementIcon.POPULAR -> drawPopular(centerX, centerY, iconSize, tint)
            AchievementIcon.CELEBRITY -> drawCelebrity(centerX, centerY, iconSize, tint)
            AchievementIcon.LEGEND -> drawLegend(centerX, centerY, iconSize, tint)
            AchievementIcon.TOURIST -> drawTourist(centerX, centerY, iconSize, tint)
            AchievementIcon.EXPLORER -> drawExplorer(centerX, centerY, iconSize, tint)
            AchievementIcon.WORLD_TRAVELER -> drawWorldTraveler(centerX, centerY, iconSize, tint)
            AchievementIcon.GLOBE_TROTTER -> drawGlobeTrotter(centerX, centerY, iconSize, tint)
            AchievementIcon.REUNION -> drawReunion(centerX, centerY, iconSize, tint)
            AchievementIcon.BEST_FRIENDS -> drawBestFriends(centerX, centerY, iconSize, tint)
            AchievementIcon.INSEPARABLE -> drawInseparable(centerX, centerY, iconSize, tint)
            AchievementIcon.COLLECTOR -> drawCollector(centerX, centerY, iconSize, tint)
            AchievementIcon.ENTHUSIAST -> drawEnthusiast(centerX, centerY, iconSize, tint)
            AchievementIcon.MASTER -> drawMaster(centerX, centerY, iconSize, tint)
            AchievementIcon.LOCKED -> drawLocked(centerX, centerY, iconSize, tint)
        }
    }
}

// First Steps - Footprint
private fun DrawScope.drawFirstSteps(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.4f

    // Heel
    drawCircle(
        color = tint,
        radius = scale * 0.4f,
        center = Offset(centerX, centerY + scale * 0.3f)
    )

    // Toes
    repeat(3) { i ->
        val offsetX = (i - 1) * scale * 0.35f
        drawCircle(
            color = tint,
            radius = scale * 0.25f,
            center = Offset(centerX + offsetX, centerY - scale * 0.4f)
        )
    }
}

// Social Butterfly
private fun DrawScope.drawSocialButterfly(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.3f

    // Body
    drawLine(
        color = tint,
        start = Offset(centerX, centerY - scale),
        end = Offset(centerX, centerY + scale),
        strokeWidth = scale * 0.2f,
        cap = StrokeCap.Round
    )

    // Wings (left)
    val leftWing = Path().apply {
        moveTo(centerX, centerY - scale * 0.5f)
        cubicTo(
            centerX - scale * 1.2f, centerY - scale * 0.8f,
            centerX - scale * 1.2f, centerY + scale * 0.2f,
            centerX, centerY + scale * 0.5f
        )
    }
    drawPath(leftWing, color = tint, style = Stroke(width = scale * 0.15f))

    // Wings (right)
    val rightWing = Path().apply {
        moveTo(centerX, centerY - scale * 0.5f)
        cubicTo(
            centerX + scale * 1.2f, centerY - scale * 0.8f,
            centerX + scale * 1.2f, centerY + scale * 0.2f,
            centerX, centerY + scale * 0.5f
        )
    }
    drawPath(rightWing, color = tint, style = Stroke(width = scale * 0.15f))
}

// Popular - Star
private fun DrawScope.drawPopular(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.4f
    val points = 5
    val outerRadius = scale
    val innerRadius = scale * 0.4f

    val path = Path().apply {
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = Math.PI * i / points - Math.PI / 2
            val x = centerX + (radius * Math.cos(angle)).toFloat()
            val y = centerY + (radius * Math.sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    drawPath(path, color = tint)
}

// Celebrity - Shining Star
private fun DrawScope.drawCelebrity(centerX: Float, centerY: Float, size: Float, tint: Color) {
    // Draw star
    drawPopular(centerX, centerY, size * 0.7f, tint)

    // Draw sparkles
    val scale = size * 0.15f
    drawLine(
        color = tint,
        start = Offset(centerX - size * 0.5f, centerY - size * 0.3f),
        end = Offset(centerX - size * 0.35f, centerY - size * 0.2f),
        strokeWidth = scale * 0.3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(centerX + size * 0.5f, centerY + size * 0.2f),
        end = Offset(centerX + size * 0.35f, centerY + size * 0.1f),
        strokeWidth = scale * 0.3f,
        cap = StrokeCap.Round
    )
}

// Legend - Crown
private fun DrawScope.drawLegend(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    val crown = Path().apply {
        moveTo(centerX - scale * 1.2f, centerY + scale * 0.8f)
        lineTo(centerX - scale * 1.2f, centerY - scale * 0.2f)
        lineTo(centerX - scale * 0.6f, centerY + scale * 0.3f)
        lineTo(centerX, centerY - scale * 0.8f)
        lineTo(centerX + scale * 0.6f, centerY + scale * 0.3f)
        lineTo(centerX + scale * 1.2f, centerY - scale * 0.2f)
        lineTo(centerX + scale * 1.2f, centerY + scale * 0.8f)
        close()
    }

    drawPath(crown, color = tint)

    // Jewels
    repeat(3) { i ->
        val x = centerX + (i - 1) * scale * 0.6f
        drawCircle(
            color = Color(0xFF2C2C2C),
            radius = scale * 0.2f,
            center = Offset(x, centerY + scale * 0.3f)
        )
    }
}

// Tourist - Map with flag
private fun DrawScope.drawTourist(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    // Folded map
    drawLine(
        color = tint,
        start = Offset(centerX - scale, centerY - scale * 0.5f),
        end = Offset(centerX - scale, centerY + scale),
        strokeWidth = scale * 0.15f
    )
    drawLine(
        color = tint,
        start = Offset(centerX, centerY - scale * 0.7f),
        end = Offset(centerX, centerY + scale * 0.8f),
        strokeWidth = scale * 0.15f
    )
    drawLine(
        color = tint,
        start = Offset(centerX + scale, centerY - scale * 0.5f),
        end = Offset(centerX + scale, centerY + scale),
        strokeWidth = scale * 0.15f
    )

    // Connecting lines
    drawLine(
        color = tint,
        start = Offset(centerX - scale * 1.1f, centerY),
        end = Offset(centerX + scale * 1.1f, centerY),
        strokeWidth = scale * 0.1f
    )
}

// Explorer - Compass
private fun DrawScope.drawExplorer(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.4f

    // Outer circle
    drawCircle(
        color = tint,
        radius = scale,
        center = Offset(centerX, centerY),
        style = Stroke(width = scale * 0.15f)
    )

    // Compass needle (pointing up)
    val needle = Path().apply {
        moveTo(centerX, centerY - scale * 0.7f)
        lineTo(centerX - scale * 0.2f, centerY + scale * 0.3f)
        lineTo(centerX, centerY + scale * 0.1f)
        lineTo(centerX + scale * 0.2f, centerY + scale * 0.3f)
        close()
    }
    drawPath(needle, color = tint)

    // Cardinal points
    drawCircle(
        color = tint,
        radius = scale * 0.1f,
        center = Offset(centerX, centerY - scale * 1.1f)
    )
}

// World Traveler - Globe
private fun DrawScope.drawWorldTraveler(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.4f

    // Circle
    drawCircle(
        color = tint,
        radius = scale,
        center = Offset(centerX, centerY),
        style = Stroke(width = scale * 0.15f)
    )

    // Latitude lines
    drawLine(
        color = tint,
        start = Offset(centerX - scale * 0.9f, centerY),
        end = Offset(centerX + scale * 0.9f, centerY),
        strokeWidth = scale * 0.1f
    )

    // Longitude line (curved)
    val longitude = Path().apply {
        moveTo(centerX, centerY - scale)
        cubicTo(
            centerX + scale * 0.5f, centerY - scale * 0.5f,
            centerX + scale * 0.5f, centerY + scale * 0.5f,
            centerX, centerY + scale
        )
    }
    drawPath(longitude, color = tint, style = Stroke(width = scale * 0.1f))
}

// Globe Trotter - Airplane
private fun DrawScope.drawGlobeTrotter(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    val plane = Path().apply {
        // Body
        moveTo(centerX - scale * 1.2f, centerY)
        lineTo(centerX + scale * 1.2f, centerY)

        // Wings
        moveTo(centerX - scale * 0.3f, centerY)
        lineTo(centerX - scale * 0.8f, centerY - scale * 0.7f)

        moveTo(centerX + scale * 0.3f, centerY)
        lineTo(centerX + scale * 0.8f, centerY - scale * 0.7f)

        // Tail
        moveTo(centerX - scale * 1.1f, centerY)
        lineTo(centerX - scale * 1.2f, centerY + scale * 0.5f)
    }

    drawPath(plane, color = tint, style = Stroke(width = scale * 0.2f, cap = StrokeCap.Round))
}

// Reunion - Handshake
private fun DrawScope.drawReunion(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.3f

    // Left hand
    drawLine(
        color = tint,
        start = Offset(centerX - scale * 1.2f, centerY + scale * 0.5f),
        end = Offset(centerX - scale * 0.3f, centerY),
        strokeWidth = scale * 0.3f,
        cap = StrokeCap.Round
    )

    // Right hand
    drawLine(
        color = tint,
        start = Offset(centerX + scale * 1.2f, centerY - scale * 0.5f),
        end = Offset(centerX + scale * 0.3f, centerY),
        strokeWidth = scale * 0.3f,
        cap = StrokeCap.Round
    )

    //握hands
    drawCircle(
        color = tint,
        radius = scale * 0.4f,
        center = Offset(centerX, centerY)
    )
}

// Best Friends - Heart
private fun DrawScope.drawBestFriends(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    val heart = Path().apply {
        moveTo(centerX, centerY + scale * 1.2f)
        cubicTo(
            centerX - scale * 1.3f, centerY + scale * 0.2f,
            centerX - scale * 1.3f, centerY - scale * 0.8f,
            centerX, centerY - scale * 0.3f
        )
        cubicTo(
            centerX + scale * 1.3f, centerY - scale * 0.8f,
            centerX + scale * 1.3f, centerY + scale * 0.2f,
            centerX, centerY + scale * 1.2f
        )
    }

    drawPath(heart, color = tint)
}

// Inseparable - Double Hearts
private fun DrawScope.drawInseparable(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.25f

    // Left heart
    drawBestFriends(centerX - scale * 0.8f, centerY, size * 0.6f, tint)

    // Right heart
    drawBestFriends(centerX + scale * 0.8f, centerY, size * 0.6f, tint)
}

// Collector - Book
private fun DrawScope.drawCollector(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    // Book cover
    drawRect(
        color = tint,
        topLeft = Offset(centerX - scale, centerY - scale * 1.2f),
        size = androidx.compose.ui.geometry.Size(scale * 2f, scale * 2.4f)
    )

    // Pages
    drawLine(
        color = Color(0xFF2C2C2C),
        start = Offset(centerX - scale * 0.5f, centerY - scale),
        end = Offset(centerX - scale * 0.5f, centerY + scale),
        strokeWidth = scale * 0.1f
    )
    drawLine(
        color = Color(0xFF2C2C2C),
        start = Offset(centerX + scale * 0.5f, centerY - scale),
        end = Offset(centerX + scale * 0.5f, centerY + scale),
        strokeWidth = scale * 0.1f
    )
}

// Enthusiast - Target
private fun DrawScope.drawEnthusiast(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.4f

    // Outer circle
    drawCircle(
        color = tint,
        radius = scale,
        center = Offset(centerX, centerY),
        style = Stroke(width = scale * 0.15f)
    )

    // Middle circle
    drawCircle(
        color = tint,
        radius = scale * 0.6f,
        center = Offset(centerX, centerY),
        style = Stroke(width = scale * 0.15f)
    )

    // Center dot
    drawCircle(
        color = tint,
        radius = scale * 0.25f,
        center = Offset(centerX, centerY)
    )
}

// Master - Trophy
private fun DrawScope.drawMaster(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    val trophy = Path().apply {
        // Cup
        moveTo(centerX - scale * 0.8f, centerY - scale * 0.8f)
        lineTo(centerX - scale * 0.6f, centerY + scale * 0.3f)
        lineTo(centerX + scale * 0.6f, centerY + scale * 0.3f)
        lineTo(centerX + scale * 0.8f, centerY - scale * 0.8f)
        close()

        // Handles
        moveTo(centerX - scale * 0.8f, centerY - scale * 0.5f)
        lineTo(centerX - scale * 1.2f, centerY - scale * 0.3f)
        lineTo(centerX - scale * 1.1f, centerY + scale * 0.1f)
        lineTo(centerX - scale * 0.7f, centerY)

        moveTo(centerX + scale * 0.8f, centerY - scale * 0.5f)
        lineTo(centerX + scale * 1.2f, centerY - scale * 0.3f)
        lineTo(centerX + scale * 1.1f, centerY + scale * 0.1f)
        lineTo(centerX + scale * 0.7f, centerY)
    }

    drawPath(trophy, color = tint)

    // Base
    drawRect(
        color = tint,
        topLeft = Offset(centerX - scale * 0.8f, centerY + scale * 0.3f),
        size = androidx.compose.ui.geometry.Size(scale * 1.6f, scale * 0.3f)
    )

    drawRect(
        color = tint,
        topLeft = Offset(centerX - scale, centerY + scale * 0.6f),
        size = androidx.compose.ui.geometry.Size(scale * 2f, scale * 0.3f)
    )
}

// Locked - Lock
private fun DrawScope.drawLocked(centerX: Float, centerY: Float, size: Float, tint: Color) {
    val scale = size * 0.35f

    // Shackle
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - scale * 0.6f, centerY - scale * 1.2f),
        size = androidx.compose.ui.geometry.Size(scale * 1.2f, scale * 1.2f),
        style = Stroke(width = scale * 0.2f)
    )

    // Body
    drawRect(
        color = tint,
        topLeft = Offset(centerX - scale, centerY - scale * 0.2f),
        size = androidx.compose.ui.geometry.Size(scale * 2f, scale * 1.4f)
    )

    // Keyhole
    drawCircle(
        color = Color(0xFF2C2C2C),
        radius = scale * 0.25f,
        center = Offset(centerX, centerY + scale * 0.2f)
    )
    drawRect(
        color = Color(0xFF2C2C2C),
        topLeft = Offset(centerX - scale * 0.15f, centerY + scale * 0.2f),
        size = androidx.compose.ui.geometry.Size(scale * 0.3f, scale * 0.5f)
    )
}
