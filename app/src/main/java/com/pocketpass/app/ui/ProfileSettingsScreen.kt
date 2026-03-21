package com.pocketpass.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.data.ShopItems
import com.pocketpass.app.util.LocalSoundManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush

@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    onOpenGameSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val soundManager = LocalSoundManager.current

    val userGreeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val userMood by userPreferences.userMoodFlow.collectAsState(initial = "HAPPY")
    val userCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")

    var customGreeting by remember { mutableStateOf(userGreeting) }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { soundManager.playBack(); onBack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OffWhite
                        )
                    }
                    Text(
                        text = "Profile Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OffWhite
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Greeting Section
                        AeroCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var greetingSaved by remember { mutableStateOf(false) }

                            LaunchedEffect(greetingSaved) {
                                if (greetingSaved) {
                                    kotlinx.coroutines.delay(2000)
                                    greetingSaved = false
                                }
                            }

                            Column(modifier = Modifier.padding(16.dp)) {
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
                                                    customGreeting = greeting
                                                    coroutineScope.launch {
                                                        userPreferences.saveGreeting(greeting)
                                                        try { SyncRepository(context).syncProfile() } catch (_: Exception) { }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                containerColor = if (userGreeting == greeting) PocketPassGreen else if (com.pocketpass.app.ui.theme.LocalDarkMode.current) Color(0xFF4A4A4A) else Color(0xFF6B6B6B),
                                                cornerRadius = 12.dp
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
                                    onValueChange = { if (it.length <= 200) customGreeting = it },
                                    label = { Text("Custom Greeting") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkText,
                                        unfocusedTextColor = DarkText
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AeroButton(
                                    onClick = {
                                        soundManager.playSuccess()
                                        coroutineScope.launch {
                                            userPreferences.saveGreeting(customGreeting)
                                            try { SyncRepository(context).syncProfile() } catch (_: Exception) { }
                                        }
                                        greetingSaved = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (greetingSaved) "Saved!" else "Save Custom Greeting", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        // Mood Selector
                        AeroCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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
                    }

                    item {
                        // About Me Section
                        AeroCard(
                            modifier = Modifier.fillMaxWidth()
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

                            Column(modifier = Modifier.padding(16.dp)) {
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
                                    onValueChange = { if (it.length <= 200) hobbiesText = com.pocketpass.app.ui.theme.formatHobbiesInput(it) },
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
                                            try { SyncRepository(context).syncProfile() } catch (_: Exception) { }
                                        }
                                        showSaved = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (showSaved) "Saved!" else "Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        // Card Style Selector — shows owned themes from shop
                        val ownedItems by userPreferences.ownedShopItemsFlow.collectAsState(initial = emptySet())
                        val ownedThemes = ShopItems.cardThemes.filter { it.id in ownedItems }

                        AeroCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
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

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        // Favourite Games
                        AeroCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Favourite Games",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
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
                                                        .background(Color(0xFFE0E0E0)),
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
                                        cornerRadius = 12.dp
                                    ) {
                                        Text(
                                            if (selectedGames.isEmpty()) "Add Games" else "Add Another Game",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
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
