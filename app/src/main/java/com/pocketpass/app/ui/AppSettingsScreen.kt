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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.pocketpass.app.data.AppUpdateRepository
import com.pocketpass.app.data.AppVersion
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.DownloadProgress
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.service.ProximityService
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.pocketpass.app.util.AynThorDevice
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(onBack: () -> Unit, onOpenMii3DTest: () -> Unit = {}) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }

    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
    val proximityEnabled by userPreferences.proximityEnabledFlow.collectAsState(initial = true)
    val sfxEnabled by userPreferences.sfxEnabledFlow.collectAsState(initial = true)
    val sfxVolume by userPreferences.sfxVolumeFlow.collectAsState(initial = 0.5f)

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
                initialOffsetX = { it / 3 },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(250))
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
                run {
                    val serviceStatus by ProximityService.serviceStatus.collectAsState()

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
                                text = if (!proximityEnabled) "Bluetooth scanning is off. Turn on to meet people nearby."
                                       else if (serviceStatus == "missing permissions") "Bluetooth scanning is enabled but cannot start."
                                       else "Bluetooth scanning is active. You'll meet people nearby automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )

                            // Warning when service can't work due to missing permissions
                            if (proximityEnabled && serviceStatus == "missing permissions") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFF3E0))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Location permission is required for Bluetooth discovery. Please grant Location access in your device's app settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dual Screen Mode (only visible on Ayn Thor)
                if (AynThorDevice.isAynThor()) {
                    val dualScreenEnabled by userPreferences.dualScreenModeFlow.collectAsState(initial = true)

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
                                    text = "Dual Screen Mode",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Switch(
                                    checked = dualScreenEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                        coroutineScope.launch {
                                            userPreferences.saveDualScreenMode(enabled)
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
                                text = if (dualScreenEnabled)
                                    "Using both screens. Each tab shows extra info on the bottom screen."
                                else
                                    "Using single screen only. Bottom screen is inactive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                        }
                    }
                }

                // Dark Mode
                run {
                    val darkModeEnabled by userPreferences.darkModeFlow.collectAsState(initial = false)

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
                                    text = "Dark Mode",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Switch(
                                    checked = darkModeEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                        coroutineScope.launch {
                                            userPreferences.saveDarkMode(enabled)
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
                                text = if (darkModeEnabled) "Using dark theme."
                                       else "Using light theme.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                        }
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

                // Cloud Sync
                run {
                    val authRepo = remember { AuthRepository() }
                    val isLoggedIn by authRepo.isLoggedIn.collectAsState(initial = false)

                    if (isLoggedIn) {
                        var isSyncing by remember { mutableStateOf(false) }
                        var syncResult by remember { mutableStateOf<String?>(null) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(OffWhite)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Cloud Sync",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (syncResult != null) syncResult!!
                                    else "Sync your profile, encounters, and stats to the cloud.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (syncResult != null) GreenText else MediumText
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        isSyncing = true
                                        syncResult = null
                                        coroutineScope.launch {
                                            try {
                                                SyncRepository(context).fullSync()
                                                syncResult = "Sync complete!"
                                                soundManager.playSuccess()
                                            } catch (e: Exception) {
                                                syncResult = "Sync failed: ${e.message}"
                                                soundManager.playError()
                                            }
                                            isSyncing = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isSyncing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SkyBlue
                                    )
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = OffWhite,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        if (isSyncing) "Syncing..." else "Sync Now",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 3D Mii Rendering Toggle
                run {
                    val enable3d by userPreferences.enable3dMiisFlow.collectAsState(initial = true)

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
                                    text = "3D Mii Rendering",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Switch(
                                    checked = enable3d,
                                    onCheckedChange = { enabled ->
                                        if (enabled) soundManager.playToggleOn() else soundManager.playToggleOff()
                                        coroutineScope.launch {
                                            userPreferences.saveEnable3dMiis(enabled)
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
                                text = if (enable3d) "Miis are rendered as 3D models in the plaza."
                                else "Miis are rendered as 2D images. Use this on low-end devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                        }
                    }
                }

                // 3D Mii Test (Developer)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OffWhite)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "3D Mii Viewer",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Preview 3D body models, hats, and costumes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MediumText
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                soundManager.playSelect()
                                onOpenMii3DTest()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PocketPassGreen
                            )
                        ) {
                            Text(
                                "Open 3D Viewer",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // App Updates
                run {
                    val updateRepo = remember { AppUpdateRepository(context) }
                    var updateState by remember { mutableStateOf<UpdateCardState>(UpdateCardState.Checking) }
                    var downloadId by remember { mutableStateOf(-1L) }
                    var showPermissionDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val update = updateRepo.checkForUpdate()
                        updateState = if (update != null) {
                            UpdateCardState.Available(update)
                        } else {
                            UpdateCardState.UpToDate
                        }
                    }

                    // Poll download progress
                    LaunchedEffect(downloadId) {
                        if (downloadId < 0) return@LaunchedEffect
                        while (true) {
                            delay(500)
                            when (val progress = updateRepo.getDownloadProgress(downloadId)) {
                                is DownloadProgress.Downloading -> {
                                    updateState = UpdateCardState.Downloading(progress.progress)
                                }
                                is DownloadProgress.Complete -> {
                                    updateState = UpdateCardState.ReadyToInstall
                                    break
                                }
                                is DownloadProgress.Failed -> {
                                    updateState = UpdateCardState.Error("Download failed")
                                    break
                                }
                            }
                        }
                    }

                    if (showPermissionDialog) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog = false },
                            title = { Text("Permission Required", fontWeight = FontWeight.Bold) },
                            text = { Text("To install updates, PocketPass needs permission to install apps from unknown sources. Tap the button below to open settings and enable it.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPermissionDialog = false
                                    updateRepo.openInstallPermissionSettings()
                                }) {
                                    Text("Open Settings", color = PocketPassGreen)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog = false }) {
                                    Text("Cancel", color = MediumText)
                                }
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OffWhite)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            when (val state = updateState) {
                                is UpdateCardState.Checking -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = PocketPassGreen,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Checking for updates...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MediumText
                                        )
                                    }
                                }
                                is UpdateCardState.UpToDate -> {
                                    Text(
                                        text = "You're up to date — v${updateRepo.getCurrentVersionName()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenText
                                    )
                                }
                                is UpdateCardState.Available -> {
                                    Text(
                                        text = "v${state.version.versionName} is available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                    if (state.version.changelog.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = state.version.changelog,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MediumText
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            soundManager.playSelect()
                                            downloadId = updateRepo.downloadApk(state.version)
                                            updateState = UpdateCardState.Downloading(0f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PocketPassGreen
                                        )
                                    ) {
                                        Text("Download Update", fontWeight = FontWeight.Bold)
                                    }
                                }
                                is UpdateCardState.Downloading -> {
                                    Text(
                                        text = "Downloading... ${(state.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediumText
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    @Suppress("DEPRECATION")
                                    LinearProgressIndicator(
                                        progress = state.progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = PocketPassGreen,
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                }
                                is UpdateCardState.ReadyToInstall -> {
                                    Text(
                                        text = "Update downloaded and ready to install.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GreenText
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            soundManager.playSelect()
                                            if (updateRepo.canInstallPackages()) {
                                                updateRepo.installApk()
                                            } else {
                                                showPermissionDialog = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PocketPassGreen
                                        )
                                    ) {
                                        Text("Install Update", fontWeight = FontWeight.Bold)
                                    }
                                }
                                is UpdateCardState.Error -> {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorText
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            soundManager.playSelect()
                                            updateState = UpdateCardState.Checking
                                            coroutineScope.launch {
                                                val update = updateRepo.checkForUpdate()
                                                updateState = if (update != null) {
                                                    UpdateCardState.Available(update)
                                                } else {
                                                    UpdateCardState.UpToDate
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SkyBlue
                                        )
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
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

                // Delete Account
                run {
                val deleteAuthRepo = remember { AuthRepository() }
                val isLoggedInForDelete by deleteAuthRepo.isLoggedIn.collectAsState(initial = false)
                if (isLoggedInForDelete) {
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    var isDeleting by remember { mutableStateOf(false) }
                    var deleteError by remember { mutableStateOf<String?>(null) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(OffWhite)
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Delete Account",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Permanently deletes your account, profile, friends, messages, encounters, and all associated data. This cannot be undone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (!showDeleteConfirm) {
                                Button(
                                    onClick = {
                                        soundManager.playDelete()
                                        showDeleteConfirm = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFC62828)
                                    )
                                ) {
                                    Text(
                                        "Delete Account",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Are you sure? This is permanent.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { showDeleteConfirm = false },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Cancel", color = DarkText)
                                    }
                                    Button(
                                        onClick = {
                                            isDeleting = true
                                            deleteError = null
                                            coroutineScope.launch {
                                                val authRepo = AuthRepository()
                                                val result = authRepo.deleteAccount()
                                                result.fold(
                                                    onSuccess = {
                                                        // Wipe all local data
                                                        userPreferences.clearAll()
                                                        val db = com.pocketpass.app.data.PocketPassDatabase.getDatabase(context)
                                                        db.encounterDao().deleteAllEncounters()
                                                        db.messageDao().deleteAll()
                                                        showDeleteConfirm = false
                                                    },
                                                    onFailure = { e ->
                                                        deleteError = e.message ?: "Failed to delete account"
                                                    }
                                                )
                                                isDeleting = false
                                            }
                                        },
                                        enabled = !isDeleting,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFC62828)
                                        )
                                    ) {
                                        if (isDeleting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Delete Forever", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                deleteError?.let { error ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
        } // AnimatedVisibility
    }
}

private sealed class UpdateCardState {
    data object Checking : UpdateCardState()
    data object UpToDate : UpdateCardState()
    data class Available(val version: AppVersion) : UpdateCardState()
    data class Downloading(val progress: Float) : UpdateCardState()
    data object ReadyToInstall : UpdateCardState()
    data class Error(val message: String) : UpdateCardState()
}
