package com.ukrailtracker.app.data.parse

import android.util.JsonReader
import android.util.JsonToken
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
const val STATIONS_ASSET_VERSION = 2

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
        var accessibilityJson: String? = null
        var stationMapUrl: String? = null
        var accessibleToilets: Boolean? = null

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
                    if (reader.peek() == JsonToken.NULL) {
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
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        addressJson = readFlatObjectAsJsonString(reader)
                    }
                }
                "stationAccessibility" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        accessibilityJson = parseAccessibilitySummary(reader)
                    }
                }
                "toiletsAndChanging" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        accessibleToilets = parseAccessibleToilets(reader)
                    }
                }
                "stationMap" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        stationMapUrl = parseStationMapUrl(reader)
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (crs.isNullOrBlank() || name.isNullOrBlank() || lat == null || lng == null) {
            return null
        }

        if (accessibleToilets != null) {
            accessibilityJson = mergeAccessibleToilets(accessibilityJson, accessibleToilets)
        }

        return StationEntity(
            crsCode = crs,
            name = name,
            latitude = lat,
            longitude = lng,
            operatorName = operatorName,
            operatorCode = operatorCode,
            addressJson = addressJson,
            accessibilityJson = accessibilityJson,
            stationMapUrl = stationMapUrl,
        )
    }

    private fun parseAccessibilitySummary(reader: JsonReader): String {
        var stepFree: String? = null
        var rampAvailable: Boolean? = null
        var tactilePaving: String? = null
        var wheelchairsAvailable: Boolean? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stepFreeCategory" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "category" -> stepFree = readNullableString(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                "trainRamp" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "available" -> rampAvailable = reader.nextBoolean()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                "tactilePaving" -> tactilePaving = readNullableString(reader)
                "wheelchairsAvailable" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        wheelchairsAvailable = reader.nextBoolean()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val obj = JSONObject()
        stepFree?.let { obj.put("stepFree", it) }
        rampAvailable?.let { obj.put("rampAvailable", it) }
        tactilePaving?.let { obj.put("tactilePaving", it) }
        wheelchairsAvailable?.let { obj.put("wheelchairsAvailable", it) }
        return obj.toString()
    }

    private fun parseAccessibleToilets(reader: JsonReader): Boolean? {
        var result: Boolean? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "toilets" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "accessibleToiletsAvailable" -> {
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull()
                                    } else {
                                        result = reader.nextBoolean()
                                    }
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return result
    }

    private fun parseStationMapUrl(reader: JsonReader): String? {
        var url: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "url" -> url = readNullableString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return url?.takeIf { it.isNotBlank() }
    }

    private fun mergeAccessibleToilets(existing: String?, accessibleToilets: Boolean): String {
        val obj = existing?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        obj.put("accessibleToilets", accessibleToilets)
        return obj.toString()
    }

    private fun readFlatObjectAsJsonString(reader: JsonReader): String {
        val map = linkedMapOf<String, String?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            when (reader.peek()) {
                JsonToken.NULL -> {
                    reader.nextNull()
                    map[key] = null
                }
                JsonToken.STRING -> map[key] = reader.nextString()
                JsonToken.NUMBER -> map[key] = reader.nextString()
                JsonToken.BOOLEAN -> map[key] = reader.nextBoolean().toString()
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

    private fun readNullableString(reader: JsonReader): String? =
        when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING -> reader.nextString()
            else -> {
                reader.skipValue()
                null
            }
        }
}
