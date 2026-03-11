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
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.Collections

/**
 * Raw BLE fallback for devices without Google Play Services.
 *
 * Protocol:
 * 1. Both devices advertise a GATT server with POCKETPASS_SERVICE_UUID
 * 2. Both devices scan for that UUID
 * 3. When a scanner finds an advertiser, the scanner connects as a GATT client
 * 4. Scanner writes its JSON payload to the WRITE characteristic
 * 5. Scanner reads the advertiser's JSON payload from the READ characteristic
 * 6. Both sides process the exchange and disconnect
 *
 * Tie-breaker: only the device with the lexicographically smaller BT address initiates.
 */
class BleProximityHandler(
    private val context: Context,
    private val onPayloadReceived: (String) -> Unit,  // JSON string
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

        // BLE can transfer larger payloads via GATT, but chunk if needed
        private const val MAX_CHUNK_SIZE = 512
        private const val MAX_PAYLOAD_SIZE = ExchangePayload.MAX_PAYLOAD_SIZE_BYTES
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
    private val incomingBuffers: MutableMap<String, StringBuilder> = Collections.synchronizedMap(mutableMapOf())

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
        stopAdvertising()
        stopScanning()
        stopGattServer()
        exchangedAddresses.clear()
        pendingConnections.clear()
        incomingBuffers.clear()
    }

    // ── GATT Server (so other devices can read our payload) ──

    private fun startGattServer() {
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                POCKETPASS_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Characteristic for others to READ our payload
            val readChar = BluetoothGattCharacteristic(
                PAYLOAD_READ_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            // Characteristic for others to WRITE their payload to us
            val writeChar = BluetoothGattCharacteristic(
                PAYLOAD_WRITE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            service.addCharacteristic(readChar)
            service.addCharacteristic(writeChar)
            gattServer?.addService(service)
            Log.d(TAG, "GATT server started")
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
            if (characteristic.uuid == PAYLOAD_READ_CHAR_UUID) {
                try {
                    val payload = getMyPayload().toByteArray(Charsets.UTF_8)
                    val chunk = if (offset < payload.size) {
                        payload.copyOfRange(offset, minOf(offset + MAX_CHUNK_SIZE, payload.size))
                    } else {
                        byteArrayOf()
                    }
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                    Log.d(TAG, "Sent payload chunk to ${device.address} (offset=$offset, size=${chunk.size})")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception on read request", e)
                }
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
            if (characteristic.uuid == PAYLOAD_WRITE_CHAR_UUID && value != null) {
                try {
                    val address = device.address
                    val existingLength = incomingBuffers[address]?.length ?: 0

                    // Drop writes that would exceed the payload size limit
                    if (existingLength + value.size > MAX_PAYLOAD_SIZE) {
                        Log.w(TAG, "Dropping oversized payload from $address")
                        incomingBuffers.remove(address)
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                        return
                    }

                    val data = String(value, Charsets.UTF_8)

                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }

                    // Check for end-of-message marker
                    if (data.endsWith("\u0000")) {
                        val buffer = incomingBuffers.getOrPut(address) { StringBuilder() }
                        buffer.append(data.dropLast(1))
                        val fullPayload = buffer.toString()
                        incomingBuffers.remove(address)

                        if (fullPayload.length > MAX_PAYLOAD_SIZE) {
                            Log.w(TAG, "Complete payload from $address exceeds size limit")
                            return
                        }

                        if (address !in exchangedAddresses) {
                            exchangedAddresses.add(address)
                            Log.d(TAG, "Received complete payload from $address via GATT server")
                            onDeviceFound(address, "exchanged")
                            onPayloadReceived(fullPayload)
                        }
                    } else {
                        val buffer = incomingBuffers.getOrPut(address) { StringBuilder() }
                        buffer.append(data)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception on write request", e)
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            try {
                val address = device.address
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT server: device disconnected: $address")
                    incomingBuffers.remove(address)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception on connection state change", e)
            }
        }
    }

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
            Log.d(TAG, "BLE advertising started")
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
            Log.d(TAG, "BLE scanning started")
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
            // On some devices scanMode triggers batch results
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }
    }

    // ── GATT Client (connect to discovered device, exchange payloads) ──

    private fun handleCharacteristicRead(
        gatt: BluetoothGatt, charUuid: UUID, data: ByteArray?, status: Int, address: String
    ) {
        try {
            if (status == BluetoothGatt.GATT_SUCCESS && charUuid == PAYLOAD_READ_CHAR_UUID) {
                if (data != null && data.isNotEmpty() && data.size <= MAX_PAYLOAD_SIZE) {
                    val jsonStr = String(data, Charsets.UTF_8)
                    if (address !in exchangedAddresses) {
                        exchangedAddresses.add(address)
                        Log.d(TAG, "Received payload from $address via GATT client read")
                        onDeviceFound(address, "exchanged")
                        onPayloadReceived(jsonStr)
                    }
                } else if (data != null && data.size > MAX_PAYLOAD_SIZE) {
                    Log.w(TAG, "Dropping oversized read payload from $address")
                }
            }
            pendingConnections.remove(address)
            gatt.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception on characteristic read", e)
            pendingConnections.remove(address)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        try {
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    try {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "GATT client connected to ${device.address}, discovering services...")
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d(TAG, "GATT client disconnected from ${device.address}")
                            pendingConnections.remove(device.address)
                            gatt.close()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception on GATT connection state change", e)
                        pendingConnections.remove(device.address)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "Service discovery failed for ${device.address}")
                        pendingConnections.remove(device.address)
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                    if (service == null) {
                        Log.e(TAG, "PocketPass service not found on ${device.address}")
                        pendingConnections.remove(device.address)
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    // First write our payload, then read theirs
                    val writeChar = service.getCharacteristic(PAYLOAD_WRITE_CHAR_UUID)
                    if (writeChar != null) {
                        val payload = getMyPayload().toByteArray(Charsets.UTF_8)
                        // Append null terminator as end-of-message marker
                        val payloadWithMarker = payload + byteArrayOf(0)
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
                            Log.d(TAG, "Writing payload to ${device.address} (${payloadWithMarker.size} bytes)")
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Security exception writing characteristic", e)
                            pendingConnections.remove(device.address)
                        }
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Payload written to ${device.address}, now reading theirs...")
                        // Now read their payload
                        val service = gatt.getService(POCKETPASS_SERVICE_UUID)
                        val readChar = service?.getCharacteristic(PAYLOAD_READ_CHAR_UUID)
                        if (readChar != null) {
                            try {
                                gatt.readCharacteristic(readChar)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Security exception reading characteristic", e)
                                pendingConnections.remove(device.address)
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to write payload to ${device.address}: status=$status")
                        pendingConnections.remove(device.address)
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }

                // API 33+ overload — receives value directly (preferred)
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    handleCharacteristicRead(gatt, characteristic.uuid, value, status, device.address)
                }

                // Pre-API 33 overload — reads value from characteristic object
                @Suppress("DEPRECATION")
                @Deprecated("Deprecated in API 33")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    handleCharacteristicRead(gatt, characteristic.uuid, characteristic.value, status, device.address)
                }
            })

            // Mark scanning as active since we're getting results
            if (!isScanning) {
                isScanning = true
                onStatusChanged(isAdvertising, isScanning)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BT permission to connect GATT", e)
            pendingConnections.remove(device.address)
        }
    }
}
