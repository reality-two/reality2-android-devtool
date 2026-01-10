package com.reality2.devtool.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.reality2.devtool.data.model.ConnectionState
import com.reality2.devtool.data.model.GattServiceInfo
import com.reality2.devtool.data.model.HotspotInfo
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.data.model.SignalNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Central manager for BLE operations
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val beaconScanner = BeaconScanner(bluetoothAdapter)
    private val gattClients = mutableMapOf<String, GattClient>()

    private val _allSignals = MutableSharedFlow<SignalNotification>()
    val allSignals: SharedFlow<SignalNotification> = _allSignals.asSharedFlow()

    private val _discoveredServices = MutableSharedFlow<GattServiceInfo>()
    val discoveredServices: SharedFlow<GattServiceInfo> = _discoveredServices.asSharedFlow()

    val discoveredNodes: StateFlow<Map<String, Reality2Node>> = beaconScanner.discoveredNodes
    val isScanning: StateFlow<Boolean> = beaconScanner.isScanning

    private val _connectedNodes = MutableStateFlow<Map<String, Reality2Node>>(emptyMap())
    val connectedNodes: StateFlow<Map<String, Reality2Node>> = _connectedNodes.asStateFlow()

    init {
        // Collect signals from all connected GATT clients
        scope.launch {
            discoveredNodes.collect { nodes ->
                nodes.values.forEach { node ->
                    if (node.connectionState == ConnectionState.CONNECTED) {
                        val client = gattClients[node.address]
                        if (client != null) {
                            scope.launch {
                                client.signals.collect { signal ->
                                    _allSignals.emit(signal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Start scanning for Reality2 nodes
     */
    fun startScan() {
        if (!isBluetoothEnabled()) {
            Timber.w("Bluetooth is not enabled")
            return
        }

        beaconScanner.startScan()
    }

    /**
     * Stop scanning
     */
    fun stopScan() {
        beaconScanner.stopScan()
    }

    /**
     * Clear discovered nodes (preserves connected)
     */
    fun clearNodes() {
        beaconScanner.clearNodes()
    }

    /**
     * Clear all nodes including connected (for visual feedback)
     */
    fun clearAllNodes() {
        beaconScanner.clearAllNodes()
    }

    /**
     * Clear all nodes briefly then restore connected ones (visual feedback for rescan)
     */
    fun restartWithVisualFeedback() {
        beaconScanner.restartWithVisualFeedback()
    }

    /**
     * Connect to a Reality2 node
     */
    suspend fun connectToNode(node: Reality2Node): Result<GattClient> {
        // Update node state to CONNECTING and refresh lastSeen to prevent cleanup during connection
        refreshNodeLastSeen(node.address)
        updateNodeConnectionState(node.address, ConnectionState.CONNECTING)

        // Check if already connected
        val existingClient = gattClients[node.address]
        if (existingClient != null && existingClient.connectionState.value == ConnectionState.CONNECTED) {
            Timber.d("Already connected to ${node.address}")
            return Result.success(existingClient)
        }

        // Get the Bluetooth device
        val device = bluetoothAdapter?.getRemoteDevice(node.address)
            ?: return Result.failure(Exception("Bluetooth device not found"))

        // Create GATT client
        val client = GattClient(context, device)
        gattClients[node.address] = client

        // Collect connection state changes
        scope.launch {
            client.connectionState.collect { state ->
                updateNodeConnectionState(node.address, state)
            }
        }

        // Collect discovered services
        scope.launch {
            client.discoveredServices.collect { serviceInfo ->
                _discoveredServices.emit(serviceInfo)
            }
        }

        // Connect
        return client.connect().map { client }
    }

    /**
     * Disconnect from a node
     */
    fun disconnectFromNode(address: String) {
        gattClients[address]?.disconnect()
        gattClients.remove(address)
        updateNodeConnectionState(address, ConnectionState.DISCONNECTED)
    }

    /**
     * Disconnect from all nodes
     */
    fun disconnectAll() {
        gattClients.values.forEach { it.disconnect() }
        gattClients.clear()
        _connectedNodes.value = emptyMap()
    }

    /**
     * Query Sentants from a connected node
     */
    suspend fun querySentants(address: String): Result<List<Sentant>> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.querySentants().onSuccess { sentants ->
            updateNodeSentants(address, sentants)
        }
    }

    /**
     * Query Sentants with raw JSON response (for debugging)
     */
    suspend fun querySentantsWithRaw(address: String): Result<Pair<List<Sentant>, String>> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.querySentantsWithRaw().onSuccess { (sentants, _) ->
            updateNodeSentants(address, sentants)
        }
    }

    /**
     * Read hotspot info from a connected node
     */
    suspend fun readHotspotInfo(address: String): Result<HotspotInfo> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.readHotspotInfo()
    }

    /**
     * Read node info from query characteristic (raw JSON)
     */
    suspend fun readNodeInfo(address: String): Result<String> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.readNodeInfo()
    }

    /**
     * Read all available info (node info + mesh info combined)
     */
    suspend fun readAllInfo(address: String): Result<String> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.readAllInfo()
    }

    /**
     * Send an event to a Sentant
     */
    suspend fun sendEvent(
        address: String,
        sentantId: String,
        event: String,
        parameters: kotlinx.serialization.json.JsonObject
    ): Result<Unit> {
        val client = gattClients[address]
            ?: return Result.failure(Exception("Not connected to $address"))

        return client.sendEvent(sentantId, event, parameters)
    }

    /**
     * Get GATT client for a node
     */
    fun getGattClient(address: String): GattClient? {
        return gattClients[address]
    }

    /**
     * Refresh a node's lastSeen timestamp to prevent it from being cleaned up
     */
    private fun refreshNodeLastSeen(address: String) {
        beaconScanner.updateNodeState(address) { node ->
            node.copy(lastSeen = System.currentTimeMillis())
        }
    }

    /**
     * Update a node's connection state
     */
    private fun updateNodeConnectionState(address: String, state: ConnectionState) {
        beaconScanner.updateNodeState(address) { node ->
            node.copy(connectionState = state)
        }

        // Update connected nodes map
        val updatedNode = beaconScanner.discoveredNodes.value[address]
        if (state == ConnectionState.CONNECTED && updatedNode != null) {
            _connectedNodes.value = _connectedNodes.value + (address to updatedNode)
        } else {
            _connectedNodes.value = _connectedNodes.value - address
        }
    }

    /**
     * Update a node's sentants list
     */
    private fun updateNodeSentants(address: String, sentants: List<Sentant>) {
        beaconScanner.updateNodeState(address) { node ->
            node.copy(sentants = sentants)
        }

        // Also update in connected nodes if applicable
        val updatedNode = beaconScanner.discoveredNodes.value[address]
        if (_connectedNodes.value.containsKey(address) && updatedNode != null) {
            _connectedNodes.value = _connectedNodes.value + (address to updatedNode)
        }
    }
}
