package com.reality2.devtool.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a Reality2 node discovered via BLE
 */
@Parcelize
data class Reality2Node(
    val nodeId: String,           // UUID from beacon
    val address: String,          // BLE MAC address
    val name: String,             // Device name
    val rssi: Int,                // Signal strength in dBm
    val major: Int,               // Beacon major version
    val minor: Int,               // Beacon minor version
    val lastSeen: Long,           // Timestamp in milliseconds
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sentants: List<Sentant> = emptyList()
) : Parcelable {
    /**
     * Calculate approximate distance in meters based on RSSI
     * Using simplified path loss formula
     */
    fun estimatedDistance(txPower: Int = -59): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.toDouble()
        } else {
            0.89976 * Math.pow(ratio, 7.7095) + 0.111
        }
    }

    /**
     * Get a short version of the node ID for display
     */
    fun shortId(): String = nodeId.take(8)
}

/**
 * Connection state for BLE connection
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
