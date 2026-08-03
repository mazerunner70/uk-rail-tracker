package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.TrainStatus
import org.json.JSONArray
import org.json.JSONObject

object DepartureBoardJson {
    fun encode(board: DepartureBoard): String {
        val root = JSONObject()
        root.put("crsCode", board.crsCode)
        root.put("stationName", board.stationName)
        root.put("generatedAtEpochMs", board.generatedAtEpochMs)
        root.put("fetchedAtEpochMs", board.fetchedAtEpochMs)
        val arr = JSONArray()
        board.departures.forEach { d ->
            arr.put(
                JSONObject()
                    .put("destination", d.destination)
                    .put("destinationCrs", d.destinationCrs)
                    .put("scheduledTimeLabel", d.scheduledTimeLabel)
                    .put("expectedLabel", d.expectedLabel)
                    .put("platform", d.platform)
                    .put("operatorName", d.operatorName)
                    .put("status", d.status.name)
                    .put("delayMinutes", d.delayMinutes)
                    .put("serviceId", d.serviceId)
                    .put("isArrival", d.isArrival),
            )
        }
        root.put("departures", arr)
        return root.toString()
    }

    fun decode(json: String, fromCache: Boolean = true): DepartureBoard {
        val root = JSONObject(json)
        val arr = root.optJSONArray("departures") ?: JSONArray()
        val departures = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Departure(
                        destination = o.optString("destination"),
                        destinationCrs = o.optString("destinationCrs").takeIf { it.isNotBlank() },
                        scheduledTimeLabel = o.optString("scheduledTimeLabel"),
                        expectedLabel = o.optString("expectedLabel"),
                        platform = o.optString("platform").takeIf { it.isNotBlank() },
                        operatorName = o.optString("operatorName"),
                        status = runCatching {
                            TrainStatus.valueOf(o.optString("status", TrainStatus.Unknown.name))
                        }.getOrDefault(TrainStatus.Unknown),
                        delayMinutes = if (o.isNull("delayMinutes")) null else o.optInt("delayMinutes"),
                        serviceId = o.optString("serviceId").takeIf { it.isNotBlank() },
                        isArrival = o.optBoolean("isArrival", false),
                    ),
                )
            }
        }
        return DepartureBoard(
            crsCode = root.optString("crsCode"),
            stationName = root.optString("stationName"),
            generatedAtEpochMs = root.optLong("generatedAtEpochMs"),
            fetchedAtEpochMs = root.optLong("fetchedAtEpochMs"),
            departures = departures,
            fromCache = fromCache,
        )
    }
}
