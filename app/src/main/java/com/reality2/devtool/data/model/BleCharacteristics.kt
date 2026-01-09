package com.reality2.devtool.data.model

import java.util.UUID

/**
 * Reality2 BLE protocol constants and UUIDs
 */
object BleCharacteristics {
    // Reality2 GATT Service UUID
    val R2_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")

    // Node Info characteristic (Read) - node metadata, version, capabilities
    val NODE_INFO_CHAR_UUID: UUID = UUID.fromString("00001237-0000-1000-8000-00805F9B34FB")

    // Mesh Info characteristic (Read) - WiFi mesh/hotspot connection details
    val MESH_INFO_CHAR_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805F9B34FB")

    // Query characteristic (Read) - legacy, falls back to node info
    val QUERY_CHAR_UUID: UUID = UUID.fromString("00002A57-0000-1000-8000-00805F9B34FB")

    // Mutation characteristic (Write) - sentantSend
    val MUTATION_CHAR_UUID: UUID = UUID.fromString("00002A58-0000-1000-8000-00805F9B34FB")

    // Subscription characteristic (Notify) - awaitSignal
    val SUBSCRIPTION_CHAR_UUID: UUID = UUID.fromString("00002A59-0000-1000-8000-00805F9B34FB")

    // Client Characteristic Configuration Descriptor (for enabling notifications)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ALTBeacon Constants
    const val R2_COMPANY_ID: Int = 0xFFFF
    const val BEACON_CODE: Short = 0xBEAC.toShort()
    const val BEACON_PAYLOAD_SIZE: Int = 24

    // Protocol Version
    const val PROTOCOL_VERSION = "1.0"
    const val MAX_PAYLOAD_SIZE = 512
}
