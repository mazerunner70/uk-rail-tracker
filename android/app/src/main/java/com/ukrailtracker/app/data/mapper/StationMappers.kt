package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.data.local.db.StationEntity
import com.ukrailtracker.app.domain.model.Station
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
    )
}
