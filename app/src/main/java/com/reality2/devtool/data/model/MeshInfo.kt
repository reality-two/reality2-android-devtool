package com.reality2.devtool.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * WiFi mesh connection information from GATT Mesh Info characteristic
 */
@Serializable
data class MeshInfo(
    @SerialName("mesh_active")
    val meshActive: Boolean,

    @SerialName("mesh_id")
    val meshId: String?,

    @SerialName("ipv6_link_local")
    val ipv6LinkLocal: String?,

    @SerialName("http_port")
    val httpPort: Int = 8080
)
