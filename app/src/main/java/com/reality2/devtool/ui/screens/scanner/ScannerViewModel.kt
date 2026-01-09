package com.reality2.devtool.ui.screens.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.model.Sentant
import com.reality2.devtool.data.repository.Reality2Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    data class NodeInfoDialogData(val nodeName: String, val nodeAddress: String, val json: String, val isError: Boolean = false)
    private val _showNodeInfoDialog = MutableStateFlow<NodeInfoDialogData?>(null)
    val showNodeInfoDialog: StateFlow<NodeInfoDialogData?> = _showNodeInfoDialog.asStateFlow()

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
                    // Read all info and show dialog
                    repository.readAllInfo(node.address)
                        .onSuccess { json ->
                            Timber.d("Received all info: ${json.take(100)}")
                            _showNodeInfoDialog.value = NodeInfoDialogData(
                                nodeName = node.name,
                                nodeAddress = node.address,
                                json = json
                            )
                        }
                        .onFailure { error ->
                            Timber.e(error, "Failed to read info from ${node.shortId()}")
                            _showNodeInfoDialog.value = NodeInfoDialogData(
                                nodeName = node.name,
                                nodeAddress = node.address,
                                json = "Error: ${error.message}",
                                isError = true
                            )
                        }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to connect to ${node.shortId()}")
                }
        }
    }

    fun clearNodes() {
        Timber.d("ViewModel: Clear nodes")
        repository.clearNodes()
    }

    fun showNodeInfo(node: Reality2Node) {
        Timber.d("ViewModel: Show node info for ${node.shortId()}")
        viewModelScope.launch {
            repository.readAllInfo(node.address)
                .onSuccess { json ->
                    Timber.d("Received all info: ${json.take(100)}")
                    _showNodeInfoDialog.value = NodeInfoDialogData(
                        nodeName = node.name,
                        nodeAddress = node.address,
                        json = json
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to read info from ${node.shortId()}")
                    _showNodeInfoDialog.value = NodeInfoDialogData(
                        nodeName = node.name,
                        nodeAddress = node.address,
                        json = "Error: ${error.message}",
                        isError = true
                    )
                }
        }
    }

    fun querySentants(node: Reality2Node) {
        Timber.d("ViewModel: Querying sentants from ${node.shortId()}")
        viewModelScope.launch {
            // Read mesh info to get WiFi mesh connection details
            repository.readMeshInfo(node.address)
                .onSuccess { meshInfo ->
                    Timber.d("Mesh info: active=${meshInfo.meshActive}, ipv6=${meshInfo.ipv6LinkLocal}")

                    if (meshInfo.meshActive && !meshInfo.ipv6LinkLocal.isNullOrBlank()) {
                        // Query via WiFi mesh (HTTP GraphQL)
                        Timber.d("Querying sentants via WiFi mesh at ${meshInfo.ipv6LinkLocal}:${meshInfo.httpPort}")
                        repository.querySentantsViaMesh(meshInfo.ipv6LinkLocal!!, meshInfo.httpPort)
                            .onSuccess { sentants ->
                                Timber.d("Successfully queried ${sentants.size} sentants via mesh from ${node.shortId()}")
                                _showSentantsDialog.value = SentantsDialogData(sentants, null)
                            }
                            .onFailure { error ->
                                Timber.e(error, "Failed to query sentants via mesh from ${node.shortId()}")
                            }
                    } else {
                        // Mesh not available - cannot query sentants
                        Timber.w("WiFi mesh not active on ${node.shortId()}, cannot query sentants")
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to read mesh info from ${node.shortId()}")
                }
        }
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
