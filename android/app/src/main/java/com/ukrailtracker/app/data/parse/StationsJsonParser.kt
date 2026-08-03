package com.ukrailtracker.app.data.parse

import android.util.JsonReader
import com.ukrailtracker.app.data.local.db.StationEntity
import com.ukrailtracker.app.data.source.SourcePayload
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

data class StationWriteBatch(
    val stations: List<StationEntity>,
    val sourceVersion: Int = STATIONS_ASSET_VERSION,
)

/** Bump when the bundled asset format or content generation changes. */
const val STATIONS_ASSET_VERSION = 1

class StationsJsonParser {
    fun parse(payload: SourcePayload): StationWriteBatch {
        val stations = ArrayList<StationEntity>(3_000)
        JsonReader(InputStreamReader(ByteArrayInputStream(payload.bytes), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "stations" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            parseStation(reader)?.let(stations::add)
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return StationWriteBatch(stations = stations)
    }

    private fun parseStation(reader: JsonReader): StationEntity? {
        var crs: String? = null
        var name: String? = null
        var lat: Double? = null
        var lng: Double? = null
        var operatorName = ""
        var operatorCode = ""
        var addressJson: String? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "crsCode" -> crs = reader.nextString()
                "name" -> name = reader.nextString()
                "location" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "latitude" -> lat = reader.nextDouble()
                            "longitude" -> lng = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "stationOperator" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> operatorName = reader.nextString()
                                "operatorCode" -> operatorCode = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                "address" -> {
                    if (reader.peek() == android.util.JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        // Consume object into a compact JSON string for detail screen
                        addressJson = readObjectAsJsonString(reader)
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (crs.isNullOrBlank() || name.isNullOrBlank() || lat == null || lng == null) {
            return null
        }
        return StationEntity(
            crsCode = crs,
            name = name,
            latitude = lat,
            longitude = lng,
            operatorName = operatorName,
            operatorCode = operatorCode,
            addressJson = addressJson,
        )
    }

    /**
     * JsonReader has already consumed the name; peek is BEGIN_OBJECT.
     * Rebuild a minimal JSON object string for later display.
     */
    private fun readObjectAsJsonString(reader: JsonReader): String {
        val map = linkedMapOf<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (reader.peek()) {
                android.util.JsonToken.NULL -> {
                    reader.nextNull()
                    map[key] = null
                }
                android.util.JsonToken.STRING -> map[key] = reader.nextString()
                android.util.JsonToken.NUMBER -> map[key] = reader.nextString()
                android.util.JsonToken.BOOLEAN -> map[key] = reader.nextBoolean().toString()
                else -> {
                    reader.skipValue()
                    map[key] = null
                }
            }
        }
        reader.endObject()
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
        return obj.toString()
    }
}
