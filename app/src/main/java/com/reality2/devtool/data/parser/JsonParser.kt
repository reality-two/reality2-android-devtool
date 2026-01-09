package com.reality2.devtool.data.parser

import com.reality2.devtool.data.model.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Parser for Reality2 JSON protocol messages
 */
object JsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Parse SentantAllResponse from JSON string
     */
    fun parseSentantAllResponse(jsonString: String): Result<SentantAllResponse> {
        return try {
            val response = json.decodeFromString<SentantAllResponse>(jsonString)
            Timber.d("Parsed SentantAllResponse: ${response.count} sentants")
            Result.success(response)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse SentantAllResponse")
            Result.failure(e)
        }
    }

    /**
     * Parse MutationResponse from JSON string
     */
    fun parseMutationResponse(jsonString: String): Result<MutationResponse> {
        return try {
            val response = json.decodeFromString<MutationResponse>(jsonString)
            Timber.d("Parsed MutationResponse: success=${response.success}")
            Result.success(response)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse MutationResponse")
            Result.failure(e)
        }
    }

    /**
     * Parse SignalNotification from JSON string
     */
    fun parseSignalNotification(jsonString: String): Result<SignalNotification> {
        return try {
            val notification = json.decodeFromString<SignalNotification>(jsonString)
            Timber.d("Parsed SignalNotification: signal=${notification.data.signal}")
            Result.success(notification)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse SignalNotification")
            Result.failure(e)
        }
    }

    /**
     * Parse ErrorResponse from JSON string
     */
    fun parseErrorResponse(jsonString: String): Result<ErrorResponse> {
        return try {
            val error = json.decodeFromString<ErrorResponse>(jsonString)
            Timber.d("Parsed ErrorResponse: ${error.error_type} - ${error.message}")
            Result.success(error)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to parse ErrorResponse")
            Result.failure(e)
        }
    }

    /**
     * Encode MutationRequest to JSON string
     */
    fun encodeMutationRequest(request: MutationRequest): Result<String> {
        return try {
            val jsonString = json.encodeToString(MutationRequest.serializer(), request)
            Timber.d("Encoded MutationRequest: ${request.event} to ${request.id}")
            Result.success(jsonString)
        } catch (e: SerializationException) {
            Timber.e(e, "Failed to encode MutationRequest")
            Result.failure(e)
        }
    }

    /**
     * Truncate JSON string to max size if needed
     */
    fun truncateIfNeeded(jsonString: String, maxSize: Int = BleCharacteristics.MAX_PAYLOAD_SIZE): String {
        return if (jsonString.toByteArray().size > maxSize) {
            val truncated = jsonString.toByteArray().sliceArray(0 until maxSize)
            String(truncated).trimEnd() + "\"truncated\":true}"
        } else {
            jsonString
        }
    }
}
