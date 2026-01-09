package com.reality2.devtool.data.model

import java.util.UUID

data class GattServiceInfo(
    val services: List<ServiceInfo>
) {
    data class ServiceInfo(
        val uuid: UUID,
        val characteristics: List<CharacteristicInfo>
    )

    data class CharacteristicInfo(
        val uuid: UUID,
        val properties: String
    )

    fun toReadableString(): String {
        return buildString {
            appendLine("GATT Services Discovered:")
            appendLine("=" .repeat(40))
            services.forEach { service ->
                appendLine("\nService: ${service.uuid}")
                service.characteristics.forEach { char ->
                    appendLine("  └─ ${char.uuid}")
                    appendLine("     Properties: ${char.properties}")
                }
            }
            appendLine("\n" + "=".repeat(40))
        }
    }
}
