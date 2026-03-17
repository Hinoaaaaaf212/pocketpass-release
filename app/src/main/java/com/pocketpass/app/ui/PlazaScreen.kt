package com.pocketpass.app.ui

import android.util.Log
import com.pocketpass.app.BuildConfig
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.crypto.decryptFields
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.CheckerDark
import com.pocketpass.app.ui.theme.CheckerLight
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.NavGlass
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.RadialGlow
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.NavigationState
import com.pocketpass.app.util.gamepadFocusable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState

/**
 * Nintendo-style checkered background pattern
 * Similar to the pattern seen in 3DS Miiverse and StreetPass Plaza.
 * Uses a tiled ImageBitmap shader for a single draw call instead of 600+ paths.
 */
@Composable
fun CheckeredBackground(
    modifier: Modifier = Modifier,
    gradientColors: List<androidx.compose.ui.graphics.Color>
) {
    val checkerLightColor = CheckerLight
    val checkerDarkColor = CheckerDark

    val radialGlowColor = RadialGlow

    Box(modifier = modifier) {
        // Base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = gradientColors))
        )

        // Aero radial glow — luminous sun-lit quality
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(radialGlowColor, androidx.compose.ui.graphics.Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.3f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.5f, size.height * 0.3f)
            )
        }

        // Tiled diamond checker overlay — single draw call
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val checkerSize = 24f
                    val tileW = (checkerSize * 2).toInt()
                    val tileH = (checkerSize * 2).toInt()

                    // Pre-render a 2×2 diamond tile into an ImageBitmap
                    val tile = androidx.compose.ui.graphics.ImageBitmap(tileW, tileH)
                    val tileCanvas = androidx.compose.ui.graphics.Canvas(tile)
                    val lightPaint = androidx.compose.ui.graphics.Paint().apply { color = checkerLightColor }
                    val darkPaint = androidx.compose.ui.graphics.Paint().apply { color = checkerDarkColor }

                    // Draw 2×2 grid of diamonds to create a seamlessly-tileable pattern
                    for (row in 0..2) {
                        for (col in 0..2) {
                            val x = col * checkerSize
                            val y = row * checkerSize
                            val ox = if (row % 2 == 0) 0f else checkerSize / 2

                            val path = Path().apply {
                                moveTo(x + ox, y)
                                lineTo(x + ox + checkerSize / 2, y + checkerSize / 2)
                                lineTo(x + ox, y + checkerSize)
                                lineTo(x + ox - checkerSize / 2, y + checkerSize / 2)
                                close()
                            }

                            val paint = if ((row + col) % 2 == 0) lightPaint else darkPaint
                            tileCanvas.drawPath(path, paint)
                        }
                    }

                    val shader = android.graphics.BitmapShader(
                        tile.asAndroidBitmap(),
                        android.graphics.Shader.TileMode.REPEAT,
                        android.graphics.Shader.TileMode.REPEAT
                    )
                    val shaderPaint = android.graphics.Paint().apply { this.shader = shader }

                    onDrawBehind {
                        drawContext.canvas.nativeCanvas.drawRect(
                            0f, 0f, size.width, size.height, shaderPaint
                        )
                    }
                }
        )
    }
}

/**
 * Miiverse-style navigation button with icon and label.
 * Inspired by the Nintendo Miiverse sidebar icons.
 */
@Composable
fun MiiverseNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = DarkText,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isCompact = screenWidth < 600
    val iconSize = if (isCompact) 24.dp else 36.dp
    val hPad = if (isCompact) 6.dp else 12.dp
    val vPad = if (isCompact) 6.dp else 8.dp

    val selectionShape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .then(
                if (isSelected) Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = if (com.pocketpass.app.ui.theme.LocalDarkMode.current)
                                listOf(
                                    androidx.compose.ui.graphics.Color(0xFF3A3A3A),
                                    androidx.compose.ui.graphics.Color(0xFF444444)
                                )
                            else
                                listOf(
                                    androidx.compose.ui.graphics.Color(0xFFC8C8C8),
                                    androidx.compose.ui.graphics.Color(0xFFD8D8D8)
                                )
                        ),
                        selectionShape
                    )
                else Modifier
            )
            .gamepadFocusable(
                shape = selectionShape,
                onSelect = onClick
            )
            .clickable(onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad)
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(androidx.compose.ui.graphics.Color.Red, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = DarkText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Shared persistent navigation bar used across all main screens.
 */
@Composable
fun PlazaNavBar(
    currentScreen: NavigationState.MainScreen,
    onNavigate: (NavigationState.MainScreen) -> Unit,
    unreadMessageCount: Int = 0,
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current

    val isDark = com.pocketpass.app.ui.theme.LocalDarkMode.current
    val navGlassColor = NavGlass
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isCompactNav = screenWidth < 600
    // Bottom nav on phones uses top rounding, top nav on tablets uses bottom rounding
    val navShape = if (isCompactNav)
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    else
        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, navShape)
            .clip(navShape)
            .background(navGlassColor)
            .drawBehind {
                // Thin luminous top border
                drawRect(
                    color = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                    size = Size(size.width, 1.dp.toPx())
                )
            }
    ) {
        // Base bar background
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiiverseNavButton(
                icon = Icons.Filled.Email,
                label = "Messages",
                isSelected = currentScreen == NavigationState.MainScreen.MESSAGES,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.MESSAGES) },
                modifier = Modifier.weight(1f),
                badgeCount = unreadMessageCount
            )
            NavDivider()
            MiiverseNavButton(
                icon = Icons.Filled.Star,
                label = "Friends",
                tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                isSelected = currentScreen == NavigationState.MainScreen.FRIENDS,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.FRIENDS) },
                modifier = Modifier.weight(1f)
            )
            NavDivider()
            MiiverseNavButton(
                icon = Icons.Filled.Person,
                label = "Plaza",
                tint = PocketPassGreen,
                isSelected = currentScreen == NavigationState.MainScreen.PLAZA_OVERVIEW,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.PLAZA_OVERVIEW) },
                modifier = Modifier.weight(1f)
            )
            NavDivider()
            MiiverseNavButton(
                icon = Icons.Filled.DateRange,
                label = "Stats",
                isSelected = currentScreen == NavigationState.MainScreen.STATISTICS,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.STATISTICS) },
                modifier = Modifier.weight(1f)
            )
            NavDivider()
            MiiverseNavButton(
                icon = Icons.Filled.Favorite,
                label = "Activities",
                tint = PocketPassGreen,
                isSelected = currentScreen == NavigationState.MainScreen.ACTIVITIES,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.ACTIVITIES) },
                modifier = Modifier.weight(1f)
            )
            NavDivider()
            MiiverseNavButton(
                icon = Icons.Filled.Settings,
                label = "Settings",
                isSelected = currentScreen == NavigationState.MainScreen.SETTINGS,
                onClick = { soundManager.playNavigate(); onNavigate(NavigationState.MainScreen.SETTINGS) },
                modifier = Modifier.weight(1f)
            )
        }
        // Glossy highlight overlay (4-stop Aero glass shine)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f)
                                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.60f),
                            0.35f to if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)
                                     else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                            0.50f to androidx.compose.ui.graphics.Color.Transparent,
                            1.0f to if (isDark) androidx.compose.ui.graphics.Color.Transparent
                                    else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.03f)
                        )
                    )
                )
        )

    }
}

@Composable
private fun NavDivider() {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val dividerHeight = if (screenWidth < 600) 32.dp else 48.dp
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(dividerHeight)
            .background(DarkText.copy(alpha = 0.15f))
    )
}

private fun parseHexColor(hex: String?, fallbackArgb: Long): androidx.compose.ui.graphics.Color {
    if (hex == null) return androidx.compose.ui.graphics.Color(fallbackArgb)
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        androidx.compose.ui.graphics.Color(fallbackArgb)
    }
}

// Persists across navigation so we don't false-trigger on re-entering the screen
private var lastKnownEncounterIds: Set<String> = emptySet()
private var encounterTrackerInitialized: Boolean = false
private var encounterTrackerInitStarted: Boolean = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlazaScreen(
    onOpenSpotPass: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val encountersLoaded by db.encounterDao().getAllEncountersFlow().collectAsState(initial = null)
    val encounters = remember(encountersLoaded) { encountersLoaded?.map { it.decryptFields() } ?: emptyList() }
    val userPreferences = remember { UserPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    // Single combined flow for all profile data - reduces 10 collectAsState calls to 1
    val profileData by userPreferences.profileDataFlow.collectAsState(
        initial = com.pocketpass.app.data.UserPreferences.ProfileData()
    )
    val myHex = profileData.avatarHex ?: ""
    val myName = profileData.userName ?: "Stranger"
    val myAge = profileData.userAge ?: ""
    val myHobbies = profileData.userHobbies ?: ""
    val myOrigin = profileData.userOrigin ?: "Unknown"
    val myGamesJson = remember(profileData.selectedGames) {
        if (profileData.selectedGames.isNotEmpty()) com.google.gson.Gson().toJson(profileData.selectedGames) else ""
    }
    val myGreeting = profileData.userGreeting
    val myMood = profileData.userMood
    val myCardStyle = profileData.cardStyle

    // Debug mode state
    var showDebugDialog by remember { mutableStateOf(false) }
    var showNearbyScanner by remember { mutableStateOf(false) }
    var isAddingEncounter by remember { mutableStateOf(false) }

    // New encounter animation state
    var showNewEncounterAnimation by remember { mutableStateOf(false) }
    var newEncounter by remember { mutableStateOf<Encounter?>(null) }

    // Detect new encounters - skip initial loads (including sync) to avoid false popups
    // Init delay runs in a separate LaunchedEffect(Unit) so it isn't cancelled by emissions
    LaunchedEffect(Unit) {
        if (!encounterTrackerInitStarted) {
            encounterTrackerInitStarted = true
            // Wait for sync to settle before arming the tracker
            kotlinx.coroutines.delay(5000)
            // Snapshot whatever is in the DB right now as "known"
            lastKnownEncounterIds = (encountersLoaded ?: emptyList()).map { it.encounterId }.toSet()
            encounterTrackerInitialized = true
        }
    }
    LaunchedEffect(encountersLoaded) {
        val loaded = encountersLoaded ?: return@LaunchedEffect // Still loading
        val currentIds = loaded.map { it.encounterId }.toSet()

        if (encounterTrackerInitialized) {
            val brandNew = currentIds - lastKnownEncounterIds
            if (brandNew.isNotEmpty()) {
                // Show animation for the newest encounter that wasn't previously known
                newEncounter = loaded.filter { it.encounterId in brandNew }
                    .maxByOrNull { it.timestamp }
                showNewEncounterAnimation = true

                kotlinx.coroutines.delay(3500)
                showNewEncounterAnimation = false
            }
        }

        // Always keep the known set up to date (even during init window)
        lastKnownEncounterIds = currentIds
    }

    // Active event effects for plaza banner
    var activeEventEffects by remember { mutableStateOf<List<com.pocketpass.app.data.EventEffect>>(emptyList()) }
    LaunchedEffect(Unit) {
        activeEventEffects = withContext(Dispatchers.IO) {
            try { com.pocketpass.app.data.SpotPassRepository(context).getActiveEffects() } catch (_: Exception) { emptyList() }
        }
    }

    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // Nintendo-style checkered gradient background (always visible, no animation)
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        // Content animates in with scale + fade
        AnimatedVisibility(
            visible = screenVisible,
            enter = scaleIn(
                initialScale = 0.93f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing))
        ) {
        // Content area (always show user's profile first, then encounters)
        Box(modifier = Modifier.fillMaxSize()) {
                // Create user's profile as an encounter
                val myEncounter = remember(myHex, myName, myGreeting, myOrigin, myAge, myHobbies, myGamesJson) {
                    if (!myHex.isNullOrBlank()) {
                        Encounter(
                            encounterId = "self",
                            timestamp = System.currentTimeMillis(),
                            otherUserAvatarHex = myHex ?: "",
                            otherUserName = myName ?: "Me",
                            greeting = myGreeting,
                            origin = myOrigin ?: "Unknown",
                            age = myAge ?: "",
                            hobbies = myHobbies ?: "",
                            games = myGamesJson
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
                        // Active event banner
                        if (activeEventEffects.isNotEmpty()) {
                            val specialGreeting = com.pocketpass.app.data.EventEffectManager.getSpecialGreeting(activeEventEffects)
                            val bannerTransition = rememberInfiniteTransition(label = "event_banner")
                            val bannerGlow by bannerTransition.animateFloat(
                                initialValue = 0.7f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "banner_glow"
                            )

                            if (specialGreeting != null) {
                                // Special greeting — decorative quote-style banner
                                val greetingColorHex = com.pocketpass.app.data.EventEffectManager.getSpecialGreetingColor(activeEventEffects)
                                val greetingBgHex = com.pocketpass.app.data.EventEffectManager.getSpecialGreetingBgColor(activeEventEffects)
                                val textColor = remember(greetingColorHex) {
                                    parseHexColor(greetingColorHex, 0xFFBF360C)
                                }
                                val bgBase = remember(greetingBgHex) {
                                    parseHexColor(greetingBgHex, 0xFFFFF8E1)
                                }
                                // Create a gradient: base → slightly lighter center → base
                                val bgCenter = remember(bgBase) {
                                    androidx.compose.ui.graphics.Color(
                                        red = (bgBase.red + (1f - bgBase.red) * 0.15f).coerceIn(0f, 1f),
                                        green = (bgBase.green + (1f - bgBase.green) * 0.15f).coerceIn(0f, 1f),
                                        blue = (bgBase.blue + (1f - bgBase.blue) * 0.15f).coerceIn(0f, 1f),
                                        alpha = bgBase.alpha
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    bgBase.copy(alpha = 0.85f),
                                                    bgCenter.copy(alpha = 0.9f),
                                                    bgBase.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "\u201C$specialGreeting\u201D",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor.copy(alpha = bannerGlow),
                                        textAlign = TextAlign.Center,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            } else {
                                // Regular effect badges — compact gold pill
                                val bannerText = activeEventEffects.joinToString(" | ") { effect ->
                                    when (effect.type) {
                                        com.pocketpass.app.data.EventEffectType.TOKEN_MULTIPLIER -> "x${effect.value.toInt()} Tokens"
                                        com.pocketpass.app.data.EventEffectType.PUZZLE_DROP_BOOST -> "${(effect.value * 100).toInt()}% Drop Rate"
                                        com.pocketpass.app.data.EventEffectType.SHOP_DISCOUNT -> "${effect.value.toInt()}% Off Shop"
                                        com.pocketpass.app.data.EventEffectType.WALK_BONUS -> "Walk Cap ${effect.value.toInt()}"
                                        com.pocketpass.app.data.EventEffectType.SPECIAL_GREETING -> effect.message ?: ""
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Color(0xFFFFC107).copy(alpha = 0.15f * bannerGlow)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = bannerText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color(0xFFE65100).copy(alpha = bannerGlow),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.nativeKeyEvent.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                if (pagerState.currentPage > 0) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                    soundManager.playNavigate()
                                                }
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                if (pagerState.currentPage < pagerState.pageCount - 1) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                    soundManager.playNavigate()
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
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

        // SpotPass pulsing green LED indicator
        val spotPassUnread by userPreferences.spotPassUnreadFlow.collectAsState(initial = 0)
        if (spotPassUnread > 0) {
            val infiniteTransition = rememberInfiniteTransition(label = "spotpass_led")
            val ledAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "spotpass_led_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { soundManager.playTap(); onOpenSpotPass() },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer glow
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFF00E676).copy(alpha = ledAlpha * 0.4f)
                            )
                    )
                    // Inner solid dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFF00E676).copy(alpha = ledAlpha)
                            )
                    )
                }
            }
        }

        // Debug FAB (bottom right) - only in debug builds
        if (BuildConfig.DEBUG) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = { soundManager.playTap(); showDebugDialog = true },
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF6B9D),
                    contentColor = OffWhite,
                    modifier = Modifier.gamepadFocusable(
                        shape = RoundedCornerShape(16.dp),
                        onSelect = { soundManager.playTap(); showDebugDialog = true }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Debug: Add Test Encounter",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Debug Dialogs
            if (showNearbyScanner) {
                NearbyDebugDialog(onDismiss = { showNearbyScanner = false })
            }

            if (showDebugDialog) {
                DebugAddEncounterDialog(
                    onDismiss = { showDebugDialog = false },
                    isLoading = isAddingEncounter,
                    onOpenNearbyScanner = {
                        showDebugDialog = false
                        showNearbyScanner = true
                    },
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
                                // Grant token + chance at puzzle piece
                                userPreferences.addTokens(com.pocketpass.app.data.TokenSystem.TOKENS_PER_NEW_ENCOUNTER)
                                if (kotlin.random.Random.nextFloat() < com.pocketpass.app.data.TokenSystem.PUZZLE_PIECE_DROP_CHANCE) {
                                    val progress = userPreferences.puzzleProgressFlow.first()
                                    val uncollected = progress.allUncollectedPieces(com.pocketpass.app.data.PuzzlePanels.getAll())
                                    if (uncollected.isNotEmpty()) {
                                        userPreferences.addPuzzlePiece(uncollected.random())
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

        // Animate out (minimize to friends) - smooth and coordinated
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

    val soundManager = LocalSoundManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f * alpha.value))
            .clickable { soundManager.playTap(); onDismiss() },
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
            AeroCard(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(0.85f),
                containerColor = OffWhite,
                elevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mii Avatar
                    val dims = LocalAppDimensions.current
                    Box(
                        modifier = Modifier
                            .size(dims.avatarMedium)
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
                    text = "Added to Friends!",
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

private val ConfettiColors = listOf(
    androidx.compose.ui.graphics.Color(0xFFFFC107),
    androidx.compose.ui.graphics.Color(0xFF7BC63B),
    androidx.compose.ui.graphics.Color(0xFFFF6B9D),
    androidx.compose.ui.graphics.Color(0xFF2196F3),
    androidx.compose.ui.graphics.Color(0xFFFF5722)
)

@Composable
fun ConfettiEffect(@Suppress("UNUSED_PARAMETER") visible: Boolean = true) {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")

    // Reduced to 8 pieces for better performance (was 12 = 36 animations)
    repeat(8) { index ->
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

        Box(
            modifier = Modifier
                .offset(x = (offsetX + sway).dp, y = offsetY.dp)
                .size(10.dp)
                .graphicsLayer {
                    rotationZ = rotation
                }
                .background(
                    color = ConfettiColors[index % ConfettiColors.size],
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
fun PlazaCard(encounter: Encounter, mood: String = "HAPPY", cardStyle: String = "classic", isSelf: Boolean = false) {
    // Card border color based on style — look up from ShopItems registry
    val borderBrush = run {
        val shopItem = com.pocketpass.app.data.ShopItems.findById(cardStyle)
        if (shopItem?.previewColors != null) {
            Brush.linearGradient(shopItem.previewColors.map { androidx.compose.ui.graphics.Color(it.toInt()) })
        } else {
            Brush.linearGradient(colors = BackgroundGradient)
        }
    }

    val cardPadding = if (LocalConfiguration.current.screenWidthDp < 600) 8.dp else 24.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = cardPadding, end = cardPadding, top = 8.dp, bottom = cardPadding),
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
                val isCompactCard = LocalConfiguration.current.screenWidthDp < 600
                val cardScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(OffWhite)
                        .padding(if (isCompactCard) 12.dp else 20.dp)
                        .fillMaxWidth()
                        .then(if (isCompactCard) Modifier.verticalScroll(cardScrollState) else Modifier)
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
                            val dims = LocalAppDimensions.current
                            MoodIcon(mood = moodType, modifier = Modifier.size(dims.moodIconSize))
                        }
                    }

                    Spacer(modifier = Modifier.height(if (LocalAppDimensions.current.isCompact) 8.dp else 16.dp))

                    // Main content: Mii on left, info on right
                    val dims = LocalAppDimensions.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (dims.isCompact) 8.dp else 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Mii Avatar (left side)
                        Box(
                            modifier = Modifier
                                .size(dims.avatarLarge)
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
                            val isDark = com.pocketpass.app.ui.theme.LocalDarkMode.current
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        run {
                                            val themeItem = com.pocketpass.app.data.ShopItems.findById(cardStyle)
                                            val baseColor = themeItem?.previewColors?.firstOrNull()
                                            if (baseColor != null) {
                                                val c = androidx.compose.ui.graphics.Color(baseColor.toInt())
                                                if (isDark) c.copy(alpha = 0.15f) else c.copy(alpha = 0.1f)
                                            } else {
                                                if (isDark) androidx.compose.ui.graphics.Color(0xFF333333) else androidx.compose.ui.graphics.Color(0xFFF0F0F0)
                                            }
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
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = encounter.age,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = DarkText
                                    )
                                }
                            }

                            // Location with flag
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = RegionFlags.getFlagForRegion(encounter.origin),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = encounter.origin,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MediumText
                                )
                            }

                            // Hobbies
                            if (encounter.hobbies.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    com.pocketpass.app.ui.theme.HobbiesIcon(
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    com.pocketpass.app.ui.theme.HobbyChips(encounter.hobbies)
                                }
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
                                        color = GreenText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Favourite Games section (right side of card)
                        if (encounter.games.isNotBlank()) {
                            val gamesList = remember(encounter.games) {
                                try {
                                    val type = object : com.google.gson.reflect.TypeToken<List<com.pocketpass.app.data.IgdbGame>>() {}.type
                                    com.google.gson.Gson().fromJson<List<com.pocketpass.app.data.IgdbGame>>(encounter.games, type) ?: emptyList()
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                            if (gamesList.isNotEmpty()) {
                                val isDarkGames = com.pocketpass.app.ui.theme.LocalDarkMode.current
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDarkGames) androidx.compose.ui.graphics.Color(0xFF2A2A2A) else androidx.compose.ui.graphics.Color(0xFFF0F0F0))
                                        .padding(10.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "\uD83C\uDFAE Favourite Games",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            val gameDims = LocalAppDimensions.current
                                            gamesList.take(3).forEach { game ->
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.width(gameDims.gameCoverWidth)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = gameDims.gameCoverWidth, height = gameDims.gameCoverHeight)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val coverUrl = game.coverUrl("t_cover_big")
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
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = game.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MediumText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
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
    onAddEncounter: (Encounter) -> Unit,
    onOpenNearbyScanner: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
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
                        label = { Text("Pal Hex Data (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Leave blank for default Pal") },
                        maxLines = 3,
                        supportingText = {
                            Text(
                                text = "To use a custom Pal: Create one in the Pal Creator, then copy the hex from your saved Pals",
                                style = MaterialTheme.typography.labelSmall,
                                color = MediumText
                            )
                        }
                    )
                }
                item {
                    AeroButton(
                        onClick = {
                            soundManager.playNavigate()
                            onOpenNearbyScanner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Nearby Devices", fontWeight = FontWeight.Bold)
                    }
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
                        AeroButton(
                            onClick = {
                                soundManager.playTap()
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
                        AeroButton(
                            onClick = {
                                soundManager.playTap()
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
                        AeroButton(
                            onClick = {
                                soundManager.playTap()
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
                        AeroButton(
                            onClick = {
                                soundManager.playTap()
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
            AeroButton(
                onClick = {
                    if (name.isNotBlank() && greeting.isNotBlank() && !isLoading) {
                        soundManager.playSuccess()
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
            TextButton(onClick = { soundManager.playBack(); onDismiss() }) {
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

    // Cache the ImageRequest so it's not rebuilt on every recomposition
    val imageRequest = remember(renderUrl) {
        coil.request.ImageRequest.Builder(context)
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
            .build()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            coil.compose.SubcomposeAsyncImage(
                model = imageRequest,
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

/**
 * Debug dialog that shows nearby PocketPass devices discovered by the ProximityService.
 * Reads from ProximityService's static StateFlow — no separate discovery needed.
 */
@Composable
fun NearbyDebugDialog(onDismiss: () -> Unit) {
    val soundManager = LocalSoundManager.current

    val devices by com.pocketpass.app.service.ProximityService.nearbyDevices.collectAsState()
    val serviceStatus by com.pocketpass.app.service.ProximityService.serviceStatus.collectAsState()
    val isScanning = serviceStatus != "stopped"

    // Pulse animation for scanning indicator
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanPulse"
    )

    AlertDialog(
        onDismissRequest = {
            soundManager.playBack()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { alpha = if (isScanning) scanPulse else 0.3f }
                        .background(
                            color = if (isScanning) PocketPassGreen
                            else androidx.compose.ui.graphics.Color(0xFFFF5722),
                            shape = CircleShape
                        )
                )
                Text(
                    text = "Nearby Devices",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Service: $serviceStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MediumText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${devices.size} device${if (devices.size != 1) "s" else ""} found",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.height(12.dp))

                val scanDims = LocalAppDimensions.current
                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scanDims.emptyStateHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = PocketPassGreen,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Looking for PocketPass users...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediumText
                                )
                            } else {
                                Text(
                                    text = "Service not running",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediumText
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(scanDims.dialogListHeight)
                    ) {
                        items(devices.values.toList().sortedByDescending { it.discoveredAt }, key = { it.endpointId }) { device ->
                            val stateColor = when (device.state) {
                                "discovered" -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                                "connecting" -> androidx.compose.ui.graphics.Color(0xFFFFC107)
                                "connected" -> PocketPassGreen
                                "exchanged" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                "failed" -> androidx.compose.ui.graphics.Color(0xFFFF5722)
                                else -> MediumText
                            }
                            AeroCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 12.dp,
                                containerColor = OffWhite,
                                elevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText
                                        )
                                        Text(
                                            text = device.endpointId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText
                                        )
                                        val elapsed = (System.currentTimeMillis() - device.discoveredAt) / 1000
                                        Text(
                                            text = "${elapsed}s ago",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(color = stateColor, shape = CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = device.state,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = stateColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                soundManager.playBack()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}
