package com.pocketpass.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.util.LocalSoundManager

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(
    onPermissionsGranted: () -> Unit
) {
    val permissionsToRequest = mutableListOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
        permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissionsToRequest.add(android.Manifest.permission.BLUETOOTH)
        permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_ADMIN)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // Step counter for earning tokens by walking (Android 10+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissionsToRequest.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    if (permissionsState.allPermissionsGranted) {
        // Request battery optimization exemption so the proximity service isn't killed
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) { }
            }
            onPermissionsGranted()
        }
    } else {
        PermissionsRequestUI(permissionsState = permissionsState)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsRequestUI(permissionsState: MultiplePermissionsState) {
    val soundManager = LocalSoundManager.current
    val backgroundBrush = Brush.verticalGradient(
        colors = BackgroundGradient
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        AeroCard(
            modifier = Modifier.fillMaxWidth(0.85f),
            cornerRadius = 32.dp,
            containerColor = OffWhite
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to PocketPass!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We need a few permissions to get started:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PermissionExplanation("Bluetooth", "Find and connect with people nearby")
                    PermissionExplanation("Location", "Discover friends in your area")
                    PermissionExplanation("Notifications", "Know when someone is nearby")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PermissionExplanation("Activity", "Count your steps to earn tokens")
                    }
                    PermissionExplanation("Battery", "Keep scanning in the background")
                }

                Spacer(modifier = Modifier.height(24.dp))

                AeroButton(
                    onClick = { soundManager.playSuccess(); permissionsState.launchMultiplePermissionRequest() },
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "Let's Go!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionExplanation(label: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MediumText
        )
    }
}