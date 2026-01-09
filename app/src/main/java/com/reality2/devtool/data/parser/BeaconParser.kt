package com.reality2.devtool.data.parser

import com.reality2.devtool.data.model.BleCharacteristics
import com.reality2.devtool.data.model.Reality2Node
import timber.log.Timber
import java.util.UUID

/**
 * Parser for Reality2 ALTBeacon format
 *
 * Payload structure (24 bytes):
 * [0-1]   : Beacon code (0xBEAC)
 * [2-17]  : UUID (16 bytes) - Reality2 node ID
 * [18-19] : Major value (2 bytes)
 * [20-21] : Minor value (2 bytes)
 * [22]    : RSSI at 1m (1 byte, signed)
 * [23]    : Reserved (1 byte)
 */
object BeaconParser {

    /**
     * Parse ALTBeacon manufacturer data into a Reality2Node
     *
     * @param data Manufacturer specific data from scan result
     * @param address BLE MAC address of the device
     * @param name Device name from scan result
     * @param rssi Current RSSI value
     * @return Reality2Node if parsing successful, null otherwise
     */
    fun parse(data: ByteArray, address: String, name: String, rssi: Int): Reality2Node? {
        try {
            // Validate payload size
            if (data.size < BleCharacteristics.BEACON_PAYLOAD_SIZE) {
                Timber.w("Beacon payload too short: ${data.size} bytes")
                return null
            }

            // Verify beacon code (0xBEAC)
            val beaconCode = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            if (beaconCode.toShort() != BleCharacteristics.BEACON_CODE) {
                Timber.w("Invalid beacon code: 0x${beaconCode.toString(16)}")
                return null
            }

            // Extract UUID (16 bytes starting at offset 2)
            val uuidBytes = data.sliceArray(2..17)
            val uuid = bytesToUUID(uuidBytes)

            // Extract major (2 bytes, big-endian)
            val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)

            // Extract minor (2 bytes, big-endian)
            val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)

            // RSSI at 1m (1 byte, signed)
            // Not currently used but available for distance calculation
            // val rssiAt1m = data[22].toInt()

            Timber.d("Parsed R2 node: uuid=$uuid, major=$major, minor=$minor, rssi=$rssi")

            return Reality2Node(
                nodeId = uuid,
                address = address,
                name = name.ifEmpty { "R2 Node" },
                rssi = rssi,
                major = major,
                minor = minor,
                lastSeen = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse beacon data")
            return null
        }
    }

    /**
     * Convert 16 bytes to UUID string
     */
    private fun bytesToUUID(bytes: ByteArray): String {
        require(bytes.size == 16) { "UUID must be 16 bytes" }

        val mostSigBits = bytes.sliceArray(0..7).fold(0L) { acc, byte ->
            (acc shl 8) or (byte.toLong() and 0xFF)
        }

        val leastSigBits = bytes.sliceArray(8..15).fold(0L) { acc, byte ->
            (acc shl 8) or (byte.toLong() and 0xFF)
        }

        return UUID(mostSigBits, leastSigBits).toString()
    }
}
