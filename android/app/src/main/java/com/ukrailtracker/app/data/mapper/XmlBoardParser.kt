package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.Departure
import com.ukrailtracker.app.domain.model.DepartureBoard
import com.ukrailtracker.app.domain.model.TrainStatus
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory

/** SOAP/XML fallback parser for OpenLDBWS-shaped payloads. */
internal object XmlBoardParser {
    fun parse(soapXml: String, fetchedAtEpochMs: Long): DepartureBoard {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = true
        }
        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(soapXml.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        val crs = firstText(root, "crs").orEmpty()
        val locationName = firstText(root, "locationName").orEmpty()
        val generatedAtEpochMs = firstText(root, "generatedAt")
            ?.let(::parseGeneratedAt)
            ?: fetchedAtEpochMs

        val services = elementsByLocalName(root, "service").mapNotNull(::parseService)
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

    private fun parseService(service: Element): Departure? {
        val sta = childText(service, "sta")
        val eta = childText(service, "eta")
        val std = childText(service, "std")
        val etd = childText(service, "etd")
        val platform = childText(service, "platform")
        val operatorName = childText(service, "operator").orEmpty()
        val serviceId = childText(service, "serviceID")
        val isCancelled = childText(service, "isCancelled").equals("true", ignoreCase = true)

        val destinationEl = firstChildElement(service, "destination")
        val originEl = firstChildElement(service, "origin")
        val destinationName = destinationEl?.let { firstText(it, "locationName") }.orEmpty()
        val destinationCrs = destinationEl?.let { firstText(it, "crs") }
        val originName = originEl?.let { firstText(it, "locationName") }.orEmpty()

        val isArrival = std.isNullOrBlank() && !sta.isNullOrBlank()
        val scheduled = if (isArrival) sta.orEmpty() else std ?: sta.orEmpty()
        if (scheduled.isBlank()) return null
        val expected = if (isArrival) eta.orEmpty() else etd ?: eta.orEmpty()
        val labelDestination = if (isArrival) {
            if (originName.isNotBlank()) "from $originName" else "Arrival"
        } else {
            destinationName.ifBlank { "Unknown" }
        }

        val status = ArrDepBoardWithDetailsParser.resolveStatus(expected, isCancelled)
        val delay = if (status == TrainStatus.Delayed) {
            ArrDepBoardWithDetailsParser.delayMinutes(scheduled, expected)
        } else {
            null
        }

        return Departure(
            destination = labelDestination,
            destinationCrs = if (isArrival) null else destinationCrs,
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

    private fun parseGeneratedAt(raw: String): Long? =
        try {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }

    private fun firstText(root: Element, localName: String): String? =
        elementsByLocalName(root, localName).firstOrNull()?.textContent?.trim()?.ifBlank { null }

    private fun childText(parent: Element, localName: String): String? =
        firstChildElement(parent, localName)?.textContent?.trim()?.ifBlank { null }

    private fun firstChildElement(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val name = el.localName ?: el.tagName.substringAfter(':')
                if (name == localName) return el
            }
        }
        return null
    }

    private fun elementsByLocalName(root: Element, localName: String): List<Element> {
        val out = ArrayList<Element>()
        collect(root, localName, out)
        return out
    }

    private fun collect(node: Node, localName: String, out: MutableList<Element>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val el = node as Element
            val name = el.localName ?: el.tagName.substringAfter(':')
            if (name == localName) out += el
        }
        val children: NodeList = node.childNodes
        for (i in 0 until children.length) {
            collect(children.item(i), localName, out)
        }
    }
}
