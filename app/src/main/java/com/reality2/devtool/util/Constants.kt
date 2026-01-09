package com.reality2.devtool.util

/**
 * Application-wide constants
 */
object Constants {
    // Scan settings
    const val SCAN_TIMEOUT_MS = 30_000L           // Stop scan after 30 seconds
    const val NODE_TIMEOUT_MS = 30_000L           // Consider node lost after 30 seconds

    // GATT settings
    const val GATT_CONNECTION_TIMEOUT_MS = 10_000L
    const val GATT_OPERATION_TIMEOUT_MS = 5_000L

    // UI settings
    const val MAX_SIGNAL_HISTORY = 100            // Keep last 100 signals
    const val SIGNAL_ANIMATION_DURATION_MS = 300

    // Navigation routes
    const val ROUTE_SCANNER = "scanner"
    const val ROUTE_NODE_DETAIL = "node_detail/{nodeId}"
    const val ROUTE_SENTANT_LIST = "sentant_list/{nodeId}"
    const val ROUTE_SIGNAL_MONITOR = "signal_monitor"

    // Navigation arguments
    const val ARG_NODE_ID = "nodeId"
}
