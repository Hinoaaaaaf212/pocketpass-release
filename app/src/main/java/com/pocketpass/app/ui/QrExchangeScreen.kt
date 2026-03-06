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
import com.pocketpass.app.ui.theme.DarkText
import com.pocketpass.app.ui.theme.LightText
import com.pocketpass.app.ui.theme.MediumText
import com.pocketpass.app.ui.theme.OffWhite
import com.pocketpass.app.ui.theme.PocketPassGreen
import com.pocketpass.app.ui.theme.SkyBlue
import com.pocketpass.app.util.LocalSoundManager
import com.pocketpass.app.util.RegionFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "QrExchange"

@Composable
fun QrExchangeScreen(onBack: () -> Unit) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val db = remember { PocketPassDatabase.getDatabase(context) }
    val userPreferences = remember { UserPreferences(context) }

    // User profile data
    val userName by userPreferences.userNameFlow.collectAsState(initial = null)
    val avatarHex by userPreferences.avatarHexFlow.collectAsState(initial = "")
    val greeting by userPreferences.userGreetingFlow.collectAsState(initial = "Hello! Nice to meet you!")
    val origin by userPreferences.userOriginFlow.collectAsState(initial = "")
    val age by userPreferences.userAgeFlow.collectAsState(initial = "")
    val hobbies by userPreferences.userHobbiesFlow.collectAsState(initial = "")

    // Scan result state
    var scanResultName by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Stable userId for self-scan detection
    val userId = remember { UUID.randomUUID().toString() }

    // Build QR payload
    val qrPayloadJson = remember(userName, avatarHex, greeting, origin, age, hobbies) {
        if (userName.isNullOrBlank()) null
        else gson.toJson(ExchangePayload(
            userId = userId,
            userName = userName!!,
            avatarHex = avatarHex ?: "",
            greeting = greeting,
            origin = origin ?: "",
            age = age ?: "",
            hobbies = hobbies ?: ""
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

    // Process scanned QR content (shared by camera scan and image pick)
    fun processQrContent(contents: String) {
        coroutineScope.launch {
            try {
                val payload = gson.fromJson(contents, ExchangePayload::class.java)

                if (payload.userName.isBlank()) {
                    scanError = "Invalid QR code data"
                    return@launch
                }

                if (payload.userName == userName) {
                    scanError = "That's your own QR code!"
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val existing = db.encounterDao().getEncounterByUserName(payload.userName)
                    if (existing != null) {
                        db.encounterDao().updateEncounter(
                            existing.copy(
                                timestamp = System.currentTimeMillis(),
                                meetCount = existing.meetCount + 1
                            )
                        )
                    } else {
                        db.encounterDao().insertEncounter(Encounter(
                            encounterId = "qr_${System.currentTimeMillis()}",
                            timestamp = System.currentTimeMillis(),
                            otherUserAvatarHex = payload.avatarHex,
                            otherUserName = payload.userName,
                            greeting = payload.greeting,
                            origin = payload.origin,
                            age = payload.age,
                            hobbies = payload.hobbies
                        ))
                    }
                }

                scanResultName = payload.userName
                Log.d(TAG, "QR encounter saved: ${payload.userName}")

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
            gradientColors = listOf(PocketPassGreen, SkyBlue)
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
                                    modifier = Modifier.size(180.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = PocketPassGreen)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

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

    // Success dialog
    if (scanResultName != null) {
        AlertDialog(
            onDismissRequest = { scanResultName = null },
            title = { Text("New Friend!", fontWeight = FontWeight.Bold) },
            text = { Text("$scanResultName has been added to your plaza!") },
            confirmButton = {
                Button(
                    onClick = { soundManager.playSuccess(); scanResultName = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketPassGreen)
                ) {
                    Text("Awesome!", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Error dialog
    if (scanError != null) {
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text("Scan Failed", fontWeight = FontWeight.Bold) },
            text = { Text(scanError!!) },
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
