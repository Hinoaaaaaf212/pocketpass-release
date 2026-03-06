package com.pocketpass.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketpass.app.data.PuzzleTheme

// ── Color Palettes ──

private object ParkPalette {
    val sky = Color(0xFF87CEEB)
    val cloudWhite = Color(0xFFFFFFFF)
    val treeTrunk = Color(0xFF8B4513)
    val treeGreen = Color(0xFF228B22)
    val treeGreenLight = Color(0xFF32CD32)
    val grass = Color(0xFF7CFC00)
    val grassDark = Color(0xFF4CAF50)
    val pond = Color(0xFF4FC3F7)
    val pondDark = Color(0xFF0288D1)
    val bench = Color(0xFFA0522D)
    val benchLight = Color(0xFFCD853F)
    val sandDark = Color(0xFFD4A017)
    val flowerRed = Color(0xFFFF4444)
    val flowerYellow = Color(0xFFFFEB3B)
}

private object BeachPalette {
    val oceanDeep = Color(0xFF0077B6)
    val oceanMid = Color(0xFF00B4D8)
    val oceanLight = Color(0xFF90E0EF)
    val foam = Color(0xFFFFFFFF)
    val sand = Color(0xFFF4D35E)
    val sandDark = Color(0xFFD4A017)
    val umbrellaPole = Color(0xFF8B4513)
    val umbrellaRed = Color(0xFFFF4444)
    val umbrellaWhite = Color(0xFFFFFFFF)
    val castleSand = Color(0xFFDEB887)
    val castleFlag = Color(0xFFFF6B6B)
    val sky = Color(0xFF87CEEB)
    val sun = Color(0xFFFFD700)
    val shell = Color(0xFFFFB6C1)
}

private object SpacePalette {
    val darkSpace = Color(0xFF0D1B2A)
    val midSpace = Color(0xFF1B2838)
    val starWhite = Color(0xFFFFFFFF)
    val starYellow = Color(0xFFFFD700)
    val planetRed = Color(0xFFFF6B6B)
    val planetRedDark = Color(0xFFCC3333)
    val moonGray = Color(0xFFC0C0C0)
    val moonCrater = Color(0xFF999999)
    val rocketWhite = Color(0xFFE8E8E8)
    val rocketRed = Color(0xFFFF4444)
    val rocketBlue = Color(0xFF4FC3F7)
    val flame = Color(0xFFFF9800)
    val flameYellow = Color(0xFFFFEB3B)
}

private object CastlePalette {
    val sky = Color(0xFFB39DDB)
    val skyLight = Color(0xFFCE93D8)
    val stoneGray = Color(0xFF9E9E9E)
    val stoneDark = Color(0xFF757575)
    val stoneLight = Color(0xFFBDBDBD)
    val flagRed = Color(0xFFFF4444)
    val flagPole = Color(0xFF8B4513)
    val windowDark = Color(0xFF37474F)
    val doorBrown = Color(0xFF5D4037)
    val doorLight = Color(0xFF8D6E63)
    val grassGreen = Color(0xFF4CAF50)
    val grassLight = Color(0xFF66BB6A)
    val gold = Color(0xFFFFD700)
}

/**
 * Draws pixel art for a single puzzle piece cell.
 *
 * @param theme Which puzzle panel theme to draw
 * @param row Grid row (0-indexed from top)
 * @param col Grid column (0-indexed from left)
 * @param x Left edge of the cell in pixels
 * @param y Top edge of the cell in pixels
 * @param cellSize Width/height of the cell in pixels
 */
fun DrawScope.drawPuzzlePiece(theme: PuzzleTheme, row: Int, col: Int, x: Float, y: Float, cellSize: Float) {
    when (theme) {
        PuzzleTheme.PARK -> drawParkPiece(row, col, x, y, cellSize)
        PuzzleTheme.BEACH -> drawBeachPiece(row, col, x, y, cellSize)
        PuzzleTheme.SPACE -> drawSpacePiece(row, col, x, y, cellSize)
        PuzzleTheme.CASTLE -> drawCastlePiece(row, col, x, y, cellSize)
    }
}

// ══════════════════════════════════════════════════════════════
// PARK THEME (3x3)
// [0,0: Sky+Cloud] [0,1: Sky       ] [0,2: Sky+Cloud]
// [1,0: Tree      ] [1,1: Grass+Path] [1,2: Flowers  ]
// [2,0: Pond      ] [2,1: Bench     ] [2,2: Grass    ]
// ══════════════════════════════════════════════════════════════

private fun DrawScope.drawParkPiece(row: Int, col: Int, x: Float, y: Float, s: Float) {
    val p = s / 8f // pixel unit
    // Background sky or grass
    val bg = if (row == 0) ParkPalette.sky else ParkPalette.grass
    drawRect(bg, Offset(x, y), Size(s, s))

    when {
        // Row 0: Sky
        row == 0 && col == 0 -> {
            // Cloud left
            drawCircle(ParkPalette.cloudWhite, p * 1.5f, Offset(x + p * 3, y + p * 3))
            drawCircle(ParkPalette.cloudWhite, p * 1.2f, Offset(x + p * 5, y + p * 2.5f))
            drawCircle(ParkPalette.cloudWhite, p * 1f, Offset(x + p * 6.5f, y + p * 3))
        }
        row == 0 && col == 1 -> {
            // Sun
            drawCircle(Color(0xFFFFD700), p * 1.5f, Offset(x + p * 4, y + p * 4))
            // Rays
            for (i in 0 until 8) {
                val angle = i * 45f * (Math.PI / 180f).toFloat()
                val startR = p * 2f
                val endR = p * 2.8f
                drawLine(
                    Color(0xFFFFD700),
                    Offset(x + p * 4 + kotlin.math.cos(angle) * startR, y + p * 4 + kotlin.math.sin(angle) * startR),
                    Offset(x + p * 4 + kotlin.math.cos(angle) * endR, y + p * 4 + kotlin.math.sin(angle) * endR),
                    strokeWidth = p * 0.4f
                )
            }
        }
        row == 0 && col == 2 -> {
            // Cloud right
            drawCircle(ParkPalette.cloudWhite, p * 1.3f, Offset(x + p * 2, y + p * 5))
            drawCircle(ParkPalette.cloudWhite, p * 1.5f, Offset(x + p * 4, y + p * 4.5f))
            drawCircle(ParkPalette.cloudWhite, p * 1.1f, Offset(x + p * 5.5f, y + p * 5))
        }
        // Row 1: Ground details
        row == 1 && col == 0 -> {
            // Tree
            drawRect(ParkPalette.treeTrunk, Offset(x + p * 3.5f, y + p * 4), Size(p * 1, p * 4))
            drawCircle(ParkPalette.treeGreen, p * 2.5f, Offset(x + p * 4, y + p * 3))
            drawCircle(ParkPalette.treeGreenLight, p * 1.5f, Offset(x + p * 3.5f, y + p * 2))
        }
        row == 1 && col == 1 -> {
            // Path
            drawRect(ParkPalette.sandDark, Offset(x + p * 2.5f, y), Size(p * 3, s))
            drawRect(Color(0xFFDEB887), Offset(x + p * 3, y), Size(p * 2, s))
        }
        row == 1 && col == 2 -> {
            // Flowers
            val flowerPositions = listOf(Pair(2f, 2f), Pair(5f, 3f), Pair(3f, 6f), Pair(6f, 6f))
            flowerPositions.forEach { (fx, fy) ->
                drawCircle(ParkPalette.flowerRed, p * 0.6f, Offset(x + p * fx, y + p * fy))
                drawCircle(ParkPalette.flowerYellow, p * 0.3f, Offset(x + p * fx, y + p * fy))
            }
        }
        // Row 2: Bottom
        row == 2 && col == 0 -> {
            // Pond
            drawOval(ParkPalette.pond, Offset(x + p * 1, y + p * 2), Size(p * 6, p * 4))
            drawOval(ParkPalette.pondDark, Offset(x + p * 2, y + p * 3), Size(p * 4, p * 2))
        }
        row == 2 && col == 1 -> {
            // Bench
            drawRect(ParkPalette.bench, Offset(x + p * 1, y + p * 3), Size(p * 6, p * 0.8f))
            drawRect(ParkPalette.bench, Offset(x + p * 1, y + p * 5), Size(p * 6, p * 0.8f))
            // Legs
            drawRect(ParkPalette.benchLight, Offset(x + p * 1.5f, y + p * 5.8f), Size(p * 0.5f, p * 1.5f))
            drawRect(ParkPalette.benchLight, Offset(x + p * 6f, y + p * 5.8f), Size(p * 0.5f, p * 1.5f))
            // Back
            drawRect(ParkPalette.bench, Offset(x + p * 1, y + p * 1.5f), Size(p * 6, p * 0.6f))
        }
        row == 2 && col == 2 -> {
            // Grass with small bushes
            drawCircle(ParkPalette.grassDark, p * 1.5f, Offset(x + p * 3, y + p * 5))
            drawCircle(ParkPalette.treeGreen, p * 1.2f, Offset(x + p * 6, y + p * 3))
            drawCircle(ParkPalette.grassDark, p * 1f, Offset(x + p * 2, y + p * 2))
        }
    }
}

// ══════════════════════════════════════════════════════════════
// BEACH THEME (4x4)
// [0,0: Sky+Sun ] [0,1: Sky     ] [0,2: Sky     ] [0,3: Sky+Cloud]
// [1,0: Ocean   ] [1,1: Wave+Umb] [1,2: Wave+Umb] [1,3: Ocean    ]
// [2,0: Sand    ] [2,1: Umbrella] [2,2: Towel   ] [2,3: Sand+Shell]
// [3,0: Sandcast] [3,1: Sand    ] [3,2: Sand    ] [3,3: Shells   ]
// ══════════════════════════════════════════════════════════════

private fun DrawScope.drawBeachPiece(row: Int, col: Int, x: Float, y: Float, s: Float) {
    val p = s / 8f
    when (row) {
        0 -> {
            drawRect(BeachPalette.sky, Offset(x, y), Size(s, s))
            when (col) {
                0 -> {
                    // Sun
                    drawCircle(BeachPalette.sun, p * 2f, Offset(x + p * 3, y + p * 3))
                }
                3 -> {
                    // Cloud
                    drawCircle(BeachPalette.foam, p * 1.5f, Offset(x + p * 3, y + p * 4))
                    drawCircle(BeachPalette.foam, p * 1.8f, Offset(x + p * 5, y + p * 3.5f))
                    drawCircle(BeachPalette.foam, p * 1.3f, Offset(x + p * 6.5f, y + p * 4))
                }
                else -> {
                    // Birds
                    val path = Path().apply {
                        moveTo(x + p * 2, y + p * 3)
                        quadraticBezierTo(x + p * 3, y + p * 2, x + p * 4, y + p * 3)
                    }
                    drawPath(path, Color(0xFF333333), style = androidx.compose.ui.graphics.drawscope.Stroke(p * 0.3f))
                }
            }
        }
        1 -> {
            drawRect(BeachPalette.oceanMid, Offset(x, y), Size(s, s))
            // Wave foam line
            drawRect(BeachPalette.foam, Offset(x, y + p * 6), Size(s, p * 0.5f))
            drawRect(BeachPalette.oceanLight, Offset(x, y + p * 6.5f), Size(s, p * 1.5f))
            when (col) {
                1 -> {
                    // Umbrella pole top
                    drawRect(BeachPalette.umbrellaPole, Offset(x + p * 4, y), Size(p * 0.4f, s))
                    // Umbrella top half
                    val path = Path().apply {
                        moveTo(x + p * 0.5f, y + p * 4)
                        lineTo(x + p * 4.2f, y + p * 0.5f)
                        lineTo(x + p * 7.5f, y + p * 4)
                        close()
                    }
                    drawPath(path, BeachPalette.umbrellaRed)
                    // Stripes
                    drawRect(BeachPalette.umbrellaWhite, Offset(x + p * 2.5f, y + p * 1.5f), Size(p * 1, p * 2.5f))
                    drawRect(BeachPalette.umbrellaWhite, Offset(x + p * 5, y + p * 1.5f), Size(p * 1, p * 2.5f))
                }
                2 -> {
                    // Surfboard floating
                    drawOval(BeachPalette.umbrellaRed, Offset(x + p * 2, y + p * 1), Size(p * 4, p * 1.5f))
                    drawLine(Color(0xFFFFFFFF), Offset(x + p * 3, y + p * 1.3f), Offset(x + p * 5, y + p * 2f), p * 0.3f)
                }
                else -> {
                    // Waves
                    drawOval(BeachPalette.oceanLight, Offset(x + p * 1, y + p * 2), Size(p * 3, p * 1))
                    drawOval(BeachPalette.oceanLight, Offset(x + p * 4, y + p * 4), Size(p * 3, p * 1))
                }
            }
        }
        2 -> {
            drawRect(BeachPalette.sand, Offset(x, y), Size(s, s))
            when (col) {
                1 -> {
                    // Umbrella pole base + towel
                    drawRect(BeachPalette.umbrellaPole, Offset(x + p * 4, y), Size(p * 0.4f, p * 3))
                    drawRect(Color(0xFF4FC3F7), Offset(x + p * 1, y + p * 4), Size(p * 6, p * 3))
                    drawRect(Color(0xFFFFFFFF), Offset(x + p * 1, y + p * 5.5f), Size(p * 6, p * 0.3f))
                }
                2 -> {
                    // Beach ball
                    drawCircle(BeachPalette.umbrellaRed, p * 1.5f, Offset(x + p * 4, y + p * 4))
                    drawCircle(Color(0xFF4FC3F7), p * 1.5f * 0.7f, Offset(x + p * 4.5f, y + p * 3.5f))
                    drawCircle(BeachPalette.umbrellaWhite, p * 0.5f, Offset(x + p * 4, y + p * 3.5f))
                }
                3 -> {
                    // Shell
                    drawOval(BeachPalette.shell, Offset(x + p * 2, y + p * 4), Size(p * 2.5f, p * 2))
                    drawOval(Color(0xFFFF8A80), Offset(x + p * 5, y + p * 2), Size(p * 1.5f, p * 1.2f))
                }
                else -> {
                    // Footprints
                    drawOval(BeachPalette.sandDark, Offset(x + p * 2, y + p * 2), Size(p * 1, p * 1.5f))
                    drawOval(BeachPalette.sandDark, Offset(x + p * 5, y + p * 5), Size(p * 1, p * 1.5f))
                }
            }
        }
        3 -> {
            drawRect(BeachPalette.sand, Offset(x, y), Size(s, s))
            when (col) {
                0 -> {
                    // Sandcastle
                    drawRect(BeachPalette.castleSand, Offset(x + p * 1, y + p * 3), Size(p * 6, p * 4))
                    // Towers
                    drawRect(BeachPalette.castleSand, Offset(x + p * 1, y + p * 1), Size(p * 2, p * 3))
                    drawRect(BeachPalette.castleSand, Offset(x + p * 5, y + p * 1), Size(p * 2, p * 3))
                    // Flag
                    drawRect(BeachPalette.umbrellaPole, Offset(x + p * 2, y + p * 0.5f), Size(p * 0.3f, p * 1.5f))
                    drawRect(BeachPalette.castleFlag, Offset(x + p * 2.3f, y + p * 0.5f), Size(p * 1.2f, p * 0.8f))
                }
                3 -> {
                    // Starfish + shells
                    // Star shape (simplified as circle with lines)
                    drawCircle(Color(0xFFFF9800), p * 1.2f, Offset(x + p * 4, y + p * 4))
                    for (i in 0 until 5) {
                        val angle = (i * 72f - 90f) * (Math.PI / 180f).toFloat()
                        drawLine(
                            Color(0xFFFF9800),
                            Offset(x + p * 4, y + p * 4),
                            Offset(x + p * 4 + kotlin.math.cos(angle) * p * 2, y + p * 4 + kotlin.math.sin(angle) * p * 2),
                            p * 0.6f
                        )
                    }
                    drawOval(BeachPalette.shell, Offset(x + p * 1, y + p * 6), Size(p * 1.5f, p * 1f))
                }
                else -> {
                    // Sandy texture dots
                    val dots = listOf(Pair(2f, 3f), Pair(5f, 2f), Pair(3f, 6f), Pair(6f, 5f), Pair(4f, 4f))
                    dots.forEach { (dx, dy) ->
                        drawCircle(BeachPalette.sandDark, p * 0.2f, Offset(x + p * dx, y + p * dy))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// SPACE THEME (3x3)
// [0,0: Stars     ] [0,1: Stars     ] [0,2: Planet    ]
// [1,0: Stars+Neb ] [1,1: Rocket    ] [1,2: Stars     ]
// [2,0: Moon      ] [2,1: Stars+Neb ] [2,2: Stars     ]
// ══════════════════════════════════════════════════════════════

private fun DrawScope.drawSpacePiece(row: Int, col: Int, x: Float, y: Float, s: Float) {
    val p = s / 8f
    // Dark space background
    drawRect(SpacePalette.darkSpace, Offset(x, y), Size(s, s))

    // Random stars for every cell
    val starSeeds = listOf(
        Pair(1.5f, 1.5f), Pair(6f, 2f), Pair(3f, 6.5f), Pair(7f, 5f), Pair(2f, 4f)
    )
    starSeeds.forEach { (sx, sy) ->
        val offsetX = (row * 3 + col) * 1.7f // deterministic variation
        val adjustedX = ((sx + offsetX) % 7f) + 0.5f
        drawCircle(SpacePalette.starWhite, p * 0.2f, Offset(x + p * adjustedX, y + p * sy))
    }

    when {
        row == 0 && col == 2 -> {
            // Planet (red with ring)
            drawCircle(SpacePalette.planetRed, p * 2.5f, Offset(x + p * 4, y + p * 4))
            drawCircle(SpacePalette.planetRedDark, p * 1.5f, Offset(x + p * 3.5f, y + p * 4.5f))
            // Ring
            drawOval(SpacePalette.starYellow.copy(alpha = 0.6f), Offset(x + p * 0.5f, y + p * 3.2f), Size(p * 7, p * 1.6f))
        }
        row == 1 && col == 1 -> {
            // Rocket
            // Body
            drawRect(SpacePalette.rocketWhite, Offset(x + p * 3, y + p * 1.5f), Size(p * 2, p * 4))
            // Nose cone
            val nose = Path().apply {
                moveTo(x + p * 4, y + p * 0.5f)
                lineTo(x + p * 5, y + p * 1.5f)
                lineTo(x + p * 3, y + p * 1.5f)
                close()
            }
            drawPath(nose, SpacePalette.rocketRed)
            // Window
            drawCircle(SpacePalette.rocketBlue, p * 0.6f, Offset(x + p * 4, y + p * 3))
            // Fins
            val leftFin = Path().apply {
                moveTo(x + p * 3, y + p * 4.5f)
                lineTo(x + p * 2, y + p * 5.5f)
                lineTo(x + p * 3, y + p * 5.5f)
                close()
            }
            drawPath(leftFin, SpacePalette.rocketRed)
            val rightFin = Path().apply {
                moveTo(x + p * 5, y + p * 4.5f)
                lineTo(x + p * 6, y + p * 5.5f)
                lineTo(x + p * 5, y + p * 5.5f)
                close()
            }
            drawPath(rightFin, SpacePalette.rocketRed)
            // Flame
            val flame = Path().apply {
                moveTo(x + p * 3.3f, y + p * 5.5f)
                lineTo(x + p * 4, y + p * 7.5f)
                lineTo(x + p * 4.7f, y + p * 5.5f)
                close()
            }
            drawPath(flame, SpacePalette.flame)
            drawPath(Path().apply {
                moveTo(x + p * 3.6f, y + p * 5.5f)
                lineTo(x + p * 4, y + p * 6.8f)
                lineTo(x + p * 4.4f, y + p * 5.5f)
                close()
            }, SpacePalette.flameYellow)
        }
        row == 2 && col == 0 -> {
            // Moon
            drawCircle(SpacePalette.moonGray, p * 2.5f, Offset(x + p * 4, y + p * 4))
            // Craters
            drawCircle(SpacePalette.moonCrater, p * 0.6f, Offset(x + p * 3, y + p * 3.5f))
            drawCircle(SpacePalette.moonCrater, p * 0.8f, Offset(x + p * 5, y + p * 4.5f))
            drawCircle(SpacePalette.moonCrater, p * 0.4f, Offset(x + p * 3.5f, y + p * 5.5f))
        }
        row == 0 && col == 0 -> {
            // Shooting star
            drawLine(SpacePalette.starYellow, Offset(x + p * 6, y + p * 2), Offset(x + p * 2, y + p * 5), p * 0.5f)
            drawCircle(SpacePalette.starWhite, p * 0.5f, Offset(x + p * 2, y + p * 5))
        }
        row == 1 && col == 0 -> {
            // Nebula glow
            drawCircle(Color(0xFF9C27B0).copy(alpha = 0.3f), p * 3f, Offset(x + p * 4, y + p * 4))
            drawCircle(Color(0xFF673AB7).copy(alpha = 0.2f), p * 2f, Offset(x + p * 4, y + p * 4))
        }
        row == 2 && col == 1 -> {
            // Nebula + distant galaxy
            drawCircle(Color(0xFF2196F3).copy(alpha = 0.2f), p * 3f, Offset(x + p * 4, y + p * 4))
            drawOval(Color(0xFFE1BEE7).copy(alpha = 0.4f), Offset(x + p * 2, y + p * 3), Size(p * 4, p * 2))
        }
        else -> {
            // Extra stars
            drawCircle(SpacePalette.starYellow, p * 0.3f, Offset(x + p * 4, y + p * 2))
            drawCircle(SpacePalette.starWhite, p * 0.25f, Offset(x + p * 2, y + p * 6))
        }
    }
}

// ══════════════════════════════════════════════════════════════
// CASTLE THEME (4x4)
// [0,0: Tower+Flag] [0,1: Sky+Flag ] [0,2: Sky+Flag ] [0,3: Tower+Flag]
// [1,0: Wall      ] [1,1: Wall+Wind] [1,2: Wall+Wind] [1,3: Wall      ]
// [2,0: Wall      ] [2,1: Gate top ] [2,2: Gate top ] [2,3: Wall      ]
// [3,0: Grass     ] [3,1: Gate btm ] [3,2: Gate btm ] [3,3: Grass     ]
// ══════════════════════════════════════════════════════════════

private fun DrawScope.drawCastlePiece(row: Int, col: Int, x: Float, y: Float, s: Float) {
    val p = s / 8f
    when (row) {
        0 -> {
            // Sky + tower tops
            drawRect(CastlePalette.sky, Offset(x, y), Size(s, s))
            when (col) {
                0, 3 -> {
                    // Tower
                    drawRect(CastlePalette.stoneGray, Offset(x + p * 1, y + p * 2), Size(p * 6, p * 6))
                    // Battlements
                    drawRect(CastlePalette.stoneGray, Offset(x + p * 1, y + p * 1), Size(p * 1.5f, p * 2))
                    drawRect(CastlePalette.stoneGray, Offset(x + p * 4.5f, y + p * 1), Size(p * 1.5f, p * 2))
                    // Flag
                    drawRect(CastlePalette.flagPole, Offset(x + p * 3.8f, y + p * 0.3f), Size(p * 0.3f, p * 2.5f))
                    drawRect(CastlePalette.flagRed, Offset(x + p * 4.1f, y + p * 0.3f), Size(p * 2, p * 1.2f))
                }
                1, 2 -> {
                    // Upper wall with banner
                    drawRect(CastlePalette.stoneGray, Offset(x, y + p * 4), Size(s, p * 4))
                    drawRect(CastlePalette.stoneDark, Offset(x, y + p * 3.5f), Size(s, p * 0.5f))
                    // Banner
                    drawRect(CastlePalette.gold, Offset(x + p * 2, y + p * 5), Size(p * 4, p * 2.5f))
                    drawRect(CastlePalette.flagRed, Offset(x + p * 2.5f, y + p * 5.5f), Size(p * 3, p * 1.5f))
                }
            }
        }
        1 -> {
            // Stone wall
            drawRect(CastlePalette.stoneGray, Offset(x, y), Size(s, s))
            // Brick pattern
            for (br in 0 until 4) {
                val yOff = br * p * 2
                val xShift = if (br % 2 == 0) 0f else p * 2
                for (bc in 0 until 5) {
                    drawRect(
                        CastlePalette.stoneDark,
                        Offset(x + bc * p * 2 + xShift, y + yOff),
                        Size(p * 1.9f, p * 1.9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(p * 0.15f)
                    )
                }
            }
            when (col) {
                1, 2 -> {
                    // Window
                    drawRect(CastlePalette.windowDark, Offset(x + p * 2.5f, y + p * 2), Size(p * 3, p * 4))
                    drawOval(CastlePalette.windowDark, Offset(x + p * 2.5f, y + p * 1), Size(p * 3, p * 2.5f))
                    // Window frame
                    drawRect(CastlePalette.stoneLight, Offset(x + p * 3.8f, y + p * 1), Size(p * 0.4f, p * 5))
                    drawRect(CastlePalette.stoneLight, Offset(x + p * 2.5f, y + p * 3.5f), Size(p * 3, p * 0.4f))
                }
            }
        }
        2 -> {
            drawRect(CastlePalette.stoneGray, Offset(x, y), Size(s, s))
            // Brick pattern
            for (br in 0 until 4) {
                val yOff = br * p * 2
                val xShift = if (br % 2 == 0) p * 1 else 0f
                for (bc in 0 until 5) {
                    drawRect(
                        CastlePalette.stoneDark,
                        Offset(x + bc * p * 2 + xShift, y + yOff),
                        Size(p * 1.9f, p * 1.9f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(p * 0.15f)
                    )
                }
            }
            when (col) {
                1 -> {
                    // Gate arch left half
                    drawRect(CastlePalette.doorBrown, Offset(x + p * 4, y + p * 2), Size(p * 4, p * 6))
                    drawOval(CastlePalette.doorBrown, Offset(x + p * 2, y + p * 0.5f), Size(p * 6, p * 4))
                }
                2 -> {
                    // Gate arch right half
                    drawRect(CastlePalette.doorBrown, Offset(x, y + p * 2), Size(p * 4, p * 6))
                    drawOval(CastlePalette.doorBrown, Offset(x, y + p * 0.5f), Size(p * 6, p * 4))
                    // Portcullis lines
                    for (i in 0 until 3) {
                        drawLine(CastlePalette.stoneDark, Offset(x + p * (1 + i * 1.5f), y + p * 2), Offset(x + p * (1 + i * 1.5f), y + s), p * 0.3f)
                    }
                }
            }
        }
        3 -> {
            // Ground / grass
            drawRect(CastlePalette.grassGreen, Offset(x, y), Size(s, s))
            drawRect(CastlePalette.grassLight, Offset(x, y), Size(s, p * 2))
            when (col) {
                1 -> {
                    // Gate bottom left - drawbridge
                    drawRect(CastlePalette.doorBrown, Offset(x + p * 4, y), Size(p * 4, p * 4))
                    drawRect(CastlePalette.doorLight, Offset(x + p * 5, y), Size(p * 1, p * 4))
                    drawRect(CastlePalette.doorLight, Offset(x + p * 7, y), Size(p * 0.5f, p * 4))
                }
                2 -> {
                    // Gate bottom right - drawbridge
                    drawRect(CastlePalette.doorBrown, Offset(x, y), Size(p * 4, p * 4))
                    drawRect(CastlePalette.doorLight, Offset(x + p * 1, y), Size(p * 1, p * 4))
                    drawRect(CastlePalette.doorLight, Offset(x + p * 3, y), Size(p * 0.5f, p * 4))
                }
                0 -> {
                    // Path stones
                    drawOval(CastlePalette.stoneDark, Offset(x + p * 5, y + p * 5), Size(p * 2, p * 1.5f))
                    drawOval(CastlePalette.stoneDark, Offset(x + p * 3, y + p * 7), Size(p * 1.5f, p * 1f))
                }
                3 -> {
                    // Flowers
                    drawCircle(Color(0xFFFF4444), p * 0.5f, Offset(x + p * 2, y + p * 5))
                    drawCircle(Color(0xFFFFEB3B), p * 0.5f, Offset(x + p * 4, y + p * 6))
                    drawCircle(Color(0xFF4FC3F7), p * 0.5f, Offset(x + p * 6, y + p * 4.5f))
                }
            }
        }
    }
}

/**
 * Draws the entire puzzle as a complete image (for previews / completed panels).
 */
fun DrawScope.drawFullPuzzle(theme: PuzzleTheme, gridSize: Int) {
    val cellSize = size.width / gridSize
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            drawPuzzlePiece(theme, row, col, col * cellSize, row * cellSize, cellSize)
        }
    }
}
