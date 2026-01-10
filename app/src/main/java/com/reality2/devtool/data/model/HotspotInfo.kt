package com.reality2.devtool.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * WiFi hotspot connection information from GATT Hotspot Info characteristic.
 * Contains credentials and rendezvous info for connecting to a Reality2 node's hotspot.
 */
@Serializable
data class HotspotInfo(
    @SerialName("hotspot_available")
    val hotspotAvailable: Boolean,

    val ssid: String? = null,

    val psk: String? = null,

    val channel: Int? = null,

    val security: String? = null,

    @SerialName("rendezvous_ip")
    val rendezvousIp: String? = null,

    @SerialName("rendezvous_port")
    val rendezvousPort: Int = 4005,

    @SerialName("offer_expiry")
    val offerExpiry: Long? = null,

    @SerialName("host_node_id")
    val hostNodeId: String? = null,

    val timestamp: Long? = null,

    val message: String? = null
)
