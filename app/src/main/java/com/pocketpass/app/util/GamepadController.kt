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

// ── Navigation State (refactored from MainActivity) ──

class NavigationState {
    var showSettings by mutableStateOf(false)
    var showAppSettings by mutableStateOf(false)
    var showHistory by mutableStateOf(false)
    var showStatistics by mutableStateOf(false)
    var showFavorites by mutableStateOf(false)
    var showPlazaOverview by mutableStateOf(false)
    var showQrExchange by mutableStateOf(false)
    var forceCreateNewMii by mutableStateOf(false)
    var showGames by mutableStateOf(false)
    var showPuzzleSwap by mutableStateOf(false)
    var selectedPuzzlePanel by mutableStateOf<String?>(null)

    enum class MainScreen { PLAZA, HISTORY, FAVORITES, STATISTICS, GAMES, SETTINGS }

    private val mainScreenOrder = MainScreen.values().toList()

    fun currentMainScreen(): MainScreen {
        return when {
            showHistory -> MainScreen.HISTORY
            showFavorites -> MainScreen.FAVORITES
            showStatistics -> MainScreen.STATISTICS
            showGames -> MainScreen.GAMES
            showSettings -> MainScreen.SETTINGS
            else -> MainScreen.PLAZA
        }
    }

    fun isOnSubScreen(): Boolean {
        return showAppSettings || showQrExchange || showPlazaOverview ||
                forceCreateNewMii || showPuzzleSwap || selectedPuzzlePanel != null
    }

    fun switchScreen(direction: Int) {
        if (isOnSubScreen()) return

        val current = currentMainScreen()
        val currentIndex = mainScreenOrder.indexOf(current)
        val newIndex = (currentIndex + direction).mod(mainScreenOrder.size)
        navigateToMainScreen(mainScreenOrder[newIndex])
    }

    private fun navigateToMainScreen(screen: MainScreen) {
        showSettings = false
        showAppSettings = false
        showHistory = false
        showStatistics = false
        showFavorites = false
        showPlazaOverview = false
        showQrExchange = false
        showGames = false
        showPuzzleSwap = false
        selectedPuzzlePanel = null

        when (screen) {
            MainScreen.PLAZA -> { /* all cleared = plaza */ }
            MainScreen.HISTORY -> showHistory = true
            MainScreen.FAVORITES -> showFavorites = true
            MainScreen.STATISTICS -> showStatistics = true
            MainScreen.GAMES -> showGames = true
            MainScreen.SETTINGS -> showSettings = true
        }
    }

    fun handleBack(): Boolean {
        return when {
            selectedPuzzlePanel != null -> { selectedPuzzlePanel = null; true }
            showPuzzleSwap -> { showPuzzleSwap = false; showGames = true; true }
            showAppSettings -> { showAppSettings = false; showSettings = true; true }
            showQrExchange -> { showQrExchange = false; showSettings = true; true }
            showPlazaOverview -> { showPlazaOverview = false; true }
            showGames -> { showGames = false; true }
            showSettings -> { showSettings = false; true }
            showHistory -> { showHistory = false; true }
            showStatistics -> { showStatistics = false; true }
            showFavorites -> { showFavorites = false; true }
            else -> false
        }
    }
}
