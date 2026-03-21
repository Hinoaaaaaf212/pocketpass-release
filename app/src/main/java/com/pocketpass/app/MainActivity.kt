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
import com.pocketpass.app.util.Screen
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
import com.pocketpass.app.ui.ActivitiesScreen
import com.pocketpass.app.ui.AnimatedPlazaCompanionScreen
import com.pocketpass.app.ui.AnimatedPlazaScreen
import com.pocketpass.app.ui.SpotPassInboxScreen
import com.pocketpass.app.service.SpotPassSyncWorker
import com.pocketpass.app.ui.Mii3DTestScreen
import com.pocketpass.app.ui.SettingsScreen
import com.pocketpass.app.ui.StatisticsScreen
import com.pocketpass.app.ui.WorldTourMapScreen
import com.pocketpass.app.ui.WorldTourSecondaryScreen
import com.pocketpass.app.ui.GameSearchScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.crypto.decryptFields
import com.pocketpass.app.ui.CheckeredBackground
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.LocalEncounters
import com.pocketpass.app.ui.theme.LocalUserPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.PocketPassGreenDark
import com.pocketpass.app.ui.theme.PocketPassTheme
import com.pocketpass.app.ui.AuthScreen
import com.pocketpass.app.ui.ChatScreen
import com.pocketpass.app.ui.ConversationsScreen
import com.pocketpass.app.ui.NotificationsScreen
import com.pocketpass.app.ui.DualScreenPresentation
import com.pocketpass.app.ui.PlazaSecondaryScreen
import com.pocketpass.app.ui.HistorySecondaryScreen
import com.pocketpass.app.ui.FriendsSecondaryScreen
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
    private var lastSyncTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set crypto storage path early so keys can be loaded lazily
        com.pocketpass.app.data.crypto.CryptoManager.setNoBackupDir(noBackupFilesDir.absolutePath)

        val userPreferences = UserPreferences(this)

        soundManager = SoundManager(this)

        setContent {
            val isDarkMode by userPreferences.darkModeFlow.collectAsState(initial = false)
            PocketPassTheme(darkTheme = isDarkMode) {
                val appDimensions = rememberAppDimensions()
                // Shared encounters (one query for all screens)
                val sharedDb = remember { PocketPassDatabase.getDatabase(this@MainActivity) }
                val rawEncounters by sharedDb.encounterDao().getAllEncountersFlow()
                    .collectAsState(initial = emptyList())
                val sharedEncounters = remember(rawEncounters) {
                    rawEncounters.map { it.decryptFields() }
                }

                CompositionLocalProvider(
                    LocalSoundManager provides soundManager,
                    LocalGamepadState provides gamepadState,
                    LocalAppDimensions provides appDimensions,
                    LocalEncounters provides sharedEncounters,
                    LocalUserPreferences provides userPreferences
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
                    val userRegion by userPreferences.userOriginFlow.collectAsState(initial = null)
                    LaunchedEffect(Unit) {
                        // Wait for a single emission from DataStore to confirm it's loaded
                        userPreferences.avatarHexFlow.firstOrNull()
                        dataStoreReady = true
                    }

                    val nav = navigationState
                    val mainAuthRepo = remember { AuthRepository() }
                    val isAuthenticated by mainAuthRepo.isLoggedIn.collectAsState(initial = mainAuthRepo.currentUserId != null)

                    // ── Plaza music (persists across Plaza, Settings, Roaming Plaza) ──
                    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
                    val isOnMainScreens = permissionsGranted && dataStoreReady &&
                        avatarHex != null && !nav.forceCreateNewMii &&
                        !userRegion.isNullOrBlank()

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

                    // Single effect handles both screen changes and volume changes
                    LaunchedEffect(isOnMainScreens, musicVolume) {
                        val mp = mediaPlayerRef.value ?: return@LaunchedEffect
                        mp.setVolume(musicVolume, musicVolume)
                        if (!isOnMainScreens || musicVolume <= 0f) {
                            if (mp.isPlaying) mp.pause()
                        } else {
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
                                    if (mp != null && isOnMainScreens && musicVolume > 0f) mp.start()

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

                                    // Sync with Supabase on foreground if authenticated (throttle to once per 60s)
                                    val now = System.currentTimeMillis()
                                    if (now - lastSyncTimestamp >= 60_000L) {
                                        lastSyncTimestamp = now
                                        this@MainActivity.lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val authRepo = AuthRepository()
                                                if (authRepo.currentUserId != null) {
                                                    SyncRepository(this@MainActivity).fullSync()
                                                }
                                            } catch (_: Exception) { }
                                        }
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

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors = BackgroundGradient))
                    ) {
                        // ── Screen content ──
                        if (dataStoreReady) {
                            // Show auth if:
                            // - Not authenticated, OR
                            // - Stale session after sign-out (authenticated but no avatar in DataStore)
                            //   Gated by !setupAuthDone so fresh sign-ups (avatar not yet created) go to Mii maker instead
                            // Note: uses avatarHex only — userName can be pulled by background sync and defeat this check
                            val needsAuth = !isAuthenticated ||
                                (!nav.setupAuthDone && avatarHex == null)

                            when {
                                !permissionsGranted -> {
                                    PermissionsScreen(
                                        onPermissionsGranted = {
                                            permissionsGranted = true
                                        }
                                    )
                                }
                                needsAuth -> {
                                    AuthScreen(
                                        setupMode = true,
                                        onBack = {
                                            // No-op: account is required (Skip removed)
                                        },
                                        onAuthSuccess = { restored ->
                                            nav.setupAuthDone = true
                                            nav.signInRestored = restored
                                        }
                                    )
                                }
                                avatarHex == null && !nav.forceCreateNewMii && !nav.signInRestored -> {
                                    AvatarCreatorScreen(
                                        onAvatarSaved = {
                                            nav.forceCreateNewMii = false
                                        }
                                    )
                                }
                                userRegion.isNullOrBlank() && !nav.forceCreateNewMii && !nav.signInRestored -> {
                                    ProfileSetupScreen(
                                        onProfileSaved = { }
                                    )
                                }
                                else -> {
                                    // Shared Filament engine — hoisted above forceCreateNewMii
                                    // so it survives round-trips to the avatar creator.
                                    // Destroying + recreating the engine caused SIGSEGV in
                                    // libgltfio-jni.so due to disposal ordering races.
                                    val engine = io.github.sceneview.rememberEngine()
                                    val modelLoader = io.github.sceneview.rememberModelLoader(engine)

                                    if (nav.forceCreateNewMii) {
                                        AvatarCreatorScreen(
                                            onAvatarSaved = {
                                                nav.forceCreateNewMii = false
                                            }
                                        )
                                    } else {

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
                                            nav.screen = Screen.SpotPassInbox
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
                                        val isForced = false

                                        Dialog(
                                            onDismissRequest = {
                                                if (!isForced) showUpdateDialog = false
                                            },
                                            properties = DialogProperties(
                                                dismissOnBackPress = !isForced,
                                                dismissOnClickOutside = !isForced
                                            )
                                        ) {
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = OffWhite
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    // Green header bar
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(PocketPassGreen, PocketPassGreenDark)
                                                                ),
                                                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                                            )
                                                            .padding(horizontal = 20.dp, vertical = 16.dp)
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = if (isForced) "Required Update" else "New Update Available!",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 18.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "PocketPass v${update.versionName}",
                                                                color = Color.White.copy(alpha = 0.9f),
                                                                fontSize = 14.sp
                                                            )
                                                        }
                                                    }

                                                    // Content
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(20.dp)
                                                    ) {
                                                        if (update.changelog.isNotBlank()) {
                                                            Text(
                                                                text = "What's new:",
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = DarkText,
                                                                fontSize = 14.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Text(
                                                                text = update.changelog,
                                                                color = DarkText.copy(alpha = 0.7f),
                                                                fontSize = 13.sp,
                                                                lineHeight = 18.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                        }

                                                        if (isForced) {
                                                            Text(
                                                                text = "This update is required to continue using PocketPass.",
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = DarkText,
                                                                fontSize = 13.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                        }

                                                        // Buttons
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            if (!isForced) {
                                                                OutlinedButton(
                                                                    onClick = { showUpdateDialog = false },
                                                                    shape = RoundedCornerShape(8.dp)
                                                                ) {
                                                                    Text(
                                                                        "Later",
                                                                        color = DarkText.copy(alpha = 0.6f)
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                            }
                                                            Button(
                                                                onClick = {
                                                                    nav.pendingUpdate = update
                                                                    nav.screen = Screen.AppSettings
                                                                    showUpdateDialog = false
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = PocketPassGreen
                                                                )
                                                            ) {
                                                                Text(
                                                                    "Update Now",
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.White
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Dual screen support (Ayn Thor bottom screen)
                                    val secondaryDisplay = rememberSecondaryDisplay()
                                    val dualScreenEnabled by userPreferences.dualScreenModeFlow.collectAsState(initial = true)
                                    val isDualScreen = secondaryDisplay != null && dualScreenEnabled


                                    // Shared state for dual-screen plaza: selected Mii flows from bottom (3D) to top (companion)
                                    var plazaSelectedEncounter by remember { mutableStateOf<com.pocketpass.app.data.Encounter?>(null) }

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
                                            Column(modifier = Modifier
                                                .fillMaxSize()
                                                .background(Brush.verticalGradient(colors = BackgroundGradient))
                                            ) {
                                                // Nav bar on bottom screen (Thor)
                                                if (!nav.isOnSubScreen()) {
                                                    PlazaNavBar(
                                                        currentScreen = nav.currentMainScreen(),
                                                        onNavigate = { screen ->
                                                            nav.navigateToMainScreen(screen)
                                                        },
                                                        unreadMessageCount = unreadMsgCount,
                                                        unreadNotificationCount = unreadNotifCount,
                                                        onOpenNotifications = { nav.screen = Screen.Notifications }
                                                    )
                                                }

                                                // Tab-specific secondary content
                                                Box(modifier = Modifier.weight(1f)) {
                                                    when (nav.screen) {
                                                        Screen.Settings -> SettingsSecondaryScreenContent(
                                                            onCreateNewMii = {
                                                                nav.forceCreateNewMii = true
                                                                nav.screen = Screen.Plaza
                                                            },
                                                            onOpenProfileSettings = {
                                                                nav.screen = Screen.ProfileSettings
                                                            },
                                                            onOpenAppSettings = {
                                                                nav.screen = Screen.AppSettings
                                                            },
                                                            onOpenAuth = {
                                                                nav.screen = Screen.Auth
                                                            }
                                                        )
                                                        Screen.History -> HistorySecondaryScreen()
                                                        Screen.Messages, is Screen.Chat -> MessagesSecondaryScreen()
                                                        Screen.Friends, Screen.EncounterHistory -> FriendsSecondaryScreen()
                                                        Screen.PlazaOverview -> AnimatedPlazaScreen(
                                                            onBack = { nav.screen = Screen.Plaza },
                                                            sharedEngine = engine,
                                                            sharedModelLoader = modelLoader,
                                                            isDualScreen = true,
                                                            onMiiSelected = { plazaSelectedEncounter = it }
                                                        )
                                                        Screen.Statistics -> StatisticsSecondaryScreen()
                                                        Screen.WorldTourMap -> WorldTourSecondaryScreen()
                                                        Screen.Activities, Screen.Games, Screen.PuzzleSwap,
                                                        is Screen.PuzzleBoard, Screen.Shop, Screen.Leaderboard,
                                                        Screen.Bingo, Screen.SpotPassInbox -> GamesHubSecondaryScreen()
                                                        else -> PlazaSecondaryScreen()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // All navigation screens with persistent nav bar
                                    val isCompactScreen = appDimensions.isCompact
                                    val showTopNav = !nav.isOnSubScreen() && !isDualScreen && !isCompactScreen
                                    val showBottomNav = !nav.isOnSubScreen() && !isDualScreen && isCompactScreen
                                    // Measure nav bar height so content can pad below it
                                    var topNavHeightDp by remember { mutableStateOf(0.dp) }
                                    val density = androidx.compose.ui.platform.LocalDensity.current

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Top nav bar overlays content so rounded corners show through
                                        if (showTopNav) {
                                            Box(modifier = Modifier
                                                .zIndex(1f)
                                                .align(Alignment.TopCenter)
                                                .onSizeChanged { size ->
                                                    topNavHeightDp = with(density) { size.height.toDp() }
                                                }
                                            ) {
                                                PlazaNavBar(
                                                    currentScreen = nav.currentMainScreen(),
                                                    onNavigate = { screen ->
                                                        nav.navigateToMainScreen(screen)
                                                    },
                                                    unreadMessageCount = unreadMsgCount,
                                                    unreadNotificationCount = unreadNotifCount,
                                                    onOpenNotifications = { nav.screen = Screen.Notifications }
                                                )
                                            }
                                        }

                                        Box(modifier = Modifier
                                            .fillMaxSize()
                                            .then(if (showTopNav) Modifier.padding(top = topNavHeightDp) else Modifier)
                                        ) {
                                            // Background (doesn't animate)
                                            CheckeredBackground(
                                                modifier = Modifier.fillMaxSize(),
                                                gradientColors = BackgroundGradient
                                            )

                                            AnimatedContent(
                                                targetState = nav.screen,
                                                transitionSpec = {
                                                    val initial = nav.previousMainScreen
                                                    val target = nav.currentMainScreen()

                                                    if (initial == target) {
                                                        // Same tab — no transition
                                                        EnterTransition.None togetherWith ExitTransition.None
                                                    } else {
                                                        val slideRight = target.ordinal > initial.ordinal
                                                        val enter = slideInHorizontally(
                                                            initialOffsetX = { w -> if (slideRight) w / 4 else -w / 4 },
                                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                                        ) + fadeIn(tween(250))
                                                        val exit = slideOutHorizontally(
                                                            targetOffsetX = { w -> if (slideRight) -w / 4 else w / 4 },
                                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                                        ) + fadeOut(tween(250))
                                                        enter togetherWith exit using SizeTransform(clip = false)
                                                    }
                                                },
                                                label = "screen"
                                            ) { targetScreen ->
                                            when (targetScreen) {
                                                Screen.Auth -> {
                                                    AuthScreen(
                                                        onBack = { nav.screen = Screen.Settings },
                                                        onAuthSuccess = { _ -> nav.screen = Screen.Settings }
                                                    )
                                                }
                                                Screen.GameSearch -> {
                                                    GameSearchScreen(
                                                        onBack = { nav.screen = Screen.ProfileSettings }
                                                    )
                                                }
                                                Screen.ProfileSettings -> {
                                                    ProfileSettingsScreen(
                                                        onBack = { nav.screen = Screen.Settings },
                                                        onOpenGameSearch = { nav.screen = Screen.GameSearch }
                                                    )
                                                }
                                                Screen.Mii3DTest -> {
                                                    if (BuildConfig.DEBUG) {
                                                        Mii3DTestScreen(
                                                            onBack = { nav.screen = Screen.AppSettings },
                                                            avatarHex = avatarHex,
                                                            sharedEngine = engine,
                                                            sharedModelLoader = modelLoader
                                                        )
                                                    }
                                                }
                                                Screen.AppSettings -> {
                                                    val pending = remember { nav.pendingUpdate.also { nav.pendingUpdate = null } }
                                                    AppSettingsScreen(
                                                        onBack = { nav.screen = Screen.Settings },
                                                        onOpenMii3DTest = if (BuildConfig.DEBUG) { {
                                                            nav.screen = Screen.Mii3DTest
                                                        } } else { {} },
                                                        autoStartUpdate = pending
                                                    )
                                                }
                                                Screen.Settings -> {
                                                    SettingsScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onCreateNewMii = {
                                                            nav.forceCreateNewMii = true
                                                            nav.screen = Screen.Plaza
                                                        },
                                                        onOpenAppSettings = { nav.screen = Screen.AppSettings },
                                                        onOpenProfileSettings = { nav.screen = Screen.ProfileSettings },
                                                        onOpenAuth = { nav.screen = Screen.Auth },
                                                        onSignOut = {
                                                            nav.setupAuthDone = false
                                                            nav.signInRestored = false
                                                            nav.screen = Screen.Plaza
                                                        }
                                                    )
                                                }
                                                is Screen.Chat -> {
                                                    val chat = targetScreen as Screen.Chat
                                                    ChatScreen(
                                                        friendId = chat.friendId,
                                                        friendName = chat.friendName,
                                                        friendAvatarHex = chat.avatarHex,
                                                        onBack = { nav.screen = Screen.Messages }
                                                    )
                                                }
                                                Screen.Notifications -> {
                                                    NotificationsScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onNavigateToChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.screen = Screen.Chat(friendId, friendName, friendAvatarHex)
                                                        },
                                                        onNavigateToFriends = {
                                                            nav.navigateToMainScreen(NavigationState.MainScreen.FRIENDS)
                                                        }
                                                    )
                                                }
                                                Screen.EncounterHistory -> {
                                                    EncounterHistoryScreen(
                                                        onBack = { nav.screen = Screen.Friends }
                                                    )
                                                }
                                                Screen.Messages -> {
                                                    ConversationsScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onOpenChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.screen = Screen.Chat(friendId, friendName, friendAvatarHex)
                                                        }
                                                    )
                                                }
                                                Screen.History -> {
                                                    EncounterHistoryScreen(
                                                        onBack = { nav.screen = Screen.Plaza }
                                                    )
                                                }
                                                Screen.Statistics -> {
                                                    StatisticsScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onOpenWorldTourMap = { nav.screen = Screen.WorldTourMap }
                                                    )
                                                }
                                                Screen.WorldTourMap -> {
                                                    WorldTourMapScreen(
                                                        onBack = { nav.screen = Screen.Statistics },
                                                        isDualScreen = isDualScreen
                                                    )
                                                }
                                                Screen.Friends -> {
                                                    FriendsScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onOpenChat = { friendId, friendName, friendAvatarHex ->
                                                            nav.screen = Screen.Chat(friendId, friendName, friendAvatarHex)
                                                        },
                                                        onOpenHistory = { nav.screen = Screen.EncounterHistory }
                                                    )
                                                }
                                                is Screen.PuzzleBoard -> {
                                                    val board = targetScreen as Screen.PuzzleBoard
                                                    PuzzleBoardScreen(
                                                        panelId = board.panelId,
                                                        onBack = { nav.screen = Screen.PuzzleSwap }
                                                    )
                                                }
                                                Screen.Bingo -> {
                                                    MiiBingoScreen(
                                                        onBack = { nav.screen = Screen.Games }
                                                    )
                                                }
                                                Screen.Leaderboard -> {
                                                    LeaderboardScreen(
                                                        onBack = { nav.screen = Screen.Activities }
                                                    )
                                                }
                                                Screen.Shop -> {
                                                    ShopScreen(
                                                        onBack = { nav.screen = Screen.Activities }
                                                    )
                                                }
                                                Screen.PuzzleSwap -> {
                                                    PuzzleSwapScreen(
                                                        onBack = { nav.screen = Screen.Games },
                                                        onOpenPuzzleBoard = { panelId -> nav.screen = Screen.PuzzleBoard(panelId) }
                                                    )
                                                }
                                                Screen.Games -> {
                                                    GamesHubScreen(
                                                        onBack = { nav.screen = Screen.Activities },
                                                        onOpenPuzzleSwap = { nav.screen = Screen.PuzzleSwap },
                                                        onOpenBingo = { nav.screen = Screen.Bingo }
                                                    )
                                                }
                                                Screen.SpotPassInbox -> {
                                                    SpotPassInboxScreen(
                                                        onBack = { nav.screen = Screen.Plaza }
                                                    )
                                                }
                                                Screen.Activities -> {
                                                    ActivitiesScreen(
                                                        onBack = { nav.screen = Screen.Plaza },
                                                        onOpenGames = { nav.screen = Screen.Games },
                                                        onOpenShop = { nav.screen = Screen.Shop },
                                                        onOpenLeaderboard = { nav.screen = Screen.Leaderboard },
                                                        onOpenSpotPass = { nav.screen = Screen.SpotPassInbox }
                                                    )
                                                }
                                                Screen.PlazaOverview -> {
                                                    if (isDualScreen) {
                                                        AnimatedPlazaCompanionScreen(
                                                            selectedEncounter = plazaSelectedEncounter,
                                                            onBack = { nav.screen = Screen.Plaza }
                                                        )
                                                    } else {
                                                        AnimatedPlazaScreen(
                                                            onBack = { nav.screen = Screen.Plaza },
                                                            sharedEngine = engine,
                                                            sharedModelLoader = modelLoader
                                                        )
                                                    }
                                                }
                                                Screen.Plaza -> {
                                                    PlazaScreen(
                                                        onOpenSpotPass = { nav.screen = Screen.SpotPassInbox }
                                                    )
                                                }
                                            }
                                            } // AnimatedContent
                                        }

                                        // Bottom nav bar for compact/phone screens
                                        if (showBottomNav) {
                                            Box(modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .zIndex(1f)
                                            ) {
                                                PlazaNavBar(
                                                    currentScreen = nav.currentMainScreen(),
                                                    onNavigate = { screen ->
                                                        nav.navigateToMainScreen(screen)
                                                    },
                                                    unreadMessageCount = unreadMsgCount,
                                                    unreadNotificationCount = unreadNotifCount,
                                                    onOpenNotifications = { nav.screen = Screen.Notifications }
                                                )
                                            }
                                        }
                                    }
                                    } // end if/else forceCreateNewMii
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
