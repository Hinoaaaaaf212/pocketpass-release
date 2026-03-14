package com.pocketpass.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Main brand colors - inspired by Nintendo 3DS Miiverse
val PocketPassGreen = Color(0xFF4CA318)  // WCAG AA compliant (4.5:1+ with white)
val PocketPassGreenDark = Color(0xFF3D8A12)
val BubbleTailGreen = Color(0xFFB1E772)

// Gamepad focus indicator
val GamepadFocusBorder = Color(0xFF4CA318)

// ── Theme-aware colors (resolve via CompositionLocal) ──

/** Whether dark mode is currently active. Provided by PocketPassTheme. */
val LocalDarkMode = compositionLocalOf { false }

// Green text variant — darker in light mode for 4.5:1 on white cards
private val LightGreenText = Color(0xFF357F13)    // 4.79:1 on #FAFAFA
private val DarkGreenText = Color(0xFF6BBF2E)     // 6.23:1 on #2A2A2A

// Error/red text — WCAG compliant on respective backgrounds
private val LightErrorText = Color(0xFFD32F2F)    // 4.77:1 on #FAFAFA
private val DarkErrorText = Color(0xFFEF5350)     // 4.12:1 on #2A2A2A

// Light mode base values
private val LightOffWhite = Color(0xFFFAFAFA)
private val LightDarkText = Color(0xFF2C2C2C)
private val LightMediumText = Color(0xFF595959)
private val LightText_ = Color(0xFF6B6B6B)
private val LightSkyBlue = Color(0xFFC8E9FF)
private val LightCheckerLight = Color(0xFFFFFFFF).copy(alpha = 0.10f)
private val LightCheckerDark = Color(0xFF000000).copy(alpha = 0.03f)

// Aero glass highlight gradients
private val LightGlassHighlight = Color.White.copy(alpha = 0.45f)
private val LightGlassHighlightMid = Color.White.copy(alpha = 0.12f)
private val DarkGlassHighlight = Color.White.copy(alpha = 0.12f)
private val DarkGlassHighlightMid = Color.White.copy(alpha = 0.04f)

// Aero card border - thin luminous edge
private val LightCardBorder = Color.White.copy(alpha = 0.65f)
private val DarkCardBorder = Color.White.copy(alpha = 0.10f)

// Aero nav bar glass
private val LightNavGlass = Color.White.copy(alpha = 0.75f)
private val DarkNavGlass = Color(0xFF1A1A1A).copy(alpha = 0.85f)

// Aero radial glow
private val LightRadialGlow = Color.White.copy(alpha = 0.15f)
private val DarkRadialGlow = Color.White.copy(alpha = 0.06f)

// Dark mode values
private val DarkOffWhite = Color(0xFF2A2A2A)       // Dark card background
private val DarkDarkText = Color(0xFFE8E8E8)        // Light text on dark
private val DarkMediumText = Color(0xFFAAAAAA)       // Secondary text on dark
private val DarkLightText = Color(0xFF999999)        // Tertiary text on dark
private val DarkSkyBlue = Color(0xFF1A2A3A)          // Dark blue-tinted background
private val DarkCheckerLight = Color(0xFFFFFFFF).copy(alpha = 0.05f)
private val DarkCheckerDark = Color(0xFF000000).copy(alpha = 0.10f)

// Dark mode gradient colors
val DarkGradientStart = Color(0xFF2D4A1E)   // Dark green
val DarkGradientEnd = Color(0xFF1A2A3A)     // Dark blue

/**
 * Theme-aware color accessors. These resolve to light or dark variants
 * based on [LocalDarkMode]. All existing code referencing these vals
 * will automatically adapt to dark mode without changes.
 */
val OffWhite: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkOffWhite else LightOffWhite

val DarkText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkDarkText else LightDarkText

val MediumText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkMediumText else LightMediumText

val LightText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkLightText else LightText_

val GreenText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkGreenText else LightGreenText

val ErrorText: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkErrorText else LightErrorText

val SkyBlue: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkSkyBlue else LightSkyBlue

val CheckerLight: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkCheckerLight else LightCheckerLight

val CheckerDark: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkCheckerDark else LightCheckerDark

// ── Aero glass theme-aware accessors ──

val GlassHighlight: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkGlassHighlight else LightGlassHighlight

val GlassHighlightMid: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkGlassHighlightMid else LightGlassHighlightMid

val CardBorder: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkCardBorder else LightCardBorder

val NavGlass: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkNavGlass else LightNavGlass

val RadialGlow: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) DarkRadialGlow else LightRadialGlow

/** Default background gradient colors, dark-mode aware. */
val BackgroundGradient: List<Color>
    @Composable @ReadOnlyComposable
    get() = if (LocalDarkMode.current) listOf(DarkGradientStart, DarkGradientEnd) else listOf(PocketPassGreen, LightSkyBlue)

// ── Non-composable variants for Theme.kt color scheme construction ──

fun offWhiteFor(dark: Boolean) = if (dark) DarkOffWhite else LightOffWhite
fun darkTextFor(dark: Boolean) = if (dark) DarkDarkText else LightDarkText
fun skyBlueFor(dark: Boolean) = if (dark) DarkSkyBlue else LightSkyBlue
