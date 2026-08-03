package com.ukrailtracker.app.data.source.asset

import android.content.Context
import com.ukrailtracker.app.data.source.DataSource
import com.ukrailtracker.app.data.source.SourceId
import com.ukrailtracker.app.data.source.SourcePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetStationsDataSource(
    private val context: Context,
    private val assetPath: String = "stations.json",
) : DataSource {
    override suspend fun fetch(): SourcePayload = withContext(Dispatchers.IO) {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        SourcePayload(
            sourceId = SourceId.BUNDLED_STATIONS,
            bytes = bytes,
            contentType = "application/json",
            fetchedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
