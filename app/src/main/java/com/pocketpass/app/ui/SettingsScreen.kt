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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.MoodIcon
import com.pocketpass.app.ui.theme.MoodType
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCreateNewMii: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    
    val savedMiis by userPreferences.savedMiisFlow.collectAsState(initial = emptyList())
    val activeMiiHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val userName by userPreferences.userNameFlow.collectAsState(initial = "")
    val miiCount by userPreferences.getMiiCount().collectAsState(initial = 0)

    val userGreeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val userMood by userPreferences.userMoodFlow.collectAsState(initial = "😊")
    val userCardStyle by userPreferences.cardStyleFlow.collectAsState(initial = "classic")

    var customGreeting by remember { mutableStateOf(userGreeting) }

    val maxMiis = 3
    val canCreateNewMii = miiCount < maxMiis

    Box(modifier = Modifier.fillMaxSize()) {
        // Nintendo-style checkered gradient background
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

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
            IconButton(onClick = onBack) {
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        userPreferences.clearAllMiis()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF9800)
                                )
                            ) {
                                Text("Clear All Miis", style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        userPreferences.clearProfile()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFFE57373)
                                )
                            ) {
                                Text("Reset Profile", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use 'Clear All Miis' if Miis appear corrupted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Customization Section
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

                        // Preset greetings
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
                                            customGreeting = greeting
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

                        // Custom greeting input
                        OutlinedTextField(
                            value = customGreeting,
                            onValueChange = { customGreeting = it },
                            label = { Text("Custom Greeting") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
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

                        // First row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            moods.take(5).forEach { (moodType, label) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (userMood == moodType.name) PocketPassGreen.copy(alpha = 0.3f)
                                                else androidx.compose.ui.graphics.Color(0xFFF0F0F0)
                                            )
                                            .border(
                                                width = if (userMood == moodType.name) 3.dp else 1.dp,
                                                color = if (userMood == moodType.name) PocketPassGreen else DarkText.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                coroutineScope.launch {
                                                    userPreferences.saveMood(moodType.name)
                                                }
                                            },
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
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Second row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            moods.drop(5).forEach { (moodType, label) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (userMood == moodType.name) PocketPassGreen.copy(alpha = 0.3f)
                                                else androidx.compose.ui.graphics.Color(0xFFF0F0F0)
                                            )
                                            .border(
                                                width = if (userMood == moodType.name) 3.dp else 1.dp,
                                                color = if (userMood == moodType.name) PocketPassGreen else DarkText.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                coroutineScope.launch {
                                                    userPreferences.saveMood(moodType.name)
                                                }
                                            },
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
                                        coroutineScope.launch {
                                            userPreferences.saveCardStyle(styleId)
                                        }
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

                Spacer(modifier = Modifier.height(24.dp))

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
                        color = if (miiCount >= maxMiis) androidx.compose.ui.graphics.Color(0xFFE57373) else DarkText
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(savedMiis) { miiHex ->
                val isActive = miiHex == activeMiiHex
                val isCorrupted = miiHex.length < 50 || miiHex == "010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"

                android.util.Log.d("SettingsScreen", "Mii hex length: ${miiHex.length}, First 20: ${miiHex.take(20)}")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isCorrupted -> androidx.compose.ui.graphics.Color(0xFFFFCDD2)
                                isActive -> PocketPassGreen.copy(alpha = 0.2f)
                                else -> OffWhite
                            }
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mii preview (clickable to set as active)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                coroutineScope.launch {
                                    userPreferences.setActiveMii(miiHex)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color(0xFFE3F2FD),
                                            androidx.compose.ui.graphics.Color(0xFFBBDEFB)
                                        )
                                    )
                                ),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = miiHex)
                        }

                        Spacer(modifier = Modifier.size(16.dp))

                        Column {
                            if (isCorrupted) {
                                Text(
                                    text = "⚠️ Corrupted Data",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F)
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

                    // Delete button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                userPreferences.deleteMii(miiHex)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Mii",
                            tint = androidx.compose.ui.graphics.Color(0xFFE57373)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { if (canCreateNewMii) onCreateNewMii() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = canCreateNewMii,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canCreateNewMii) DarkText else DarkText.copy(alpha = 0.3f),
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
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }
    }
}