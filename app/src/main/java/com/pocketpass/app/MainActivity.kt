package com.pocketpass.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
import com.pocketpass.app.ui.FriendsScreen
import com.pocketpass.app.ui.GamepadButtonHints
import com.pocketpass.app.ui.PermissionsScreen
import com.pocketpass.app.ui.PlazaNavBar
import com.pocketpass.app.ui.PlazaScreen
import com.pocketpass.app.ui.ProfileSettingsScreen
import com.pocketpass.app.ui.ProfileSetupScreen
import com.pocketpass.app.ui.games.GamesHubScreen
import com.pocketpass.app.ui.games.LeaderboardScreen
import com.pocketpass.app.ui.games.MiiBingoScreen
import com.pocketpass.app.ui.games.ShopScreen
import com.pocketpass.app.ui.games.PuzzleSwapScreen
import com.pocketpass.app.ui.games.PuzzleBoardScreen
import com.pocketpass.app.ui.QrExchangeScreen
import com.pocketpass.app.ui.ActivitiesScreen
import com.pocketpass.app.ui.AnimatedPlazaScreen
import com.pocketpass.app.ui.SpotPassInboxScreen
import com.pocketpass.app.service.SpotPassSyncWorker
import com.pocketpass.app.ui.Mii3DTestScreen
import com.pocketpass.app.ui.SettingsScreen
import com.pocketpass.app.ui.StatisticsScreen
import com.pocketpass.app.ui.GameSearchScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.PocketPassTheme
import com.pocketpass.app.ui.AuthScreen
import com.pocketpass.app.ui.ChatScreen
import com.pocketpass.app.ui.ConversationsScreen
import com.pocketpass.app.ui.NotificationsScreen
import com.pocketpass.app.ui.DualScreenPresentation
import com.pocketpass.app.ui.PlazaSecondaryScreen
import com.pocketpass.app.ui.HistorySecondaryScreen
import com.pocketpass.app.ui.FriendsSecondaryScreen
import com.pocketpass.app.ui.AnimatedPlazaSecondaryScreen
import com.pocketpass.app.ui.StatisticsSecondaryScreen
import com.pocketpass.app.ui.GamesHubSecondaryScreen
import com.pocketpass.app.ui.MessagesSecondaryScreen
import com.pocketpass.app.ui.SettingsSecondaryScreenContent
import com.pocketpass.app.ui.rememberSecondaryDisplay
import com.pocketpass.app.data.AppUpdateRepository
import com.pocketpass.app.data.AppVersion
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.DownloadProgress
import com.pocketpass.app.data.MessageRepository
import com.pocketpass.app.data.NotificationRepository
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.rememberAppDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            val isDarkMode by userPreferences.darkModeFlow.collectAsState(initial = false)
            PocketPassTheme(darkTheme = isDarkMode) {
                val appDimensions = rememberAppDimensions()
                CompositionLocalProvider(
                    LocalSoundManager provides soundManager,
                    LocalGamepadState provides gamepadState,
                    LocalAppDimensions provides appDimensions
                ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.pressed }) {
                                        gamepadState.onTouchInput()
                                    }
                                }
                            }
                        },
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }
                    // Wait for DataStore to emit its first value so we know the real state
                    var dataStoreReady by remember { mutableStateOf(false) }
                    val avatarHex by userPreferences.avatarHexFlow.collectAsState(initial = null)
                    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
                    LaunchedEffect(Unit) {
                        // Wait for a single emission from DataStore to confirm it's loaded
                        userPreferences.avatarHexFlow.firstOrNull()
                        dataStoreReady = true
                    }

                    val nav = navigationState

                    // ── Plaza music (persists across Plaza, Settings, Roaming Plaza) ──
                    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
                    val isOnMainScreens = permissionsGranted && dataStoreReady &&
                        avatarHex != null && !nav.forceCreateNewMii &&
                        userName != null

                    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
                    // Create MediaPlayer once; pause/resume based on screen state
                    DisposableEffect(Unit) {
                        val mp = MediaPlayer.create(this@MainActivity, R.raw.plaza_theme).apply {
                            isLooping = true
                            setVolume(musicVolume, musicVolume)
                        }
                        mediaPlayerRef.value = mp
                        onDispose {
                            mp.stop()
                            mp.release()
                            mediaPlayerRef.value = null
                        }
                    }

                    LaunchedEffect(isOnMainScreens) {
                        val mp = mediaPlayerRef.value ?: return@LaunchedEffect
                        if (isOnMainScreens && musicVolume > 0f) {
                            mp.start()
                        } else {
                            if (mp.isPlaying) mp.pause()
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
                    val ledController = remember { com.pocketpass.app.util.LedController() }
                    val ledScope = rememberCoroutineScope()
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    val mp = mediaPlayerRef.value ?: return@LifecycleEventObserver
                                    if (mp.isPlaying) mp.pause()
                                }
                                Lifecycle.Event.ON_START -> {
                                    val mp = mediaPlayerRef.value
                                    if (mp != null && musicVolume > 0f) mp.start()

                                    // Blink LEDs green if there are unseen encounters
                                    if (ledController.isAvailable) {
                                        ledScope.launch {
                                            val prefs = UserPreferences(this@MainActivity)
                                            val unseen = prefs.unseenEncountersFlow.firstOrNull() ?: 0
                                            if (unseen > 0) {
                                                ledController.blinkGreen(this, times = 3)
                                                prefs.clearUnseenEncounters()
                                            }
                                        }
                                    }

                                    // Sync with Supabase on foreground if authenticated
                                    // Use Activity lifecycleScope so sync survives navigation
                                    this@MainActivity.lifecycleScope.launch(Dispatchers.IO) {
                                        try {
                                            val authRepo = AuthRepository()
                                            if (authRepo.currentUserId != null) {
                                                SyncRepository(this@MainActivity).fullSync()
                                            }
                                        } catch (_: Exception) { }
                                    }
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
                        // Show background gradient while DataStore loads
                        if (!dataStoreReady) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = BackgroundGradient
                                        )
                                    )
                            )
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
                                // First launch: ask if user has an existing account
                                !nav.setupAuthDone && avatarHex == null && !nav.forceCreateNewMii -> {
                                    AuthScreen(
                                        setupMode = true,
                                        onBack = {
                                            // "Skip" — proceed to Mii Creator without login
                                            nav.setupAuthDone = true
                                        },
                                        onAuthSuccess = {
                                            // Login + restore succeeded — skip setup if data was restored
                                            nav.setupAuthDone = true
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
                                else -> {
                                    // Shared Filament engine — created once, persists across tab switches
                                    val engine = io.github.sceneview.rememberEngine()
                                    val modelLoader = io.github.sceneview.rememberModelLoader(engine)

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

                                    // ── SpotPass periodic sync ──
                                    LaunchedEffect(Unit) {
                                        SpotPassSyncWorker.enqueue(this@MainActivity)
                                        // Handle open_spotpass intent from notification
                                        if (intent?.getBooleanExtra("open_spotpass", false) == true) {
                                            nav.showSpotPassInbox = true
                                            intent.removeExtra("open_spotpass")
                                        }
                                    }

                                    // ── Auto-update check ──
                                    val updateRepo = remember { AppUpdateRepository(this@MainActivity) }
                                    var availableUpdate by remember { mutableStateOf<AppVersion?>(null) }
                                    var showUpdateDialog by remember { mutableStateOf(false) }

                                    LaunchedEffect(Unit) {
                                        val update = updateRepo.checkForUpdate()
                                        if (update != null) {
                                            availableUpdate = update
                                            showUpdateDialog = true
                                        }
                                    }

                                    if (showUpdateDialog && availableUpdate != null) {
                                        val update = availableUpdate!!
                                        val isForced = update.minVersionCode > updateRepo.getCurrentVersionCode()

                                        AlertDialog(
                                            onDismissRequest = {
                                                if (!isForced) showUpdateDialog = false
                                            },
                                            title = {
                                                Text(
                                                    "Update Available",
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            text = {
                                                Column {
                                                    Text("v${update.versionName} is available.")
                                                    if (update.changelog.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            update.changelog,
                                                            color = DarkText.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                    if (isForced) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            "This update is required to continue using PocketPass.",
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = DarkText
                                                        )
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    updateRepo.downloadApk(update)
                                                    if (!isForced) showUpdateDialog = false
                                                }) {
                                                    Text(
                                                        "Update Now",
                                                        color = PocketPassGreen,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            },
                                            dismissButton = if (!isForced) {
                                                {
                                                    TextButton(onClick = { showUpdateDialog = false }) {
                                                        Text("Later", color = DarkText.copy(alpha = 0.6f))
                                                    }
                                                }
                                            } else null
                                        )
                                    }

                                    // Dual screen support (Ayn Thor bottom screen)
                                    val secondaryDisplay = rememberSecondaryDisplay()
                                    val dualScreenEnabled by userPreferences.dualScreenModeFlow.collectAsState(initial = true)
                                    val isDualScreen = secondaryDisplay != null && dualScreenEnabled

                                    // Unread counts for nav bar badges
                                    var unreadNotifCount by remember { mutableStateOf(0) }
                                    var unreadMsgCount by remember { mutableStateOf(0) }
                                    val notifRepo = remember { NotificationRepository() }
                                    val msgRepo = remember { MessageRepository(this@MainActivity) }

                                    // Subscribe to realtime + poll unread counts
                                    LaunchedEffect(Unit) {
                                        val authRepo = AuthRepository()
                                        if (authRepo.currentUserId != null) {
                                            // Start realtime subscriptions
                                            withContext(Dispatchers.IO) {
                                                try { msgRepo.subscribeToRealtime() } catch (_: Exception) {}
                                                try { notifRepo.subscribeToRealtime() } catch (_: Exception) {}
                                            }
                                        }
                                    }

                                    // Observe notification unread count
                                    LaunchedEffect(Unit) {
                                        notifRepo.unreadCount.collect { count ->
                                            unreadNotifCount = count
                                        }
                                    }

                                    // Observe message unread count
                                    LaunchedEffect(Unit) {
                                        msgRepo.getUnreadCountFlow().collect { count ->
                                            unreadMsgCount = count
                                        }
                                    }

                                    if (isDualScreen && secondaryDisplay != null) {
                                        DualScreenPresentation(secondaryDisplay = secondaryDisplay) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                // Nav bar on bottom screen (Thor)
                                                if (!nav.isOnSubScreen()) {
                                                    PlazaNavBar(
                                                        currentScreen = nav.currentMainScreen(),
                                                        onNavigate = { screen ->
                                                            nav.navigateToMainScreen(screen)
                                                        },
                                                        unreadMessageCount = unreadMsgCount,
                                                        unreadNotificationCount = unreadNotifCount,
                                                        onOpenNotifications = { nav.showNotifications = true }
                                                    )
                                                }

                                                // Tab-specific secondary content
                                                Box(modifier = Modifier.weight(1f)) {
                                                    when {
                                                        nav.showSettings -> SettingsSecondaryScreenContent(
                                                            onCreateNewMii = {
                                                                nav.forceCreateNewMii = true
                                                                nav.showSettings = false
                                                            },
                                                            onOpenProfileSettings = {
                                                                nav.showSettings = false
                                                                nav.showProfileSettings = true
                                                            },
                                                            onOpenAppSettings = {
                                                                nav.showSettings = false
                                                                nav.showAppSettings = true
                                                            },
                                                            onOpenQrExchange = {
                                                                nav.showSettings = false
                                                                nav.showQrExchange = true
                                                            },
                                                            onOpenAuth = {
                                                                nav.showSettings = false
                                                                nav.showAuth = true
                                                            }
                                                        )
                                                        nav.showHistory -> HistorySecondaryScreen()
                                                        nav.showMessages -> MessagesSecondaryScreen()
                                                        nav.showFriends -> FriendsSecondaryScreen()
                                                        nav.showPlazaOverview -> AnimatedPlazaSecondaryScreen()
                                                        nav.showStatistics -> StatisticsSecondaryScreen()
                                                        nav.showActivities || nav.showGames || nav.showPuzzleSwap || nav.showShop || nav.showLeaderboard || nav.showBingo -> GamesHubSecondaryScreen()
                                                        else -> PlazaSecondaryScreen()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // All navigation screens with persistent nav bar
                                    val isCompactScreen = appDimensions.isCompact
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Persistent nav bar - top on tablets, bottom on phones
                                        if (!nav.isOnSubScreen() && !isDualScreen && !isCompactScreen) {
                                            PlazaNavBar(
                                                currentScreen = nav.currentMainScreen(),
                                                onNavigate = { screen ->
                                                    nav.navigateToMainScreen(screen)
                                                },
                                                unreadMessageCount = unreadMsgCount,
                                                unreadNotificationCount = unreadNotifCount,
                                                onOpenNotifications = { nav.showNotifications = true }
                                            )
                                        }

                                        Box(modifier = Modifier.weight(1f)) {
                                            when {
                                                nav.showAuth -> {
                                                    AuthScreen(
                                                        onBack = { nav.showAuth = false; nav.showSettings = true },
                                                        onAuthSuccess = { nav.showAuth = false; nav.showSettings = true }
                                                    )
                                                }
                                                nav.showGameSearch -> {
                                                    GameSearchScreen(
                                                        onBack = { nav.showGameSearch = false; nav.showProfileSettings = true }
                                                    )
                                                }
                                                nav.showProfileSettings -> {
                                                    ProfileSettingsScreen(
                                                        onBack = { nav.showProfileSettings = false; nav.showSettings = true },
                                                        onOpenGameSearch = {
                                                            nav.showProfileSettings = false
                                                            nav.showGameSearch = true
                                                        }
                                                    )
                                                }
                                                nav.showQrExchange -> {
                                                    QrExchangeScreen(
                                                        onBack = { nav.showQrExchange = false; nav.showSettings = true }
                                                    )
                                                }
                                                nav.showMii3DTest -> {
                                                    Mii3DTestScreen(
                                                        onBack = { nav.showMii3DTest = false; nav.showAppSettings = true },
                                                        avatarHex = avatarHex,
                                                        sharedEngine = engine,
                                                        sharedModelLoader = modelLoader
                                                    )
                                                }
                                                nav.showAppSettings -> {
                                                    AppSettingsScreen(
                                                        onBack = { nav.showAppSettings = false; nav.showSettings = true },
                                                        onOpenMii3DTest = {
                                                            nav.showAppSettings = false
                                                            nav.showMii3DTest = true
                                                        }
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
                                                        },
                                                        onOpenProfileSettings = {
                                                            nav.showSettings = false
                                                            nav.showProfileSettings = true
                                                        },
                                                        onOpenAuth = {
                                                            nav.showSettings = false
                                                            nav.showAuth = true
                                                        }
                                                    )
                                                }
                                                nav.showChat && nav.chatFriendId != null -> {
                                                    ChatScreen(
                                                        friendId = nav.chatFriendId!!,
                                                        friendName = nav.chatFriendName ?: "Friend",
                                                        friendAvatarHex = nav.chatFriendAvatarHex ?: "",
                                                        onBack = {
                                                            nav.showChat = false
                                                            nav.chatFriendId = null
                                                            nav.chatFriendName = null
                                                            nav.chatFriendAvatarHex = null
                                                            nav.showMessages = true
                                                        }
                                                    )
                                                }
                                                nav.showNotifications -> {
                                                    NotificationsScreen(
                                                        onBack = { nav.showNotifications = false },
                                                        onNavigateToChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.showNotifications = false
                                                            nav.showChat = true
                                                            nav.chatFriendId = friendId
                                                            nav.chatFriendName = friendName
                                                            nav.chatFriendAvatarHex = friendAvatarHex
                                                        },
                                                        onNavigateToFriends = {
                                                            nav.showNotifications = false
                                                            nav.navigateToMainScreen(NavigationState.MainScreen.FRIENDS)
                                                        }
                                                    )
                                                }
                                                nav.showEncounterHistory -> {
                                                    EncounterHistoryScreen(
                                                        onBack = { nav.showEncounterHistory = false; nav.showFriends = true }
                                                    )
                                                }
                                                nav.showMessages -> {
                                                    ConversationsScreen(
                                                        onBack = { nav.showMessages = false },
                                                        onOpenChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.showMessages = false
                                                            nav.showChat = true
                                                            nav.chatFriendId = friendId
                                                            nav.chatFriendName = friendName
                                                            nav.chatFriendAvatarHex = friendAvatarHex
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
                                                nav.showFriends -> {
                                                    FriendsScreen(
                                                        onBack = { nav.showFriends = false },
                                                        onOpenChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.showFriends = false
                                                            nav.showChat = true
                                                            nav.chatFriendId = friendId
                                                            nav.chatFriendName = friendName
                                                            nav.chatFriendAvatarHex = friendAvatarHex
                                                        },
                                                        onOpenHistory = {
                                                            nav.showFriends = false
                                                            nav.showEncounterHistory = true
                                                        }
                                                    )
                                                }
                                                nav.selectedPuzzlePanel != null -> {
                                                    PuzzleBoardScreen(
                                                        panelId = nav.selectedPuzzlePanel!!,
                                                        onBack = { nav.selectedPuzzlePanel = null }
                                                    )
                                                }
                                                nav.showBingo -> {
                                                    MiiBingoScreen(
                                                        onBack = { nav.showBingo = false; nav.showGames = true }
                                                    )
                                                }
                                                nav.showLeaderboard -> {
                                                    LeaderboardScreen(
                                                        onBack = { nav.showLeaderboard = false; nav.showActivities = true }
                                                    )
                                                }
                                                nav.showShop -> {
                                                    ShopScreen(
                                                        onBack = { nav.showShop = false; nav.showActivities = true }
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
                                                        onBack = { nav.showGames = false; nav.showActivities = true },
                                                        onOpenPuzzleSwap = { nav.showGames = false; nav.showPuzzleSwap = true },
                                                        onOpenBingo = { nav.showGames = false; nav.showBingo = true }
                                                    )
                                                }
                                                nav.showSpotPassInbox -> {
                                                    SpotPassInboxScreen(
                                                        onBack = { nav.showSpotPassInbox = false }
                                                    )
                                                }
                                                nav.showActivities -> {
                                                    ActivitiesScreen(
                                                        onBack = { nav.showActivities = false },
                                                        onOpenGames = {
                                                            nav.showActivities = false
                                                            nav.showGames = true
                                                        },
                                                        onOpenShop = {
                                                            nav.showActivities = false
                                                            nav.showShop = true
                                                        },
                                                        onOpenLeaderboard = {
                                                            nav.showActivities = false
                                                            nav.showLeaderboard = true
                                                        },
                                                        onOpenSpotPass = {
                                                            nav.showActivities = false
                                                            nav.showSpotPassInbox = true
                                                        }
                                                    )
                                                }
                                                nav.showPlazaOverview -> {
                                                    AnimatedPlazaScreen(
                                                        onBack = { nav.showPlazaOverview = false },
                                                        sharedEngine = engine,
                                                        sharedModelLoader = modelLoader
                                                    )
                                                }
                                                else -> {
                                                    PlazaScreen(
                                                        onOpenSpotPass = { nav.showSpotPassInbox = true }
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom nav bar for compact/phone screens
                                        if (!nav.isOnSubScreen() && !isDualScreen && isCompactScreen) {
                                            PlazaNavBar(
                                                currentScreen = nav.currentMainScreen(),
                                                onNavigate = { screen ->
                                                    nav.navigateToMainScreen(screen)
                                                },
                                                unreadMessageCount = unreadMsgCount,
                                                unreadNotificationCount = unreadNotifCount,
                                                onOpenNotifications = { nav.showNotifications = true }
                                            )
                                        }
                                    }
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
