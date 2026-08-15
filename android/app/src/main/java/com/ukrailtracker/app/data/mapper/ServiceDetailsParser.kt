package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.CallingPoint
import com.ukrailtracker.app.domain.model.JourneyOption
import com.ukrailtracker.app.domain.model.TrainStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses RDM/LDBWS JSON [ServiceDetails] into a domain [JourneyOption].
 *
 * Calling points are rebuilt as previous + current board location + subsequent
 * (swagger `ServiceDetails`). Destination/origin arrays are not on this payload —
 * names/times come from calling points and the caller's CRS pair.
 */
class ServiceDetailsParser {

    fun parseToOption(
        payload: String,
        serviceId: String,
        originCrs: String,
        destinationCrs: String,
        destinationNameFallback: String = "",
    ): JourneyOption {
        val root = JSONObject(payload.trim())
        val details = root.optJSONObject("GetServiceDetailsResult")
            ?: root.optJSONObject("ServiceDetails")
            ?: root

        val origin = originCrs.trim().uppercase()
        val dest = destinationCrs.trim().uppercase()
        val previous = flattenCallingPointLists(details.opt("previousCallingPoints"))
        val subsequent = flattenCallingPointLists(details.opt("subsequentCallingPoints"))
        val current = currentCallingPoint(details)
        val callingPoints = mergeCallingPoints(previous, current, subsequent)

        val originPoint = callingPoints.firstOrNull { it.crs?.uppercase() == origin }
        val destPoint = callingPoints.firstOrNull { it.crs?.uppercase() == dest }
            ?: callingPoints.lastOrNull()

        val scheduledDeparture = originPoint?.scheduledTimeLabel
            ?: details.optString("std").ifBlank { null }
            ?: details.optString("sta").orEmpty()
        val expectedDeparture = originPoint?.expectedLabel
            ?: details.optString("etd").ifBlank { null }
            ?: details.optString("atd").ifBlank { null }
            ?: details.optString("eta").ifBlank { null }
            ?: details.optString("ata").orEmpty()

        val isCancelled = details.optBoolean("isCancelled", false) ||
            destPoint?.isCancelled == true
        val status = ArrDepBoardWithDetailsParser.resolveStatus(
            expected = expectedDeparture.ifBlank { destPoint?.expectedLabel.orEmpty() },
            isCancelled = isCancelled,
        )
        val delay = when {
            status != TrainStatus.Delayed -> null
            else -> ArrDepBoardWithDetailsParser.delayMinutes(
                scheduledDeparture,
                expectedDeparture,
            ) ?: run {
                val sArr = destPoint?.scheduledTimeLabel
                val eArr = destPoint?.expectedLabel
                if (sArr != null && eArr != null) {
                    ArrDepBoardWithDetailsParser.delayMinutes(sArr, eArr)
                } else {
                    null
                }
            }
        }

        val destinationName = destPoint?.locationName?.takeIf { it.isNotBlank() }
            ?: subsequent.lastOrNull()?.locationName?.takeIf { it.isNotBlank() }
            ?: destinationNameFallback.ifBlank { "Unknown" }

        return JourneyOption(
            serviceId = serviceId,
            originCrs = origin,
            destinationCrs = dest,
            destinationName = destinationName,
            scheduledDepartureLabel = scheduledDeparture,
            expectedDepartureLabel = expectedDeparture.ifBlank { "—" },
            scheduledArrivalLabel = destPoint?.scheduledTimeLabel,
            expectedArrivalLabel = destPoint?.expectedLabel,
            platform = details.optString("platform").ifBlank { null },
            operatorName = details.optString("operator"),
            status = status,
            delayMinutes = delay,
            callingPoints = callingPoints,
            isCancelled = isCancelled,
        )
    }

    private fun currentCallingPoint(details: JSONObject): CallingPoint? {
        val name = details.optString("locationName").trim()
        val crs = details.optString("crs").ifBlank { null }
        if (name.isBlank() && crs.isNullOrBlank()) return null
        val st = details.optString("std").ifBlank { null }
            ?: details.optString("sta").ifBlank { null }
        val et = details.optString("etd").ifBlank { null }
            ?: details.optString("eta").ifBlank { null }
        val at = details.optString("atd").ifBlank { null }
            ?: details.optString("ata").ifBlank { null }
        val expected = et ?: at
        return CallingPoint(
            locationName = name.ifBlank { crs.orEmpty() },
            crs = crs,
            scheduledTimeLabel = st,
            expectedLabel = expected,
            isCancelled = details.optBoolean("isCancelled", false) ||
                expected.equals("Cancelled", ignoreCase = true),
            actualTimeLabel = at,
        )
    }

    private fun mergeCallingPoints(
        previous: List<CallingPoint>,
        current: CallingPoint?,
        subsequent: List<CallingPoint>,
    ): List<CallingPoint> {
        val merged = ArrayList<CallingPoint>(previous.size + subsequent.size + 1)
        val seen = HashSet<String>()
        fun addAll(points: List<CallingPoint>) {
            for (cp in points) {
                val key = cp.crs?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                    ?: cp.locationName.trim().lowercase()
                if (key.isEmpty() || !seen.add(key)) continue
                merged += cp
            }
        }
        addAll(previous)
        if (current != null) addAll(listOf(current))
        addAll(subsequent)
        return merged
    }

    private fun flattenCallingPointLists(node: Any?): List<CallingPoint> {
        if (node == null || node == JSONObject.NULL) return emptyList()
        return when (node) {
            is JSONArray -> (0 until node.length()).flatMap { i ->
                flattenCallingPointLists(node.opt(i))
            }
            is JSONObject -> {
                when {
                    node.has("callingPointList") ->
                        flattenCallingPointLists(node.opt("callingPointList"))
                    node.has("callingPoint") ->
                        parseCallingPointArray(node.opt("callingPoint"))
                    node.has("locationName") ->
                        listOfNotNull(parseCallingPoint(node))
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseCallingPointArray(node: Any?): List<CallingPoint> {
        if (node == null || node == JSONObject.NULL) return emptyList()
        return when (node) {
            is JSONArray -> (0 until node.length()).mapNotNull { i ->
                node.optJSONObject(i)?.let(::parseCallingPoint)
            }
            is JSONObject -> listOfNotNull(parseCallingPoint(node))
            else -> emptyList()
        }
    }

    private fun parseCallingPoint(obj: JSONObject): CallingPoint? {
        val name = obj.optString("locationName").trim()
        if (name.isBlank()) return null
        val st = obj.optString("st").ifBlank { null }
        val et = obj.optString("et").ifBlank { null }
        val at = obj.optString("at").ifBlank { null }
        val expected = et ?: at
        return CallingPoint(
            locationName = name,
            crs = obj.optString("crs").ifBlank { null },
            scheduledTimeLabel = st,
            expectedLabel = expected,
            isCancelled = obj.optBoolean("isCancelled", false) ||
                expected.equals("Cancelled", ignoreCase = true),
            actualTimeLabel = at,
        )
    }
}
