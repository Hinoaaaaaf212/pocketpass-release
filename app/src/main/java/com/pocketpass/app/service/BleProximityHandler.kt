package com.pocketpass.app.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.Collections

/**
 * Raw BLE fallback for devices without Google Play Services.
 *
 * Protocol (encrypted):
 * 1. Both devices advertise a GATT server with POCKETPASS_SERVICE_UUID
 * 2. Both devices scan for that UUID
 * 3. When a scanner finds an advertiser, the scanner connects as a GATT client
 * 4. Scanner generates ephemeral EC keypair, writes public key to KEY_EXCHANGE_CHAR
 * 5. Server generates ephemeral EC keypair, writes its public key back via KEY_EXCHANGE_CHAR read
 * 6. Both sides derive shared AES-256-GCM key via ECDH + HKDF
 * 7. Scanner writes its encrypted payload to WRITE characteristic
 * 8. Scanner reads the server's encrypted payload from READ characteristic
 * 9. Both sides decrypt, process, and disconnect
 *
 * Security properties:
 * - Forward secrecy: ephemeral keypair per encounter
 * - Confidentiality: AES-256-GCM encryption
 * - Integrity + authentication: GCM auth tag (tampered data → decryption failure)
 * - Replay protection: timestamp validation + unique ephemeral keys per encounter
 *
 * Tie-breaker: only the device with the lexicographically smaller BT address initiates.
 */
class BleProximityHandler(
    private val context: Context,
    private val onPayloadReceived: (String) -> Unit,  // JSON string (decrypted)
    private val onStatusChanged: (Boolean, Boolean) -> Unit, // (isAdvertising, isScanning)
    private val onDeviceFound: (String, String) -> Unit, // (address, state)
    private val onError: (String) -> Unit,
    private val getMyPayload: () -> String // Returns JSON to send
) {
    companion object {
        private const val TAG = "BleProximity"

        // Custom UUID for PocketPass service — generated once, stays fixed
        val POCKETPASS_SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        val PAYLOAD_READ_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")
        val PAYLOAD_WRITE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892")
        val KEY_EXCHANGE_CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567893")

        // BLE can transfer larger payloads via GATT, but chunk if needed
        private const val MAX_CHUNK_SIZE = 512
        private const val MAX_PAYLOAD_SIZE = ExchangePayload.MAX_PAYLOAD_SIZE_BYTES

        // GATT connection timeout — older BLE stacks can hang indefinitely
        private const val GATT_CONNECT_TIMEOUT_MS = 15_000L // Increased for key exchange round-trip
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private var isAdvertising = false
    private var isScanning = false

    // Track devices we've already exchanged with to avoid duplicates
    private val exchangedAddresses: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val pendingConnections: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // Buffer for incoming chunked writes from GATT clients
    private val incomingBuffers: MutableMap<String, ByteArrayBuffer> = Collections.synchronizedMap(mutableMapOf())

    // Track BT addresses that have completed the key exchange
    private val knownPocketPassDevices: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // Per-device crypto handshake state (server side)
    private val serverHandshakes: MutableMap<String, BleCryptoHandshake> = Collections.synchronizedMap(mutableMapOf())

    // Timeout handler for stuck GATT connections
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val pendingGatts: MutableMap<String, BluetoothGatt> = Collections.synchronizedMap(mutableMapOf())

    /** Simple growable byte buffer for accumulating encrypted chunks */
    private class ByteArrayBuffer {
        private val chunks = mutableListOf<ByteArray>()
        private var totalSize = 0

        fun append(data: ByteArray): Boolean {
            if (totalSize + data.size > MAX_PAYLOAD_SIZE + 128) return false // Allow overhead for encryption
            chunks.add(data.copyOf())
            totalSize += data.size
            return true
        }

        fun toByteArray(): ByteArray {
            val result = ByteArray(totalSize)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        }

        val size: Int get() = totalSize
    }

    fun start() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            onError("Bluetooth not available or disabled")
            return
        }

        startGattServer()
        startAdvertising()
        startScanning()
    }

    fun stop() {
        cancelTimeouts()
        stopAdvertising()
        stopScanning()
        stopGattServer()
        exchangedAddresses.clear()
        pendingConnections.clear()
        pendingGatts.clear()
        incomingBuffers.clear()
        knownPocketPassDevices.clear()
        // Clear all crypto handshake state
        serverHandshakes.values.forEach { it.clear() }
        serverHandshakes.clear()
    }

    // ── GATT Server (so other devices can read our payload) ──

    private fun startGattServer() {
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                POCKETPASS_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Characteristic for ephemeral key exchange (32+ bytes each way)
            val keyExchangeChar = BluetoothGattCharacteristic(
                KEY_EXCHANGE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Characteristic for others to READ our encrypted payload
            val readChar = BluetoothGattCharacteristic(
                PAYLOAD_READ_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            // Characteristic for others to WRITE their encrypted payload to us
            val writeChar = BluetoothGattCharacteristic(
                PAYLOAD_WRITE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            service.addCharacteristic(keyExchangeChar)
            service.addCharacteristic(readChar)
            service.addCharacteristic(writeChar)
            gattServer?.addService(service)
            Log.d(TAG, "GATT server started (with key exchange)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission for GATT server", e)
            onError("missing BT permissions")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GATT server", e)
            onError("GATT server failed: ${e.message}")
        }
    }

    private fun stopGattServer() {
        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            try {
                val address = device.address

                when (characteristic.uuid) {
                    KEY_EXCHANGE_CHAR_UUID -> {
                        // Return our ephemeral public key
                        val handshake = serverHandshakes[address]
                        if (handshake == null) {
                            Log.w(TAG, "Key exchange read from $address but no handshake started")
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            return
                        }
                        // The handshake already generated a keypair when we received their key
                        val myPubKey = handshake.let {
                            // Public key was generated during the write phase
                            // We store it temporarily in the handshake object
                            serverPublicKeys[address]
                        }
                        if (myPubKey == null) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            return
                        }
                        val chunk = if (offset < myPubKey.size) {
                            myPubKey.copyOfRange(offset, minOf(offset + MAX_CHUNK_SIZE, myPubKey.size))
                        } else {
                            byteArrayOf()
                        }
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                        Log.d(TAG, "Sent ephemeral public key to $address (offset=$offset, size=${chunk.size})")
                    }

                    PAYLOAD_READ_CHAR_UUID -> {
                        // Reject reads from devices that haven't completed key exchange
                        if (address !in knownPocketPassDevices) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            return
                        }

                        val handshake = serverHandshakes[address]
                        if (handshake == null || !handshake.hasKey) {
                            Log.w(TAG, "Payload read from $address but no shared key")
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            return
                        }

                        // Encrypt our payload with the derived key
                        val plainPayload = getMyPayload().toByteArray(Charsets.UTF_8)
                        val encrypted = handshake.encrypt(plainPayload)
                        if (encrypted == null) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            return
                        }

                        val chunk = if (offset < encrypted.size) {
                            encrypted.copyOfRange(offset, minOf(offset + MAX_CHUNK_SIZE, encrypted.size))
                        } else {
                            byteArrayOf()
                        }
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                        Log.d(TAG, "Sent encrypted payload to $address (offset=$offset, size=${chunk.size})")
                    }

                    else -> {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception on read request", e)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (value == null) {
                if (responseNeeded) {
                    try { gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null) } catch (_: SecurityException) {}
                }
                return
            }

            try {
                val address = device.address

                when (characteristic.uuid) {
                    KEY_EXCHANGE_CHAR_UUID -> {
                        // Receive peer's ephemeral public key, generate our own, derive shared secret
                        val handshake = BleCryptoHandshake()
                        val myPubKey = handshake.generateEphemeralKeypair()

                        if (handshake.deriveSharedKey(value)) {
                            serverHandshakes[address] = handshake
                            serverPublicKeys[address] = myPubKey
                            knownPocketPassDevices.add(address)
                            Log.d(TAG, "Key exchange completed (server side) with $address")
                        } else {
                            Log.w(TAG, "Key derivation failed for $address")
                            handshake.clear()
                        }

                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                    }

                    PAYLOAD_WRITE_CHAR_UUID -> {
                        if (address !in knownPocketPassDevices) {
                            Log.w(TAG, "Payload write from $address without key exchange")
                            if (responseNeeded) {
                                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                            }
                            return
                        }

                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }

                        // Check for end-of-message marker (null byte at end)
                        val hasEndMarker = value.isNotEmpty() && value.last() == 0.toByte()
                        val dataToBuffer = if (hasEndMarker) value.copyOf(value.size - 1) else value

                        val buffer = incomingBuffers.getOrPut(address) { ByteArrayBuffer() }
                        if (!buffer.append(dataToBuffer)) {
                            Log.w(TAG, "Dropping oversized payload from $address")
                            incomingBuffers.remove(address)
                            return
                        }

                        if (hasEndMarker) {
                            val encryptedPayload = buffer.toByteArray()
                            incomingBuffers.remove(address)

                            // Decrypt with shared key
                            val handshake = serverHandshakes[address]
                            if (handshake == null || !handshake.hasKey) {
                                Log.w(TAG, "No shared key for $address, dropping payload")
                                return
                            }

                            val decrypted = handshake.decrypt(encryptedPayload)
                            if (decrypted == null) {
                                Log.w(TAG, "Decryption failed for payload from $address (tampered or wrong key)")
                                return
                            }

                            val jsonStr = String(decrypted, Charsets.UTF_8)
                            if (jsonStr.length > MAX_PAYLOAD_SIZE) {
                                Log.w(TAG, "Decrypted payload from $address exceeds size limit")
                                return
                            }

                            if (address !in exchangedAddresses) {
                                exchangedAddresses.add(address)
                                Log.d(TAG, "Received + decrypted payload from $address via GATT server")
                                onDeviceFound(address, "exchanged")
                                onPayloadReceived(jsonStr)
                            }
                        }
                    }

                    else -> {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception on write request", e)
            }
        }

        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            try {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            } catch (_: SecurityException) {}
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            try {
                val address = device.address
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT server: device disconnected: $address")
                    incomingBuffers.remove(address)
                    knownPocketPassDevices.remove(address)
                    // Clean up crypto state for this device
                    serverHandshakes.remove(address)?.clear()
                    serverPublicKeys.remove(address)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception on connection state change", e)
            }
        }
    }

    // Temporary storage for server-side public keys (needed between write and read of KEY_EXCHANGE_CHAR)
    private val serverPublicKeys: MutableMap<String, ByteArray> = Collections.synchronizedMap(mutableMapOf())

    // ── BLE Advertising ──

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE advertising not supported on this device")
            onError("BLE advertising not supported")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(POCKETPASS_SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission to advertise", e)
            onError("missing BT permissions")
        }
    }

    private fun stopAdvertising() {
        try {
            if (isAdvertising) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
        } catch (_: Exception) {}
        isAdvertising = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE advertising started (low latency)")
            isAdvertising = true
            onStatusChanged(isAdvertising, isScanning)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: errorCode=$errorCode")
            isAdvertising = false
            onStatusChanged(isAdvertising, isScanning)
            onError("BLE advertise failed (code $errorCode)")
        }
    }

    // ── BLE Scanning ──

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE scanner not available")
            onError("BLE scanner not available")
            return
        }

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(POCKETPASS_SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            onStatusChanged(isAdvertising, isScanning)
            Log.d(TAG, "BLE scanning started (low latency)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission to scan", e)
            onError("missing BT permissions")
        }
    }

    private fun stopScanning() {
        try {
            if (isScanning) {
                scanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {}
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device
                val address = device.address

                if (address in exchangedAddresses || address in pendingConnections) return

                // On Android <15, use BT address tie-breaker so only one side initiates.
                // On Android 15+, getAddress() returns a dummy value, so both sides initiate
                // (duplicates are filtered by exchangedAddresses).
                if (Build.VERSION.SDK_INT < 35) {
                    val myAddress = try { bluetoothAdapter?.address } catch (_: SecurityException) { null }
                    if (myAddress != null && myAddress != "02:00:00:00:00:00" && myAddress > address) {
                        Log.d(TAG, "Found PocketPass device $address, but waiting for them to initiate")
                        return
                    }
                }

                Log.d(TAG, "Found PocketPass device $address, connecting as GATT client...")
                pendingConnections.add(address)
                onDeviceFound(address, "connecting")
                connectToDevice(device)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception in scan callback", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: errorCode=$errorCode")
            isScanning = false
            onStatusChanged(isAdvertising, isScanning)
            onError("BLE scan failed (code $errorCode)")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }
    }

    // ── GATT Client (connect to discovered device, exchange payloads) ──

    private fun handleEncryptedRead(
        gatt: BluetoothGatt, charUuid: UUID, data: ByteArray?, status: Int,
        address: String, handshake: BleCryptoHandshake
    ) {
        try {
            if (status == BluetoothGatt.GATT_SUCCESS && charUuid == PAYLOAD_READ_CHAR_UUID) {
                if (data != null && data.isNotEmpty()) {
                    if (data.size > MAX_PAYLOAD_SIZE + 128) {
                        Log.w(TAG, "Dropping oversized encrypted read from $address")
                    } else {
                        // Decrypt the payload
                        val decrypted = handshake.decrypt(data)
                        if (decrypted == null) {
                            Log.w(TAG, "Decryption failed for read from $address (tampered or wrong key)")
                        } else {
                            val jsonStr = String(decrypted, Charsets.UTF_8)
                            if (address !in exchangedAddresses) {
                                exchangedAddresses.add(address)
                                Log.d(TAG, "Received + decrypted payload from $address via GATT client read")
                                onDeviceFound(address, "exchanged")
                                onPayloadReceived(jsonStr)
                            }
                        }
                    }
                }
            }
            pendingConnections.remove(address)
            handshake.clear()
            gatt.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception on characteristic read", e)
            pendingConnections.remove(address)
            handshake.clear()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            // Client-side crypto handshake
            val handshake = BleCryptoHandshake()
            val myPubKey = handshake.generateEphemeralKeypair()

            val gattRef = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    try {
                        pendingGatts.remove(device.address)

                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "GATT client connected to ${device.address}, discovering services...")
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d(TAG, "GATT client disconnected from ${device.address}")
                            pendingConnections.remove(device.address)
                            handshake.clear()
                            gatt.close()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception on GATT connection state change", e)
                        pendingConnections.remove(device.address)
                        handshake.clear()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    pendingGatts.remove(device.address)
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "Service discovery failed for ${device.address}")
                        pendingConnections.remove(device.address)
                        handshake.clear()
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                    if (service == null) {
                        Log.e(TAG, "PocketPass service not found on ${device.address}")
                        pendingConnections.remove(device.address)
                        handshake.clear()
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    // Step 1: Write our ephemeral public key to KEY_EXCHANGE_CHAR
                    val keyExchangeChar = service.getCharacteristic(KEY_EXCHANGE_CHAR_UUID)
                    if (keyExchangeChar == null) {
                        Log.e(TAG, "Key exchange characteristic not found on ${device.address}")
                        pendingConnections.remove(device.address)
                        handshake.clear()
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(
                                keyExchangeChar,
                                myPubKey,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            keyExchangeChar.value = myPubKey
                            keyExchangeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(keyExchangeChar)
                        }
                        Log.d(TAG, "Sent ephemeral public key to ${device.address} (${myPubKey.size} bytes)")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception writing key exchange", e)
                        pendingConnections.remove(device.address)
                        handshake.clear()
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "Write failed to ${device.address}: status=$status, char=${characteristic.uuid}")
                        pendingConnections.remove(device.address)
                        handshake.clear()
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    when (characteristic.uuid) {
                        KEY_EXCHANGE_CHAR_UUID -> {
                            // Step 2: Read server's ephemeral public key
                            Log.d(TAG, "Key written to ${device.address}, reading server's public key...")
                            val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                            val keyChar = service?.getCharacteristic(KEY_EXCHANGE_CHAR_UUID)
                            if (keyChar != null) {
                                try {
                                    gatt.readCharacteristic(keyChar)
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "Security exception reading key exchange", e)
                                    pendingConnections.remove(device.address)
                                    handshake.clear()
                                }
                            }
                        }

                        PAYLOAD_WRITE_CHAR_UUID -> {
                            // Step 5: Read server's encrypted payload
                            Log.d(TAG, "Encrypted payload written to ${device.address}, reading theirs...")
                            val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                            val readChar = service?.getCharacteristic(PAYLOAD_READ_CHAR_UUID)
                            if (readChar != null) {
                                try {
                                    gatt.readCharacteristic(readChar)
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "Security exception reading payload", e)
                                    pendingConnections.remove(device.address)
                                    handshake.clear()
                                }
                            }
                        }
                    }
                }

                // API 33+ overload
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    handleCharRead(gatt, characteristic.uuid, value, status, device.address, handshake)
                }

                // Pre-API 33 overload
                @Suppress("DEPRECATION")
                @Deprecated("Deprecated in API 33")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    handleCharRead(gatt, characteristic.uuid, characteristic.value, status, device.address, handshake)
                }

                private fun handleCharRead(
                    gatt: BluetoothGatt, charUuid: UUID, data: ByteArray?, status: Int,
                    address: String, hs: BleCryptoHandshake
                ) {
                    when (charUuid) {
                        KEY_EXCHANGE_CHAR_UUID -> {
                            // Step 3: Derive shared secret from server's public key
                            if (status != BluetoothGatt.GATT_SUCCESS || data == null || data.isEmpty()) {
                                Log.e(TAG, "Failed to read server public key from $address")
                                pendingConnections.remove(address)
                                hs.clear()
                                try { gatt.close() } catch (_: Exception) {}
                                return
                            }

                            if (!hs.deriveSharedKey(data)) {
                                Log.e(TAG, "Key derivation failed for $address")
                                pendingConnections.remove(address)
                                hs.clear()
                                try { gatt.close() } catch (_: Exception) {}
                                return
                            }

                            Log.d(TAG, "Key exchange completed (client side) with $address")

                            // Step 4: Encrypt and write our payload
                            val plainPayload = getMyPayload().toByteArray(Charsets.UTF_8)
                            val encrypted = hs.encrypt(plainPayload)
                            if (encrypted == null) {
                                Log.e(TAG, "Encryption failed for $address")
                                pendingConnections.remove(address)
                                hs.clear()
                                try { gatt.close() } catch (_: Exception) {}
                                return
                            }

                            // Append null terminator as end-of-message marker
                            val payloadWithMarker = encrypted + byteArrayOf(0)

                            val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                            val writeChar = service?.getCharacteristic(PAYLOAD_WRITE_CHAR_UUID)
                            if (writeChar != null) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        gatt.writeCharacteristic(
                                            writeChar,
                                            payloadWithMarker,
                                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        writeChar.value = payloadWithMarker
                                        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                        @Suppress("DEPRECATION")
                                        gatt.writeCharacteristic(writeChar)
                                    }
                                    Log.d(TAG, "Writing encrypted payload to $address (${payloadWithMarker.size} bytes)")
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "Security exception writing encrypted payload", e)
                                    pendingConnections.remove(address)
                                    hs.clear()
                                }
                            }
                        }

                        PAYLOAD_READ_CHAR_UUID -> {
                            // Step 6: Decrypt server's payload
                            handleEncryptedRead(gatt, charUuid, data, status, address, hs)
                        }
                    }
                }
            })

            // Store GATT ref and schedule timeout
            if (gattRef != null) {
                pendingGatts[device.address] = gattRef
                timeoutHandler.postDelayed({
                    val stuckGatt = pendingGatts.remove(device.address)
                    if (stuckGatt != null) {
                        Log.w(TAG, "GATT connection to ${device.address} timed out, closing")
                        pendingConnections.remove(device.address)
                        handshake.clear()
                        onDeviceFound(device.address, "timeout")
                        try {
                            stuckGatt.disconnect()
                            stuckGatt.close()
                        } catch (_: Exception) {}
                    }
                }, GATT_CONNECT_TIMEOUT_MS)
            }

            if (!isScanning) {
                isScanning = true
                onStatusChanged(isAdvertising, isScanning)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission to connect GATT", e)
            pendingConnections.remove(device.address)
        }
    }

    /** Cancel all pending connection timeouts */
    private fun cancelTimeouts() {
        timeoutHandler.removeCallbacksAndMessages(null)
        pendingGatts.clear()
    }
}
