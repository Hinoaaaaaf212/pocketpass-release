package com.pocketpass.app.ui

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.ui.theme.PocketPassTheme
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCreateNewMii: () -> Unit,
    onOpenQrExchange: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
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

    // Detect secondary display (Ayn Thor bottom screen)
    val displayManager = remember {
        context.getSystemService(android.content.Context.DISPLAY_SERVICE) as? DisplayManager
    }
    val secondaryDisplay = remember {
        displayManager?.displays?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }
    val isDualScreen = secondaryDisplay != null

    // (transition handled by AnimatedContent in MainActivity)

    // Manage secondary display Presentation (Ayn Thor bottom screen)
    if (isDualScreen && secondaryDisplay != null) {
        val activity = context as ComponentActivity
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(Unit) {
            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setContent {
                    PocketPassTheme {
                        SettingsSecondaryScreen(
                            onCreateNewMii = onCreateNewMii,
                            soundManager = soundManager
                        )
                    }
                }
            }
            val presentation = Presentation(activity, secondaryDisplay)
            presentation.setContentView(composeView)
            presentation.show()

            // Dismiss when app goes to background, re-show when back
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (presentation.isShowing) presentation.dismiss()
                    }
                    Lifecycle.Event.ON_START -> {
                        if (!presentation.isShowing) presentation.show()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (presentation.isShowing) presentation.dismiss()
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        ) {
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
                // ── Dual-screen: Primary (top) screen shows profile card only ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Large Mii avatar preview
                    if (!activeMiiHex.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFE3F2FD),
                                            Color(0xFFBBDEFB)
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
                                text = "Profile: $userName",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { soundManager.playNavigate(); onOpenQrExchange() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PocketPassGreen,
                                    contentColor = OffWhite
                                )
                            ) {
                                Text("Share QR Code", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { soundManager.playNavigate(); onOpenAppSettings() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkText.copy(alpha = 0.7f),
                                    contentColor = OffWhite
                                )
                            ) {
                                Text("App Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Customization options on bottom screen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumText,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // ── Single-screen: Everything in one LazyColumn (normal devices) ──
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        SettingsProfileCard(
                            userName = userName,
                            onOpenQrExchange = onOpenQrExchange,
                            onOpenAppSettings = onOpenAppSettings,
                            soundManager = soundManager
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        SettingsCustomizationContent(
                            userPreferences = userPreferences,
                            userGreeting = userGreeting,
                            customGreeting = customGreeting,
                            onCustomGreetingChange = { customGreeting = it },
                            userMood = userMood,
                            userCardStyle = userCardStyle,
                            coroutineScope = coroutineScope,
                            soundManager = soundManager
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        SettingsMiisHeader(miiCount = miiCount, maxMiis = maxMiis)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(savedMiis) { miiHex ->
                        SettingsMiiRow(
                            miiHex = miiHex,
                            isActive = miiHex == activeMiiHex,
                            onSetActive = { soundManager.playSelect(); coroutineScope.launch { userPreferences.setActiveMii(miiHex) } },
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
                }
            }
        }
        } // AnimatedVisibility
    }
}

// ── Content shown on the secondary (bottom) display on Ayn Thor ──

@Composable
private fun SettingsSecondaryScreen(
    onCreateNewMii: () -> Unit,
    soundManager: com.pocketpass.app.util.SoundManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }

    val userGreeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val userMood by userPreferences.userMoodFlow.collectAsState(initial = "\uD83D\uDE0A")
    val userCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")
    val savedMiis by userPreferences.savedMiisFlow.collectAsState(initial = emptyList())
    val activeMiiHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val miiCount by userPreferences.getMiiCount().collectAsState(initial = 0)

    var customGreeting by remember { mutableStateOf(userGreeting) }
    val maxMiis = 3
    val canCreateNewMii = miiCount < maxMiis

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))

                SettingsCustomizationContent(
                    userPreferences = userPreferences,
                    userGreeting = userGreeting,
                    customGreeting = customGreeting,
                    onCustomGreetingChange = { customGreeting = it },
                    userMood = userMood,
                    userCardStyle = userCardStyle,
                    coroutineScope = coroutineScope,
                    soundManager = soundManager
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SettingsMiisHeader(miiCount = miiCount, maxMiis = maxMiis)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(savedMiis) { miiHex ->
                SettingsMiiRow(
                    miiHex = miiHex,
                    isActive = miiHex == activeMiiHex,
                    onSetActive = { soundManager.playSelect(); coroutineScope.launch { userPreferences.setActiveMii(miiHex) } },
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
        }
    }
}

// ── Shared composable sections used by both single-screen and dual-screen layouts ──

@Composable
private fun SettingsProfileCard(
    userName: String?,
    onOpenQrExchange: () -> Unit,
    onOpenAppSettings: () -> Unit,
    soundManager: com.pocketpass.app.util.SoundManager
) {
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
                text = "Profile: $userName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { soundManager.playNavigate(); onOpenQrExchange() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketPassGreen,
                    contentColor = OffWhite
                )
            ) {
                Text("Share QR Code", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { soundManager.playNavigate(); onOpenAppSettings() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkText.copy(alpha = 0.7f),
                    contentColor = OffWhite
                )
            ) {
                Text("App Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
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
    soundManager: com.pocketpass.app.util.SoundManager
) {
    Column {
        // Customization header
        Text(
            text = "Card Customization",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Greeting Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OffWhite)
                .padding(16.dp)
        ) {
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
                            Button(
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userGreeting == greeting) PocketPassGreen else DarkText.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(12.dp)
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
                Button(
                    onClick = {
                        soundManager.playSuccess()
                        coroutineScope.launch {
                            userPreferences.saveGreeting(customGreeting)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                ) {
                    Text("Save Custom Greeting")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mood Selector
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

        // Card Style Selector
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

                val cardStyles = listOf(
                    "classic" to "Classic",
                    "gradient" to "Sunny",
                    "cool" to "Cool",
                    "warm" to "Warm"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cardStyles.forEach { (styleId, styleName) ->
                        Button(
                            onClick = {
                                soundManager.playSelect()
                                coroutineScope.launch { userPreferences.saveCardStyle(styleId) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userCardStyle == styleId) PocketPassGreen else DarkText.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(styleName, color = OffWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
            color = if (miiCount >= maxMiis) Color(0xFFE57373) else DarkText
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
                                Color(0xFFE3F2FD),
                                Color(0xFFBBDEFB)
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
                        color = Color(0xFFD32F2F)
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
                            color = PocketPassGreen
                        )
                    }
                } else {
                    Text(
                        text = "Tap to use",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediumText
                    )
                    Text(
                        text = "Data: ${miiHex.length} chars",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightText
                    )
                }
            }
        }

        IconButton(onClick = { onDelete() }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete Mii",
                tint = Color(0xFFE57373)
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
    Button(
        onClick = { if (canCreateNewMii) { soundManager.playNavigate(); onCreateNewMii() } },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = canCreateNewMii,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (canCreateNewMii) DarkText.copy(alpha = 0.7f) else DarkText.copy(alpha = 0.3f),
            contentColor = OffWhite,
            disabledContainerColor = DarkText.copy(alpha = 0.3f),
            disabledContentColor = OffWhite.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (canCreateNewMii) "Create New Mii" else "Max Miis Reached (3/3)",
            fontWeight = FontWeight.Bold
        )
    }

    if (!canCreateNewMii) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Delete a Mii to create a new one",
            style = MaterialTheme.typography.bodySmall,
            color = MediumText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
