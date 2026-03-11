package com.pocketpass.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.service.ExchangePayload
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.FriendRepository
import com.pocketpass.app.data.SupabaseFriendship
import com.pocketpass.app.ui.theme.BackgroundGradient
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.LocalAppDimensions
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.ErrorText
import com.pocketpass.app.ui.theme.GreenText
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

private const val TAG = "QrExchange"

@Composable
fun QrExchangeScreen(onBack: () -> Unit) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val userPreferences = remember { UserPreferences(context) }
    val authRepo = remember { AuthRepository() }
    val friendRepo = remember { FriendRepository() }

    // User profile data
    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
    val avatarHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val greeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val origin by userPreferences.userOriginFlow.collectAsState(initial = "")
    val age by userPreferences.userAgeFlow.collectAsState(initial = "")
    val hobbies by userPreferences.userHobbiesFlow.collectAsState(initial = "")
    val selectedGames by userPreferences.selectedGamesFlow.collectAsState(initial = emptyList())

    // Scan result state
    var scanError by remember { mutableStateOf<String?>(null) }
    var saveStatus by remember { mutableStateOf("idle") } // idle, saving, saved, failed

    // Friend preview state after scanning
    var scannedPayload by remember { mutableStateOf<ExchangePayload?>(null) }
    var friendshipStatus by remember { mutableStateOf<String?>(null) } // null, "none", "pending_sent", "pending_received", "accepted", "sending", "sent"
    var friendshipId by remember { mutableStateOf<String?>(null) }
    var friendError by remember { mutableStateOf<String?>(null) }

    // Use the authenticated user's ID for the QR payload
    val userId = authRepo.currentUserId ?: ""

    // Build QR payload with auth-linked ID and selected games
    val gamesJson = remember(selectedGames) { gson.toJson(selectedGames) }
    val qrPayloadJson = remember(userName, avatarHex, greeting, origin, age, hobbies, userId, gamesJson) {
        if (userName.isNullOrBlank() || userId.isBlank()) null
        else gson.toJson(ExchangePayload(
            userId = userId,
            userName = userName!!,
            avatarHex = avatarHex ?: "",
            greeting = greeting,
            origin = origin ?: "",
            age = age ?: "",
            hobbies = hobbies ?: "",
            games = gamesJson
        ))
    }

    // Generate QR bitmap with themed colors
    val qrBitmap = remember(qrPayloadJson) {
        qrPayloadJson?.let { json ->
            try {
                val writer = com.google.zxing.MultiFormatWriter()
                val bitMatrix = writer.encode(json, BarcodeFormat.QR_CODE, 512, 512)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val fgColor = 0xFF2C2C2C.toInt() // DarkText
                val bgColor = 0xFFFFFFFF.toInt() // White
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) fgColor else bgColor)
                    }
                }
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate QR: ${e.message}", e)
                null
            }
        }
    }

    // Save QR launcher — opens system "Save As" picker
    val saveQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null && qrBitmap != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        val cardBitmap = buildCardBitmap(
                            qrBitmap = qrBitmap,
                            name = userName ?: "",
                            originText = origin ?: "",
                            regionFlag = RegionFlags.getFlagForRegion(origin ?: ""),
                            greetingText = greeting,
                            ageText = age ?: "",
                            hobbiesText = hobbies ?: ""
                        )
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        cardBitmap.recycle()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save QR card: ${e.message}", e)
                        false
                    }
                }
                saveStatus = if (saved) "saved" else "failed"
                if (saved) soundManager.playSuccess()
            }
        }
    }

    // Process scanned QR content — show preview instead of auto-adding
    fun processQrContent(contents: String) {
        coroutineScope.launch {
            try {
                if (contents.toByteArray(Charsets.UTF_8).size > ExchangePayload.MAX_PAYLOAD_SIZE_BYTES) {
                    scanError = "QR data too large"
                    return@launch
                }

                val payload = ExchangePayload.fromJsonSafe(contents, gson)

                if (payload == null) {
                    scanError = "Invalid QR code data"
                    return@launch
                }

                if (payload.userId == userId) {
                    scanError = "That's your own QR code!"
                    return@launch
                }

                // Show the scanned user's preview
                scannedPayload = payload

                // Check existing friendship status
                friendshipStatus = "none"
                friendshipId = null
                try {
                    val existing = withContext(Dispatchers.IO) {
                        friendRepo.getFriendshipWith(payload.userId)
                    }
                    if (existing != null) {
                        friendshipId = existing.id
                        friendshipStatus = when {
                            existing.status == "accepted" -> "accepted"
                            existing.requesterId == userId -> "pending_sent"
                            else -> "pending_received"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check friendship: ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse QR: ${e.message}", e)
                scanError = "Not a valid PocketPass QR code"
            }
        }
    }

    // Camera QR Scanner launcher
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        processQrContent(contents)
    }

    // Image picker launcher - decode QR from gallery image
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap == null) {
                        null
                    } else {
                        val width = bitmap.width
                        val height = bitmap.height
                        val pixels = IntArray(width * height)
                        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                        val source = RGBLuminanceSource(width, height, pixels)
                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                        val reader = MultiFormatReader()
                        val result = reader.decode(binaryBitmap)
                        result.text
                    }
                }

                if (decoded != null) {
                    processQrContent(decoded)
                } else {
                    scanError = "Could not read the image"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode QR from image: ${e.message}", e)
                scanError = "No QR code found in this image"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CheckeredBackground(
            modifier = Modifier.fillMaxSize(),
            gradientColors = BackgroundGradient
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { soundManager.playBack(); onBack() }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = DarkText)
                }
                Text(
                    text = "QR Exchange",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Main content - horizontal layout for landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left side: Profile card with QR
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = OffWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Green header strip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PocketPassGreen, Color(0xFF9BD85B))
                                    )
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Mii avatar
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!avatarHex.isNullOrBlank()) {
                                        MiiAvatarViewer(hexData = avatarHex ?: "")
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = userName ?: "Loading...",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (!origin.isNullOrBlank()) {
                                        Text(
                                            text = "${RegionFlags.getFlagForRegion(origin ?: "")} ${origin}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // QR Code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Your QR Code",
                                    modifier = Modifier.size(LocalAppDimensions.current.qrCodeSize),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(LocalAppDimensions.current.qrCodeSize),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = PocketPassGreen)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save to Photos button
                        if (qrBitmap != null) {
                            Button(
                                onClick = {
                                    soundManager.playTap()
                                    saveStatus = "idle"
                                    saveQrLauncher.launch("PocketPass_QR_${userName ?: "code"}.png")
                                },
                                modifier = Modifier.fillMaxWidth(0.7f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (saveStatus == "saved") PocketPassGreen.copy(alpha = 0.7f) else PocketPassGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = when (saveStatus) {
                                        "saved" -> "Saved!"
                                        "failed" -> "Save Failed - Retry"
                                        else -> "Save to Photos"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Profile info
                        Text(
                            text = "\"${greeting}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MediumText,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (!age.isNullOrBlank() || !hobbies.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (!age.isNullOrBlank()) {
                                    Text(
                                        text = "Age: $age",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LightText
                                    )
                                }
                                if (!age.isNullOrBlank() && !hobbies.isNullOrBlank()) {
                                    Text(
                                        text = "  •  ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LightText
                                    )
                                }
                                if (!hobbies.isNullOrBlank()) {
                                    Text(
                                        text = hobbies ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LightText
                                    )
                                }
                            }
                        }
                    }
                }

                // Right side: Actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Add a Friend",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Show your card to a friend, or scan theirs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MediumText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Scan with camera button
                    Button(
                        onClick = {
                            soundManager.playNavigate()
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a friend's PocketPass QR code")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                            scanLauncher.launch(options)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketPassGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Scan with Camera",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Upload image button
                    OutlinedButton(
                        onClick = {
                            soundManager.playNavigate()
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DarkText
                        ),
                        border = ButtonDefaults.outlinedButtonBorder
                    ) {
                        Text(
                            text = "Upload QR Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "You can also upload a screenshot\nof someone's QR code",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Friend preview dialog after scanning
    if (scannedPayload != null) {
        val payload = scannedPayload!!
        AlertDialog(
            onDismissRequest = { scannedPayload = null; friendshipStatus = null; friendError = null },
            title = {
                Text("PocketPass User", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Mii Avatar
                    if (payload.avatarHex.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE3F2FD)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiiAvatarViewer(hexData = payload.avatarHex)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = payload.userName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )

                    if (payload.origin.isNotBlank()) {
                        Text(
                            text = "${RegionFlags.getFlagForRegion(payload.origin)} ${payload.origin}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText
                        )
                    }

                    if (payload.greeting.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"${payload.greeting}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediumText,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (payload.hobbies.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = payload.hobbies,
                            style = MaterialTheme.typography.bodySmall,
                            color = LightText
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Friendship action
                    when (friendshipStatus) {
                        "accepted" -> {
                            Text(
                                text = "You're already friends!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GreenText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        "pending_sent" -> {
                            Text(
                                text = "Friend request already sent",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MediumText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        "pending_received" -> {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        friendshipStatus = "sending"
                                        val result = friendRepo.acceptFriendRequest(friendshipId!!)
                                        result.onSuccess {
                                            soundManager.playSuccess()
                                            friendshipStatus = "accepted"
                                            friendError = null
                                            // Also save encounter locally
                                            withContext(Dispatchers.IO) {
                                                val existing = db.encounterDao().getEncounterByUserName(payload.userName)
                                                if (existing == null) {
                                                    db.encounterDao().insertEncounter(Encounter(
                                                        encounterId = "qr_${System.currentTimeMillis()}",
                                                        timestamp = System.currentTimeMillis(),
                                                        otherUserAvatarHex = payload.avatarHex,
                                                        otherUserName = payload.userName,
                                                        otherUserId = payload.userId,
                                                        greeting = payload.greeting,
                                                        origin = payload.origin,
                                                        age = payload.age,
                                                        hobbies = payload.hobbies,
                                                        games = payload.games
                                                    ))
                                                }
                                            }
                                        }.onFailure { e ->
                                            Log.e(TAG, "Failed to accept friend request", e)
                                            soundManager.playError()
                                            friendError = e.message ?: "Failed to accept request"
                                            friendshipStatus = "pending_received"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PocketPassGreen,
                                    contentColor = Color.White
                                ),
                                enabled = friendshipStatus != "sending"
                            ) {
                                Text("Accept Friend Request", fontWeight = FontWeight.Bold)
                            }
                        }
                        "sent" -> {
                            Text(
                                text = "Friend request sent!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GreenText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        "sending" -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PocketPassGreen,
                                strokeWidth = 2.dp
                            )
                        }
                        else -> {
                            // "none" — can send friend request
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        friendshipStatus = "sending"
                                        val result = friendRepo.sendFriendRequest(payload.userId)
                                        result.onSuccess {
                                            soundManager.playSuccess()
                                            friendshipStatus = "sent"
                                            friendError = null
                                            // Save encounter locally
                                            withContext(Dispatchers.IO) {
                                                val existing = db.encounterDao().getEncounterByUserName(payload.userName)
                                                if (existing == null) {
                                                    db.encounterDao().insertEncounter(Encounter(
                                                        encounterId = "qr_${System.currentTimeMillis()}",
                                                        timestamp = System.currentTimeMillis(),
                                                        otherUserAvatarHex = payload.avatarHex,
                                                        otherUserName = payload.userName,
                                                        otherUserId = payload.userId,
                                                        greeting = payload.greeting,
                                                        origin = payload.origin,
                                                        age = payload.age,
                                                        hobbies = payload.hobbies,
                                                        games = payload.games
                                                    ))
                                                }
                                            }
                                        }.onFailure { e ->
                                            Log.e(TAG, "Failed to send friend request", e)
                                            soundManager.playError()
                                            friendError = e.message ?: "Failed to send request"
                                            friendshipStatus = "none"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PocketPassGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Add Friend", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Show error message if friend request failed
                    if (friendError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = friendError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scannedPayload = null
                    friendshipStatus = null
                    friendError = null
                }) {
                    Text("Close", color = DarkText)
                }
            }
        )
    }

    // Error dialog
    if (scanError != null) {
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Scan Failed", fontWeight = FontWeight.Bold, color = ErrorText) },
            text = { Text(scanError!!, color = ErrorText) },
            confirmButton = {
                Button(
                    onClick = { soundManager.playBack(); scanError = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** Renders a profile card bitmap matching the on-screen QR card preview. */
private fun buildCardBitmap(
    qrBitmap: Bitmap,
    name: String,
    originText: String,
    regionFlag: String,
    greetingText: String,
    ageText: String,
    hobbiesText: String
): Bitmap {
    val cardWidth = 600
    val padding = 40f
    val cornerRadius = 48f
    val headerHeight = 120f
    val qrSize = 360
    val qrPadding = 16f

    // Pre-calculate text heights for dynamic card sizing
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 40f; typeface = Typeface.DEFAULT_BOLD
    }
    val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE6FFFFFF.toInt(); textSize = 28f
    }
    val greetingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt(); textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    }
    val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF999999.toInt(); textSize = 26f
    }
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAAAAA.toInt(); textSize = 22f; textAlign = Paint.Align.CENTER
    }

    // Build info line
    val infoLine = buildString {
        if (ageText.isNotBlank()) append("Age: $ageText")
        if (ageText.isNotBlank() && hobbiesText.isNotBlank()) append("  •  ")
        if (hobbiesText.isNotBlank()) append(hobbiesText)
    }

    // Calculate total card height
    var totalHeight = padding // top padding
    totalHeight += headerHeight // green header
    totalHeight += 24f // spacer
    totalHeight += qrSize + qrPadding * 2 // QR with padding
    totalHeight += 20f // spacer
    if (greetingText.isNotBlank()) totalHeight += 44f // greeting
    if (infoLine.isNotBlank()) totalHeight += 40f // info line
    totalHeight += 32f // spacer before brand
    totalHeight += 30f // brand text
    totalHeight += padding // bottom padding

    val cardHeight = totalHeight.toInt()
    val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Card background (white with rounded corners)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAFAFA.toInt() }
    canvas.drawRoundRect(RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat()), cornerRadius, cornerRadius, bgPaint)

    // Green header gradient
    val headerRect = RectF(padding, padding, cardWidth - padding, padding + headerHeight)
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            headerRect.left, 0f, headerRect.right, 0f,
            0xFF4CAF50.toInt(), 0xFF9BD85B.toInt(),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRoundRect(headerRect, 32f, 32f, headerPaint)

    // Name on header
    val nameX = padding + 24f
    val nameY = padding + headerHeight / 2f - 4f
    canvas.drawText(name, nameX, nameY, namePaint)

    // Origin under name
    if (originText.isNotBlank()) {
        val originStr = "$regionFlag $originText"
        canvas.drawText(originStr, nameX, nameY + 36f, originPaint)
    }

    // QR code (centered, white background box)
    var y = padding + headerHeight + 24f
    val qrBoxLeft = (cardWidth - qrSize - qrPadding * 2) / 2f
    val qrBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    canvas.drawRoundRect(
        RectF(qrBoxLeft, y, qrBoxLeft + qrSize + qrPadding * 2, y + qrSize + qrPadding * 2),
        24f, 24f, qrBoxPaint
    )
    val scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
    canvas.drawBitmap(scaledQr, qrBoxLeft + qrPadding, y + qrPadding, null)
    scaledQr.recycle()

    y += qrSize + qrPadding * 2 + 20f

    // Greeting
    if (greetingText.isNotBlank()) {
        val quoted = "\"$greetingText\""
        val greetingWidth = greetingPaint.measureText(quoted)
        canvas.drawText(quoted, (cardWidth - greetingWidth) / 2f, y + 30f, greetingPaint)
        y += 44f
    }

    // Age / Hobbies info
    if (infoLine.isNotBlank()) {
        val infoWidth = infoPaint.measureText(infoLine)
        canvas.drawText(infoLine, (cardWidth - infoWidth) / 2f, y + 28f, infoPaint)
        y += 40f
    }

    // Brand footer
    y += 32f
    canvas.drawText("PocketPass", cardWidth / 2f, y, brandPaint)

    return bitmap
}
