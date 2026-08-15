package com.ukrailtracker.app.data.remote.darwin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MissingDarwinServiceDetailsApiKeyException : IllegalStateException(
    "DARWIN_LDB_SERVICE_DETAILS_API_KEY is not set in android/local.properties",
)

/**
 * Rail Data Marketplace LDBWS JSON client for single-service detail.
 *
 * Spec path: `/api/20220120/GetServiceDetails/{serviceid}`
 * Gateway host/product path from RDM Service Details subscription.
 */
class OpenLdbServiceDetailsApi(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) {
    /**
     * @param serviceId Darwin `serviceID` from a board row (URL-encoded as a path segment).
     */
    suspend fun getServiceDetails(serviceId: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw MissingDarwinServiceDetailsApiKeyException()
        val id = serviceId.trim()
        require(id.isNotEmpty()) { "serviceId required" }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(PATH_PREFIX.trimStart('/'))
            .addPathSegment(id)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("x-apikey", apiKey)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "LDBWS GetServiceDetails HTTP ${response.code}: ${text.take(300)}",
                )
            }
            text
        }
    }

    companion object {
        /** RDM product gateway for Service Details. */
        const val BASE_URL =
            "https://api1.raildata.org.uk/1010-service-details1_2/LDBWS"
        const val PATH_PREFIX = "/api/20220120/GetServiceDetails"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
