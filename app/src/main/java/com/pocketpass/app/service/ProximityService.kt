package com.pocketpass.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
import com.pocketpass.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

import android.content.pm.ServiceInfo

class ProximityService : Service() {

    private val STRATEGY = Strategy.P2P_CLUSTER
    private val SERVICE_ID = "com.pocketpass.app.SERVICE_ID"
    private val TAG = "PocketPassProximity"

    private lateinit var connectionsClient: ConnectionsClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    
    // Default user info if not completely set up yet
    private var myUserId: String = UUID.randomUUID().toString()
    private var myUserName: String = "Stranger"
    private var myAvatarHex: String = ""
    private var myGreeting: String = "Hello from PocketPass!"
    private var myOrigin: String = "Nearby"
    private var myAge: String = ""
    private var myHobbies: String = ""

    override fun onCreate() {
        super.onCreate()
        connectionsClient = Nearby.getConnectionsClient(this)
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
            
            // Start Nearby Connections
            startAdvertising()
            startDiscovery()
        }
    }

    private fun startForegroundService() {
        val channelId = "pocketpass_proximity_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Proximity Scanning",
                NotificationManager.IMPORTANCE_LOW // Silent notification
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("PocketPass is active")
            .setContentText("Looking for friends nearby...")
            // Usually we'd use a custom icon here, using standard Android one as placeholder
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
                Log.d(TAG, "Advertising started.")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Advertising failed.", e)
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
                Log.d(TAG, "Discovery started.")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed.", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions to discover", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error starting discovery", e)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId. Requesting connection...")
            // Request connection when we find a device
            connectionsClient.requestConnection(myUserName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "Connection request sent to $endpointId")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send connection request to $endpointId", e)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: $endpointId")
            // Accept connection automatically on both sides
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connection successful to $endpointId. Sending payload...")
                sendExchangePayload(endpointId)
            } else {
                Log.e(TAG, "Connection failed to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val jsonStr = payload.asBytes()?.let { String(it) } ?: return
                Log.d(TAG, "Payload received from $endpointId: $jsonStr")
                
                try {
                    val exchangePayload = gson.fromJson(jsonStr, ExchangePayload::class.java)
                    saveEncounterToRoom(exchangePayload)
                    
                    // Disconnect after successful exchange so we don't hold the connection forever
                    connectionsClient.disconnectFromEndpoint(endpointId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse payload", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used to track progress of larger payloads (like actual images)
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
        val payload = Payload.fromBytes(jsonStr.toByteArray())
        
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Payload sent successfully to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload to $endpointId", e)
            }
    }

    private fun saveEncounterToRoom(payload: ExchangePayload) {
        serviceScope.launch {
            val db = PocketPassDatabase.getDatabase(applicationContext)
            val newEncounter = Encounter(
                encounterId = UUID.randomUUID().toString(), // Or payload.userId if 1 encounter per user
                timestamp = System.currentTimeMillis(),
                otherUserAvatarHex = payload.avatarHex,
                otherUserName = payload.userName,
                greeting = payload.greeting,
                origin = payload.origin,
                age = payload.age,
                hobbies = payload.hobbies
            )
            db.encounterDao().insertEncounter(newEncounter)
            Log.d(TAG, "Encounter saved for user: ${payload.userName}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }
}