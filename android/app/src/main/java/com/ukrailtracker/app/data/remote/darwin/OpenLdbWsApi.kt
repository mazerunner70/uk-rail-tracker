package com.ukrailtracker.app.data.remote.darwin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MissingDarwinApiKeyException : IllegalStateException(
    "DARWIN_LDB_API_KEY is not set in android/local.properties",
)

/**
 * Rail Data Marketplace LDBWS JSON client.
 *
 * Spec path: `/api/20220120/GetArrDepBoardWithDetails/{crs}`
 * Gateway host/product path from RDM subscription.
 */
class OpenLdbWsApi(
    private val apiKey: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) {
    suspend fun getArrDepBoardWithDetails(
        crs: String,
        numRows: Int = 10,
        filterCrs: String? = null,
        filterType: String = "to",
        timeOffset: Int = 0,
        timeWindow: Int = 120,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw MissingDarwinApiKeyException()

        val url = (baseUrl + PATH_TEMPLATE.replace("{crs}", crs.uppercase()))
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("numRows", numRows.coerceIn(1, 10).toString())
            .addQueryParameter("filterType", filterType)
            .addQueryParameter("timeOffset", timeOffset.toString())
            .addQueryParameter("timeWindow", timeWindow.toString())
            .apply {
                if (!filterCrs.isNullOrBlank()) {
                    addQueryParameter("filterCrs", filterCrs.uppercase())
                }
            }
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
                    "LDBWS HTTP ${response.code}: ${text.take(300)}",
                )
            }
            text
        }
    }

    companion object {
        /** RDM product gateway for Live Arrival and Departure Boards (arr + dep). */
        const val BASE_URL =
            "https://api1.raildata.org.uk/1010-live-arrival-and-departure-boards-arr-and-dep1_1/LDBWS"
        const val PATH_TEMPLATE = "/api/20220120/GetArrDepBoardWithDetails/{crs}"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
