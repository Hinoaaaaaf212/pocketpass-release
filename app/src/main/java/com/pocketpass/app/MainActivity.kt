package com.pocketpass.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.service.ProximityService
import com.pocketpass.app.ui.AvatarCreatorScreen
import com.pocketpass.app.ui.EncounterHistoryScreen
import com.pocketpass.app.ui.FavoritesScreen
import com.pocketpass.app.ui.PermissionsScreen
import com.pocketpass.app.ui.PlazaScreen
import com.pocketpass.app.ui.ProfileSetupScreen
import com.pocketpass.app.ui.SettingsScreen
import com.pocketpass.app.ui.StatisticsScreen
import com.pocketpass.app.ui.theme.PocketPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val userPreferences = UserPreferences(this)
        
        setContent {
            PocketPassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }
                    val avatarHex by userPreferences.avatarHexFlow.collectAsState(initial = null)
                    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
                    
                    var showSettings by remember { mutableStateOf(false) }
                    var showHistory by remember { mutableStateOf(false) }
                    var showStatistics by remember { mutableStateOf(false) }
                    var showFavorites by remember { mutableStateOf(false) }
                    var forceCreateNewMii by remember { mutableStateOf(false) }

                    if (!permissionsGranted) {
                        PermissionsScreen(
                            onPermissionsGranted = {
                                permissionsGranted = true
                            }
                        )
                    } else if (avatarHex == null || forceCreateNewMii) {
                        AvatarCreatorScreen(
                            onAvatarSaved = {
                                forceCreateNewMii = false
                                // Re-composition will push us to the next screen automatically
                            }
                        )
                    } else if (userName == null) {
                        ProfileSetupScreen(
                            onProfileSaved = {
                                // Re-composition handles progression
                            }
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false },
                            onCreateNewMii = {
                                forceCreateNewMii = true
                                showSettings = false
                            }
                        )
                    } else if (showHistory) {
                        EncounterHistoryScreen(
                            onBack = { showHistory = false }
                        )
                    } else if (showStatistics) {
                        StatisticsScreen(
                            onBack = { showStatistics = false }
                        )
                    } else if (showFavorites) {
                        FavoritesScreen(
                            onBack = { showFavorites = false }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            try {
                                val serviceIntent = Intent(this@MainActivity, ProximityService::class.java)
                                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Phase 5 (The Plaza)
                        PlazaScreen(
                            onOpenSettings = { showSettings = true },
                            onOpenHistory = { showHistory = true },
                            onOpenStatistics = { showStatistics = true },
                            onOpenFavorites = { showFavorites = true }
                        )
                    }
                }
            }
        }
    }
}