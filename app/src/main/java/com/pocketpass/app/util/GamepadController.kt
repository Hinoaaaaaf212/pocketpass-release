package com.pocketpass.app.util

import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

// ── Device Detection ──

object AynThorDevice {
    fun isAynThor(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""
        return manufacturer.contains("ayn") || model.contains("thor")
    }
}

// ── Input Mode ──

enum class InputMode { TOUCH, GAMEPAD }

// ── Gamepad State (provided via CompositionLocal) ──

class GamepadState {
    var inputMode by mutableStateOf(InputMode.TOUCH)
        internal set

    val isGamepadActive: Boolean get() = inputMode == InputMode.GAMEPAD
    val isAynThor: Boolean = AynThorDevice.isAynThor()

    fun onGamepadInput() {
        inputMode = InputMode.GAMEPAD
    }

    fun onTouchInput() {
        inputMode = InputMode.TOUCH
    }
}

val LocalGamepadState = compositionLocalOf { GamepadState() }

// ── Joystick to D-Pad Converter ──

class JoystickToDpad(
    private val deadZone: Float = 0.4f,
    private val repeatDelayMs: Long = 220L,
    private val repeatRateMs: Long = 120L
) {
    private var lastDirection: Int = -1
    private var lastTriggerTime: Long = 0L
    private var isFirstRepeat: Boolean = true

    fun processMotionEvent(event: MotionEvent): Int? {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return null

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val effectiveX = if (abs(hatX) > abs(x)) hatX else x
        val effectiveY = if (abs(hatY) > abs(y)) hatY else y

        val direction = when {
            effectiveY < -deadZone -> KeyEvent.KEYCODE_DPAD_UP
            effectiveY > deadZone -> KeyEvent.KEYCODE_DPAD_DOWN
            effectiveX < -deadZone -> KeyEvent.KEYCODE_DPAD_LEFT
            effectiveX > deadZone -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> -1
        }

        val now = SystemClock.uptimeMillis()

        if (direction == -1) {
            lastDirection = -1
            isFirstRepeat = true
            return null
        }

        if (direction != lastDirection) {
            lastDirection = direction
            lastTriggerTime = now
            isFirstRepeat = true
            return direction
        }

        val delay = if (isFirstRepeat) repeatDelayMs else repeatRateMs
        if (now - lastTriggerTime >= delay) {
            lastTriggerTime = now
            isFirstRepeat = false
            return direction
        }

        return null
    }
}

// ── Screen hierarchy ──

sealed class Screen {
    // Main tabs (shown with nav bar)
    object Plaza : Screen()
    object Messages : Screen()
    object Friends : Screen()
    object PlazaOverview : Screen()
    object Statistics : Screen()
    object Activities : Screen()
    object Settings : Screen()

    // Sub-screens
    object Auth : Screen()
    object ProfileSettings : Screen()
    object AppSettings : Screen()
    object GameSearch : Screen()
    object Mii3DTest : Screen()
    object History : Screen()
    object EncounterHistory : Screen()
    object Games : Screen()
    object PuzzleSwap : Screen()
    object Shop : Screen()
    object Leaderboard : Screen()
    object Bingo : Screen()
    object Notifications : Screen()
    object SpotPassInbox : Screen()
    object WorldTourMap : Screen()
    data class PuzzleBoard(val panelId: String) : Screen()
    data class Chat(val friendId: String, val friendName: String, val avatarHex: String) : Screen()
}

// ── Navigation State (refactored from MainActivity) ──

class NavigationState {
    var screen by mutableStateOf<Screen>(Screen.Plaza)
    var forceCreateNewMii by mutableStateOf(false)
    var pendingUpdate: com.pocketpass.app.data.AppVersion? = null

    /** Whether the user has completed or skipped the setup login prompt. */
    var setupAuthDone by mutableStateOf(false)

    /** Set only after sign-in restore — skip avatar/profile setup since data was pulled from cloud. */
    var signInRestored by mutableStateOf(false)

    enum class MainScreen { PLAZA, MESSAGES, FRIENDS, PLAZA_OVERVIEW, STATISTICS, ACTIVITIES, SETTINGS }

    // Slide direction tracking
    var previousMainScreen by mutableStateOf(MainScreen.PLAZA)

    private val mainScreenOrder = MainScreen.entries

    private val mainScreenSet = setOf(
        Screen.Plaza::class, Screen.Messages::class, Screen.Friends::class,
        Screen.PlazaOverview::class, Screen.Statistics::class,
        Screen.Activities::class, Screen.Settings::class
    )

    fun currentMainScreen(): MainScreen {
        return when (screen) {
            Screen.Messages, is Screen.Chat -> MainScreen.MESSAGES
            Screen.Friends, Screen.EncounterHistory -> MainScreen.FRIENDS
            Screen.PlazaOverview -> MainScreen.PLAZA_OVERVIEW
            Screen.Statistics, Screen.WorldTourMap -> MainScreen.STATISTICS
            Screen.Activities, Screen.Games, Screen.PuzzleSwap, is Screen.PuzzleBoard,
            Screen.Shop, Screen.Leaderboard, Screen.Bingo, Screen.SpotPassInbox -> MainScreen.ACTIVITIES
            Screen.Settings, Screen.Auth, Screen.ProfileSettings,
            Screen.AppSettings, Screen.GameSearch, Screen.Mii3DTest -> MainScreen.SETTINGS
            else -> MainScreen.PLAZA
        }
    }

    fun isOnSubScreen(): Boolean = screen::class !in mainScreenSet

    fun switchScreen(direction: Int) {
        if (isOnSubScreen()) return

        val current = currentMainScreen()
        val currentIndex = mainScreenOrder.indexOf(current)
        val newIndex = (currentIndex + direction).mod(mainScreenOrder.size)
        navigateToMainScreen(mainScreenOrder[newIndex])
    }

    fun navigateToMainScreen(target: MainScreen) {
        previousMainScreen = currentMainScreen()
        screen = when (target) {
            MainScreen.PLAZA -> Screen.Plaza
            MainScreen.MESSAGES -> Screen.Messages
            MainScreen.FRIENDS -> Screen.Friends
            MainScreen.PLAZA_OVERVIEW -> Screen.PlazaOverview
            MainScreen.STATISTICS -> Screen.Statistics
            MainScreen.ACTIVITIES -> Screen.Activities
            MainScreen.SETTINGS -> Screen.Settings
        }
    }

    fun handleBack(): Boolean {
        previousMainScreen = currentMainScreen()
        val parent = when (screen) {
            is Screen.PuzzleBoard -> Screen.PuzzleSwap
            Screen.Bingo -> Screen.Games
            Screen.Leaderboard -> Screen.Activities
            Screen.Shop -> Screen.Activities
            Screen.PuzzleSwap -> Screen.Games
            Screen.Games -> Screen.Activities
            Screen.GameSearch -> Screen.ProfileSettings
            Screen.ProfileSettings -> Screen.Settings
            Screen.AppSettings -> Screen.Settings
            Screen.Auth -> Screen.Settings
            is Screen.Chat -> Screen.Messages
            Screen.WorldTourMap -> Screen.Statistics
            Screen.SpotPassInbox -> Screen.Plaza
            Screen.Notifications -> Screen.Plaza
            Screen.EncounterHistory -> Screen.Friends
            Screen.Mii3DTest -> Screen.AppSettings
            Screen.History -> Screen.Plaza
            // Main screens go back to Plaza
            Screen.PlazaOverview, Screen.Activities, Screen.Settings,
            Screen.Messages, Screen.Statistics, Screen.Friends -> Screen.Plaza
            Screen.Plaza -> return false
        }
        screen = parent
        return true
    }
}
