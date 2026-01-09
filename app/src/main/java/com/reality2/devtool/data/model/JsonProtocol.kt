package com.reality2.devtool.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Response from Query characteristic (sentantAll)
 */
@Serializable
data class SentantAllResponse(
    val type: String,              // "sentant_all_response"
    val version: String,           // "1.0"
    val timestamp: String? = null,
    val count: Int,
    val data: List<Sentant>
)

/**
 * Request for Mutation characteristic (sentantSend)
 */
@Serializable
data class MutationRequest(
    val id: String,                                    // Sentant ID
    val event: String,                                 // Event name
    val parameters: JsonObject = JsonObject(emptyMap()),
    val passthrough: JsonElement? = null
)

/**
 * Response from Mutation (via notification)
 */
@Serializable
data class MutationResponse(
    val type: String,              // "mutation_response"
    val mutation: String,          // "sentantSend"
    val success: Boolean,
    val error: String? = null,
    val version: String,
    val timestamp: String,
    val data: Sentant? = null
)

/**
 * Signal notification from Subscription characteristic (awaitSignal)
 */
@Serializable
data class SignalNotification(
    val type: String,              // "await_signal"
    val version: String,
    val timestamp: String,
    val data: SignalData
)

/**
 * Signal data payload
 */
@Serializable
data class SignalData(
    val sentant_id: String,
    val signal: String,
    val event: String,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val passthrough: JsonElement? = null
)

/**
 * Error response
 */
@Serializable
data class ErrorResponse(
    val type: String,              // "error"
    val version: String,
    val error_type: String,
    val message: String,
    val timestamp: String
)
