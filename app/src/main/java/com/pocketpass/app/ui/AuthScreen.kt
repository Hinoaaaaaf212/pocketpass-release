package com.pocketpass.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.ui.theme.AeroButton
import com.pocketpass.app.ui.theme.AeroCard
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.gamepadFocusable
import kotlinx.coroutines.launch

/**
 * @param setupMode When true, this screen is shown during initial setup.
 *   Sign-in will restore profile from cloud. Back button becomes "Skip".
 * @param onBack Called when user presses back/skip.
 * @param onAuthSuccess Called after successful auth (and restore, if setupMode).
 */
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onAuthSuccess: () -> Unit,
    setupMode: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val soundManager = LocalSoundManager.current
    val authRepo = remember { AuthRepository() }
    val syncRepo = remember { SyncRepository(context) }
    val userPreferences = remember { UserPreferences(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(!setupMode) } // Default to sign-in in setup mode
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

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
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!setupMode) {
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
                    }
                    Text(
                        text = if (isSignUp) "Create Account" else "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        containerColor = OffWhite
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isSignUp) "Sign up to sync your data" else "Welcome back!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your data is saved locally and synced to the cloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediumText
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = { newVal ->
                                    username = newVal.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                                    errorMessage = null
                                },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it.take(64); errorMessage = null },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (isSignUp) {
                                Text(
                                    text = "Username: 3-20 chars, alphanumeric. Password: 6+ chars.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediumText
                                )
                            }

                            // Error message
                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorText
                                )
                            }

                            // Success message
                            if (successMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = successMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GreenText
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Submit button
                            AeroButton(
                                onClick = {
                                    errorMessage = null
                                    successMessage = null

                                    // Check internet connectivity
                                    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                    val network = cm.activeNetwork
                                    val caps = network?.let { cm.getNetworkCapabilities(it) }
                                    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                                    if (!hasInternet) {
                                        errorMessage = "No internet connection. Please connect to the internet to create an account."
                                        return@AeroButton
                                    }

                                    // Validate
                                    if (username.length < 3) {
                                        errorMessage = "Username must be at least 3 characters"
                                        return@AeroButton
                                    }
                                    if (password.length < 6) {
                                        errorMessage = "Password must be at least 6 characters"
                                        return@AeroButton
                                    }

                                    isLoading = true
                                    coroutineScope.launch {
                                        val result = if (isSignUp) {
                                            authRepo.signUp(username, password, context)
                                        } else {
                                            authRepo.signIn(username, password, context)
                                        }

                                        result.onSuccess {
                                            if (setupMode && !isSignUp) {
                                                // Setup mode sign-in: restore profile from cloud
                                                successMessage = "Restoring your data..."
                                                val restoreResult = syncRepo.restoreFromCloud()
                                                restoreResult.onSuccess {
                                                    soundManager.playSuccess()
                                                    isLoading = false
                                                    onAuthSuccess()
                                                }.onFailure { re ->
                                                    // Restore failed but auth succeeded — continue anyway
                                                    soundManager.playSuccess()
                                                    isLoading = false
                                                    errorMessage = "Signed in, but restore failed: ${re.message}"
                                                    // Still navigate — they can sync later
                                                    onAuthSuccess()
                                                }
                                            } else {
                                                // Save signup username as display name
                                                if (isSignUp) {
                                                    userPreferences.saveUserName(username.trim())
                                                }
                                                soundManager.playSuccess()
                                                // Run full sync after auth
                                                try {
                                                    syncRepo.fullSync()
                                                } catch (_: Exception) {
                                                    // If full sync fails, at least push profile
                                                    try { syncRepo.syncProfile() } catch (_: Exception) { }
                                                }
                                                isLoading = false
                                                onAuthSuccess()
                                            }
                                        }.onFailure { e ->
                                            isLoading = false
                                            soundManager.playError()
                                            errorMessage = when {
                                                e.message?.contains("already", ignoreCase = true) == true ->
                                                    "Username already taken"
                                                e.message?.contains("invalid", ignoreCase = true) == true ->
                                                    "Invalid username or password"
                                                e.message?.contains("network", ignoreCase = true) == true ->
                                                    "Network error. Check your connection."
                                                e.message?.contains("too many", ignoreCase = true) == true ->
                                                    "Too many attempts. Try again later."
                                                else -> e.message ?: "Something went wrong"
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = !isLoading && username.length >= 3 && password.length >= 6,
                                containerColor = PocketPassGreen,
                                contentColor = OffWhite,
                                cornerRadius = 12.dp
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = OffWhite,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isSignUp) "Create Account" else "Sign In",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Toggle sign up / sign in
                            TextButton(
                                onClick = {
                                    soundManager.playTap()
                                    isSignUp = !isSignUp
                                    errorMessage = null
                                    successMessage = null
                                }
                            ) {
                                Text(
                                    text = if (isSignUp) "Already have an account? Sign In"
                                    else "Don't have an account? Sign Up",
                                    color = GreenText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
