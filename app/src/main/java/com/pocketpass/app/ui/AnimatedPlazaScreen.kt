package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.FriendRepository
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.crypto.decryptFields
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.rendering.Plaza3DMiiManager
import com.pocketpass.app.rendering.PlazaEnvironmentLoader
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalGamepadState
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import com.pocketpass.app.util.gamepadFocusable
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Constants ──

private const val MAX_MIIS = 20

@Composable
fun AnimatedPlazaScreen(
    onBack: () -> Unit,
    sharedEngine: com.google.android.filament.Engine? = null,
    sharedModelLoader: io.github.sceneview.loaders.ModelLoader? = null,
    isDualScreen: Boolean = false,
    onMiiSelected: ((Encounter?) -> Unit)? = null
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val isDark = LocalDarkMode.current
    val coroutineScope = rememberCoroutineScope()

    val db = remember { PocketPassDatabase.getDatabase(context) }
    val rawEncounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val encounters = remember(rawEncounters) { rawEncounters.map { it.decryptFields() } }

    val userPreferences = remember { UserPreferences(context) }
    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
    val userAvatarHex by userPreferences.avatarHexFlow.collectAsState(initial = null)
    val userCostume by userPreferences.selectedCostumeFlow.collectAsState(initial = null)

    val friendRepo = remember { FriendRepository() }
    val authRepo = remember { AuthRepository() }
    val isLoggedIn = authRepo.currentUserId != null

    // Interaction state
    var selectedEncounter by remember { mutableStateOf<Encounter?>(null) }
    var showProfileDetail by remember { mutableStateOf(false) }
    var friendshipStatus by remember { mutableStateOf("none") }
    var friendshipId by remember { mutableStateOf<String?>(null) }
    var friendActionResult by remember { mutableStateOf<String?>(null) }
    var friendActionIsError by remember { mutableStateOf(false) }

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Scene dimensions for background
    var sceneWidthPx by remember { mutableFloatStateOf(1080f) }
    var sceneHeightPx by remember { mutableFloatStateOf(1920f) }


    // 3D scene engine — use shared engine from MainActivity to avoid recreating on tab switches
    val engine = sharedEngine ?: rememberEngine()
    val modelLoader = sharedModelLoader ?: rememberModelLoader(engine)
    val miiManager = remember {
        Plaza3DMiiManager(context, engine, modelLoader, coroutineScope)
    }
    val environmentLoader = remember {
        PlazaEnvironmentLoader(context, modelLoader, coroutineScope)
    }

    // Combined node list: environment nodes + Mii nodes
    val combinedNodes by remember {
        derivedStateOf { environmentLoader.nodes + miiManager.nodes }
    }

    // Load 3D environment models
    LaunchedEffect(Unit) {
        environmentLoader.loadEnvironment()
    }

    // Clean up 3D resources when leaving the screen
    DisposableEffect(miiManager, environmentLoader) {
        onDispose {
            miiManager.clear()
            environmentLoader.clear()
        }
    }

    // Sync encounters with 3D manager
    LaunchedEffect(encounters) {
        val subset = if (encounters.size > MAX_MIIS) encounters.shuffled().take(MAX_MIIS) else encounters
        miiManager.syncEncounters(subset, userAvatarHex)
    }

    // Add user's own Mii
    LaunchedEffect(userAvatarHex, userCostume) {
        if (!userAvatarHex.isNullOrBlank()) {
            miiManager.addUserMii(userAvatarHex!!, userCostume)
        }
    }

    // Lifecycle awareness — pause game loop when app is backgrounded or screen not visible
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Game loop — only runs when screen is visible and resumed
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        var lastTime = 0L
        while (isActive) {
            withFrameNanos { frameTime ->
                if (lastTime == 0L) { lastTime = frameTime; return@withFrameNanos }
                val dt = (frameTime - lastTime) / 1_000_000_000f
                lastTime = frameTime
                miiManager.updateFrame(dt)
            }
        }
    }

    val gamepadState = LocalGamepadState.current
    val cursorIndex by miiManager.selectedIndex
    val density = LocalDensity.current

    // Helper to handle Mii selection (both tap and gamepad)
    fun selectMii(encounter: Encounter) {
        soundManager.playSelect()
        miiManager.onMiiTapped(encounter)
        if (isDualScreen) {
            onMiiSelected?.invoke(encounter)
        } else {
            selectedEncounter = encounter
            showProfileDetail = false
            friendActionResult = null; friendActionIsError = false
        }
    }

    // Scene focus requester for d-pad input
    val sceneFocusRequester = remember { FocusRequester() }

    // ── UI ──

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F8F0))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backFocus = remember { FocusRequester() }
                    IconButton(
                        onClick = { soundManager.playBack(); onBack() },
                        modifier = Modifier.gamepadFocusable(
                            focusRequester = backFocus,
                            shape = CircleShape,
                            onSelect = { soundManager.playBack(); onBack() }
                        )
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = DarkText)
                    }
                    Text(
                        text = if (encounters.isEmpty()) "StreetPass Plaza" else "StreetPass Plaza \u2022 ${encounters.size.coerceAtMost(MAX_MIIS)} Miis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Scene + labels area with d-pad navigation
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            sceneWidthPx = size.width.toFloat()
                            sceneHeightPx = size.height.toFloat()
                        }
                        .focusRequester(sceneFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val keyCode = keyEvent.nativeKeyEvent.keyCode
                            when (keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_UP,
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val enc = miiManager.moveCursor(keyCode)
                                    if (enc != null) {
                                        soundManager.playNavigate()
                                        if (isDualScreen) onMiiSelected?.invoke(enc)
                                    }
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_BUTTON_A,
                                AndroidKeyEvent.KEYCODE_ENTER,
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                                    val enc = miiManager.confirmSelection()
                                    if (enc != null) selectMii(enc)
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                                    if (cursorIndex >= 0) {
                                        miiManager.clearSelection()
                                        if (isDualScreen) onMiiSelected?.invoke(null)
                                        true
                                    } else {
                                        soundManager.playBack()
                                        onBack()
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                        .focusable()
                ) {
                    // Request focus on the scene so d-pad works
                    LaunchedEffect(Unit) {
                        sceneFocusRequester.requestFocus()
                    }

                    // 3D SceneView (opaque, renders its own sky background)
                    Plaza3DScene(
                        encounters = encounters.take(MAX_MIIS),
                        userAvatarHex = userAvatarHex,
                        userCostume = userCostume,
                        miiManager = miiManager,
                        engine = engine,
                        modelLoader = modelLoader,
                        childNodes = combinedNodes,
                        isDark = isDark,
                        onMiiTapped = { encounter ->
                            selectMii(encounter)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Empty state overlay
                    if (encounters.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Your plaza is empty!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Meet people nearby to populate your plaza",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Selector square overlay for gamepad cursor
                    if (cursorIndex >= 0) {
                        val nonUserMiis = miiManager.getNonUserMiiStates()
                        if (cursorIndex < nonUserMiis.size) {
                            val mii = nonUserMiis[cursorIndex]
                            val aspectRatio = if (sceneHeightPx > 0f) sceneWidthPx / sceneHeightPx else 1f
                            val (screenX, screenY) = projectWorldToScreen(
                                mii.positionX, Plaza3DMiiManager.MII_CENTER_Y, mii.positionZ, aspectRatio
                            )
                            // Convert normalized screen coords to pixel offset
                            val selectorSizeDp = 64.dp
                            val selectorSizePx = with(density) { selectorSizeDp.toPx() }
                            val offsetX = (screenX * sceneWidthPx - selectorSizePx / 2f).toInt()
                            val offsetY = (screenY * sceneHeightPx - selectorSizePx / 2f).toInt()

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(offsetX, offsetY) }
                                    .size(selectorSizeDp)
                                    .border(3.dp, Color(0xFFFFD700), RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    // Tap-to-select: map screen tap to normalized coords → nearest Mii via 2D projection
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { /* handled by pointerInput below */ }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val normX = offset.x / sceneWidthPx
                                    val normY = offset.y / sceneHeightPx
                                    val aspectRatio = if (sceneHeightPx > 0f) sceneWidthPx / sceneHeightPx else 1f

                                    val tappedEncounter = miiManager.findMiiNearScreenPosition(
                                        normX, normY, aspectRatio, tolerance = 0.12f
                                    )
                                    if (tappedEncounter != null) {
                                        selectMii(tappedEncounter)
                                    }
                                }
                            }
                    )
                } // end scene + labels Box
            } // end Column
        } // end AnimatedVisibility

        // ── Interaction Dialog (only on non-dual-screen; dual-screen shows detail on companion) ──
        if (!isDualScreen && selectedEncounter != null && !showProfileDetail) {
            LaunchedEffect(selectedEncounter?.otherUserId) {
                friendshipStatus = "checking"
                friendshipId = null
                if (!isLoggedIn || selectedEncounter?.otherUserId.isNullOrBlank()) {
                    friendshipStatus = "none"
                    return@LaunchedEffect
                }
                try {
                    val existing = withContext(Dispatchers.IO) {
                        friendRepo.getFriendshipWith(selectedEncounter!!.otherUserId)
                    }
                    if (existing != null) {
                        friendshipId = existing.id
                        friendshipStatus = when {
                            existing.status == "accepted" -> "accepted"
                            existing.requesterId == authRepo.currentUserId -> "pending_sent"
                            else -> "pending_received"
                        }
                    } else {
                        friendshipStatus = "none"
                    }
                } catch (e: Exception) {
                    friendshipStatus = "none"
                }
            }

            AlertDialog(
                onDismissRequest = { selectedEncounter = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isDark) Color(0xFF1A2A3A) else Color(0xFFE3F2FD),
                                            if (isDark) Color(0xFF1A3A3A) else Color(0xFFBBDEFB)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = selectedEncounter!!.otherUserAvatarHex)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                selectedEncounter!!.otherUserName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                val flag = RegionFlags.getFlagForRegion(selectedEncounter!!.origin)
                                Text(
                                    "$flag ${selectedEncounter!!.origin}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                                if (selectedEncounter!!.meetCount > 1) {
                                    Text(
                                        " \u2022 Met ${selectedEncounter!!.meetCount}x",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenText
                                    )
                                }
                            }
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                soundManager.playNavigate()
                                showProfileDetail = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Person, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View Profile")
                        }

                        val friendButtonText = when (friendshipStatus) {
                            "accepted" -> "Already Friends"
                            "pending_sent" -> "Request Sent"
                            "pending_received" -> "Accept Request"
                            "checking" -> "Checking..."
                            else -> "Add Friend"
                        }
                        val friendButtonEnabled = friendshipStatus in listOf("none", "pending_received")

                        AeroButton(
                            onClick = onClick@{
                                if (!isLoggedIn) {
                                    soundManager.playError()
                                    friendActionResult = "Sign in to add friends. Go to Settings to create an account."
                                    friendActionIsError = true
                                    return@onClick
                                }
                                val enc = selectedEncounter ?: return@onClick
                                if (enc.otherUserId.isBlank()) {
                                    soundManager.playError()
                                    friendActionResult = "This person doesn't have an account yet."
                                    friendActionIsError = true
                                    return@onClick
                                }
                                coroutineScope.launch {
                                    when (friendshipStatus) {
                                        "none" -> {
                                            val result = friendRepo.sendFriendRequest(enc.otherUserId)
                                            result.onSuccess {
                                                soundManager.playSuccess()
                                                friendshipStatus = "pending_sent"
                                                friendActionResult = "Friend request sent!"
                                                friendActionIsError = false
                                            }.onFailure { e ->
                                                soundManager.playError()
                                                friendActionResult = e.message ?: "Failed"
                                                friendActionIsError = true
                                            }
                                        }
                                        "pending_received" -> {
                                            val fId = friendshipId ?: return@launch
                                            val result = friendRepo.acceptFriendRequest(fId)
                                            result.onSuccess {
                                                soundManager.playSuccess()
                                                friendshipStatus = "accepted"
                                                friendActionResult = "Friend request accepted!"
                                                friendActionIsError = false
                                            }.onFailure { e ->
                                                soundManager.playError()
                                                friendActionResult = e.message ?: "Failed"
                                                friendActionIsError = true
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            enabled = if (!isLoggedIn) true else friendButtonEnabled,
                            containerColor = if (!isLoggedIn) Color(0xFFFF9800) else PocketPassGreen
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (!isLoggedIn) "Sign in to add friends" else friendButtonText)
                        }

                        if (friendActionResult != null) {
                            Text(
                                text = friendActionResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (friendActionIsError) ErrorText else GreenText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { soundManager.playBack(); selectedEncounter = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // ── Profile Detail Dialog ──
        if (!isDualScreen && selectedEncounter != null && showProfileDetail) {
            AlertDialog(
                onDismissRequest = { showProfileDetail = false },
                title = { Text(selectedEncounter!!.otherUserName, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            if (isDark) Color(0xFF1A2A3A) else Color(0xFFE3F2FD),
                                            if (isDark) Color(0xFF1A3A3A) else Color(0xFFBBDEFB)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = selectedEncounter!!.otherUserAvatarHex)
                        }
                        Spacer(Modifier.height(16.dp))

                        if (selectedEncounter!!.greeting.isNotBlank()) {
                            AeroCard(
                                cornerRadius = 12.dp,
                                containerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
                            ) {
                                Text(
                                    text = "\"${selectedEncounter!!.greeting}\"",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = DarkText,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        val flag = RegionFlags.getFlagForRegion(selectedEncounter!!.origin)
                        ProfileDetailRow("From", "$flag ${selectedEncounter!!.origin}")
                        if (selectedEncounter!!.age.isNotBlank()) ProfileDetailRow("Age", selectedEncounter!!.age)
                        if (selectedEncounter!!.hobbies.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("Hobbies", style = MaterialTheme.typography.bodySmall, color = MediumText, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                com.pocketpass.app.ui.theme.HobbyChips(selectedEncounter!!.hobbies, modifier = Modifier.weight(1f))
                            }
                        }
                        ProfileDetailRow("Met", "${selectedEncounter!!.meetCount} time${if (selectedEncounter!!.meetCount != 1) "s" else ""}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { soundManager.playBack(); showProfileDetail = false }) {
                        Text("Back")
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MediumText, fontWeight = FontWeight.SemiBold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = DarkText)
    }
}

/**
 * Companion screen for the dual-screen plaza (shown on the top screen of Ayn Thor).
 * Shows selected Mii's greeting card and profile info, or a visitor list when nothing is selected.
 */
@Composable
fun AnimatedPlazaCompanionScreen(
    selectedEncounter: Encounter?,
    onBack: () -> Unit
) {
    val isDark = LocalDarkMode.current
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val rawEncounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val encounters = remember(rawEncounters) { rawEncounters.map { it.decryptFields() } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F8F0))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "StreetPass Plaza",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                modifier = Modifier.padding(start = 8.dp)
            )
            Text(
                text = "${encounters.size} Miis",
                style = MaterialTheme.typography.bodySmall,
                color = MediumText,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        if (selectedEncounter != null) {
            // ── Selected Mii greeting card ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDark) Color(0xFF1A2A3A) else Color(0xFFE8F5E9),
                                if (isDark) Color(0xFF0A1628) else Color(0xFFC8E6C9)
                            )
                        )
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (isDark) Color(0xFF1A2A3A) else Color(0xFFE3F2FD),
                                    if (isDark) Color(0xFF1A3A3A) else Color(0xFFBBDEFB)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MiiAvatarViewer(hexData = selectedEncounter.otherUserAvatarHex)
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = selectedEncounter.otherUserName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else DarkText
                )

                val flag = RegionFlags.getFlagForRegion(selectedEncounter.origin)
                Text(
                    text = "$flag ${selectedEncounter.origin}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumText
                )

                if (selectedEncounter.meetCount > 1) {
                    Text(
                        text = "Met ${selectedEncounter.meetCount} times",
                        style = MaterialTheme.typography.bodySmall,
                        color = GreenText
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Greeting card
                if (selectedEncounter.greeting.isNotBlank()) {
                    AeroCard(
                        cornerRadius = 16.dp,
                        containerColor = if (isDark) Color(0xFF2A2A2A) else Color.White
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Greeting",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MediumText
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "\"${selectedEncounter.greeting}\"",
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = if (isDark) Color.White else DarkText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Profile details
                AeroCard(
                    cornerRadius = 16.dp,
                    containerColor = if (isDark) Color(0xFF2A2A2A) else Color.White
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProfileDetailRow("From", "$flag ${selectedEncounter.origin}")
                        if (selectedEncounter.age.isNotBlank()) ProfileDetailRow("Age", selectedEncounter.age)
                        if (selectedEncounter.hobbies.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("Hobbies", style = MaterialTheme.typography.bodySmall, color = MediumText, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(8.dp))
                                com.pocketpass.app.ui.theme.HobbyChips(selectedEncounter.hobbies, modifier = Modifier.weight(1f))
                            }
                        }
                        ProfileDetailRow("Met", "${selectedEncounter.meetCount} time${if (selectedEncounter.meetCount != 1) "s" else ""}")
                    }
                }
            }
        } else {
            // ── No Mii selected — show visitor list ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDark) Color(0xFF1A2A3A) else Color(0xFFE8F5E9),
                                if (isDark) Color(0xFF0A1628) else Color(0xFFC8E6C9)
                            )
                        )
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select a Piip with the D-Pad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else DarkText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Press A to view their greeting card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumText
                )
            }
        }
    }
}

