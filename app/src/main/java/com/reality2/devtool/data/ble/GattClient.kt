package com.reality2.devtool.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.reality2.devtool.data.model.*
import com.reality2.devtool.data.parser.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * GATT client for communicating with a Reality2 node
 */
@SuppressLint("MissingPermission")
class GattClient(
    private val context: Context,
    private val device: BluetoothDevice
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var gatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _signals = MutableSharedFlow<SignalNotification>()
    val signals: SharedFlow<SignalNotification> = _signals.asSharedFlow()

    private val _discoveredServices = MutableSharedFlow<GattServiceInfo>()
    val discoveredServices: SharedFlow<GattServiceInfo> = _discoveredServices.asSharedFlow()

    private var nodeInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var hotspotInfoCharacteristic: BluetoothGattCharacteristic? = null
    private var queryCharacteristic: BluetoothGattCharacteristic? = null
    private var mutationCharacteristic: BluetoothGattCharacteristic? = null
    private var subscriptionCharacteristic: BluetoothGattCharacteristic? = null

    private var pendingRead: ((Result<ByteArray>) -> Unit)? = null
    private var pendingWrite: ((Result<Unit>) -> Unit)? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("Connection state changed: status=$status, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Don't set CONNECTED yet - wait for service discovery
                    _connectionState.value = ConnectionState.CONNECTING
                    Timber.d("Connected to ${device.address}, discovering services...")
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Timber.d("Disconnected from ${device.address}")
                    cleanup()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Services discovered")

                // Collect service info for UI display
                val serviceInfoList = gatt.services.map { service ->
                    val characteristics = service.characteristics.map { char ->
                        val props = buildString {
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("READ ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("WRITE ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("NOTIFY ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("INDICATE ")
                        }
                        GattServiceInfo.CharacteristicInfo(char.uuid, props.trim())
                    }
                    GattServiceInfo.ServiceInfo(service.uuid, characteristics)
                }

                // Emit service info to UI
                scope.launch {
                    _discoveredServices.emit(GattServiceInfo(serviceInfoList))
                }

                // Log ALL discovered services and characteristics for debugging
                Timber.d("========== GATT SERVICE DUMP ==========")
                gatt.services.forEach { service ->
                    Timber.d("Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        val props = buildString {
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("READ ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("WRITE ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("NOTIFY ")
                            if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("INDICATE ")
                        }
                        Timber.d("  ├─ Characteristic: ${char.uuid} [${props.trim()}]")
                    }
                }
                Timber.d("======================================")

                // Find the Reality2 service that has our characteristics
                // Note: There may be multiple services with the same UUID, we need the right one
                val service = gatt.services
                    .filter { it.uuid == BleCharacteristics.R2_SERVICE_UUID }
                    .firstOrNull { svc ->
                        svc.getCharacteristic(BleCharacteristics.QUERY_CHAR_UUID) != null
                    }

                if (service != null) {
                    nodeInfoCharacteristic = service.getCharacteristic(BleCharacteristics.NODE_INFO_CHAR_UUID)
                    hotspotInfoCharacteristic = service.getCharacteristic(BleCharacteristics.HOTSPOT_INFO_CHAR_UUID)
                    queryCharacteristic = service.getCharacteristic(BleCharacteristics.QUERY_CHAR_UUID)
                    mutationCharacteristic = service.getCharacteristic(BleCharacteristics.MUTATION_CHAR_UUID)
                    subscriptionCharacteristic = service.getCharacteristic(BleCharacteristics.SUBSCRIPTION_CHAR_UUID)

                    val charCount = listOfNotNull(nodeInfoCharacteristic, hotspotInfoCharacteristic, queryCharacteristic, mutationCharacteristic, subscriptionCharacteristic).size
                    Timber.d("Reality2 service found with $charCount characteristics (checked ${gatt.services.count { it.uuid == BleCharacteristics.R2_SERVICE_UUID }} service instances)")

                    if (charCount == 0) {
                        Timber.e("ERROR: Reality2 service found but NO characteristics are exposed!")
                        Timber.e("Expected characteristics:")
                        Timber.e("  - Query (Read): ${BleCharacteristics.QUERY_CHAR_UUID}")
                        Timber.e("  - Mutation (Write): ${BleCharacteristics.MUTATION_CHAR_UUID}")
                        Timber.e("  - Subscription (Notify): ${BleCharacteristics.SUBSCRIPTION_CHAR_UUID}")
                        Timber.e("Check your Reality2 node GATT server configuration!")
                        _connectionState.value = ConnectionState.ERROR
                        disconnect()
                        return
                    }

                    // Set connected state now that characteristics are verified
                    _connectionState.value = ConnectionState.CONNECTED

                    // Enable notifications on subscription characteristic
                    enableSignalNotifications()
                } else {
                    Timber.e("Reality2 service NOT FOUND!")
                    Timber.e("Expected service UUID: ${BleCharacteristics.R2_SERVICE_UUID}")
                    Timber.e("Available services:")
                    gatt.services.forEach { s ->
                        Timber.e("  - ${s.uuid}")
                    }
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                Timber.e("Service discovery failed with status: $status")
                _connectionState.value = ConnectionState.ERROR
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Characteristic read: ${characteristic.uuid}, ${value.size} bytes")
                pendingRead?.invoke(Result.success(value))
            } else {
                Timber.e("Characteristic read failed: status=$status")
                pendingRead?.invoke(Result.failure(Exception("Read failed with status: $status")))
            }
            pendingRead = null
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onCharacteristicRead(gatt, characteristic, characteristic.value, status)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Characteristic write successful: ${characteristic.uuid}")
                pendingWrite?.invoke(Result.success(Unit))
            } else {
                Timber.e("Characteristic write failed: status=$status")
                pendingWrite?.invoke(Result.failure(Exception("Write failed with status: $status")))
            }
            pendingWrite = null
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Timber.d("Characteristic changed: ${characteristic.uuid}, ${value.size} bytes")
            if (characteristic.uuid == BleCharacteristics.SUBSCRIPTION_CHAR_UUID) {
                handleSignalNotification(value)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onCharacteristicChanged(gatt, characteristic, characteristic.value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Descriptor write successful: ${descriptor.uuid}")
            } else {
                Timber.e("Descriptor write failed: status=$status")
            }
        }
    }

    /**
     * Connect to the device
     */
    suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            _connectionState.value = ConnectionState.CONNECTING
            Timber.d("Connecting to ${device.address}...")

            gatt = device.connectGatt(context, false, gattCallback)

            continuation.invokeOnCancellation {
                disconnect()
            }

            // Wait for connection
            scope.launch {
                withTimeoutOrNull(10000) {
                    connectionState.collect { state ->
                        when (state) {
                            ConnectionState.CONNECTED -> {
                                continuation.resume(Result.success(Unit))
                            }
                            ConnectionState.ERROR -> {
                                continuation.resume(Result.failure(Exception("Connection failed")))
                            }
                            else -> {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect")
            _connectionState.value = ConnectionState.ERROR
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Disconnect from the device
     */
    fun disconnect() {
        Timber.d("Disconnecting from ${device.address}")
        gatt?.disconnect()
    }

    /**
     * Query all Sentants (write-then-read pattern)
     */
    suspend fun querySentants(): Result<List<Sentant>> = suspendCancellableCoroutine { continuation ->
        val characteristic = queryCharacteristic
        if (characteristic == null) {
            continuation.resume(Result.failure(Exception("Query characteristic not available")))
            return@suspendCancellableCoroutine
        }

        Timber.d("Querying sentants: First writing trigger, then reading response")

        // First write a trigger byte to tell server to populate the characteristic
        val triggerData = byteArrayOf(0x01)

        pendingWrite = { writeResult ->
            writeResult.fold(
                onSuccess = {
                    Timber.d("Trigger write successful, now reading response...")

                    // Small delay to let server populate the characteristic
                    scope.launch {
                        kotlinx.coroutines.delay(100)

                        // Now set up the read handler
                        pendingRead = { readResult ->
                            readResult.fold(
                                onSuccess = { data ->
                                    val jsonString = data.decodeToString()
                                    Timber.d("Received JSON: ${jsonString.take(200)}")

                                    JsonParser.parseSentantAllResponse(jsonString).fold(
                                        onSuccess = { response ->
                                            continuation.resume(Result.success(response.data))
                                        },
                                        onFailure = { error ->
                                            continuation.resume(Result.failure(error))
                                        }
                                    )
                                },
                                onFailure = { error ->
                                    continuation.resume(Result.failure(error))
                                }
                            )
                        }

                        // Initiate the read
                        if (!gatt!!.readCharacteristic(characteristic)) {
                            pendingRead = null
                            continuation.resume(Result.failure(Exception("Failed to initiate read")))
                        }
                    }
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(Exception("Failed to write trigger: ${error.message}")))
                }
            )
        }

        // Write trigger to query characteristic
        characteristic.value = triggerData
        if (!gatt!!.writeCharacteristic(characteristic)) {
            pendingWrite = null
            continuation.resume(Result.failure(Exception("Failed to initiate write")))
        }
    }

    /**
     * Query all Sentants with raw JSON (for debugging)
     */
    suspend fun querySentantsWithRaw(): Result<Pair<List<Sentant>, String>> = suspendCancellableCoroutine { continuation ->
        val characteristic = queryCharacteristic
        if (characteristic == null) {
            continuation.resume(Result.failure(Exception("Query characteristic not available")))
            return@suspendCancellableCoroutine
        }

        Timber.d("Querying sentants: First writing trigger, then reading response")

        // First write a trigger byte to tell server to populate the characteristic
        val triggerData = byteArrayOf(0x01)  // Any data triggers the refresh

        pendingWrite = { writeResult ->
            writeResult.fold(
                onSuccess = {
                    Timber.d("Trigger write successful, now reading response...")

                    // Small delay to let server populate the characteristic
                    scope.launch {
                        kotlinx.coroutines.delay(500)  // Increased to 500ms to allow Elixir processing

                        // Now set up the read handler
                        pendingRead = { readResult ->
                            readResult.fold(
                                onSuccess = { data ->
                                    val jsonString = data.decodeToString()
                                    Timber.d("Received JSON: ${jsonString.take(200)}")

                                    JsonParser.parseSentantAllResponse(jsonString).fold(
                                        onSuccess = { response ->
                                            continuation.resume(Result.success(Pair(response.data, jsonString)))
                                        },
                                        onFailure = { error ->
                                            continuation.resume(Result.failure(error))
                                        }
                                    )
                                },
                                onFailure = { error ->
                                    continuation.resume(Result.failure(error))
                                }
                            )
                        }

                        // Initiate the read
                        if (!gatt!!.readCharacteristic(characteristic)) {
                            pendingRead = null
                            continuation.resume(Result.failure(Exception("Failed to initiate read")))
                        }
                    }
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(Exception("Failed to write trigger: ${error.message}")))
                }
            )
        }

        // Write trigger to query characteristic
        characteristic.value = triggerData
        if (!gatt!!.writeCharacteristic(characteristic)) {
            pendingWrite = null
            continuation.resume(Result.failure(Exception("Failed to initiate write")))
        }
    }

    /**
     * Send an event to a Sentant (write mutation characteristic)
     */
    /**
     * Read node info from the query characteristic (raw JSON)
     */
    suspend fun readNodeInfo(): Result<String> = suspendCancellableCoroutine { continuation ->
        // Prefer new node info characteristic, fallback to legacy query characteristic
        val characteristic = nodeInfoCharacteristic ?: queryCharacteristic
        if (characteristic == null) {
            continuation.resume(Result.failure(Exception("Node info characteristic not available")))
            return@suspendCancellableCoroutine
        }

        Timber.d("Reading node info from characteristic ${characteristic.uuid}")

        pendingRead = { readResult ->
            readResult.fold(
                onSuccess = { data ->
                    val jsonString = data.decodeToString()
                    Timber.d("Received node info JSON: $jsonString")
                    continuation.resume(Result.success(jsonString))
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(error))
                }
            )
        }

        if (!gatt!!.readCharacteristic(characteristic)) {
            pendingRead = null
            continuation.resume(Result.failure(Exception("Failed to initiate read")))
        }
    }

    /**
     * Read all available info (node info + mesh info combined)
     */
    suspend fun readAllInfo(): Result<String> {
        val results = mutableMapOf<String, String>()
        val errors = mutableListOf<String>()

        Timber.d("readAllInfo: nodeInfo=${nodeInfoCharacteristic != null}, hotspotInfo=${hotspotInfoCharacteristic != null}, query=${queryCharacteristic != null}")

        // Read node info (try new UUID first, then legacy)
        val nodeInfoChar = nodeInfoCharacteristic ?: queryCharacteristic
        if (nodeInfoChar != null) {
            Timber.d("Reading node info from ${nodeInfoChar.uuid}")
            readCharacteristic(nodeInfoChar)
                .onSuccess { data ->
                    if (data.isNotBlank()) {
                        results["node_info"] = data
                        Timber.d("Got node_info: ${data.take(50)}")
                    } else {
                        errors.add("node_info: empty response")
                    }
                }
                .onFailure { error ->
                    Timber.e("Failed to read node info: ${error.message}")
                    errors.add("node_info: ${error.message}")
                }
        } else {
            errors.add("node_info: characteristic not available")
        }

        // Read hotspot info
        if (hotspotInfoCharacteristic != null) {
            Timber.d("Reading hotspot info from ${hotspotInfoCharacteristic!!.uuid}")
            readCharacteristic(hotspotInfoCharacteristic!!)
                .onSuccess { data ->
                    if (data.isNotBlank()) {
                        results["hotspot_info"] = data
                        Timber.d("Got hotspot_info: ${data.take(50)}")
                    } else {
                        errors.add("hotspot_info: empty response")
                    }
                }
                .onFailure { error ->
                    Timber.e("Failed to read hotspot info: ${error.message}")
                    errors.add("hotspot_info: ${error.message}")
                }
        } else {
            errors.add("hotspot_info: characteristic not available")
        }

        // Read query characteristic (contains sentant data with count)
        if (queryCharacteristic != null && nodeInfoCharacteristic != null) {
            // Only read separately if we didn't already fall back to it for node_info
            Timber.d("Reading query/sentants info from ${queryCharacteristic!!.uuid}")
            readCharacteristic(queryCharacteristic!!)
                .onSuccess { data ->
                    if (data.isNotBlank()) {
                        results["sentants"] = data
                        Timber.d("Got sentants: ${data.take(50)}")
                    }
                }
                .onFailure { error ->
                    Timber.w("Failed to read sentants: ${error.message}")
                }
        }

        // If we got any data, return it
        if (results.isNotEmpty()) {
            // If only one result and it looks like valid JSON object/array, return it directly
            if (results.size == 1) {
                val value = results.values.first().trim()
                if (value.startsWith("{") || value.startsWith("[")) {
                    return Result.success(value)
                }
            }

            // Otherwise combine into a JSON object
            val combined = buildString {
                append("{\n")
                results.entries.forEachIndexed { index, (key, value) ->
                    val trimmedValue = value.trim()
                    // Check if value is valid JSON (starts with { or [)
                    if (trimmedValue.startsWith("{") || trimmedValue.startsWith("[")) {
                        append("  \"$key\": $trimmedValue")
                    } else {
                        // Wrap non-JSON as a string
                        append("  \"$key\": \"${trimmedValue.replace("\"", "\\\"")}\"")
                    }
                    if (index < results.size - 1) append(",")
                    append("\n")
                }
                append("}")
            }
            return Result.success(combined)
        }

        // No data at all - return error with details
        return Result.failure(Exception("Failed to read: ${errors.joinToString("; ")}"))
    }

    private suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): Result<String> =
        suspendCancellableCoroutine { continuation ->
            Timber.d("Reading characteristic ${characteristic.uuid}")

            pendingRead = { readResult ->
                readResult.fold(
                    onSuccess = { data ->
                        val jsonString = data.decodeToString()
                        Timber.d("Received JSON from ${characteristic.uuid}: ${jsonString.take(100)}")
                        continuation.resume(Result.success(jsonString))
                    },
                    onFailure = { error ->
                        continuation.resume(Result.failure(error))
                    }
                )
            }

            if (!gatt!!.readCharacteristic(characteristic)) {
                pendingRead = null
                continuation.resume(Result.failure(Exception("Failed to initiate read")))
            }
        }

    /**
     * Read hotspot info from the node (WiFi hotspot connection details)
     */
    suspend fun readHotspotInfo(): Result<HotspotInfo> = suspendCancellableCoroutine { continuation ->
        val characteristic = hotspotInfoCharacteristic
        if (characteristic == null) {
            continuation.resume(Result.failure(Exception("Hotspot info characteristic not available")))
            return@suspendCancellableCoroutine
        }

        Timber.d("Reading hotspot info")

        pendingRead = { readResult ->
            readResult.fold(
                onSuccess = { data ->
                    val jsonString = data.decodeToString()
                    Timber.d("Received hotspot info JSON: $jsonString")

                    try {
                        val hotspotInfo = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            .decodeFromString<HotspotInfo>(jsonString)
                        continuation.resume(Result.success(hotspotInfo))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse hotspot info")
                        continuation.resume(Result.failure(e))
                    }
                },
                onFailure = { error ->
                    continuation.resume(Result.failure(error))
                }
            )
        }

        // Initiate the read
        if (!gatt!!.readCharacteristic(characteristic)) {
            pendingRead = null
            continuation.resume(Result.failure(Exception("Failed to initiate read")))
        }
    }

    suspend fun sendEvent(
        sentantId: String,
        event: String,
        parameters: kotlinx.serialization.json.JsonObject
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val characteristic = mutationCharacteristic
        if (characteristic == null) {
            continuation.resume(Result.failure(Exception("Mutation characteristic not available")))
            return@suspendCancellableCoroutine
        }

        val request = MutationRequest(sentantId, event, parameters)
        JsonParser.encodeMutationRequest(request).fold(
            onSuccess = { jsonString ->
                Timber.d("Sending mutation: $jsonString")

                val data = jsonString.toByteArray()
                pendingWrite = { result ->
                    continuation.resume(result)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (gatt!!.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) != BluetoothGatt.GATT_SUCCESS) {
                        pendingWrite = null
                        continuation.resume(Result.failure(Exception("Failed to initiate write")))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = data
                    @Suppress("DEPRECATION")
                    if (!gatt!!.writeCharacteristic(characteristic)) {
                        pendingWrite = null
                        continuation.resume(Result.failure(Exception("Failed to initiate write")))
                    }
                }
            },
            onFailure = { error ->
                continuation.resume(Result.failure(error))
            }
        )
    }

    /**
     * Enable notifications on the subscription characteristic
     */
    private fun enableSignalNotifications() {
        val characteristic = subscriptionCharacteristic
        if (characteristic == null) {
            Timber.w("Subscription characteristic not available")
            return
        }

        Timber.d("Enabling signal notifications")

        if (!gatt!!.setCharacteristicNotification(characteristic, true)) {
            Timber.e("Failed to enable characteristic notification")
            return
        }

        val descriptor = characteristic.getDescriptor(BleCharacteristics.CCCD_UUID)
        if (descriptor == null) {
            Timber.e("CCCD descriptor not found")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt!!.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt!!.writeDescriptor(descriptor)
        }
    }

    /**
     * Handle incoming signal notification
     */
    private fun handleSignalNotification(data: ByteArray) {
        try {
            val jsonString = data.decodeToString()
            Timber.d("Signal notification: ${jsonString.take(100)}")

            JsonParser.parseSignalNotification(jsonString).onSuccess { signal ->
                scope.launch {
                    _signals.emit(signal)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse signal notification")
        }
    }

    /**
     * Cleanup resources
     */
    private fun cleanup() {
        nodeInfoCharacteristic = null
        hotspotInfoCharacteristic = null
        queryCharacteristic = null
        mutationCharacteristic = null
        subscriptionCharacteristic = null
        gatt?.close()
        gatt = null
    }
}
