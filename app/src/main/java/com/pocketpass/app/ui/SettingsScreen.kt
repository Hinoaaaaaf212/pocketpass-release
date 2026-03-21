package com.pocketpass.app.ui

import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.pocketpass.app.R
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import com.pocketpass.app.ui.theme.AeroButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.LocalUserPreferences
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.PocketPassGreenDark
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.ui.theme.DangerRed
import com.pocketpass.app.ui.theme.AvatarGradientTop
import com.pocketpass.app.ui.theme.AvatarGradientBottom
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.ShopItems
import com.pocketpass.app.data.SyncRepository
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCreateNewMii: () -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onOpenProfileSettings: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = LocalUserPreferences.current
    val soundManager = LocalSoundManager.current

    val savedMiis by userPreferences.savedMiisFlow.collectAsState(initial = emptyList())
    val activeMiiHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val userName by userPreferences.userNameFlow.collectAsState(initial = "")
    val miiCount by userPreferences.getMiiCount().collectAsState(initial = 0)

    val userGreeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val userMood by userPreferences.userMoodFlow.collectAsState(initial = "\uD83D\uDE0A")
    val userCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")

    var customGreeting by remember { mutableStateOf(userGreeting) }

    val maxMiis = 3
    val canCreateNewMii = miiCount < maxMiis

    // Dual-screen detection
    // Managed in MainActivity
    val displayManager = remember {
        context.getSystemService(android.content.Context.DISPLAY_SERVICE) as? DisplayManager
    }
    val secondaryDisplay = remember {
        displayManager?.displays?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }
    val dualScreenEnabled by userPreferences.dualScreenModeFlow.collectAsState(initial = true)
    val isDualScreen = secondaryDisplay != null && dualScreenEnabled

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (isDualScreen) {
                // ── Dual-screen layout ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Avatar preview
                    val settingsDims = LocalAppDimensions.current
                    if (!activeMiiHex.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(settingsDims.avatarLarge)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            AvatarGradientTop,
                                            AvatarGradientBottom
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = activeMiiHex ?: "")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Profile card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(OffWhite)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (userName.isNullOrBlank()) "Profile" else "Profile: $userName",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                        }
                    }
                }
            } else {
                // ── Single-screen layout ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        SettingsProfileCard(
                            userName = userName,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenProfileSettings = onOpenProfileSettings,
                            onOpenAuth = onOpenAuth,
                            onSignOut = onSignOut,
                            soundManager = soundManager
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        SettingsMiisHeader(miiCount = miiCount, maxMiis = maxMiis)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(savedMiis, key = { it }) { miiHex ->
                        SettingsMiiRow(
                            miiHex = miiHex,
                            isActive = miiHex == activeMiiHex,
                            onSetActive = { soundManager.playSelect(); coroutineScope.launch { userPreferences.setActiveMii(miiHex); try { SyncRepository(context).syncProfile() } catch (_: Exception) { } } },
                            onDelete = { soundManager.playDelete(); coroutineScope.launch { userPreferences.deleteMii(miiHex) } }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsCreateMiiButton(
                            canCreateNewMii = canCreateNewMii,
                            onCreateNewMii = onCreateNewMii,
                            soundManager = soundManager
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    item {
                        SettingsCreditsSection()
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

// ── Shared sections ──

@Composable
private fun SettingsProfileCard(
    userName: String?,
    onOpenAppSettings: () -> Unit,
    onOpenProfileSettings: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onSignOut: () -> Unit = {},
    soundManager: com.pocketpass.app.util.SoundManager
) {
    val authRepo = remember { AuthRepository() }
    val isLoggedIn by authRepo.isLoggedIn.collectAsState(initial = false)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val syncRepo = remember { SyncRepository(context) }
    val userPreferences = LocalUserPreferences.current
    var syncStatus by remember { mutableStateOf("idle") } // idle, syncing, synced, failed
    var showSignOutConfirm by remember { mutableStateOf(false) }

    // Auto-sync on open
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            syncStatus = "syncing"
            try {
                // NonCancellable — finish even if user navigates away
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    syncRepo.fullSync()
                }
                syncStatus = "synced"
            } catch (_: Exception) {
                syncStatus = "failed"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(OffWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (userName.isNullOrBlank()) "Profile" else "Profile: $userName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Auth status
            if (isLoggedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (syncStatus) {
                        "syncing" -> {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = PocketPassGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Syncing to cloud...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        "synced" -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = GreenText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Synced to cloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = GreenText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        "failed" -> {
                            Text(
                                text = "Sync failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        else -> {
                            Text(
                                text = "Signed in",
                                style = MaterialTheme.typography.bodySmall,
                                color = GreenText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            AeroButton(
                onClick = { soundManager.playNavigate(); onOpenProfileSettings() },
                modifier = Modifier.fillMaxWidth(),
                contentColor = OffWhite
            ) {
                Text("Profile Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            AeroButton(
                onClick = { soundManager.playNavigate(); onOpenAppSettings() },
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF4A4A4A) else Color(0xFF6B6B6B),
                contentColor = OffWhite
            ) {
                Text("App Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(8.dp))
                AeroButton(
                    onClick = {
                        soundManager.playTap()
                        showSignOutConfirm = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = DangerRed,
                    contentColor = OffWhite
                ) {
                    Text("Sign Out", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showSignOutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out? All local data (profile, encounters, messages, tokens, and settings) will be removed from this device.") },
            confirmButton = {
                AeroButton(
                    onClick = {
                        showSignOutConfirm = false
                        coroutineScope.launch {
                            authRepo.signOut()
                            // Clear all local data
                            userPreferences.clearAll()
                            val db = PocketPassDatabase.getDatabase(context)
                            db.clearAllTables()
                            onSignOut()
                        }
                    },
                    containerColor = DangerRed,
                    contentColor = OffWhite
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showSignOutConfirm = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCustomizationContent(
    userPreferences: UserPreferences,
    userGreeting: String,
    customGreeting: String,
    onCustomGreetingChange: (String) -> Unit,
    userMood: String,
    userCardStyle: String,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    soundManager: com.pocketpass.app.util.SoundManager,
    onOpenGameSearch: () -> Unit = {}
) {
    Column {
        // Customization
        Text(
            text = "Card Customization",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Greeting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OffWhite)
                .padding(16.dp)
        ) {
            var greetingSaved by remember { mutableStateOf(false) }

            LaunchedEffect(greetingSaved) {
                if (greetingSaved) {
                    kotlinx.coroutines.delay(2000)
                    greetingSaved = false
                }
            }

            Column {
                Text(
                    text = "Greeting Message",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                val presetGreetings = listOf(
                    "Hello! Nice to meet you!",
                    "Hi there! Let's be friends!",
                    "Greetings, traveler!",
                    "Hey! What's up?",
                    "Nice to see you!",
                    "Let's hang out sometime!"
                )

                presetGreetings.chunked(2).forEach { rowGreetings ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowGreetings.forEach { greeting ->
                            AeroButton(
                                onClick = {
                                    soundManager.playTap()
                                    onCustomGreetingChange(greeting)
                                    coroutineScope.launch {
                                        userPreferences.saveGreeting(greeting)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                containerColor = if (userGreeting == greeting) PocketPassGreen else if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF4A4A4A) else Color(0xFF6B6B6B)
                            ) {
                                Text(
                                    text = greeting.take(15) + if (greeting.length > 15) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OffWhite
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = customGreeting,
                    onValueChange = onCustomGreetingChange,
                    label = { Text("Custom Greeting") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                AeroButton(
                    onClick = {
                        soundManager.playSuccess()
                        coroutineScope.launch {
                            userPreferences.saveGreeting(customGreeting)
                        }
                        greetingSaved = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (greetingSaved) "Saved!" else "Save Custom Greeting")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mood
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OffWhite)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Mood",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                val moods = listOf(
                    MoodType.HAPPY to "Happy",
                    MoodType.EXCITED to "Excited",
                    MoodType.COOL to "Cool",
                    MoodType.FRIENDLY to "Friendly",
                    MoodType.SHY to "Shy",
                    MoodType.SLEEPY to "Sleepy",
                    MoodType.PARTY to "Party",
                    MoodType.THOUGHTFUL to "Think",
                    MoodType.PEACEFUL to "Calm",
                    MoodType.CHEERFUL to "Cheer"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    moods.take(5).forEach { (moodType, label) ->
                        MoodItem(
                            moodType = moodType,
                            label = label,
                            isSelected = userMood == moodType.name,
                            onClick = {
                                soundManager.playSelect()
                                coroutineScope.launch { userPreferences.saveMood(moodType.name) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    moods.drop(5).forEach { (moodType, label) ->
                        MoodItem(
                            moodType = moodType,
                            label = label,
                            isSelected = userMood == moodType.name,
                            onClick = {
                                soundManager.playSelect()
                                coroutineScope.launch { userPreferences.saveMood(moodType.name) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Me
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OffWhite)
                .padding(16.dp)
        ) {
            val currentAge by userPreferences.userAgeFlow.collectAsState(initial = null)
            val currentHobbies by userPreferences.userHobbiesFlow.collectAsState(initial = null)
            var ageText by remember(currentAge) { mutableStateOf(currentAge ?: "") }
            var hobbiesText by remember(currentHobbies) { mutableStateOf(currentHobbies ?: "") }
            var showSaved by remember { mutableStateOf(false) }

            LaunchedEffect(showSaved) {
                if (showSaved) {
                    kotlinx.coroutines.delay(2000)
                    showSaved = false
                }
            }

            Column {
                Text(
                    text = "About Me",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { newValue ->
                        val digits = newValue.filter { it.isDigit() }
                        val num = digits.toIntOrNull()
                        if (digits.isEmpty() || (num != null && num in 1..99)) {
                            ageText = digits
                        }
                    },
                    label = { Text("Age (1-99)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = hobbiesText,
                    onValueChange = { hobbiesText = com.pocketpass.app.ui.theme.formatHobbiesInput(it) },
                    label = { Text("Hobbies (separate with spaces)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (hobbiesText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    com.pocketpass.app.ui.theme.HobbyChips(hobbiesText)
                }

                Spacer(modifier = Modifier.height(8.dp))

                AeroButton(
                    onClick = {
                        soundManager.playSuccess()
                        coroutineScope.launch {
                            userPreferences.saveAge(ageText)
                            userPreferences.saveHobbies(hobbiesText)
                        }
                        showSaved = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showSaved) "Saved!" else "Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card styles (owned only)
        val ownedItems by userPreferences.ownedShopItemsFlow.collectAsState(initial = emptySet())
        val ownedThemes = ShopItems.cardThemes.filter { it.id in ownedItems }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OffWhite)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Card Style",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ownedThemes.forEach { theme ->
                        val isSelected = userCardStyle == theme.id
                        val gradientColors = theme.previewColors?.map { Color(it.toInt()) }
                            ?: listOf(PocketPassGreen, PocketPassGreen)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(gradientColors))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, PocketPassGreen, RoundedCornerShape(12.dp))
                                    else Modifier.border(1.dp, DarkText.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                )
                                .clickable {
                                    soundManager.playSelect()
                                    coroutineScope.launch { userPreferences.saveCardStyle(theme.id) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme.icon,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // ── Favourite Games ──
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Favourite Games",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Spacer(modifier = Modifier.height(8.dp))

        val selectedGames by userPreferences.selectedGamesFlow.collectAsState(initial = emptyList())

        if (selectedGames.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedGames.forEach { game ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 60.dp, height = 85.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverUrl = game.coverUrl("t_cover_small")
                            if (coverUrl != null) {
                                coil.compose.AsyncImage(
                                    model = coverUrl,
                                    contentDescription = game.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("\uD83C\uDFAE", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MediumText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                soundManager.playDelete()
                                coroutineScope.launch {
                                    userPreferences.saveSelectedGames(
                                        selectedGames.filter { it.id != game.id }
                                    )
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove",
                                tint = MediumText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedGames.size < 3) {
            AeroButton(
                onClick = { soundManager.playNavigate(); onOpenGameSearch() },
                modifier = Modifier.fillMaxWidth(),
                contentColor = OffWhite
            ) {
                Text(
                    if (selectedGames.isEmpty()) "Add Games" else "Add Another Game",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MoodItem(
    moodType: MoodType,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) PocketPassGreen.copy(alpha = 0.3f)
                    else Color(0xFFF0F0F0)
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) PocketPassGreen else DarkText.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            MoodIcon(mood = moodType, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MediumText
        )
    }
}

@Composable
private fun SettingsMiisHeader(miiCount: Int, maxMiis: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "My Miis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Text(
            text = "$miiCount / $maxMiis",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (miiCount >= maxMiis) ErrorText else DarkText
        )
    }
}

@Composable
private fun SettingsMiiRow(
    miiHex: String,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    val isCorrupted = miiHex.length < 50 || miiHex == "010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

    android.util.Log.d("SettingsScreen", "Mii hex length: ${miiHex.length}, First 20: ${miiHex.take(20)}")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isCorrupted -> Color(0xFFFFCDD2)
                    isActive -> PocketPassGreen.copy(alpha = 0.2f)
                    else -> OffWhite
                }
            )
            .defaultMinSize(minHeight = 100.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSetActive),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                AvatarGradientTop,
                                AvatarGradientBottom
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                MiiAvatarViewer(hexData = miiHex)
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column {
                if (isCorrupted) {
                    Text(
                        text = "Corrupted Data",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = ErrorText
                    )
                    Text(
                        text = "Length: ${miiHex.length} chars",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText
                    )
                } else if (isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Active",
                            tint = PocketPassGreen
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenText
                        )
                    }
                } else {
                    Text(
                        text = "Tap to use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText
                    )
                }
            }
        }

        IconButton(onClick = { onDelete() }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete Mii",
                tint = ErrorText
            )
        }
    }
}

@Composable
private fun SettingsCreateMiiButton(
    canCreateNewMii: Boolean,
    onCreateNewMii: () -> Unit,
    soundManager: com.pocketpass.app.util.SoundManager
) {
    AeroButton(
        onClick = { if (canCreateNewMii) { soundManager.playNavigate(); onCreateNewMii() } },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = canCreateNewMii,
        containerColor = if (canCreateNewMii) {
            PocketPassGreenDark
        } else {
            if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF3A3A3A) else Color(0xFFAAAAAA)
        },
        contentColor = OffWhite
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (canCreateNewMii) "Create New Piip" else "Max Piips Reached (3/3)",
            fontWeight = FontWeight.Bold
        )
    }

    if (!canCreateNewMii) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Delete a Piip to create a new one",
            style = MaterialTheme.typography.bodySmall,
            color = MediumText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsCreditsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OffWhite)
            .padding(16.dp)
    ) {
        Text(
            text = "Credits",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Piip Creator",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Text(
            text = "The Piip editor used in this app was made by the following people:",
            style = MaterialTheme.typography.bodySmall,
            color = MediumText
        )

        Spacer(modifier = Modifier.height(8.dp))

        CreditEntry("datkat21", "Creator and lead developer", imageRes = R.drawable.credit_datkat21)
        CreditEntry("ariankordi", "Piip rendering API and contributions", imageRes = R.drawable.credit_ariankordi)
        CreditEntry("Timiimiimii", "Contributions")

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Piip Plaza 3D Assets",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        CreditEntry("Takachi", "3D plaza models", assetPath = "credits_takachi.png")
    }
}

@Composable
private fun CreditEntry(name: String, role: String, imageRes: Int? = null, assetPath: String? = null) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (assetPath != null) {
            val bitmap = remember(assetPath) {
                context.assets.open(assetPath).use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(PocketPassGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().uppercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OffWhite
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MediumText
            )
        }
    }
}
