package com.reality2.devtool.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality2.devtool.data.model.HotspotInfo
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.data.repository.Reality2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import timber.log.Timber

/**
 * ViewModel for the Scanner screen
 */
class ScannerViewModel(
    private val repository: Reality2Repository
) : ViewModel() {

    val isScanning: StateFlow<Boolean> = repository.isScanning

    val discoveredNodes: StateFlow<List<Reality2Node>> = repository.discoveredNodes
        .map { nodes ->
            // Sort by address only to keep stable positions
            nodes.values.sortedBy { it.address }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    data class SentantsDialogData(val sentants: List<Sentant>, val rawJson: String?)
    private val _showSentantsDialog = MutableStateFlow<SentantsDialogData?>(null)
    val showSentantsDialog: StateFlow<SentantsDialogData?> = _showSentantsDialog.asStateFlow()

    data class NodeInfoDialogData(
        val nodeName: String,
        val nodeAddress: String,
        val nodeId: String,
        val rssi: Int,
        val sentantsCount: Int,
        val json: String,
        val isError: Boolean = false
    )
    private val _showNodeInfoDialog = MutableStateFlow<NodeInfoDialogData?>(null)
    val showNodeInfoDialog: StateFlow<NodeInfoDialogData?> = _showNodeInfoDialog.asStateFlow()

    data class HotspotDialogData(val nodeName: String, val nodeAddress: String, val hotspotInfo: HotspotInfo)
    private val _showHotspotDialog = MutableStateFlow<HotspotDialogData?>(null)
    val showHotspotDialog: StateFlow<HotspotDialogData?> = _showHotspotDialog.asStateFlow()

    val isBluetoothAvailable: Boolean = repository.isBluetoothAvailable()
    val isBluetoothEnabled: Boolean = repository.isBluetoothEnabled()

    fun startScan() {
        Timber.d("ViewModel: Start scan")
        repository.startScan()
    }

    fun stopScan() {
        Timber.d("ViewModel: Stop scan")
        repository.stopScan()
    }

    fun restartScan() {
        Timber.d("ViewModel: Restart scan - disconnecting all and clearing")
        repository.stopScan()
        repository.disconnectAll()  // Disconnect all GATT connections
        repository.clearAllNodes()  // Clear all nodes from the list
        repository.startScan()
    }

    fun toggleScan() {
        if (isScanning.value) {
            stopScan()
        } else {
            startScan()
        }
    }

    fun connectToNode(node: Reality2Node) {
        Timber.d("ViewModel: Connect to ${node.shortId()}")
        viewModelScope.launch {
            repository.connectToNode(node)
                .onSuccess {
                    Timber.d("Successfully connected to ${node.shortId()}")
                    // Read all info with retry logic (GATT may need time to stabilize)
                    readInfoWithRetry(node, maxRetries = 3, delayMs = 500)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to connect to ${node.shortId()}")
                }
        }
    }

    private suspend fun readInfoWithRetry(node: Reality2Node, maxRetries: Int, delayMs: Long) {
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            if (attempt > 0) {
                Timber.d("Retrying read info (attempt ${attempt + 1}/$maxRetries) after ${delayMs}ms delay")
                delay(delayMs)
            }
            repository.readAllInfo(node.address)
                .onSuccess { json ->
                    Timber.d("Received all info on attempt ${attempt + 1}: ${json.take(100)}")

                    // Query sentants using proper write-then-read (sentantAll)
                    val sentantsCount = querySentantsCount(node.address)

                    _showNodeInfoDialog.value = NodeInfoDialogData(
                        nodeName = node.name,
                        nodeAddress = node.address,
                        nodeId = node.nodeId,
                        rssi = node.rssi,
                        sentantsCount = sentantsCount,
                        json = json
                    )
                    return
                }
                .onFailure { error ->
                    Timber.w("Read info attempt ${attempt + 1} failed: ${error.message}")
                    lastError = error
                }
        }
        // All retries failed
        Timber.e(lastError, "Failed to read info from ${node.shortId()} after $maxRetries attempts")
        _showNodeInfoDialog.value = NodeInfoDialogData(
            nodeName = node.name,
            nodeAddress = node.address,
            nodeId = node.nodeId,
            rssi = node.rssi,
            sentantsCount = 0,
            json = "Error: ${lastError?.message}",
            isError = true
        )
    }

    private suspend fun querySentantsCount(address: String): Int {
        return repository.querySentants(address)
            .map { sentants -> sentants.size }
            .getOrElse {
                Timber.w("Failed to query sentants count: ${it.message}")
                0
            }
    }

    private fun parseSentantsCount(json: String): Int {
        return try {
            val jsonElement = Json.parseToJsonElement(json)
            val obj = jsonElement.jsonObject

            // Try to get count from "sentants" object (has "count" field)
            obj["sentants"]?.jsonObject?.get("count")?.jsonPrimitive?.intOrNull?.let { return it }

            // Try to get count from "sentants" -> "data" array length
            obj["sentants"]?.jsonObject?.get("data")?.jsonArray?.size?.let { return it }

            // Try to get count from "node_info" object (fallback when query char used for node_info)
            obj["node_info"]?.jsonObject?.get("count")?.jsonPrimitive?.intOrNull?.let { return it }

            // Try to get count from "node_info" -> "data" array length
            obj["node_info"]?.jsonObject?.get("data")?.jsonArray?.size?.let { return it }

            // Try direct "count" field (if sentants response is at root)
            obj["count"]?.jsonPrimitive?.intOrNull?.let { return it }

            // Try direct "data" array length
            obj["data"]?.jsonArray?.size?.let { return it }

            0
        } catch (e: Exception) {
            Timber.w("Failed to parse sentants count from JSON: ${e.message}")
            0
        }
    }

    fun clearNodes() {
        Timber.d("ViewModel: Clear nodes")
        repository.clearNodes()
    }

    fun showNodeInfo(node: Reality2Node) {
        Timber.d("ViewModel: Show node info for ${node.shortId()}")
        viewModelScope.launch {
            readInfoWithRetry(node, maxRetries = 3, delayMs = 500)
        }
    }

    fun querySentants(node: Reality2Node) {
        Timber.d("ViewModel: Querying sentants from ${node.shortId()}")
        viewModelScope.launch {
            // Read hotspot info to get WiFi hotspot connection details
            repository.readHotspotInfo(node.address)
                .onSuccess { hotspotInfo ->
                    Timber.d("Hotspot info: available=${hotspotInfo.hotspotAvailable}, ssid=${hotspotInfo.ssid}")

                    if (hotspotInfo.hotspotAvailable && !hotspotInfo.rendezvousIp.isNullOrBlank()) {
                        // Show hotspot connection dialog
                        Timber.d("Showing hotspot dialog for ${node.shortId()}")
                        _showHotspotDialog.value = HotspotDialogData(
                            nodeName = node.name,
                            nodeAddress = node.address,
                            hotspotInfo = hotspotInfo
                        )
                    } else {
                        // Hotspot not available
                        Timber.w("WiFi hotspot not available on ${node.shortId()}: ${hotspotInfo.message ?: "unknown reason"}")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to read hotspot info from ${node.shortId()}")
                }
        }
    }

    fun confirmQuerySentants() {
        val dialogData = _showHotspotDialog.value ?: return
        val hotspotInfo = dialogData.hotspotInfo

        Timber.d("ViewModel: Confirming query via hotspot at ${hotspotInfo.rendezvousIp}:${hotspotInfo.rendezvousPort}")
        viewModelScope.launch {
            repository.querySentantsViaHotspot(hotspotInfo.rendezvousIp!!, hotspotInfo.rendezvousPort)
                .onSuccess { sentants ->
                    Timber.d("Successfully queried ${sentants.size} sentants via hotspot")
                    _showHotspotDialog.value = null
                    _showSentantsDialog.value = SentantsDialogData(sentants, null)
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to query sentants via hotspot")
                    // Keep hotspot dialog open so user can retry
                }
        }
    }

    fun dismissHotspotDialog() {
        _showHotspotDialog.value = null
    }

    fun dismissSentantsDialog() {
        _showSentantsDialog.value = null
    }

    fun dismissNodeInfoDialog() {
        _showNodeInfoDialog.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
