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
    var showFriends by mutableStateOf(false)
    var showPlazaOverview by mutableStateOf(false)
    var showQrExchange by mutableStateOf(false)
    var showGameSearch by mutableStateOf(false)
    var showProfileSettings by mutableStateOf(false)
    var forceCreateNewMii by mutableStateOf(false)
    var showActivities by mutableStateOf(false)
    var showGames by mutableStateOf(false)
    var showPuzzleSwap by mutableStateOf(false)
    var showShop by mutableStateOf(false)
    var showLeaderboard by mutableStateOf(false)
    var selectedPuzzlePanel by mutableStateOf<String?>(null)
    var showAuth by mutableStateOf(false)
    var showMessages by mutableStateOf(false)
    var showChat by mutableStateOf(false)
    var showNotifications by mutableStateOf(false)
    var chatFriendId by mutableStateOf<String?>(null)
    var chatFriendName by mutableStateOf<String?>(null)
    var chatFriendAvatarHex by mutableStateOf<String?>(null)
    var showEncounterHistory by mutableStateOf(false)
    var showBingo by mutableStateOf(false)
    var showMii3DTest by mutableStateOf(false)
    var showSpotPassInbox by mutableStateOf(false)

    /** Whether the user has completed or skipped the setup login prompt. */
    var setupAuthDone by mutableStateOf(false)

    enum class MainScreen { PLAZA, MESSAGES, FRIENDS, PLAZA_OVERVIEW, STATISTICS, ACTIVITIES, SETTINGS }

    private val mainScreenOrder = MainScreen.values().toList()

    fun currentMainScreen(): MainScreen {
        return when {
            showMessages -> MainScreen.MESSAGES
            showFriends -> MainScreen.FRIENDS
            showPlazaOverview -> MainScreen.PLAZA_OVERVIEW
            showStatistics -> MainScreen.STATISTICS
            showActivities -> MainScreen.ACTIVITIES
            showSettings -> MainScreen.SETTINGS
            else -> MainScreen.PLAZA
        }
    }

    fun isOnSubScreen(): Boolean {
        return showAppSettings || showQrExchange || showGameSearch || showProfileSettings ||
                forceCreateNewMii || showGames || showPuzzleSwap || selectedPuzzlePanel != null || showAuth ||
                showChat || showNotifications || showEncounterHistory || showMii3DTest || showShop ||
                showLeaderboard || showBingo || showSpotPassInbox
    }

    fun switchScreen(direction: Int) {
        if (isOnSubScreen()) return

        val current = currentMainScreen()
        val currentIndex = mainScreenOrder.indexOf(current)
        val newIndex = (currentIndex + direction).mod(mainScreenOrder.size)
        navigateToMainScreen(mainScreenOrder[newIndex])
    }

    fun navigateToMainScreen(screen: MainScreen) {
        showSettings = false
        showAppSettings = false
        showHistory = false
        showStatistics = false
        showFriends = false
        showPlazaOverview = false
        showQrExchange = false
        showGameSearch = false
        showProfileSettings = false
        showActivities = false
        showGames = false
        showPuzzleSwap = false
        showShop = false
        showLeaderboard = false
        showBingo = false
        selectedPuzzlePanel = null
        showAuth = false
        showMessages = false
        showChat = false
        showNotifications = false
        chatFriendId = null
        chatFriendName = null
        chatFriendAvatarHex = null
        showEncounterHistory = false
        showMii3DTest = false
        showSpotPassInbox = false

        when (screen) {
            MainScreen.PLAZA -> { /* all cleared = plaza */ }
            MainScreen.MESSAGES -> showMessages = true
            MainScreen.FRIENDS -> showFriends = true
            MainScreen.PLAZA_OVERVIEW -> showPlazaOverview = true
            MainScreen.STATISTICS -> showStatistics = true
            MainScreen.ACTIVITIES -> showActivities = true
            MainScreen.SETTINGS -> showSettings = true
        }
    }

    fun handleBack(): Boolean {
        return when {
            selectedPuzzlePanel != null -> { selectedPuzzlePanel = null; true }
            showBingo -> { showBingo = false; showGames = true; true }
            showLeaderboard -> { showLeaderboard = false; showActivities = true; true }
            showShop -> { showShop = false; showActivities = true; true }
            showPuzzleSwap -> { showPuzzleSwap = false; showGames = true; true }
            showGames -> { showGames = false; showActivities = true; true }
            showGameSearch -> { showGameSearch = false; showProfileSettings = true; true }
            showProfileSettings -> { showProfileSettings = false; showSettings = true; true }
            showAppSettings -> { showAppSettings = false; showSettings = true; true }
            showQrExchange -> { showQrExchange = false; showSettings = true; true }
            showAuth -> { showAuth = false; showSettings = true; true }
            showChat -> { showChat = false; chatFriendId = null; chatFriendName = null; chatFriendAvatarHex = null; showMessages = true; true }
            showSpotPassInbox -> { showSpotPassInbox = false; true }
            showNotifications -> { showNotifications = false; true }
            showEncounterHistory -> { showEncounterHistory = false; showFriends = true; true }
            showMii3DTest -> { showMii3DTest = false; showAppSettings = true; true }
            showPlazaOverview -> { showPlazaOverview = false; true }
            showActivities -> { showActivities = false; true }
            showSettings -> { showSettings = false; true }
            showMessages -> { showMessages = false; true }
            showHistory -> { showHistory = false; true }
            showStatistics -> { showStatistics = false; true }
            showFriends -> { showFriends = false; true }
            else -> false
        }
    }
}
