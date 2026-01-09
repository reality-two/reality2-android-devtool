package com.reality2.devtool.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import com.reality2.devtool.data.model.BleCharacteristics
import com.reality2.devtool.data.model.ConnectionState
import com.reality2.devtool.data.model.Reality2Node
import com.reality2.devtool.data.parser.BeaconParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Scans for Reality2 nodes using BLE ALTBeacon format
 */
class BeaconScanner(
    private val bluetoothAdapter: BluetoothAdapter?
) {
    private val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _discoveredNodes = MutableStateFlow<Map<String, Reality2Node>>(emptyMap())
    val discoveredNodes: StateFlow<Map<String, Reality2Node>> = _discoveredNodes.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var cleanupJob: Job? = null

    companion object {
        private const val NODE_TIMEOUT_MS = 30_000L  // Remove nodes not seen in 30 seconds
        private const val DISCONNECT_TIMEOUT_MS = 5_000L  // Remove disconnected nodes after 5 seconds
        private const val CLEANUP_INTERVAL_MS = 10_000L  // Check every 10 seconds
    }

    /**
     * Start scanning for Reality2 beacons
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        Timber.d("startScan called, current isScanning=${_isScanning.value}, scanCallback=${scanCallback != null}")

        if (scanner == null) {
            Timber.e("BLE scanner not available")
            return
        }

        // Stop any existing scan first to ensure clean state
        if (scanCallback != null || _isScanning.value) {
            Timber.d("Stopping existing scan before starting new one")
            try {
                scanCallback?.let { scanner.stopScan(it) }
            } catch (e: Exception) {
                Timber.w(e, "Error stopping previous scan")
            }
            scanCallback = null
            _isScanning.value = false
        }

        Timber.d("Starting BLE scan for Reality2 nodes")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        val filters = buildScanFilters()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Timber.d("BLE scan started successfully")
            startCleanupJob()
        } catch (e: SecurityException) {
            Timber.e(e, "Missing Bluetooth permission")
            scanCallback = null
            _isScanning.value = false
        } catch (e: Exception) {
            Timber.e(e, "Failed to start BLE scan")
            scanCallback = null
            _isScanning.value = false
        }
    }

    /**
     * Stop scanning
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        Timber.d("stopScan called, current isScanning=${_isScanning.value}")

        if (!_isScanning.value && scanCallback == null) {
            Timber.d("Not scanning, nothing to stop")
            return
        }

        Timber.d("Stopping BLE scan")

        try {
            scanCallback?.let {
                scanner?.stopScan(it)
                Timber.d("Scanner stopped successfully")
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Missing Bluetooth permission")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping scan")
        } finally {
            _isScanning.value = false
            scanCallback = null
            stopCleanupJob()
            Timber.d("Scan state reset, isScanning=${_isScanning.value}")
        }
    }

    /**
     * Clear discovered nodes
     */
    fun clearNodes() {
        Timber.d("Clearing discovered nodes (preserving connected)")
        // Preserve connected nodes since they won't be re-discovered via beacon
        _discoveredNodes.value = _discoveredNodes.value.filterValues { node ->
            node.connectionState == ConnectionState.CONNECTED ||
            node.connectionState == ConnectionState.CONNECTING
        }
    }

    /**
     * Clear all nodes including connected ones (for visual feedback on rescan)
     */
    fun clearAllNodes() {
        Timber.d("Clearing ALL discovered nodes")
        _discoveredNodes.value = emptyMap()
    }

    /**
     * Clear all nodes briefly then restore connected ones (visual feedback for rescan)
     */
    fun restartWithVisualFeedback() {
        // Save connected nodes
        val connectedNodes = _discoveredNodes.value.filterValues { node ->
            node.connectionState == ConnectionState.CONNECTED ||
            node.connectionState == ConnectionState.CONNECTING
        }
        Timber.d("Saving ${connectedNodes.size} connected nodes before restart")

        // Clear all nodes
        _discoveredNodes.value = emptyMap()

        // Restore connected nodes after a brief delay for visual feedback
        if (connectedNodes.isNotEmpty()) {
            scope.launch {
                delay(300)  // Brief delay so user sees the list clear
                _discoveredNodes.value = _discoveredNodes.value.toMutableMap().apply {
                    putAll(connectedNodes)
                }
                Timber.d("Restored ${connectedNodes.size} connected nodes")
            }
        }
    }

    /**
     * Handle a scan result
     */
    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val manufacturerData = result.scanRecord?.getManufacturerSpecificData(
            BleCharacteristics.R2_COMPANY_ID
        ) ?: return

        val node = BeaconParser.parse(
            manufacturerData,
            result.device.address,
            result.device.name ?: "Unknown",
            result.rssi
        ) ?: return

        scope.launch {
            updateNode(node)
        }
    }

    /**
     * Update or add a node to the discovered list
     */
    private fun updateNode(node: Reality2Node) {
        _discoveredNodes.value = _discoveredNodes.value.toMutableMap().apply {
            val existing = this[node.address]
            if (existing != null) {
                // Update existing node, preserving connection state and sentants
                this[node.address] = node.copy(
                    connectionState = existing.connectionState,
                    sentants = existing.sentants
                )
            } else {
                // Add new node
                this[node.address] = node
                Timber.d("New R2 node discovered: ${node.shortId()} (${node.name})")
            }
        }
    }

    /**
     * Update a node in the discovered list (public API for BleManager)
     * If the node becomes DISCONNECTED and hasn't been seen recently, remove it immediately
     */
    fun updateNodeState(address: String, updater: (Reality2Node) -> Reality2Node) {
        _discoveredNodes.value = _discoveredNodes.value.toMutableMap().apply {
            val existing = this[address]
            if (existing != null) {
                val updated = updater(existing)
                // If node disconnected and hasn't been seen in last 5 seconds, remove it immediately
                // This prevents the connect button from briefly appearing when a node is turned off
                val timeSinceLastSeen = System.currentTimeMillis() - updated.lastSeen
                if (updated.connectionState == ConnectionState.DISCONNECTED && timeSinceLastSeen > DISCONNECT_TIMEOUT_MS) {
                    Timber.d("Removing disconnected node not seen in ${timeSinceLastSeen}ms: ${address.take(8)}")
                    remove(address)
                } else {
                    this[address] = updated
                }
            }
        }
    }

    /**
     * Build scan filters for Reality2 ALTBeacon
     */
    private fun buildScanFilters(): List<ScanFilter> {
        return listOf(
            ScanFilter.Builder()
                .setManufacturerData(
                    BleCharacteristics.R2_COMPANY_ID,
                    byteArrayOf(
                        (BleCharacteristics.BEACON_CODE.toInt() shr 8).toByte(),
                        (BleCharacteristics.BEACON_CODE.toInt() and 0xFF).toByte()
                    )
                )
                .build()
        )
    }

    /**
     * Start periodic cleanup of stale nodes
     */
    private fun startCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                removeStaleNodes()
            }
        }
    }

    /**
     * Stop periodic cleanup
     */
    private fun stopCleanupJob() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    /**
     * Remove nodes that haven't been seen recently
     * Note: Connected nodes are never removed even if stale
     */
    private fun removeStaleNodes() {
        val now = System.currentTimeMillis()
        val updated = _discoveredNodes.value.toMutableMap()
        val removed = mutableListOf<String>()

        updated.entries.removeAll { (address, node) ->
            // Never remove connected or connecting nodes
            if (node.connectionState == ConnectionState.CONNECTED ||
                node.connectionState == ConnectionState.CONNECTING) {
                return@removeAll false
            }

            val isStale = (now - node.lastSeen) > NODE_TIMEOUT_MS
            if (isStale) {
                removed.add(address)
            }
            isStale
        }

        if (removed.isNotEmpty()) {
            Timber.d("Removed ${removed.size} stale node(s): ${removed.joinToString { it.take(8) }}")
            _discoveredNodes.value = updated
        }
    }
}
