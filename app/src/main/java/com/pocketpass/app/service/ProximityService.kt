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
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
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
import java.util.UUID
import java.util.Collections

import android.content.pm.ServiceInfo

class ProximityService : Service() {

    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.pocketpass.app.SERVICE_ID"
    private val TAG = "PocketPassProximity"
    private val FOREGROUND_CHANNEL_ID = "pocketpass_proximity_channel"
    private val ENCOUNTER_CHANNEL_ID = "pocketpass_encounter_channel"
    private var encounterNotificationId = 100

    private lateinit var connectionsClient: ConnectionsClient
    private var soundManager: SoundManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // Track endpoints we're currently connecting/connected to, to avoid duplicate requests
    private val pendingEndpoints: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val connectedEndpoints: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // Default user info if not completely set up yet
    private var myUserId: String = UUID.randomUUID().toString()
    private var myUserName: String = "Stranger"
    private var myAvatarHex: String = ""
    private var myGreeting: String = "Hello from PocketPass!"
    private var myOrigin: String = ""
    private var myAge: String = ""
    private var myHobbies: String = ""

    override fun onCreate() {
        super.onCreate()
        connectionsClient = Nearby.getConnectionsClient(this)
        soundManager = SoundManager(applicationContext)
        startForegroundService()

        serviceScope.launch {
            val userPrefs = UserPreferences(applicationContext)
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

            Log.d(TAG, "User profile loaded: name=$myUserName, origin=$myOrigin, greeting=$myGreeting")

            // Start Nearby Connections
            startAdvertising()
            startDiscovery()
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

    private fun startAdvertising() {
        try {
            val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            connectionsClient.startAdvertising(
                myUserName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
            ).addOnSuccessListener {
                Log.d(TAG, "Advertising started successfully")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Advertising failed: ${e.message}", e)
                // Retry after a delay
                serviceScope.launch {
                    kotlinx.coroutines.delay(5000)
                    Log.d(TAG, "Retrying advertising...")
                    startAdvertising()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions to advertise", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error starting advertising", e)
        }
    }

    private fun startDiscovery() {
        try {
            val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            connectionsClient.startDiscovery(
                SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
            ).addOnSuccessListener {
                Log.d(TAG, "Discovery started successfully")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed: ${e.message}", e)
                // Retry after a delay
                serviceScope.launch {
                    kotlinx.coroutines.delay(5000)
                    Log.d(TAG, "Retrying discovery...")
                    startDiscovery()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions to discover", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error starting discovery", e)
        }
    }

    // Track endpoint names we've already exchanged with to avoid duplicates in one session
    private val exchangedNames: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (name: ${info.endpointName})")

            // Skip if we're already connecting or connected to this endpoint
            if (endpointId in pendingEndpoints || endpointId in connectedEndpoints) {
                Log.d(TAG, "Already handling endpoint $endpointId, skipping")
                return
            }

            // Skip if we've already exchanged with this person in this session
            if (info.endpointName in exchangedNames) {
                Log.d(TAG, "Already exchanged with ${info.endpointName}, skipping")
                return
            }

            // Use a deterministic tie-breaker to avoid both sides requesting simultaneously.
            // Only the device whose name is lexicographically smaller initiates the connection.
            // The other device just waits for the incoming connection request.
            val shouldInitiate = myUserName <= info.endpointName
            if (!shouldInitiate) {
                Log.d(TAG, "Waiting for ${info.endpointName} to initiate (tie-breaker)")
                return
            }

            pendingEndpoints.add(endpointId)
            Log.d(TAG, "Requesting connection to $endpointId (${info.endpointName})...")

            try {
                connectionsClient.requestConnection(myUserName, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener {
                        Log.d(TAG, "Connection request sent to $endpointId")
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to request connection to $endpointId: ${e.message}")
                        pendingEndpoints.remove(endpointId)
                        // Retry once after a short delay in case of race condition
                        serviceScope.launch {
                            kotlinx.coroutines.delay(2000)
                            if (endpointId !in connectedEndpoints && info.endpointName !in exchangedNames) {
                                Log.d(TAG, "Retrying connection to $endpointId...")
                                pendingEndpoints.add(endpointId)
                                try {
                                    connectionsClient.requestConnection(myUserName, endpointId, connectionLifecycleCallback)
                                        .addOnFailureListener { e2 ->
                                            Log.e(TAG, "Retry also failed for $endpointId: ${e2.message}")
                                            pendingEndpoints.remove(endpointId)
                                        }
                                } catch (_: Exception) {
                                    pendingEndpoints.remove(endpointId)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception requesting connection to $endpointId", e)
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            pendingEndpoints.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: $endpointId (${connectionInfo.endpointName}), accepting...")
            pendingEndpoints.add(endpointId)
            // Accept connection automatically on both sides
            try {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener {
                        Log.d(TAG, "Accepted connection from $endpointId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to accept connection from $endpointId: ${e.message}")
                        pendingEndpoints.remove(endpointId)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception accepting connection from $endpointId", e)
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId! Sending payload...")
                connectedEndpoints.add(endpointId)
                sendExchangePayload(endpointId)
            } else {
                Log.e(TAG, "Connection failed to $endpointId: status=${result.status.statusCode} (${result.status.statusMessage})")
                connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            pendingEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val jsonStr = payload.asBytes()?.let { String(it) } ?: return
                Log.d(TAG, "Payload received from $endpointId: $jsonStr")

                try {
                    val exchangePayload = gson.fromJson(jsonStr, ExchangePayload::class.java)
                    if (exchangePayload.userName.isBlank()) {
                        Log.e(TAG, "Received empty userName, ignoring")
                        return
                    }
                    exchangedNames.add(exchangePayload.userName)
                    saveEncounterToRoom(exchangePayload)

                    // Disconnect after successful exchange
                    connectionsClient.disconnectFromEndpoint(endpointId)
                    connectedEndpoints.remove(endpointId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse payload", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track progress of larger payloads if needed
        }
    }

    private fun sendExchangePayload(endpointId: String) {
        val payloadData = ExchangePayload(
            userId = myUserId,
            userName = myUserName,
            avatarHex = myAvatarHex,
            greeting = myGreeting,
            origin = myOrigin,
            age = myAge,
            hobbies = myHobbies
        )
        val jsonStr = gson.toJson(payloadData)
        Log.d(TAG, "Sending payload to $endpointId: name=$myUserName")
        val payload = Payload.fromBytes(jsonStr.toByteArray())

        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Payload sent successfully to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload to $endpointId: ${e.message}", e)
            }
    }

    private fun saveEncounterToRoom(payload: ExchangePayload) {
        serviceScope.launch {
            val db = PocketPassDatabase.getDatabase(applicationContext)
            val existing = db.encounterDao().getEncounterByUserName(payload.userName)

            if (existing != null) {
                // Already encountered — update timestamp and bump meet count
                db.encounterDao().updateEncounter(
                    existing.copy(
                        timestamp = System.currentTimeMillis(),
                        meetCount = existing.meetCount + 1
                    )
                )
                Log.d(TAG, "Encounter updated for returning user: ${payload.userName}")
            } else {
                // Genuinely new encounter
                val newEncounter = Encounter(
                    encounterId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    otherUserAvatarHex = payload.avatarHex,
                    otherUserName = payload.userName,
                    greeting = payload.greeting,
                    origin = payload.origin,
                    age = payload.age,
                    hobbies = payload.hobbies
                )
                db.encounterDao().insertEncounter(newEncounter)
                Log.d(TAG, "New encounter saved for user: ${payload.userName}")

                soundManager?.playEncounter()
                showEncounterNotification(payload.userName, payload.greeting)
            }

            // Grant play token and chance at puzzle piece
            val prefs = UserPreferences(applicationContext)
            prefs.addTokens(TokenSystem.TOKENS_PER_NEW_ENCOUNTER)
            if (kotlin.random.Random.nextFloat() < TokenSystem.PUZZLE_PIECE_DROP_CHANCE) {
                val progress = prefs.puzzleProgressFlow.first()
                val uncollected = progress.allUncollectedPieces(PuzzlePanels.getAll())
                if (uncollected.isNotEmpty()) {
                    prefs.addPuzzlePiece(uncollected.random())
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        pendingEndpoints.clear()
        connectedEndpoints.clear()
        exchangedNames.clear()
    }
}
