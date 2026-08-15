package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDetailsParserTest {

    private val parser = ServiceDetailsParser()

    @Test
    fun parsesSwaggerShapedServiceDetails() {
        val option = parser.parseToOption(
            payload = SAMPLE_JSON,
            serviceId = "AMQxIFns3fc8Q4+ZeM3xmw==",
            originCrs = "LDS",
            destinationCrs = "EDB",
            destinationNameFallback = "Edinburgh",
        )

        assertEquals("AMQxIFns3fc8Q4+ZeM3xmw==", option.serviceId)
        assertEquals("LDS", option.originCrs)
        assertEquals("EDB", option.destinationCrs)
        assertEquals("Edinburgh", option.destinationName)
        assertEquals("CrossCountry", option.operatorName)
        assertEquals("11D", option.platform)
        assertEquals("19:08", option.scheduledDepartureLabel)
        assertEquals("19:22", option.expectedDepartureLabel)
        assertEquals(TrainStatus.Delayed, option.status)
        assertEquals(14, option.delayMinutes)
        assertFalse(option.isCancelled)

        // previous (PLY) + current (LDS) + subsequent (YRK, EDB)
        assertEquals(4, option.callingPoints.size)
        assertEquals("PLY", option.callingPoints[0].crs)
        assertEquals("12:01", option.callingPoints[0].actualTimeLabel)
        assertEquals("12:01", option.callingPoints[0].expectedLabel)
        assertEquals("LDS", option.callingPoints[1].crs)
        assertEquals("YRK", option.callingPoints[2].crs)
        assertEquals("EDB", option.callingPoints[3].crs)
        assertEquals("22:10", option.scheduledArrivalLabel)
        assertEquals("On time", option.expectedArrivalLabel)
    }

    @Test
    fun wrapsGetServiceDetailsResult() {
        val wrapped = """
            { "GetServiceDetailsResult": $SAMPLE_JSON }
        """.trimIndent()
        val option = parser.parseToOption(
            payload = wrapped,
            serviceId = "svc",
            originCrs = "LDS",
            destinationCrs = "EDB",
        )
        assertEquals("YRK", option.callingPoints[2].crs)
        assertTrue(option.callingPoints.any { it.crs == "EDB" })
    }

    @Test
    fun marksCancelledService() {
        val json = """
            {
              "locationName": "Leeds",
              "crs": "LDS",
              "operator": "Northern",
              "isCancelled": true,
              "std": "10:00",
              "etd": "Cancelled",
              "subsequentCallingPoints": [
                {
                  "callingPoint": [
                    {"locationName": "York", "crs": "YRK", "st": "10:45", "et": "Cancelled"}
                  ]
                }
              ]
            }
        """.trimIndent()
        val option = parser.parseToOption(
            payload = json,
            serviceId = "x",
            originCrs = "LDS",
            destinationCrs = "YRK",
        )
        assertTrue(option.isCancelled)
        assertEquals(TrainStatus.Cancelled, option.status)
    }

    companion object {
        private val SAMPLE_JSON = """
            {
              "generatedAt": "2016-10-18T19:10:23.254674+01:00",
              "serviceType": "train",
              "locationName": "Leeds",
              "crs": "LDS",
              "operator": "CrossCountry",
              "operatorCode": "XC",
              "platform": "11D",
              "std": "19:08",
              "etd": "19:22",
              "isCancelled": false,
              "previousCallingPoints": [
                {
                  "callingPoint": [
                    {"locationName": "Plymouth", "crs": "PLY", "st": "12:00", "at": "12:01"}
                  ]
                }
              ],
              "subsequentCallingPoints": [
                {
                  "callingPoint": [
                    {"locationName": "York", "crs": "YRK", "st": "19:45", "et": "19:55"},
                    {"locationName": "Edinburgh", "crs": "EDB", "st": "22:10", "et": "On time"}
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
