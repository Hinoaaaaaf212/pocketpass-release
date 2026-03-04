package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.CheckerDark
import com.pocketpass.app.ui.theme.CheckerLight
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.util.RegionFlags
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Nintendo-style checkered background pattern
 * Similar to the pattern seen in 3DS Miiverse and StreetPass Plaza
 */
@Composable
fun CheckeredBackground(
    modifier: Modifier = Modifier,
    gradientColors: List<androidx.compose.ui.graphics.Color>
) {
    Box(modifier = modifier) {
        // Base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = gradientColors))
        )

        // Checkered pattern overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val checkerSize = 24f  // Size of each diamond/checker
            val rows = (size.height / checkerSize).toInt() + 2
            val cols = (size.width / checkerSize).toInt() + 2

            for (row in 0..rows) {
                for (col in 0..cols) {
                    val x = col * checkerSize
                    val y = row * checkerSize

                    // Offset every other row for diamond pattern
                    val offsetX = if (row % 2 == 0) 0f else checkerSize / 2

                    // Draw diamond shape
                    val path = Path().apply {
                        moveTo(x + offsetX, y)  // Top
                        lineTo(x + offsetX + checkerSize / 2, y + checkerSize / 2)  // Right
                        lineTo(x + offsetX, y + checkerSize)  // Bottom
                        lineTo(x + offsetX - checkerSize / 2, y + checkerSize / 2)  // Left
                        close()
                    }

                    // Alternate colors for checkerboard effect
                    val color = if ((row + col) % 2 == 0) CheckerLight else CheckerDark
                    drawPath(path, color)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlazaScreen(
    onOpenSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenStatistics: () -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encounters by db.encounterDao().getAllEncountersFlow().collectAsState(initial = emptyList())
    val userPreferences = remember { UserPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val myHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val myName by userPreferences.userNameFlow.collectAsState(initial = "Stranger")
    val myAge by userPreferences.userAgeFlow.collectAsState(initial = "")
    val myHobbies by userPreferences.userHobbiesFlow.collectAsState(initial = "")
    val myOrigin by userPreferences.userOriginFlow.collectAsState(initial = "Unknown")
    val myGreeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val myMood by userPreferences.userMoodFlow.collectAsState(initial = "HAPPY")
    val myCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")

    // Debug mode state
    var showDebugDialog by remember { mutableStateOf(false) }
    var isAddingEncounter by remember { mutableStateOf(false) }

    // New encounter animation state
    var showNewEncounterAnimation by remember { mutableStateOf(false) }
    var newEncounter by remember { mutableStateOf<Encounter?>(null) }
    var previousEncounterCount by remember { mutableStateOf(0) }

    // Detect new encounters
    LaunchedEffect(encounters.size) {
        if (encounters.size > previousEncounterCount && previousEncounterCount > 0) {
            // New encounter detected!
            newEncounter = encounters.maxByOrNull { it.timestamp }
            showNewEncounterAnimation = true

            // Auto-dismiss after animation (reduced time for less lag)
            kotlinx.coroutines.delay(3500)
            showNewEncounterAnimation = false
        }
        previousEncounterCount = encounters.size
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Nintendo-style checkered gradient background
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

        // Main content column
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - History button
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "Encounter History",
                        tint = DarkText,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center - Logo/Title
                Text(
                    text = "PocketPass Plaza",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                // Right side - Favorites, Stats and Settings
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenFavorites) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorites",
                            tint = androidx.compose.ui.graphics.Color(0xFFFFC107), // Gold star
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onOpenStatistics) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "Statistics",
                            tint = DarkText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = DarkText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Content area (always show user's profile first, then encounters)
            Box(modifier = Modifier.weight(1f)) {
                // Create user's profile as an encounter
                val myEncounter = remember(myHex, myName, myGreeting, myOrigin, myAge, myHobbies) {
                    if (!myHex.isNullOrBlank()) {
                        Encounter(
                            encounterId = "self",
                            timestamp = System.currentTimeMillis(),
                            otherUserAvatarHex = myHex ?: "",
                            otherUserName = myName ?: "Me",
                            greeting = myGreeting,
                            origin = myOrigin ?: "Unknown",
                            age = myAge ?: "",
                            hobbies = myHobbies ?: ""
                        )
                    } else null
                }

                if (myEncounter == null) {
                    // No profile set up yet
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No profile found",
                            style = MaterialTheme.typography.titleLarge,
                            color = DarkText,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please set up your profile in Settings",
                            style = MaterialTheme.typography.bodyLarge,
                            color = DarkText
                        )
                    }
                } else {
                    // Always include user's profile as first page, followed by encounters
                    val allCards = remember(myEncounter, encounters) {
                        listOf(myEncounter) + encounters
                    }

                    val pagerState = rememberPagerState(pageCount = { allCards.size })

                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            if (page == 0) {
                                // User's own profile
                                PlazaCard(encounter = myEncounter, mood = myMood, cardStyle = myCardStyle, isSelf = true)
                            } else {
                                // Other encounters
                                val encounter = encounters[page - 1]
                                PlazaCard(encounter = encounter, mood = "HAPPY", cardStyle = "classic")
                            }
                        }

                        // Page indicator (optional, shows which page you're on)
                        if (allCards.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (pagerState.currentPage == 0) {
                                        "My Profile"
                                    } else {
                                        "Friend ${pagerState.currentPage} of ${encounters.size}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Debug FAB (bottom right) - overlay on top
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showDebugDialog = true },
                containerColor = androidx.compose.ui.graphics.Color(0xFFFF6B9D),
                contentColor = OffWhite
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Debug: Add Test Encounter",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Debug Dialog
        if (showDebugDialog) {
            DebugAddEncounterDialog(
                onDismiss = { showDebugDialog = false },
                isLoading = isAddingEncounter,
                onAddEncounter = { encounter ->
                    coroutineScope.launch {
                        isAddingEncounter = true
                        try {
                            withContext(Dispatchers.IO) {
                                // Check if we've met this person before
                                val existingEncounter = db.encounterDao().getEncounterByUserName(encounter.otherUserName)

                                if (existingEncounter != null) {
                                    // Update the existing encounter with incremented meet count and new timestamp
                                    val updatedEncounter = existingEncounter.copy(
                                        timestamp = System.currentTimeMillis(),
                                        meetCount = existingEncounter.meetCount + 1
                                    )
                                    db.encounterDao().updateEncounter(updatedEncounter)
                                } else {
                                    // New encounter
                                    db.encounterDao().insertEncounter(encounter)
                                }
                            }
                            showDebugDialog = false
                        } catch (e: Exception) {
                            android.util.Log.e("PlazaScreen", "Error adding encounter: ${e.message}", e)
                        } finally {
                            isAddingEncounter = false
                        }
                    }
                }
            )
        }

        // Loading overlay when adding encounter
        if (isAddingEncounter) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = PocketPassGreen
                )
            }
        }

        // New Encounter Animation Overlay
        AnimatedVisibility(
            visible = showNewEncounterAnimation && newEncounter != null,
            enter = fadeIn(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(
                animationSpec = tween(500, easing = FastOutSlowInEasing)
            )
        ) {
            NewEncounterAnimation(
                encounter = newEncounter!!,
                onDismiss = { showNewEncounterAnimation = false }
            )
        }
    }
}

@Composable
fun NewEncounterAnimation(
    encounter: Encounter,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(100f) }
    val rotation = remember { Animatable(-10f) }

    // Pulse animation for celebration text (slower for better performance)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val textScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textPulse"
    )

    LaunchedEffect(Unit) {
        // Animate in - smooth spring with bounce
        launch {
            scale.animateTo(
                targetValue = 1.1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            // Subtle scale down to 1.0 for settle effect
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = EaseInOutCubic)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // Wait (reduced for better performance)
        delay(2200)

        // Animate out (minimize to favorites) - smooth and coordinated
        launch {
            scale.animateTo(
                targetValue = 0.2f,
                animationSpec = tween(600, easing = EaseInOutCubic)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = -600f,
                animationSpec = tween(600, easing = EaseInOutCubic)
            )
        }
        launch {
            rotation.animateTo(
                targetValue = -15f,
                animationSpec = tween(600, easing = EaseInOutCubic)
            )
        }

        delay(100)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f * alpha.value))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    translationY = offsetY.value
                    rotationZ = rotation.value
                    // Enable hardware acceleration for smoother animations
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
        ) {
            // Celebration text with pulse animation
            Text(
                text = "✨ New Friend! ✨",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFFFFC107),
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                    }
            )

            // Simplified encounter card for better performance
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.85f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = OffWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mii Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color(0xFFE3F2FD),
                                        androidx.compose.ui.graphics.Color(0xFFBBDEFB)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                    }

                    // Info
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = encounter.otherUserName,
                            style = MaterialTheme.typography.titleLarge,
                            color = DarkText,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = RegionFlags.getFlagForRegion(encounter.origin),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = encounter.origin,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediumText
                            )
                        }
                    }
                }
            }

            // Hint text with delayed fade-in
            androidx.compose.animation.AnimatedVisibility(
                visible = alpha.value > 0.8f,
                enter = fadeIn(
                    animationSpec = tween(600, easing = FastOutSlowInEasing)
                )
            ) {
                Text(
                    text = "Added to Favorites!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OffWhite,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }

        // Confetti particles (delayed to reduce initial lag)
        if (alpha.value > 0.7f) {
            ConfettiEffect(visible = true)
        }
    }
}

@Composable
fun ConfettiEffect(visible: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")

    // Create multiple confetti pieces with smooth animations (reduced count for performance)
    repeat(12) { index ->
        val offsetX = remember { (-200..200).random().toFloat() }
        val horizontalSway = remember { (-20..20).random().toFloat() }
        val duration = remember { (2000..2800).random() }

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation$index"
        )

        val offsetY by infiniteTransition.animateFloat(
            initialValue = -100f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fall$index"
        )

        // Add horizontal sway for more natural motion (simplified)
        val sway by infiniteTransition.animateFloat(
            initialValue = -horizontalSway,
            targetValue = horizontalSway,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sway$index"
        )

        val colors = listOf(
            androidx.compose.ui.graphics.Color(0xFFFFC107),
            androidx.compose.ui.graphics.Color(0xFF7BC63B),
            androidx.compose.ui.graphics.Color(0xFFFF6B9D),
            androidx.compose.ui.graphics.Color(0xFF2196F3),
            androidx.compose.ui.graphics.Color(0xFFFF5722)
        )

        Box(
            modifier = Modifier
                .offset(x = (offsetX + sway).dp, y = offsetY.dp)
                .size(10.dp)
                .graphicsLayer {
                    rotationZ = rotation
                }
                .background(
                    color = colors[index % colors.size],
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
fun PlazaCard(encounter: Encounter, mood: String = "HAPPY", cardStyle: String = "classic", isSelf: Boolean = false) {
    // Card border color based on style
    val borderBrush = when (cardStyle) {
        "gradient" -> Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFFFFC107),
                androidx.compose.ui.graphics.Color(0xFFFFEB3B)
            )
        )
        "cool" -> Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFF2196F3),
                androidx.compose.ui.graphics.Color(0xFF03A9F4)
            )
        )
        "warm" -> Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFFFF5722),
                androidx.compose.ui.graphics.Color(0xFFFF9800)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(PocketPassGreen, SkyBlue)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Single unified StreetPass-style card with enhanced contrast
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .background(borderBrush)
                    .padding(4.dp)  // Thinner border like Nintendo UI
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OffWhite)
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    // Header: Name + "Met via PocketPass"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = encounter.otherUserName,
                                style = MaterialTheme.typography.titleLarge,
                                color = DarkText,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSelf) "My Profile" else "Met via PocketPass",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Custom mood icon in top right
                        if (mood.isNotBlank()) {
                            val moodType = try {
                                MoodType.valueOf(mood)
                            } catch (e: Exception) {
                                MoodType.HAPPY
                            }
                            MoodIcon(mood = moodType, modifier = Modifier.size(56.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main content: Mii on left, info on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Mii Avatar (left side) - Larger and more prominent
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color(0xFFE3F2FD),
                                            androidx.compose.ui.graphics.Color(0xFFBBDEFB)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = encounter.otherUserAvatarHex)
                        }

                        // Info section (right side)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Greeting bubble with better contrast
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (cardStyle) {
                                            "gradient" -> androidx.compose.ui.graphics.Color(0xFFFFF8E1)  // Warmer yellow
                                            "cool" -> androidx.compose.ui.graphics.Color(0xFFE3F2FD)  // Lighter blue
                                            "warm" -> androidx.compose.ui.graphics.Color(0xFFFFECB3)  // Lighter orange
                                            else -> androidx.compose.ui.graphics.Color(0xFFF0F0F0)  // Slightly darker gray for contrast
                                        }
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "\"${encounter.greeting}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DarkText,
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }

                            // Time info
                            if (!isSelf) {
                                val encounterCal = Calendar.getInstance().apply {
                                    timeInMillis = encounter.timestamp
                                }
                                val todayCal = Calendar.getInstance()

                                val isToday = encounterCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                        encounterCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                                val timeAgo = if (isToday) {
                                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(encounter.timestamp))
                                    "Today at $timeStr"
                                } else {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(encounter.timestamp))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⏰",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediumText
                                    )
                                }
                            }

                            // Age
                            if (encounter.age.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🎂",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = encounter.age,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DarkText
                                    )
                                }
                            }

                            // Location with flag
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = RegionFlags.getFlagForRegion(encounter.origin),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = encounter.origin,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }

                            // Meet count
                            if (!isSelf && encounter.meetCount > 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🤝",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Met ${encounter.meetCount} times",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PocketPassGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Bottom section: Hobbies with better contrast
                    if (encounter.hobbies.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFFF0F0F0))  // Solid color for better contrast
                                .padding(12.dp)
                        ) {
                            Row {
                                Text(
                                    text = "💭 ",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = encounter.hobbies,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebugAddEncounterDialog(
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    onAddEncounter: (Encounter) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var hobbies by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("Hello! Nice to meet you!") }
    var origin by remember { mutableStateOf("Debug Location") }
    var miiHex by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🐛 Debug: Add Test Encounter",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., 25") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = hobbies,
                        onValueChange = { hobbies = it },
                        label = { Text("Hobbies") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Gaming, Music") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        label = { Text("Greeting *") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Hello! Nice to meet you!") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = it },
                        label = { Text("Location/Origin") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g., United Kingdom") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = miiHex,
                        onValueChange = { miiHex = it },
                        label = { Text("Mii Hex Data (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Leave blank for default Mii") },
                        maxLines = 3,
                        supportingText = {
                            Text(
                                text = "To use a custom Mii: Create one in the Mii Creator, then copy the hex from your saved Miis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediumText
                            )
                        }
                    )
                }
                item {
                    Text(
                        text = "Quick Fill Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MediumText
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                name = "Link"
                                age = "17"
                                hobbies = "Adventuring, Sword Fighting"
                                greeting = "It's dangerous to go alone!"
                                origin = "Hyrule"
                                miiHex = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Link", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                name = "Mario"
                                age = "26"
                                hobbies = "Plumbing, Jumping"
                                greeting = "Wahoo! Let's-a go!"
                                origin = "Mushroom Kingdom"
                                miiHex = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mario", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                name = "Samus"
                                age = "Unknown"
                                hobbies = "Bounty Hunting, Exploration"
                                greeting = "The mission is complete."
                                origin = "Planet Zebes"
                                miiHex = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Samus", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                name = "Kirby"
                                age = "???"
                                hobbies = "Eating, Copying Abilities"
                                greeting = "Poyo! ⭐"
                                origin = "Dream Land"
                                miiHex = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kirby", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && greeting.isNotBlank() && !isLoading) {
                        val encounter = Encounter(
                            encounterId = "debug_${System.currentTimeMillis()}",
                            timestamp = System.currentTimeMillis(),
                            otherUserName = name,
                            otherUserAvatarHex = miiHex,
                            greeting = greeting,
                            origin = origin,
                            age = age,
                            hobbies = hobbies
                        )
                        onAddEncounter(encounter)
                    }
                },
                enabled = name.isNotBlank() && greeting.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = OffWhite
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isLoading) "Adding..." else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MiiAvatarViewer(hexData: String) {
    val context = LocalContext.current

    // Log hex data for debugging
    LaunchedEffect(hexData) {
        android.util.Log.d("MiiAvatarViewer", "Hex data: ${hexData.take(50)}... (length: ${hexData.length})")
    }

    // Use state for connectivity to avoid blocking
    var isConnected by remember { mutableStateOf(true) }

    // Check connectivity asynchronously
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                isConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                if (!isConnected) {
                    android.util.Log.e("MiiAvatarViewer", "❌ NO INTERNET CONNECTION!")
                }
            } catch (e: Exception) {
                android.util.Log.e("MiiAvatarViewer", "Error checking connectivity: ${e.message}")
                isConnected = false
            }
        }
    }

    // Handle empty hex data
    if (hexData.isBlank()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "👤",
                style = MaterialTheme.typography.displayLarge
            )
        }
        return
    }

    // Build URL with verification DISABLED to bypass BIRTH_PLATFORM checks
    val renderUrl = remember(hexData) {
        val url = "https://mii-unsecure.ariankordi.net/miis/image.png?" +
                "type=face&" +
                "width=1024&" +
                "verifyCharInfo=0&" +
                "instanceCount=1&" +
                "bgColor=00000000&" +
                "data=${java.net.URLEncoder.encode(hexData, "UTF-8")}"
        android.util.Log.d("MiiAvatarViewer", "Loading Mii from URL: $url")
        url
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            coil.compose.SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(renderUrl)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .listener(
                        onError = { _, result ->
                            android.util.Log.e("MiiAvatarViewer", "Failed to load Mii image: ${result.throwable.message}", result.throwable)
                        },
                        onSuccess = { _, _ ->
                            android.util.Log.d("MiiAvatarViewer", "✅ Successfully loaded Mii image")
                        }
                    )
                    .build(),
                contentDescription = "Mii Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = PocketPassGreen,
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👤",
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Internet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkText,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Connect to WiFi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediumText
                )
            }
        }
    }
}
