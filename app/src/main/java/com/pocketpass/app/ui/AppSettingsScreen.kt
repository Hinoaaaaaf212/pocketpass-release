package com.pocketpass.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }

    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
    val proximityEnabled by userPreferences.proximityEnabledFlow.collectAsState(initial = true)
    val sfxEnabled by userPreferences.sfxEnabledFlow.collectAsState(initial = true)
    val sfxVolume by userPreferences.sfxVolumeFlow.collectAsState(initial = 0.5f)

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = listOf(PocketPassGreen, SkyBlue)
        )

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
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nearby Encounters
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby Encounters",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Switch(
                                checked = proximityEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                    coroutineScope.launch {
                                        userPreferences.saveProximityEnabled(enabled)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PocketPassGreen
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (proximityEnabled) "Bluetooth scanning is active. You'll meet people nearby automatically."
                                   else "Bluetooth scanning is off. Turn on to meet people nearby.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                    }
                }

                // Sound Effects
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sound Effects",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Switch(
                                checked = sfxEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                    coroutineScope.launch {
                                        userPreferences.saveSfxEnabled(enabled)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PocketPassGreen
                                )
                            )
                        }

                        if (sfxEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vol",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                                Slider(
                                    value = sfxVolume,
                                    onValueChange = { newVolume ->
                                        coroutineScope.launch {
                                            userPreferences.saveSfxVolume(newVolume)
                                        }
                                    },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = PocketPassGreen,
                                        activeTrackColor = PocketPassGreen
                                    )
                                )
                                Text(
                                    text = "${(sfxVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }

                // Plaza Music
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Plaza Music",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Switch(
                                checked = musicVolume > 0f,
                                onCheckedChange = { enabled ->
                                    if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                    coroutineScope.launch {
                                        userPreferences.saveMusicVolume(if (enabled) 0.3f else 0f)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PocketPassGreen
                                )
                            )
                        }

                        if (musicVolume > 0f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vol",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                                Slider(
                                    value = musicVolume,
                                    onValueChange = { newVolume ->
                                        coroutineScope.launch {
                                            userPreferences.saveMusicVolume(newVolume)
                                        }
                                    },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = PocketPassGreen,
                                        activeTrackColor = PocketPassGreen
                                    )
                                )
                                Text(
                                    text = "${(musicVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediumText
                                )
                            }
                        }
                    }
                }

                // Clear All Miis
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Clear All Miis",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use this if your Miis appear corrupted or broken.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                soundManager.playDelete()
                                coroutineScope.launch {
                                    userPreferences.clearAllMiis()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            )
                        ) {
                            Text(
                                "Clear All Miis",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Reset Profile
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Reset Profile",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clears your name, age, and hobbies. Your saved Miis are kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                soundManager.playDelete()
                                coroutineScope.launch {
                                    userPreferences.clearProfile()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE57373)
                            )
                        ) {
                            Text(
                                "Reset Profile",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
