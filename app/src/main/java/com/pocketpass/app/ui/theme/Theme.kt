package com.pocketpass.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Aero status bar colors
private val AeroStatusBarLight = Color(0xFF3A8A10)   // Richer green
private val AeroStatusBarDark = Color(0xFF1A3A0F)    // Deep forest

private val LightColorScheme = lightColorScheme(
    primary = PocketPassGreen,
    secondary = offWhiteFor(false),
    tertiary = BubbleTailGreen,
    background = offWhiteFor(false),
    surface = offWhiteFor(false),
    surfaceVariant = Color.White.copy(alpha = 0.7f),
    onPrimary = darkTextFor(false),
    onSecondary = darkTextFor(false),
    onTertiary = darkTextFor(false),
    onBackground = darkTextFor(false),
    onSurface = darkTextFor(false),
    outline = Color.White.copy(alpha = 0.3f)
)

private val DarkColorScheme = darkColorScheme(
    primary = PocketPassGreen,
    secondary = offWhiteFor(true),
    tertiary = BubbleTailGreen,
    background = offWhiteFor(true),
    surface = offWhiteFor(true),
    surfaceVariant = Color(0xFF2A2A2A).copy(alpha = 0.7f),
    onPrimary = darkTextFor(true),
    onSecondary = darkTextFor(true),
    onTertiary = darkTextFor(true),
    onBackground = darkTextFor(true),
    onSurface = darkTextFor(true),
    outline = Color.White.copy(alpha = 0.08f)
)

@Composable
fun PocketPassTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) AeroStatusBarDark.toArgb() else AeroStatusBarLight.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalDarkMode provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
