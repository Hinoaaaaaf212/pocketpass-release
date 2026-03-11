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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Nintendo-style hobbies icon — artist palette with paint dabs and a brush.
 * Matches the hand-drawn aesthetic of the MoodIcon family.
 */
@Composable
fun HobbiesIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outlineColor = Color(0xFF5F4B3C)
        val strokeW = w * 0.06f

        // ── Palette body ──
        // Organic rounded shape (slightly egg-shaped, wider at bottom-left)
        val palette = Path().apply {
            moveTo(cx + w * 0.05f, cy - h * 0.38f)
            // top-right curve
            cubicTo(
                cx + w * 0.32f, cy - h * 0.38f,
                cx + w * 0.42f, cy - h * 0.18f,
                cx + w * 0.38f, cy + h * 0.02f
            )
            // right side dip (thumb hole area)
            cubicTo(
                cx + w * 0.35f, cy + h * 0.14f,
                cx + w * 0.20f, cy + h * 0.16f,
                cx + w * 0.18f, cy + h * 0.08f
            )
            // inner thumb hole curve
            cubicTo(
                cx + w * 0.16f, cy + h * 0.00f,
                cx + w * 0.24f, cy - h * 0.05f,
                cx + w * 0.28f, cy + h * 0.04f
            )
            // continue bottom
            cubicTo(
                cx + w * 0.32f, cy + h * 0.12f,
                cx + w * 0.28f, cy + h * 0.30f,
                cx + w * 0.05f, cy + h * 0.38f
            )
            // bottom-left curve
            cubicTo(
                cx - w * 0.20f, cy + h * 0.42f,
                cx - w * 0.42f, cy + h * 0.30f,
                cx - w * 0.42f, cy + h * 0.05f
            )
            // left side back to top
            cubicTo(
                cx - w * 0.42f, cy - h * 0.20f,
                cx - w * 0.28f, cy - h * 0.38f,
                cx + w * 0.05f, cy - h * 0.38f
            )
            close()
        }

        // Fill
        drawPath(palette, color = Color(0xFFF5ECD7), style = Fill)
        // Outline
        drawPath(palette, color = outlineColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // ── Paint dabs ──
        val dabRadius = w * 0.065f
        // Red dab (top-left area)
        drawCircle(color = Color(0xFFE53935), radius = dabRadius, center = Offset(cx - w * 0.18f, cy - h * 0.18f))
        drawCircle(color = outlineColor, radius = dabRadius, center = Offset(cx - w * 0.18f, cy - h * 0.18f), style = Stroke(width = strokeW * 0.5f))

        // Blue dab
        drawCircle(color = Color(0xFF1E88E5), radius = dabRadius, center = Offset(cx + w * 0.05f, cy - h * 0.22f))
        drawCircle(color = outlineColor, radius = dabRadius, center = Offset(cx + w * 0.05f, cy - h * 0.22f), style = Stroke(width = strokeW * 0.5f))

        // Green dab
        drawCircle(color = Color(0xFF43A047), radius = dabRadius, center = Offset(cx - w * 0.28f, cy + h * 0.02f))
        drawCircle(color = outlineColor, radius = dabRadius, center = Offset(cx - w * 0.28f, cy + h * 0.02f), style = Stroke(width = strokeW * 0.5f))

        // Yellow dab
        drawCircle(color = Color(0xFFFDD835), radius = dabRadius, center = Offset(cx - w * 0.10f, cy + h * 0.18f))
        drawCircle(color = outlineColor, radius = dabRadius, center = Offset(cx - w * 0.10f, cy + h * 0.18f), style = Stroke(width = strokeW * 0.5f))

        // ── Paintbrush ──
        // Handle (diagonal, bottom-right to upper-right)
        val handleStart = Offset(cx + w * 0.42f, cy + h * 0.42f)
        val handleEnd = Offset(cx + w * 0.22f, cy - h * 0.08f)
        drawLine(
            color = Color(0xFF8D6E53),  // Brown wood handle
            start = handleStart,
            end = handleEnd,
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
        // Handle outline
        drawLine(
            color = outlineColor,
            start = handleStart,
            end = handleEnd,
            strokeWidth = w * 0.07f + strokeW,
            cap = StrokeCap.Round
        )
        // Redraw handle over outline
        drawLine(
            color = Color(0xFF8D6E53),
            start = handleStart,
            end = handleEnd,
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )

        // Ferrule (metal band)
        val ferruleCenter = Offset(
            handleEnd.x + (handleStart.x - handleEnd.x) * 0.08f,
            handleEnd.y + (handleStart.y - handleEnd.y) * 0.08f
        )
        drawCircle(color = Color(0xFFBDBDBD), radius = w * 0.05f, center = ferruleCenter)
        drawCircle(color = outlineColor, radius = w * 0.05f, center = ferruleCenter, style = Stroke(width = strokeW * 0.5f))

        // Bristle tip
        val bristleTip = Offset(
            handleEnd.x - (handleStart.x - handleEnd.x) * 0.06f,
            handleEnd.y - (handleStart.y - handleEnd.y) * 0.06f
        )
        drawCircle(color = Color(0xFFE53935), radius = w * 0.055f, center = bristleTip)
        drawCircle(color = outlineColor, radius = w * 0.055f, center = bristleTip, style = Stroke(width = strokeW * 0.5f))
    }
}
