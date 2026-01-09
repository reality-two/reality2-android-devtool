package com.reality2.devtool.data.repository

import com.reality2.devtool.data.ble.BleManager
import com.reality2.devtool.data.mesh.GraphQLClient
import com.reality2.devtool.data.model.GattServiceInfo
import com.reality2.devtool.data.model.MeshInfo
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.data.model.SignalNotification
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * Repository for Reality2 BLE and WiFi mesh operations
 * Provides a clean API for the UI layer
 */
class Reality2Repository(private val bleManager: BleManager) {
    private val graphQLClient = GraphQLClient()

    // Discovered nodes from scanning
    val discoveredNodes: StateFlow<Map<String, Reality2Node>> = bleManager.discoveredNodes

    // Currently connected nodes
    val connectedNodes: StateFlow<Map<String, Reality2Node>> = bleManager.connectedNodes

    // All signals from all connected nodes
    val allSignals: SharedFlow<SignalNotification> = bleManager.allSignals

    // Discovered GATT services (for debugging)
    val discoveredServices: SharedFlow<GattServiceInfo> = bleManager.discoveredServices

    // Scanning state
    val isScanning: StateFlow<Boolean> = bleManager.isScanning

    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothAvailable(): Boolean {
        return bleManager.isBluetoothAvailable()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    /**
     * Start scanning for Reality2 nodes
     */
    fun startScan() {
        Timber.d("Repository: Starting scan")
        bleManager.startScan()
    }

    /**
     * Stop scanning
     */
    fun stopScan() {
        Timber.d("Repository: Stopping scan")
        bleManager.stopScan()
    }

    /**
     * Clear discovered nodes (preserves connected)
     */
    fun clearNodes() {
        Timber.d("Repository: Clearing nodes")
        bleManager.clearNodes()
    }

    /**
     * Clear all nodes including connected (for visual feedback on rescan)
     */
    fun clearAllNodes() {
        Timber.d("Repository: Clearing ALL nodes")
        bleManager.clearAllNodes()
    }

    /**
     * Clear all nodes briefly then restore connected ones (visual feedback for rescan)
     */
    fun restartWithVisualFeedback() {
        Timber.d("Repository: Restart with visual feedback")
        bleManager.restartWithVisualFeedback()
    }

    /**
     * Connect to a Reality2 node
     */
    suspend fun connectToNode(node: Reality2Node): Result<Unit> {
        Timber.d("Repository: Connecting to ${node.shortId()}")
        return bleManager.connectToNode(node).map { Unit }
    }

    /**
     * Disconnect from a node
     */
    fun disconnectFromNode(address: String) {
        Timber.d("Repository: Disconnecting from $address")
        bleManager.disconnectFromNode(address)
    }

    /**
     * Disconnect from all nodes
     */
    fun disconnectAll() {
        Timber.d("Repository: Disconnecting from all nodes")
        bleManager.disconnectAll()
    }

    /**
     * Query Sentants with raw JSON via GATT
     * DEPRECATED: Use querySentantsViaMesh() instead - GATT queries removed from server
     */
    @Deprecated("Use querySentantsViaMesh()", ReplaceWith("querySentantsViaMesh(ipv6Address, port)"))
    suspend fun querySentantsWithRaw(address: String): Result<Pair<List<Sentant>, String>> {
        Timber.d("Repository: Querying sentants with raw JSON from $address")
        return bleManager.querySentantsWithRaw(address)
    }

    /**
     * Query all Sentants from a connected node via GATT
     * DEPRECATED: Use querySentantsViaMesh() instead - GATT queries removed from server
     */
    @Deprecated("Use querySentantsViaMesh()", ReplaceWith("querySentantsViaMesh(ipv6Address, port)"))
    suspend fun querySentants(address: String): Result<List<Sentant>> {
        Timber.d("Repository: Querying sentants from $address")
        return bleManager.querySentants(address)
    }

    /**
     * Send an event to a Sentant
     */
    suspend fun sendEvent(
        address: String,
        sentantId: String,
        event: String,
        parameters: JsonObject
    ): Result<Unit> {
        Timber.d("Repository: Sending event '$event' to sentant $sentantId")
        return bleManager.sendEvent(address, sentantId, event, parameters)
    }

    /**
     * Get a specific node by address
     */
    fun getNode(address: String): Reality2Node? {
        return discoveredNodes.value[address] ?: connectedNodes.value[address]
    }

    /**
     * Get connected node by address
     */
    fun getConnectedNode(address: String): Reality2Node? {
        return connectedNodes.value[address]
    }

    /**
     * Read WiFi mesh info from a connected node
     */
    suspend fun readMeshInfo(address: String): Result<MeshInfo> {
        Timber.d("Repository: Reading mesh info from $address")
        return bleManager.readMeshInfo(address)
    }

    /**
     * Read node info from query characteristic (raw JSON)
     */
    suspend fun readNodeInfo(address: String): Result<String> {
        Timber.d("Repository: Reading node info from $address")
        return bleManager.readNodeInfo(address)
    }

    /**
     * Read all available info (node info + mesh info combined)
     */
    suspend fun readAllInfo(address: String): Result<String> {
        Timber.d("Repository: Reading all info from $address")
        return bleManager.readAllInfo(address)
    }

    /**
     * Query sentants via WiFi mesh (HTTP GraphQL)
     * Use this instead of GATT for better performance and no size limits
     */
    suspend fun querySentantsViaMesh(ipv6Address: String, port: Int = 8080): Result<List<Sentant>> {
        Timber.d("Repository: Querying sentants via mesh from $ipv6Address:$port")
        return graphQLClient.querySentants(ipv6Address, port)
    }
}
