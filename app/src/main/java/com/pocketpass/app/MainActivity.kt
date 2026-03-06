package com.pocketpass.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.service.ProximityService
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.SoundManager
import com.pocketpass.app.util.GamepadState
import com.pocketpass.app.util.JoystickToDpad
import com.pocketpass.app.util.LocalGamepadState
import com.pocketpass.app.util.NavigationState
import com.pocketpass.app.ui.AppSettingsScreen
import com.pocketpass.app.ui.AvatarCreatorScreen
import com.pocketpass.app.ui.EncounterHistoryScreen
import com.pocketpass.app.ui.FavoritesScreen
import com.pocketpass.app.ui.GamepadButtonHints
import com.pocketpass.app.ui.PermissionsScreen
import com.pocketpass.app.ui.PlazaScreen
import com.pocketpass.app.ui.ProfileSetupScreen
import com.pocketpass.app.ui.GamesHubScreen
import com.pocketpass.app.ui.PuzzleSwapScreen
import com.pocketpass.app.ui.PuzzleBoardScreen
import com.pocketpass.app.ui.QrExchangeScreen
import com.pocketpass.app.ui.RoamingPlazaScreen
import com.pocketpass.app.ui.SettingsScreen
import com.pocketpass.app.ui.StatisticsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.ui.theme.PocketPassTheme

class MainActivity : ComponentActivity() {

    private val gamepadState = GamepadState()
    private val joystickToDpad = JoystickToDpad()
    private val navigationState = NavigationState()
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userPreferences = UserPreferences(this)

        soundManager = SoundManager(this)

        setContent {
            PocketPassTheme {
                CompositionLocalProvider(
                    LocalSoundManager provides soundManager,
                    LocalGamepadState provides gamepadState
                ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { gamepadState.onTouchInput() }
                        },
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }
                    // Splash screen: wait for DataStore + minimum 2.5s display
                    var splashFinished by remember { mutableStateOf(false) }
                    val avatarHex by userPreferences.avatarHexFlow.collectAsState(initial = null)
                    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2500L)
                        splashFinished = true
                    }

                    val nav = navigationState

                    // ── Plaza music (persists across Plaza, Settings, Roaming Plaza) ──
                    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
                    val isOnMainScreens = permissionsGranted && splashFinished &&
                        avatarHex != null && !nav.forceCreateNewMii &&
                        userName != null

                    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
                    DisposableEffect(isOnMainScreens) {
                        if (isOnMainScreens) {
                            val mp = MediaPlayer.create(this@MainActivity, R.raw.plaza_theme).apply {
                                isLooping = true
                                setVolume(musicVolume, musicVolume)
                            }
                            mediaPlayerRef.value = mp
                            if (musicVolume > 0f) mp.start()
                            onDispose {
                                mp.stop()
                                mp.release()
                                mediaPlayerRef.value = null
                            }
                        } else {
                            onDispose { }
                        }
                    }

                    LaunchedEffect(musicVolume) {
                        val mp = mediaPlayerRef.value ?: return@LaunchedEffect
                        if (musicVolume <= 0f) {
                            if (mp.isPlaying) mp.pause()
                        } else {
                            mp.setVolume(musicVolume, musicVolume)
                            if (!mp.isPlaying) mp.start()
                        }
                    }

                    // Pause music when app goes to background, resume when back
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            val mp = mediaPlayerRef.value ?: return@LifecycleEventObserver
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    if (mp.isPlaying) mp.pause()
                                }
                                Lifecycle.Event.ON_START -> {
                                    if (musicVolume > 0f) mp.start()
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── Splash screen ──
                        if (!splashFinished) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(PocketPassGreen, SkyBlue)
                                        )
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "PocketPass",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = OffWhite,
                                    strokeWidth = 4.dp
                                )
                            }
                        }
                        // ── Screen content ──
                        else {
                            when {
                                !permissionsGranted -> {
                                    PermissionsScreen(
                                        onPermissionsGranted = {
                                            permissionsGranted = true
                                        }
                                    )
                                }
                                avatarHex == null || nav.forceCreateNewMii -> {
                                    AvatarCreatorScreen(
                                        onAvatarSaved = {
                                            nav.forceCreateNewMii = false
                                        }
                                    )
                                }
                                userName == null -> {
                                    ProfileSetupScreen(
                                        onProfileSaved = { }
                                    )
                                }
                                nav.showQrExchange -> {
                                    QrExchangeScreen(
                                        onBack = { nav.showQrExchange = false }
                                    )
                                }
                                nav.showAppSettings -> {
                                    AppSettingsScreen(
                                        onBack = { nav.showAppSettings = false }
                                    )
                                }
                                nav.showSettings -> {
                                    SettingsScreen(
                                        onBack = { nav.showSettings = false },
                                        onCreateNewMii = {
                                            nav.forceCreateNewMii = true
                                            nav.showSettings = false
                                        },
                                        onOpenQrExchange = {
                                            nav.showSettings = false
                                            nav.showQrExchange = true
                                        },
                                        onOpenAppSettings = {
                                            nav.showSettings = false
                                            nav.showAppSettings = true
                                        }
                                    )
                                }
                                nav.showHistory -> {
                                    EncounterHistoryScreen(
                                        onBack = { nav.showHistory = false }
                                    )
                                }
                                nav.showStatistics -> {
                                    StatisticsScreen(
                                        onBack = { nav.showStatistics = false }
                                    )
                                }
                                nav.showFavorites -> {
                                    FavoritesScreen(
                                        onBack = { nav.showFavorites = false }
                                    )
                                }
                                nav.selectedPuzzlePanel != null -> {
                                    PuzzleBoardScreen(
                                        panelId = nav.selectedPuzzlePanel!!,
                                        onBack = { nav.selectedPuzzlePanel = null }
                                    )
                                }
                                nav.showPuzzleSwap -> {
                                    PuzzleSwapScreen(
                                        onBack = { nav.showPuzzleSwap = false; nav.showGames = true },
                                        onOpenPuzzleBoard = { panelId -> nav.selectedPuzzlePanel = panelId }
                                    )
                                }
                                nav.showGames -> {
                                    GamesHubScreen(
                                        onBack = { nav.showGames = false },
                                        onOpenPuzzleSwap = { nav.showGames = false; nav.showPuzzleSwap = true }
                                    )
                                }
                                nav.showPlazaOverview -> {
                                    RoamingPlazaScreen(
                                        onBack = { nav.showPlazaOverview = false }
                                    )
                                }
                                else -> {
                                    val proximityEnabled by userPreferences.proximityEnabledFlow.collectAsState(initial = true)

                                    LaunchedEffect(proximityEnabled) {
                                        try {
                                            val serviceIntent = Intent(this@MainActivity, ProximityService::class.java)
                                            if (proximityEnabled) {
                                                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                                            } else {
                                                stopService(serviceIntent)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    PlazaScreen(
                                        onOpenSettings = { nav.showSettings = true },
                                        onOpenHistory = { nav.showHistory = true },
                                        onOpenStatistics = { nav.showStatistics = true },
                                        onOpenFavorites = { nav.showFavorites = true },
                                        onOpenPlazaOverview = { nav.showPlazaOverview = true },
                                        onOpenGames = { nav.showGames = true }
                                    )
                                }
                            }
                        }

                        // ── Gamepad button hints overlay ──
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            GamepadButtonHints()
                        }
                    }
                }
            } // CompositionLocalProvider
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B -> {
                    gamepadState.onGamepadInput()
                    soundManager.playBack()
                    if (navigationState.handleBack()) return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    gamepadState.onGamepadInput()
                    soundManager.playNavigate()
                    navigationState.switchScreen(-1)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    gamepadState.onGamepadInput()
                    soundManager.playNavigate()
                    navigationState.switchScreen(+1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    gamepadState.onGamepadInput()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            val keyCode = joystickToDpad.processMotionEvent(event)
            if (keyCode != null) {
                gamepadState.onGamepadInput()
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                window.decorView.dispatchKeyEvent(downEvent)
                window.decorView.dispatchKeyEvent(upEvent)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
