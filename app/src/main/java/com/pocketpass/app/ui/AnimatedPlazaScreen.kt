package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.FriendRepository
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.rendering.Plaza3DMiiManager
import com.pocketpass.app.rendering.PlazaAnimState
import com.pocketpass.app.rendering.PlazaEnvironmentLoader
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.PocketPassGreen
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

private const val MAX_MIIS = 10

@Composable
fun AnimatedPlazaScreen(
    onBack: () -> Unit,
    sharedEngine: com.google.android.filament.Engine? = null,
    sharedModelLoader: io.github.sceneview.loaders.ModelLoader? = null
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val isDark = LocalDarkMode.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())

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

    // Frame tick to drive recomposition of name label overlays
    var frameTick by remember { mutableIntStateOf(0) }

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

    // Game loop — update 3D Mii positions each frame
    LaunchedEffect(Unit) {
        var lastTime = 0L
        var frameCount = 0
        while (isActive) {
            withFrameNanos { frameTime ->
                if (lastTime == 0L) { lastTime = frameTime; return@withFrameNanos }
                val dt = (frameTime - lastTime) / 1_000_000_000f
                lastTime = frameTime
                miiManager.updateFrame(dt)
                // Throttle label recomposition to ~30fps (every 2nd frame at 60fps)
                frameCount++
                if (frameCount % 2 == 0) frameTick++
            }
        }
    }

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
                        text = if (encounters.isEmpty()) "Roaming Plaza" else "Roaming Plaza \u2022 ${encounters.size.coerceAtMost(MAX_MIIS)} Miis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(48.dp))
                }

                // Scene + labels area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            sceneWidthPx = size.width.toFloat()
                            sceneHeightPx = size.height.toFloat()
                        }
                ) {
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
                            soundManager.playSelect()
                            selectedEncounter = encounter
                            showProfileDetail = false
                            friendActionResult = null; friendActionIsError = false
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Empty state overlay text
                    if (encounters.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Your plaza is empty!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Meet people to populate your plaza",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Name labels + tap targets — projected to Mii positions
                    @Suppress("UNUSED_EXPRESSION") frameTick
                    val miiStates = miiManager.getMiiStates()
                    val aspectRatio = if (sceneHeightPx > 0f) sceneWidthPx / sceneHeightPx else 1f
                    for (mii in miiStates) {
                        if (mii.modelNode == null) continue

                        // Use fixed Z=0 for X projection — Z variance is tiny (±0.5) and
                        // would cause horizontal jitter through perspective math
                        val screenX = projectWorldToScreenX(mii.positionX, 0f, aspectRatio) * sceneWidthPx

                        // Project Mii head position to screen Y (above the head)
                        val headWorldY = 1.4f
                        val screenYFraction = projectWorldToScreenY(headWorldY, mii.positionZ)
                        val labelY = sceneHeightPx * screenYFraction

                        // Tap target covering the Mii body area (below the label)
                        if (!mii.isUser) {
                            val bodyYFraction = projectWorldToScreenY(0f, mii.positionZ)
                            val tapTopY = sceneHeightPx * screenYFraction
                            val tapBottomY = sceneHeightPx * bodyYFraction
                            val tapHeight = (tapBottomY - tapTopY).coerceAtLeast(80f)
                            val tapWidth = 80f

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(
                                            start = with(density) { (screenX - tapWidth / 2).coerceAtLeast(0f).toDp() },
                                            top = with(density) { tapTopY.toDp() }
                                        )
                                        .size(
                                            width = with(density) { tapWidth.toDp() },
                                            height = with(density) { tapHeight.toDp() }
                                        )
                                        .clickable {
                                            soundManager.playSelect()
                                            val encounter = miiManager.onMiiTapped(mii.encounter)
                                            if (encounter != null) {
                                                selectedEncounter = encounter
                                                showProfileDetail = false
                                                friendActionResult = null; friendActionIsError = false
                                            }
                                        }
                                )
                            }
                        }

                        // Name label
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = with(density) { (screenX - 30f).coerceAtLeast(0f).toDp() },
                                        top = with(density) { labelY.toDp() }
                                    )
                                    .then(
                                        if (!mii.isUser) Modifier.clickable {
                                            soundManager.playSelect()
                                            val encounter = miiManager.onMiiTapped(mii.encounter)
                                            if (encounter != null) {
                                                selectedEncounter = encounter
                                                showProfileDetail = false
                                                friendActionResult = null; friendActionIsError = false
                                            }
                                        } else Modifier
                                    )
                            ) {
                                // Speech bubble when greeting
                                if (mii.animState == PlazaAnimState.GREETING && mii.encounter.greeting.isNotBlank()) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isDark) Color(0xFF2A2A2A) else Color.White
                                        ),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Text(
                                            text = mii.encounter.greeting.take(30) + if (mii.encounter.greeting.length > 30) "\u2026" else "",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontStyle = FontStyle.Italic,
                                            color = DarkText,
                                            maxLines = 1,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                }

                                // Name tag
                                val labelText = if (mii.isUser) (userName ?: "You") else mii.encounter.otherUserName
                                val labelColor = if (mii.isUser) PocketPassGreen else (if (isDark) Color(0xFF2A2A2A) else Color(0xCC000000))
                                Card(
                                    shape = RoundedCornerShape(6.dp),
                                    colors = CardDefaults.cardColors(containerColor = labelColor)
                                ) {
                                    Text(
                                        text = labelText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                } // end scene + labels Box
            } // end Column
        } // end AnimatedVisibility

        // ── Interaction Dialog ──
        if (selectedEncounter != null && !showProfileDetail) {
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

                        Button(
                            onClick = {
                                if (!isLoggedIn) {
                                    soundManager.playError()
                                    friendActionResult = "Sign in to add friends. Go to Settings to create an account."
                                    friendActionIsError = true
                                    return@Button
                                }
                                val enc = selectedEncounter ?: return@Button
                                if (enc.otherUserId.isBlank()) {
                                    soundManager.playError()
                                    friendActionResult = "This person doesn't have an account yet."
                                    friendActionIsError = true
                                    return@Button
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
                            shape = RoundedCornerShape(12.dp),
                            enabled = if (!isLoggedIn) true else friendButtonEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isLoggedIn) Color(0xFFFF9800) else PocketPassGreen,
                                contentColor = Color.White
                            )
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
        if (selectedEncounter != null && showProfileDetail) {
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
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF0F0F0))
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
                        if (selectedEncounter!!.hobbies.isNotBlank()) ProfileDetailRow("Hobbies", selectedEncounter!!.hobbies)
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

