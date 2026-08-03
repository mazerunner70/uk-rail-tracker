package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.TrainStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Parses RDM/LDBWS JSON `StationBoardWithDetails` into domain [DepartureBoard].
 *
 * Tolerates both swagger-shaped arrays and SOAP-JSON hybrids where
 * `trainServices` is `{ "service": [ ... ] }` or a single service object.
 */
class ArrDepBoardWithDetailsParser {
    fun parse(payload: String, fetchedAtEpochMs: Long = System.currentTimeMillis()): DepartureBoard {
        val trimmed = payload.trim()
        return if (trimmed.startsWith("<")) {
            // Legacy SOAP XML path (unit fixtures / old host)
            ArrDepBoardWithDetailsXmlParser.parse(trimmed, fetchedAtEpochMs)
        } else {
            parseJson(trimmed, fetchedAtEpochMs)
        }
    }

    private fun parseJson(json: String, fetchedAtEpochMs: Long): DepartureBoard {
        val root = JSONObject(json)
        val board = root.optJSONObject("GetStationBoardResult")
            ?: root.optJSONObject("StationBoardWithDetails")
            ?: root

        val crs = board.optString("crs").trim()
        val locationName = board.optString("locationName").trim()
        val generatedAtEpochMs = board.optString("generatedAt")
            .takeIf { it.isNotBlank() }
            ?.let(::parseGeneratedAt)
            ?: fetchedAtEpochMs

        val services = mutableListOf<Departure>()
        services += parseServiceList(board.opt("trainServices"))
        services += parseServiceList(board.opt("busServices"))

        val ordered = services.sortedWith(
            compareBy<Departure> { it.isArrival }
                .thenBy { it.scheduledTimeLabel },
        )

        return DepartureBoard(
            crsCode = crs,
            stationName = locationName,
            generatedAtEpochMs = generatedAtEpochMs,
            fetchedAtEpochMs = fetchedAtEpochMs,
            departures = ordered,
            fromCache = false,
        )
    }

    private fun parseServiceList(node: Any?): List<Departure> {
        if (node == null || node == JSONObject.NULL) return emptyList()
        return when (node) {
            is JSONArray -> (0 until node.length()).mapNotNull { i ->
                node.optJSONObject(i)?.let(::parseService)
            }
            is JSONObject -> {
                when {
                    node.has("service") -> parseServiceList(node.opt("service"))
                    else -> listOfNotNull(parseService(node))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseService(service: JSONObject): Departure? {
        val sta = service.optString("sta").ifBlank { null }
        val eta = service.optString("eta").ifBlank { null }
        val std = service.optString("std").ifBlank { null }
        val etd = service.optString("etd").ifBlank { null }
        val platform = service.optString("platform").ifBlank { null }
        val operatorName = service.optString("operator")
        val serviceId = service.optString("serviceID").ifBlank { null }
        val isCancelled = service.optBoolean("isCancelled", false)

        val destination = firstLocation(service.opt("destination"))
        val origin = firstLocation(service.opt("origin"))

        val isArrival = std.isNullOrBlank() && !sta.isNullOrBlank()
        val scheduled = if (isArrival) sta.orEmpty() else std ?: sta.orEmpty()
        if (scheduled.isBlank()) return null
        val expected = if (isArrival) eta.orEmpty() else etd ?: eta.orEmpty()
        val labelDestination = if (isArrival) {
            val originName = origin?.locationName.orEmpty()
            if (originName.isNotBlank()) "from $originName" else "Arrival"
        } else {
            destination?.locationName?.ifBlank { null } ?: "Unknown"
        }

        val status = resolveStatus(expected = expected, isCancelled = isCancelled)
        val delay = if (status == TrainStatus.Delayed) delayMinutes(scheduled, expected) else null

        return Departure(
            destination = labelDestination,
            destinationCrs = if (isArrival) null else destination?.crs,
            scheduledTimeLabel = scheduled,
            expectedLabel = expected.ifBlank { "—" },
            platform = platform,
            operatorName = operatorName,
            status = status,
            delayMinutes = delay,
            serviceId = serviceId,
            isArrival = isArrival,
        )
    }

    private data class Loc(val locationName: String, val crs: String?)

    private fun firstLocation(node: Any?): Loc? {
        if (node == null || node == JSONObject.NULL) return null
        return when (node) {
            is JSONArray -> {
                if (node.length() == 0) null
                else node.optJSONObject(0)?.let {
                    Loc(it.optString("locationName"), it.optString("crs").ifBlank { null })
                }
            }
            is JSONObject -> {
                when {
                    node.has("location") -> firstLocation(node.opt("location"))
                    node.has("locationName") -> Loc(
                        node.optString("locationName"),
                        node.optString("crs").ifBlank { null },
                    )
                    else -> null
                }
            }
            else -> null
        }
    }

    companion object {
        fun resolveStatus(expected: String, isCancelled: Boolean): TrainStatus {
            if (isCancelled || expected.equals("Cancelled", true)) return TrainStatus.Cancelled
            if (expected.equals("On time", true)) return TrainStatus.OnTime
            if (expected.equals("Delayed", true)) return TrainStatus.Delayed
            if (expected.equals("Departed", true)) return TrainStatus.Departed
            if (TIME_REGEX.matches(expected)) return TrainStatus.Delayed
            return TrainStatus.Unknown
        }

        fun delayMinutes(scheduled: String, expected: String): Int? {
            val s = parseHm(scheduled) ?: return null
            val e = parseHm(expected) ?: return null
            var diff = e - s
            if (diff < -12 * 60) diff += 24 * 60
            if (diff > 12 * 60) diff -= 24 * 60
            return diff.takeIf { it > 0 }
        }

        private fun parseHm(value: String): Int? {
            val m = TIME_REGEX.matchEntire(value.trim()) ?: return null
            return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
        }

        private fun parseGeneratedAt(raw: String): Long? =
            try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }

        private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")
    }
}

/** Kept for SOAP XML fixtures / fallback payloads. */
internal object ArrDepBoardWithDetailsXmlParser {
    fun parse(soapXml: String, fetchedAtEpochMs: Long): DepartureBoard {
        // Reuse JSON-style mapping via a thin XML→fields walk from the previous DOM parser.
        return XmlBoardParser.parse(soapXml, fetchedAtEpochMs)
    }
}
