package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.data.local.db.StationEntity
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.domain.model.StationAccessibility
import org.json.JSONObject

fun StationEntity.toDomain(): Station {
    val lines = mutableListOf<String>()
    var postcode: String? = null
    addressJson?.let { raw ->
        runCatching {
            val obj = JSONObject(raw)
            listOf(
                "addressLine1",
                "addressLine2",
                "addressLine3",
                "addressLine4",
                "addressLine5",
            ).forEach { key ->
                val value = obj.optString(key, "").trim()
                if (value.isNotEmpty() && value != "null") lines += value
            }
            val pc = obj.optString("postcode", "").trim()
            if (pc.isNotEmpty() && pc != "null") postcode = pc
        }
    }
    return Station(
        crsCode = crsCode,
        name = name,
        latitude = latitude,
        longitude = longitude,
        operatorName = operatorName,
        operatorCode = operatorCode,
        addressLines = lines,
        postcode = postcode,
        accessibility = accessibilityJson.toAccessibility(),
        stationMapUrl = stationMapUrl,
    )
}

private fun String?.toAccessibility(): StationAccessibility {
    if (this.isNullOrBlank()) return StationAccessibility()
    return runCatching {
        val obj = JSONObject(this)
        StationAccessibility(
            stepFreeCategory = obj.optString("stepFree").takeIf { it.isNotBlank() },
            rampAvailable = if (obj.has("rampAvailable")) obj.optBoolean("rampAvailable") else null,
            tactilePaving = obj.optString("tactilePaving").takeIf { it.isNotBlank() },
            wheelchairsAvailable = if (obj.has("wheelchairsAvailable")) {
                obj.optBoolean("wheelchairsAvailable")
            } else {
                null
            },
            accessibleToiletsAvailable = if (obj.has("accessibleToilets")) {
                obj.optBoolean("accessibleToilets")
            } else {
                null
            },
        )
    }.getOrDefault(StationAccessibility())
}
