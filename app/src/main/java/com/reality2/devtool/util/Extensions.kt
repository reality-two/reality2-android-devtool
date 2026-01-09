package com.reality2.devtool.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kotlin extension functions
 */

/**
 * Format timestamp to readable string
 */
fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Format timestamp to date and time string
 */
fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Convert RSSI to signal strength description
 */
fun Int.toSignalStrength(): String {
    return when {
        this >= -50 -> "Excellent"
        this >= -60 -> "Good"
        this >= -70 -> "Fair"
        this >= -80 -> "Weak"
        else -> "Very Weak"
    }
}

/**
 * Truncate string to max length with ellipsis
 */
fun String.truncate(maxLength: Int): String {
    return if (this.length <= maxLength) this
    else "${this.take(maxLength - 3)}..."
}

/**
 * Convert ByteArray to hex string
 */
fun ByteArray.toHexString(): String {
    return joinToString(separator = " ") { byte ->
        "%02X".format(byte)
    }
}
