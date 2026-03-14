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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.pocketpass.app.data.IgdbApi
import com.pocketpass.app.data.IgdbGame
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.AeroTopBar
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.LocalDarkMode
import com.pocketpass.app.util.LocalSoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameSearchScreen(
    onBack: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val igdbApi = remember { IgdbApi(context) }
    val dims = LocalAppDimensions.current
    val isDark = LocalDarkMode.current

    val selectedGames by userPreferences.selectedGamesFlow.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<IgdbGame>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

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
            AeroTopBar(
                title = "Search Games",
                onBack = { soundManager.playBack(); onBack() }
            )

            // Selected games (current picks)
            if (selectedGames.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Your Games (${selectedGames.size}/3)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MediumText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedGames.forEach { game ->
                            AeroCard(
                                modifier = Modifier.weight(1f),
                                cornerRadius = 12.dp,
                                containerColor = OffWhite,
                                elevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val coverUrl = game.coverUrl("t_thumb")
                                    if (coverUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = coverUrl,
                                            contentDescription = game.name,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = game.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = DarkText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
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
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Search bar inside an AeroCard
            AeroCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                cornerRadius = 16.dp,
                elevation = 4.dp,
                containerColor = OffWhite
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        searchJob?.cancel()
                        if (query.length >= 2) {
                            searchJob = coroutineScope.launch {
                                delay(400) // debounce
                                isSearching = true
                                searchResults = igdbApi.searchGames(query)
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    placeholder = {
                        Text(
                            "Search for a game...",
                            color = LightText
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = PocketPassGreen
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PocketPassGreen,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = MediumText
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PocketPassGreen,
                        unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
                        cursorColor = PocketPassGreen,
                        focusedTextColor = DarkText,
                        unfocusedTextColor = DarkText
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search results
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Empty state
                if (!isSearching && searchResults.isEmpty() && searchQuery.length >= 2) {
                    item {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            containerColor = OffWhite
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No games found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try a different search term",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }

                // Hint when idle
                if (searchQuery.isEmpty() && selectedGames.size < 3) {
                    item {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp,
                            containerColor = OffWhite
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Pick up to 3 games",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "These will show on your profile when you meet others",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }

                itemsIndexed(searchResults, key = { _, game -> game.id }) { index, game ->
                    val alreadySelected = selectedGames.any { it.id == game.id }
                    val canAdd = selectedGames.size < 3 && !alreadySelected

                    var itemVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 40L)
                        itemVisible = true
                    }

                    AnimatedVisibility(
                        visible = itemVisible,
                        enter = fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                        )
                    ) {
                        AeroCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                            containerColor = if (alreadySelected) PocketPassGreen.copy(alpha = 0.12f) else OffWhite,
                            elevation = if (alreadySelected) 1.dp else 3.dp,
                            onClick = if (canAdd) { {
                                soundManager.playSelect()
                                coroutineScope.launch {
                                    userPreferences.saveSelectedGames(selectedGames + game)
                                }
                            } } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cover art
                                val coverUrl = game.coverUrl("t_cover_small")
                                Box(
                                    modifier = Modifier
                                        .size(width = dims.gameCoverWidth, height = dims.gameCoverHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFE8E8E8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (coverUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = coverUrl,
                                            contentDescription = game.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = MediumText,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = game.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (alreadySelected) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = GreenText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Added",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = GreenText
                                            )
                                        }
                                    } else if (!canAdd) {
                                        Text(
                                            text = "Max 3 games reached",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediumText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
        } // AnimatedVisibility
    }
}
