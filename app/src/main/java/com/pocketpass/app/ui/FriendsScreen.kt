package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.FriendRepository
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.SupabaseFriendship
import com.pocketpass.app.data.SupabaseProfile
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import com.pocketpass.app.util.gamepadFocusable
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    onOpenChat: (friendId: String, friendName: String, friendAvatarHex: String) -> Unit = { _, _, _ -> },
    onOpenHistory: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    val authRepo = remember { AuthRepository() }
    val isLoggedIn by authRepo.isLoggedIn.collectAsState(initial = false)
    val friendRepo = remember { FriendRepository() }

    var selectedEncounter by remember { mutableStateOf<Encounter?>(null) }
    var showFriendCodeDialog by remember { mutableStateOf(false) }

    // Friend IDs for badge display
    var acceptedFriendIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Pending incoming requests
    var pendingRequests by remember { mutableStateOf<List<SupabaseFriendship>>(emptyList()) }
    var pendingProfiles by remember { mutableStateOf<Map<String, SupabaseProfile>>(emptyMap()) }

    // Refresh counter — increment to force a data reload
    var refreshKey by remember { mutableStateOf(0) }

    // Shared refresh logic: fetch friends, pending requests, create missing encounters
    suspend fun refreshFriendData() = withContext(Dispatchers.IO) {
        acceptedFriendIds = friendRepo.getAcceptedFriendIds()
        pendingRequests = friendRepo.getPendingRequests()
        // Load profiles for pending request senders
        val profiles = mutableMapOf<String, SupabaseProfile>()
        for (req in pendingRequests) {
            friendRepo.getRequesterProfile(req.requesterId)?.let {
                profiles[req.requesterId] = it
            }
        }
        pendingProfiles = profiles

        // Create local encounters for accepted friends that don't have one yet
        for (friendId in acceptedFriendIds) {
            val existing = db.encounterDao().getEncounterByOtherUserId(friendId)
            if (existing != null) continue
            try {
                val profile = friendRepo.getRequesterProfile(friendId)
                if (profile != null) {
                    db.encounterDao().insertEncounter(Encounter(
                        encounterId = "friend_${friendId}",
                        timestamp = System.currentTimeMillis(),
                        otherUserAvatarHex = profile.avatarHex,
                        otherUserName = profile.userName,
                        otherUserId = friendId,
                        greeting = profile.greeting,
                        origin = profile.origin,
                        age = profile.age,
                        hobbies = profile.hobbies,
                        isMale = profile.isMale
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsScreen", "Failed to create encounter for $friendId", e)
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Load friend data on every screen entry + when refreshKey changes
    LaunchedEffect(isLoggedIn, refreshKey) {
        if (isLoggedIn) {
            refreshFriendData()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkText)
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "Friends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    val friendCount = if (isLoggedIn) {
                        encounters.count { it.otherUserId.isNotEmpty() && it.otherUserId in acceptedFriendIds }
                    } else 0
                    Text(
                        text = "$friendCount friend${if (friendCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumText
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Add Friend button
                if (isLoggedIn) {
                    val addFocus = remember { FocusRequester() }
                    Button(
                        onClick = { soundManager.playSelect(); showFriendCodeDialog = true },
                        modifier = Modifier.gamepadFocusable(
                            focusRequester = addFocus,
                            shape = RoundedCornerShape(12.dp),
                            onSelect = { soundManager.playSelect(); showFriendCodeDialog = true }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OffWhite),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", tint = PocketPassGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", style = MaterialTheme.typography.labelMedium, color = PocketPassGreen)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                // History button (moved from nav bar)
                val historyFocus = remember { FocusRequester() }
                Button(
                    onClick = { soundManager.playNavigate(); onOpenHistory() },
                    modifier = Modifier.gamepadFocusable(
                        focusRequester = historyFocus,
                        shape = RoundedCornerShape(12.dp),
                        onSelect = { soundManager.playNavigate(); onOpenHistory() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OffWhite),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.List, contentDescription = "History", tint = DarkText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History", style = MaterialTheme.typography.labelMedium, color = DarkText)
                }
            }

            // Pending Friend Requests Banner
            if (isLoggedIn && pendingRequests.isNotEmpty()) {
                PendingRequestsBanner(
                    requests = pendingRequests,
                    profiles = pendingProfiles,
                    encounters = encounters,
                    friendRepo = friendRepo,
                    onRequestHandled = {
                        coroutineScope.launch { refreshFriendData() }
                    }
                )
            }

            // Only show confirmed friends (encounters whose otherUserId is in acceptedFriendIds)
            val confirmedFriends = if (isLoggedIn) {
                encounters.filter { it.otherUserId.isNotEmpty() && it.otherUserId in acceptedFriendIds }
            } else {
                emptyList()
            }

            if (!isLoggedIn) {
                // Not logged in
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Friends Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sign in from Settings to add friends\nfrom your encounter history.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MediumText,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (confirmedFriends.isEmpty()) {
                // Logged in but no confirmed friends
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Friends Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add friends from your encounter history!\nTap on someone you've met to send a request.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MediumText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Grid of confirmed friends
                val friendDims = LocalAppDimensions.current
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = friendDims.contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(confirmedFriends.sortedByDescending { it.timestamp }, key = { it.encounterId }) { encounter ->
                        FriendCard(
                            encounter = encounter,
                            isFriend = true,
                            onClick = { soundManager.playSelect(); selectedEncounter = encounter }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
        } // AnimatedVisibility

        // Detail Dialog
        selectedEncounter?.let { encounter ->
            FriendDetailDialog(
                encounter = encounter,
                isLoggedIn = isLoggedIn,
                friendRepo = friendRepo,
                onDismiss = { selectedEncounter = null },
                onDelete = { encounterToDelete ->
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            db.encounterDao().deleteEncounter(encounterToDelete)
                        }
                    }
                },
                onOpenChat = { friendId, friendName, friendAvatarHex ->
                    selectedEncounter = null
                    onOpenChat(friendId, friendName, friendAvatarHex)
                },
                onFriendshipChanged = {
                    coroutineScope.launch { refreshFriendData() }
                }
            )
        }

        // Friend Code Dialog
        if (showFriendCodeDialog) {
            FriendCodeDialog(
                friendRepo = friendRepo,
                onDismiss = { showFriendCodeDialog = false },
                onRequestSent = {
                    showFriendCodeDialog = false
                    coroutineScope.launch { refreshFriendData() }
                }
            )
        }
    }
}

// ── Friend Code Dialog ──

@Composable
private fun FriendCodeDialog(
    friendRepo: FriendRepository,
    onDismiss: () -> Unit,
    onRequestSent: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var codeInput by remember { mutableStateOf("") }
    var myFriendCode by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Load my friend code
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            myFriendCode = friendRepo.getMyFriendCode()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = OffWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Friend Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(16.dp))

                // My friend code display
                Text(
                    text = "Your Friend Code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val displayCode = myFriendCode?.let {
                        "${it.substring(0, 4)}-${it.substring(4)}"
                    } ?: "Loading..."
                    Text(
                        text = displayCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PocketPassGreen,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(3f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            myFriendCode?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                soundManager.playSelect()
                                statusMessage = "Copied!"
                                isError = false
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, MediumText)
                    ) {
                        Text("Copy", style = MaterialTheme.typography.labelSmall, color = DarkText)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MediumText.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enter a friend's code
                Text(
                    text = "Add a Friend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { newVal ->
                        // Only allow digits, max 8
                        val digits = newVal.filter { it.isDigit() }.take(8)
                        codeInput = digits
                        statusMessage = null
                    },
                    label = { Text("8-digit friend code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status message
                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) ErrorText else GreenText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Send request button
                Button(
                    onClick = {
                        if (codeInput.length != 8) {
                            statusMessage = "Enter a full 8-digit code"
                            isError = true
                            return@Button
                        }
                        isLoading = true
                        statusMessage = null
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                friendRepo.sendFriendRequestByCode(codeInput)
                            }
                            isLoading = false
                            result.fold(
                                onSuccess = { profile ->
                                    soundManager.playSuccess()
                                    statusMessage = "Friend request sent to ${profile.userName}!"
                                    isError = false
                                    codeInput = ""
                                    onRequestSent()
                                },
                                onFailure = { error ->
                                    soundManager.playError()
                                    statusMessage = error.message ?: "Failed to send request"
                                    isError = true
                                }
                            )
                        }
                    },
                    enabled = codeInput.length == 8 && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Friend Request")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MediumText)
                ) {
                    Text("Close", color = DarkText)
                }
            }
        }
    }
}

// ── Pending Requests Banner ──

@Composable
private fun PendingRequestsBanner(
    requests: List<SupabaseFriendship>,
    profiles: Map<String, SupabaseProfile>,
    encounters: List<Encounter>,
    friendRepo: FriendRepository,
    onRequestHandled: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Friend Requests (${requests.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        for (request in requests) {
            val profile = profiles[request.requesterId]
            // Try to find matching encounter for avatar
            val matchingEncounter = encounters.find { it.otherUserId == request.requesterId }
            val displayName = profile?.userName ?: matchingEncounter?.otherUserName ?: "Unknown"
            val avatarHex = profile?.avatarHex ?: matchingEncounter?.otherUserAvatarHex ?: ""

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OffWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarHex.isNotEmpty()) {
                            MiiAvatarViewer(hexData = avatarHex)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Accept button
                    Button(
                        onClick = {
                            soundManager.playSuccess()
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    friendRepo.acceptFriendRequest(request.id)

                                    // Immediately create local encounter for this friend
                                    val friendId = request.requesterId
                                    val existing = db.encounterDao().getEncounterByOtherUserId(friendId)
                                    if (existing == null) {
                                        val friendProfile = profile ?: friendRepo.getRequesterProfile(friendId)
                                        if (friendProfile != null) {
                                            db.encounterDao().insertEncounter(Encounter(
                                                encounterId = "friend_${friendId}",
                                                timestamp = System.currentTimeMillis(),
                                                otherUserAvatarHex = friendProfile.avatarHex,
                                                otherUserName = friendProfile.userName,
                                                otherUserId = friendId,
                                                greeting = friendProfile.greeting,
                                                origin = friendProfile.origin,
                                                age = friendProfile.age,
                                                hobbies = friendProfile.hobbies,
                                                isMale = friendProfile.isMale
                                            ))
                                        }
                                    }
                                }
                                onRequestHandled()
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Decline button
                    OutlinedButton(
                        onClick = {
                            soundManager.playDelete()
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    friendRepo.rejectFriendRequest(request.id)
                                }
                                onRequestHandled()
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorText),
                        border = BorderStroke(1.dp, ErrorText),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("Decline", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Friend Card ──

@Composable
fun FriendCard(
    encounter: Encounter,
    isFriend: Boolean = false,
    onClick: () -> Unit
) {
    val dims = LocalAppDimensions.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(dims.friendCardHeight)
            .gamepadFocusable(
                shape = RoundedCornerShape(16.dp),
                onSelect = onClick
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (dims.isCompact) 8.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mii avatar
                Box(
                    modifier = Modifier
                        .size(dims.avatarMedium)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Name
                Text(
                    text = encounter.otherUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Location with flag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = RegionFlags.getFlagForRegion(encounter.origin),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = encounter.origin,
                        style = MaterialTheme.typography.labelSmall,
                        color = MediumText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meet count
                if (encounter.meetCount > 1) {
                    Text(
                        text = "Met ${encounter.meetCount}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = GreenText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Friend badge
            if (isFriend) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PocketPassGreen)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Friend",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Friend Detail Dialog ──

@Composable
fun FriendDetailDialog(
    encounter: Encounter,
    isLoggedIn: Boolean,
    friendRepo: FriendRepository,
    onDismiss: () -> Unit,
    onDelete: (Encounter) -> Unit,
    onFriendshipChanged: () -> Unit,
    onOpenChat: (friendId: String, friendName: String, friendAvatarHex: String) -> Unit = { _, _, _ -> }
) {
    val soundManager = LocalSoundManager.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalDarkMode.current
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }

    // Friendship state
    var friendship by remember { mutableStateOf<SupabaseFriendship?>(null) }
    var friendshipLoading by remember { mutableStateOf(false) }
    var friendshipChecked by remember { mutableStateOf(false) }

    val canShowFriendButton = isLoggedIn && encounter.otherUserId.isNotEmpty()
    val myUserId = friendRepo.currentUserId

    // Check friendship status on open
    LaunchedEffect(encounter.otherUserId) {
        if (canShowFriendButton) {
            friendshipLoading = true
            withContext(Dispatchers.IO) {
                friendship = friendRepo.getFriendshipWith(encounter.otherUserId)
            }
            friendshipLoading = false
            friendshipChecked = true
        }
    }

    val detailDims = LocalAppDimensions.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = OffWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(detailDims.contentPadding)
                    .then(
                        if (detailDims.isCompact) Modifier.verticalScroll(
                            rememberScrollState()
                        ) else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Mii
                Box(
                    modifier = Modifier
                        .size(detailDims.avatarLarge)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                Text(
                    text = encounter.otherUserName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Greeting
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) Color(0xFF333333) else Color(0xFFF0F0F0)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "\"${encounter.greeting}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkText,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Details
                if (encounter.age.isNotBlank()) {
                    DetailRow(icon = "🎂", label = "Age", value = encounter.age)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                DetailRow(icon = RegionFlags.getFlagForRegion(encounter.origin), label = "From", value = encounter.origin)

                if (encounter.meetCount > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(icon = "🤝", label = "Met", value = "${encounter.meetCount} times")
                }

                if (encounter.hobbies.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.pocketpass.app.ui.theme.HobbiesIcon(
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hobbies: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText
                        )
                        Text(
                            text = encounter.hobbies,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val dateStr = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT
                ).format(Date(encounter.timestamp))
                DetailRow(icon = "⏰", label = "Met", value = dateStr)

                // ── Friend Action Button ──
                if (canShowFriendButton && friendshipChecked) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val fs = friendship
                    when {
                        friendshipLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PocketPassGreen,
                                strokeWidth = 2.dp
                            )
                        }
                        // Accepted
                        fs != null && fs.status == "accepted" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "You're friends!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GreenText,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        soundManager.playTap()
                                        onDismiss()
                                        onOpenChat(encounter.otherUserId, encounter.otherUserName, encounter.otherUserAvatarHex)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                                ) {
                                    Text("Message", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        soundManager.playDelete()
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                friendRepo.removeFriend(fs.id)
                                                // Delete the local encounter so it doesn't get re-synced
                                                val friendId = encounter.otherUserId
                                                if (friendId.isNotEmpty()) {
                                                    val friendEnc = db.encounterDao().getEncounterByOtherUserId(friendId)
                                                    if (friendEnc != null) {
                                                        db.encounterDao().deleteEncounter(friendEnc)
                                                        // Soft-delete on Supabase so it doesn't get pulled back down
                                                        try {
                                                            SyncRepository(context).softDeleteEncounter(friendEnc.encounterId)
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            }
                                            friendship = null
                                            onDismiss()
                                            onFriendshipChanged()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ErrorText
                                    ),
                                    border = BorderStroke(1.dp, ErrorText)
                                ) {
                                    Text("Remove", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        // I sent a pending request
                        fs != null && fs.status == "pending" && fs.requesterId == myUserId -> {
                            Button(
                                onClick = { },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = if (isDark) Color(0xFF444444) else Color(0xFFE0E0E0),
                                    disabledContentColor = MediumText
                                )
                            ) {
                                Text("Request Sent", fontWeight = FontWeight.Bold)
                            }
                        }
                        // They sent me a pending request
                        fs != null && fs.status == "pending" && fs.addresseeId == myUserId -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        soundManager.playSuccess()
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                friendRepo.acceptFriendRequest(fs.id)
                                                friendship = friendRepo.getFriendshipWith(encounter.otherUserId)
                                            }
                                            onFriendshipChanged()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                                ) {
                                    Text("Accept", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        soundManager.playDelete()
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                friendRepo.rejectFriendRequest(fs.id)
                                            }
                                            friendship = null
                                            onFriendshipChanged()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ErrorText
                                    ),
                                    border = BorderStroke(1.dp, ErrorText)
                                ) {
                                    Text("Decline", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        // No friendship exists
                        else -> {
                            Button(
                                onClick = {
                                    soundManager.playSuccess()
                                    friendshipLoading = true
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            friendRepo.sendFriendRequest(encounter.otherUserId)
                                            friendship = friendRepo.getFriendshipWith(encounter.otherUserId)
                                        }
                                        friendshipLoading = false
                                        onFriendshipChanged()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                            ) {
                                Text("Add Friend", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delete / Close buttons
                // Hide "Delete" when already friends — the "Remove" button above handles full cleanup
                val isFriend = friendship?.status == "accepted"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isFriend) {
                        OutlinedButton(
                            onClick = {
                                soundManager.playDelete()
                                onDelete(encounter)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .gamepadFocusable(
                                    shape = RoundedCornerShape(24.dp),
                                    onSelect = { soundManager.playDelete(); onDelete(encounter); onDismiss() }
                                ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorText
                            ),
                            border = BorderStroke(1.dp, ErrorText)
                        ) {
                            Text("Delete")
                        }
                    }

                    Button(
                        onClick = { soundManager.playBack(); onDismiss() },
                        modifier = Modifier
                            .weight(1f)
                            .gamepadFocusable(
                                shape = RoundedCornerShape(24.dp),
                                onSelect = { soundManager.playBack(); onDismiss() }
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MediumText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
            modifier = Modifier.weight(1f)
        )
    }
}
