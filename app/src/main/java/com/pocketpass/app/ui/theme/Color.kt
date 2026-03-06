package com.pocketpass.app.ui.theme

import androidx.compose.ui.graphics.Color

// Main brand colors - inspired by Nintendo 3DS Miiverse
val PocketPassGreen = Color(0xFF7BC63B)  // Slightly darker for better contrast
val PocketPassGreenDark = Color(0xFF5FA830)
val SkyBlue = Color(0xFFC8E9FF)
val OffWhite = Color(0xFFFAFAFA)  // Slightly brighter for better contrast

// Text colors - WCAG AA compliant
val DarkText = Color(0xFF2C2C2C)  // Darker for better contrast (ratio 14.8:1 on white)
val MediumText = Color(0xFF5F5F5F)  // For secondary text (ratio 7.0:1 on white)
val LightText = Color(0xFF757575)  // For tertiary text (ratio 4.6:1 on white)

// Accent colors
val BubbleTailGreen = Color(0xFFB1E772)

// Gamepad focus indicator
val GamepadFocusBorder = Color(0xFF7BC63B)

// Nintendo-style checkered pattern colors
val CheckerLight = Color(0xFFFFFFFF).copy(alpha = 0.15f)
val CheckerDark = Color(0xFF000000).copy(alpha = 0.05f)
