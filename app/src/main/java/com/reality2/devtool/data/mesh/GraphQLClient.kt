package com.reality2.devtool.data.mesh

import com.reality2.devtool.data.model.Sentant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * GraphQL client for querying Reality2 nodes over WiFi hotspot
 */
class GraphQLClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class GraphQLRequest(
        val query: String
    )

    @Serializable
    private data class GraphQLResponse(
        val data: GraphQLData? = null,
        val errors: List<GraphQLError>? = null
    )

    @Serializable
    private data class GraphQLData(
        val sentant_all: List<Sentant>? = null,
        val sentantAll: List<Sentant>? = null  // Support both naming conventions
    )

    @Serializable
    private data class GraphQLError(
        val message: String
    )

    /**
     * Query all sentants from a node via HTTP GraphQL
     *
     * @param ipAddress IP address of the Reality2 node (e.g., "192.168.42.1")
     * @param port HTTP port (default 4005)
     * @return Result with list of sentants or error
     */
    suspend fun querySentants(ipAddress: String, port: Int = 4005): Result<List<Sentant>> = withContext(Dispatchers.IO) {
        try {
            // URL format: http://ip:port/reality2
            val url = "http://$ipAddress:$port/reality2"

            Timber.d("Querying sentants from $url")

            val query = """
                query {
                    sentantAll {
                        id
                        name
                        description
                        events {
                            event
                        }
                        signals
                    }
                }
            """.trimIndent()

            val requestBody = GraphQLRequest(query)
            val requestJson = json.encodeToString(GraphQLRequest.serializer(), requestBody)

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    Timber.e("HTTP error: ${response.code} - $responseBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }

                if (responseBody.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                Timber.d("GraphQL response: ${responseBody.take(200)}")

                val graphQLResponse = json.decodeFromString<GraphQLResponse>(responseBody)

                // Check for GraphQL errors
                if (!graphQLResponse.errors.isNullOrEmpty()) {
                    val errorMessages = graphQLResponse.errors.joinToString("; ") { it.message }
                    Timber.e("GraphQL errors: $errorMessages")
                    return@withContext Result.failure(Exception("GraphQL errors: $errorMessages"))
                }

                // Extract sentants from response
                val sentants = graphQLResponse.data?.sentantAll ?: graphQLResponse.data?.sentant_all
                if (sentants == null) {
                    Timber.e("No sentant data in response")
                    return@withContext Result.failure(Exception("No sentant data in response"))
                }

                Timber.d("Successfully retrieved ${sentants.size} sentants")
                Result.success(sentants)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query sentants via GraphQL")
            Result.failure(e)
        }
    }
}
