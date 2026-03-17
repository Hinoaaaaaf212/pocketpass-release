package com.pocketpass.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketpass.app.MainActivity
import com.google.gson.Gson
import com.pocketpass.app.data.Encounter
import com.pocketpass.app.data.PocketPassDatabase
import com.pocketpass.app.data.PuzzlePanels
import com.pocketpass.app.data.TokenSystem
import com.pocketpass.app.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.pocketpass.app.util.SoundManager
import com.pocketpass.app.data.AuthRepository
import com.pocketpass.app.data.SyncRepository
import java.util.UUID
import java.util.Collections
import com.pocketpass.app.data.crypto.CryptoManager

import android.content.pm.ServiceInfo

class ProximityService : Service(), android.hardware.SensorEventListener {

    companion object {
        /** Observable state of nearby discovered devices, for debug UI. */
        data class NearbyDevice(
            val endpointId: String,
            val name: String,
            val discoveredAt: Long = System.currentTimeMillis(),
            val state: String = "discovered" // discovered, connecting, connected, exchanged
        )

        private val _nearbyDevices = kotlinx.coroutines.flow.MutableStateFlow<Map<String, NearbyDevice>>(emptyMap())
        val nearbyDevices: kotlinx.coroutines.flow.StateFlow<Map<String, NearbyDevice>> = _nearbyDevices

        private val _serviceStatus = kotlinx.coroutines.flow.MutableStateFlow("stopped")
        val serviceStatus: kotlinx.coroutines.flow.StateFlow<String> = _serviceStatus
    }

    private val TAG = "PocketPassProximity"
    private val FOREGROUND_CHANNEL_ID = "pocketpass_proximity_channel"
    private val ENCOUNTER_CHANNEL_ID = "pocketpass_encounter_channel"
    private var encounterNotificationId = 100

    private var bleHandler: BleProximityHandler? = null
    private var soundManager: SoundManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // Step counter for earning tokens (batched: only process every STEP_BATCH_SIZE steps)
    private var sensorManager: android.hardware.SensorManager? = null
    private var lastProcessedSteps: Int = -1
    private val STEP_BATCH_SIZE = 10
    private lateinit var userPrefs: UserPreferences
    private lateinit var database: PocketPassDatabase

    // Track endpoint names we've already exchanged with to avoid duplicates in one session
    private val exchangedNames: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // Default user info if not completely set up yet
    private var myUserId: String = UUID.randomUUID().toString()
    private var myUserName: String = "Stranger"
    private var myAvatarHex: String = ""
    private var myGreeting: String = "Hello from PocketPass!"
    private var myOrigin: String = ""
    private var myAge: String = ""
    private var myHobbies: String = ""
    private var myGames: String = ""
    private var myHat: String = ""
    private var myCostume: String = ""
    private var myIsMale: Boolean = true

    override fun onCreate() {
        super.onCreate()
        soundManager = SoundManager(applicationContext)
        _serviceStatus.value = "starting"
        startForegroundService()

        serviceScope.launch {
            loadUserProfile()
            startBle()
        }

        // Periodically clean up stale "connecting" entries from the debug device list
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val now = System.currentTimeMillis()
                val staleThreshold = 60_000L // 60 seconds
                _nearbyDevices.value = _nearbyDevices.value.filterValues { device ->
                    // Keep "exchanged" entries and recent entries
                    device.state == "exchanged" || (now - device.discoveredAt) < staleThreshold
                }
            }
        }

        // Init shared instances
        userPrefs = UserPreferences(applicationContext)
        database = PocketPassDatabase.getDatabase(applicationContext)

        // Register step counter sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            sensorManager?.registerListener(this, stepSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Step counter sensor registered")
        } else {
            Log.w(TAG, "Step counter sensor not available on this device")
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Silent channel for the ongoing foreground service
            manager.createNotificationChannel(NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Proximity Scanning",
                NotificationManager.IMPORTANCE_LOW
            ))

            // Encounter alerts channel (pops up with sound)
            manager.createNotificationChannel(NotificationChannel(
                ENCOUNTER_CHANNEL_ID,
                "New Encounters",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you encounter a new person nearby"
            })
        }

        val notification: Notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("PocketPass is active")
            .setContentText("Looking for friends nearby...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(1, notification)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        } else {
            startForeground(1, notification)
        }
    }

    private suspend fun loadUserProfile() {
        try {
            val hex = userPrefs.avatarHexFlow.firstOrNull()
            if (hex != null) myAvatarHex = hex

            val name = userPrefs.userNameFlow.firstOrNull()
            if (!name.isNullOrBlank()) myUserName = name

            val age = userPrefs.userAgeFlow.firstOrNull()
            if (age != null) myAge = age

            val hobbies = userPrefs.userHobbiesFlow.firstOrNull()
            if (hobbies != null) myHobbies = hobbies

            val greeting = userPrefs.userGreetingFlow.firstOrNull()
            if (!greeting.isNullOrBlank()) myGreeting = greeting

            val origin = userPrefs.userOriginFlow.firstOrNull()
            if (!origin.isNullOrBlank()) myOrigin = origin

            val games = userPrefs.selectedGamesFlow.firstOrNull()
            if (!games.isNullOrEmpty()) {
                myGames = Gson().toJson(games)
            }

            val hat = userPrefs.selectedHatFlow.firstOrNull()
            if (!hat.isNullOrBlank()) myHat = hat

            val costume = userPrefs.selectedCostumeFlow.firstOrNull()
            if (!costume.isNullOrBlank()) myCostume = costume

            // Determine gender from avatar data for body model selection
            if (myAvatarHex.isNotBlank()) {
                myIsMale = com.pocketpass.app.rendering.MiiStudioDecoder.isMale(myAvatarHex)
            }

            // Use Supabase auth ID if logged in, so both devices share the same userId
            try {
                val authId = AuthRepository().currentUserId
                if (!authId.isNullOrBlank()) myUserId = authId
            } catch (_: Exception) { }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load user profile, using defaults", e)
        }

        Log.d(TAG, "User profile loaded: name=$myUserName")
    }

    private fun buildExchangePayloadJson(): String {
        return gson.toJson(ExchangePayload(
            userId = myUserId,
            userName = myUserName,
            avatarHex = myAvatarHex,
            greeting = myGreeting,
            origin = myOrigin,
            age = myAge,
            hobbies = myHobbies,
            games = myGames,
            hatId = myHat,
            costumeId = myCostume,
            isMale = myIsMale
        ))
    }

    private fun startBle() {
        Log.d(TAG, "Starting BLE proximity")

        bleHandler = BleProximityHandler(
            context = applicationContext,
            onPayloadReceived = { jsonStr ->
                serviceScope.launch {
                    try {
                        val payload = ExchangePayload.fromJsonSafe(jsonStr, gson)
                        if (payload == null) {
                            Log.w(TAG, "BLE: Invalid or oversized payload, ignoring")
                            return@launch
                        }

                        // Skip same account
                        if (payload.userId.isNotBlank() && payload.userId == myUserId) {
                            Log.d(TAG, "BLE: Same account detected, ignoring")
                            return@launch
                        }

                        // Skip if already exchanged this session
                        if (payload.userName in exchangedNames) return@launch
                        exchangedNames.add(payload.userName)

                        saveEncounterToRoom(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "BLE: Failed to parse payload", e)
                    }
                }
            },
            onStatusChanged = { adv, scan ->
                _serviceStatus.value = when {
                    adv && scan -> "advertising + scanning"
                    adv -> "advertising"
                    scan -> "scanning"
                    else -> "failed"
                }
            },
            onDeviceFound = { address, state ->
                if (state == "timeout") {
                    // Remove timed-out devices instead of keeping stale entries
                    _nearbyDevices.value = _nearbyDevices.value - address
                } else {
                    _nearbyDevices.value = _nearbyDevices.value + (address to NearbyDevice(
                        endpointId = address,
                        name = address,
                        state = state
                    ))
                }
            },
            onError = { error ->
                Log.e(TAG, "BLE error: $error")
            },
            getMyPayload = { buildExchangePayloadJson() }
        )

        bleHandler?.start()
    }

    private fun encryptField(value: String): String {
        if (!CryptoManager.isInitialized || value.isBlank()) return value
        return try { CryptoManager.encryptForSelf(value) } catch (_: Exception) { value }
    }

    private fun saveEncounterToRoom(payload: ExchangePayload) {
        serviceScope.launch {
            val db = database
            val existing = db.encounterDao().getEncounterByUserName(payload.userName)

            if (existing != null) {
                // Already encountered — update timestamp and bump meet count
                val updated = existing.copy(
                    timestamp = System.currentTimeMillis(),
                    meetCount = existing.meetCount + 1,
                    needsSync = true,
                    otherUserId = if (existing.otherUserId.isEmpty() && payload.userId.isNotBlank()) payload.userId else existing.otherUserId
                )
                db.encounterDao().updateEncounter(updated)
                Log.d(TAG, "Encounter updated for returning user: ${payload.userName}")

                // Push to cloud
                try {
                    if (AuthRepository().currentUserId != null) {
                        SyncRepository(applicationContext).pushEncounter(updated)
                    }
                } catch (_: Exception) { }
            } else {
                // Genuinely new encounter — encrypt sensitive fields before Room insert
                val newEncounter = Encounter(
                    encounterId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    otherUserAvatarHex = encryptField(payload.avatarHex),
                    otherUserName = payload.userName, // Keep plaintext for lookup/dedup
                    greeting = encryptField(payload.greeting),
                    origin = encryptField(payload.origin),
                    age = encryptField(payload.age),
                    hobbies = encryptField(payload.hobbies),
                    games = encryptField(payload.games),
                    needsSync = true,
                    otherUserId = payload.userId,
                    hatId = payload.hatId,
                    costumeId = payload.costumeId,
                    isMale = payload.isMale
                )
                db.encounterDao().insertEncounter(newEncounter)
                Log.d(TAG, "New encounter saved for user: ${payload.userName}")

                // Push to cloud
                try {
                    if (AuthRepository().currentUserId != null) {
                        SyncRepository(applicationContext).pushEncounter(newEncounter)
                    }
                } catch (_: Exception) { }

                soundManager?.playEncounter()
                showEncounterNotification(payload.userName, payload.greeting)

                // Flag for LED blink when user opens the device
                userPrefs.incrementUnseenEncounters()
            }

            // Grant play token and chance at puzzle piece (with active event effects)
            val effects = try {
                com.pocketpass.app.data.SpotPassRepository(applicationContext).getActiveEffects()
            } catch (_: Exception) { emptyList() }
            val tokenMultiplier = com.pocketpass.app.data.EventEffectManager.getTokenMultiplier(effects)
            val dropChance = com.pocketpass.app.data.EventEffectManager.getPuzzleDropChance(effects)
            userPrefs.addTokens(TokenSystem.TOKENS_PER_NEW_ENCOUNTER * tokenMultiplier)
            if (kotlin.random.Random.nextFloat() < dropChance) {
                val progress = userPrefs.puzzleProgressFlow.first()
                val claimedSpotPass = try { database.spotPassDao().getClaimedPuzzlePanels() } catch (_: Exception) { emptyList() }
                val allPanels = PuzzlePanels.getAllIncludingSpotPass(claimedSpotPass)
                val uncollected = progress.allUncollectedPieces(allPanels)
                if (uncollected.isNotEmpty()) {
                    userPrefs.addPuzzlePiece(uncollected.random())
                    Log.d(TAG, "Puzzle piece granted from encounter!")
                }
            }
        }
    }

    private fun showEncounterNotification(userName: String, greeting: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ENCOUNTER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New friend: $userName")
            .setContentText(greeting)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(encounterNotificationId++, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-schedule the service so it restarts after the app is swiped from recents
        val restartIntent = Intent(applicationContext, ProximityService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
            android.os.SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Step counter ──

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event?.sensor?.type == android.hardware.Sensor.TYPE_STEP_COUNTER) {
            val totalSteps = event.values[0].toInt()

            // Batch: skip DataStore writes until STEP_BATCH_SIZE steps accumulate
            if (lastProcessedSteps < 0) lastProcessedSteps = totalSteps
            if (totalSteps - lastProcessedSteps < STEP_BATCH_SIZE) return

            lastProcessedSteps = totalSteps
            serviceScope.launch {
                val effects = try {
                    com.pocketpass.app.data.SpotPassRepository(applicationContext).getActiveEffects()
                } catch (_: Exception) { emptyList() }
                val walkCap = com.pocketpass.app.data.EventEffectManager.getWalkTokenCap(effects)
                val tokenMult = com.pocketpass.app.data.EventEffectManager.getTokenMultiplier(effects)

                val awarded = userPrefs.processSteps(totalSteps, maxStepTokensOverride = walkCap)
                // Apply token multiplier as bonus on top of base award
                if (awarded > 0 && tokenMult > 1) {
                    userPrefs.addTokens(awarded * (tokenMult - 1))
                }
                if (awarded > 0) {
                    Log.d(TAG, "Awarded $awarded token(s) x$tokenMult from walking (total steps: $totalSteps)")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        bleHandler?.stop()
        bleHandler = null
        exchangedNames.clear()
        _nearbyDevices.value = emptyMap()
        _serviceStatus.value = "stopped"
    }
}
