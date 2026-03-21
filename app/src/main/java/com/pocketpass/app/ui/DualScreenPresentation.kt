package com.pocketpass.app.ui

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.LocalEncounters
import com.pocketpass.app.ui.theme.LocalUserPreferences
import com.pocketpass.app.ui.theme.PocketPassTheme
import com.pocketpass.app.util.LocalGamepadState
import com.pocketpass.app.util.LocalSoundManager

/**
 * Finds the secondary display (e.g. Ayn Thor bottom screen), if present.
 */
@Composable
fun rememberSecondaryDisplay(): Display? {
    val context = LocalContext.current
    return remember {
        val dm = context.getSystemService(android.content.Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.displays?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }
}

/**
 * Shows [content] on the secondary display via a [Presentation].
 * Automatically handles lifecycle (dismiss on background, re-show on foreground)
 * and cleanup on dispose.
 *
 * @param secondaryDisplay The secondary display to render on.
 * @param content The composable content to show on the secondary screen.
 */
@Composable
fun DualScreenPresentation(
    secondaryDisplay: Display,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = LocalDarkMode.current
    // Pass locals to secondary display
    val soundManager = LocalSoundManager.current
    val gamepadState = LocalGamepadState.current
    val encounters = LocalEncounters.current
    val userPreferences = LocalUserPreferences.current

    DisposableEffect(secondaryDisplay) {
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent {
                PocketPassTheme(darkTheme = isDark) {
                    CompositionLocalProvider(
                        LocalSoundManager provides soundManager,
                        LocalGamepadState provides gamepadState,
                        LocalEncounters provides encounters,
                        LocalUserPreferences provides userPreferences
                    ) {
                        content()
                    }
                }
            }
        }
        val presentation = object : Presentation(activity, secondaryDisplay) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                // Consume BACK key so it doesn't close the app when
                // the secondary screen has focus (e.g. after touch)
                if (event.keyCode == KeyEvent.KEYCODE_BACK ||
                    event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        presentation.setContentView(composeView)
        presentation.show()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (presentation.isShowing) presentation.dismiss()
                }
                Lifecycle.Event.ON_START -> {
                    if (!presentation.isShowing) presentation.show()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (presentation.isShowing) presentation.dismiss()
        }
    }
}
