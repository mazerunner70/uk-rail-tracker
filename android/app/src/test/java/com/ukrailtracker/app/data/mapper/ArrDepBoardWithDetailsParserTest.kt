package com.ukrailtracker.app.data.mapper

import com.ukrailtracker.app.domain.model.TrainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrDepBoardWithDetailsParserTest {

    private val parser = ArrDepBoardWithDetailsParser()

    @Test
    fun parsesSwaggerShapedJsonBoard() {
        val board = parser.parse(SAMPLE_JSON, fetchedAtEpochMs = 1_000L)
        assertEquals("LDS", board.crsCode)
        assertEquals("Leeds", board.stationName)
        assertTrue(board.departures.size >= 2)

        val xc = board.departures.first { it.destination.contains("Edinburgh") }
        assertEquals("19:08", xc.scheduledTimeLabel)
        assertEquals("19:22", xc.expectedLabel)
        assertEquals("11D", xc.platform)
        assertEquals(TrainStatus.Delayed, xc.status)
        assertEquals(14, xc.delayMinutes)
        assertEquals(false, xc.isArrival)
    }

    @Test
    fun parsesSoapXmlFixture() {
        val board = parser.parse(SAMPLE_SOAP, fetchedAtEpochMs = 1_000L)
        assertEquals("LDS", board.crsCode)
        val xc = board.departures.first { it.destination.contains("Edinburgh") }
        assertEquals(14, xc.delayMinutes)
    }

    @Test
    fun resolvesStatusLabels() {
        assertEquals(
            TrainStatus.OnTime,
            ArrDepBoardWithDetailsParser.resolveStatus("On time", false),
        )
        assertEquals(
            TrainStatus.Cancelled,
            ArrDepBoardWithDetailsParser.resolveStatus("Cancelled", false),
        )
        assertEquals(
            TrainStatus.Delayed,
            ArrDepBoardWithDetailsParser.resolveStatus("19:22", false),
        )
        assertEquals(14, ArrDepBoardWithDetailsParser.delayMinutes("19:08", "19:22"))
    }

    companion object {
        private val SAMPLE_JSON = """
            {
              "generatedAt": "2016-10-18T19:10:23.254674+01:00",
              "locationName": "Leeds",
              "crs": "LDS",
              "platformAvailable": true,
              "trainServices": [
                {
                  "sta": "19:07",
                  "eta": "On time",
                  "platform": "4B",
                  "operator": "Northern",
                  "operatorCode": "NT",
                  "serviceID": "AWse9ImRTDwOvAGPzGXOeQ==",
                  "origin": [{"locationName": "Appleby", "crs": "APP"}],
                  "destination": [{"locationName": "Leeds", "crs": "LDS"}]
                },
                {
                  "sta": "19:03",
                  "eta": "19:21",
                  "std": "19:08",
                  "etd": "19:22",
                  "platform": "11D",
                  "operator": "CrossCountry",
                  "operatorCode": "XC",
                  "serviceID": "AMQxIFns3fc8Q4+ZeM3xmw==",
                  "origin": [{"locationName": "Plymouth", "crs": "PLY"}],
                  "destination": [{"locationName": "Edinburgh", "crs": "EDB"}]
                }
              ]
            }
        """.trimIndent()

        private val SAMPLE_SOAP = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <GetArrDepBoardWithDetailsResponse>
                  <GetStationBoardResult>
                    <generatedAt>2016-10-18T19:10:23.254674+01:00</generatedAt>
                    <locationName>Leeds</locationName>
                    <crs>LDS</crs>
                    <trainServices>
                      <service>
                        <std>19:08</std>
                        <etd>19:22</etd>
                        <platform>11D</platform>
                        <operator>CrossCountry</operator>
                        <destination>
                          <location>
                            <locationName>Edinburgh</locationName>
                            <crs>EDB</crs>
                          </location>
                        </destination>
                      </service>
                    </trainServices>
                  </GetStationBoardResult>
                </GetArrDepBoardWithDetailsResponse>
              </soap:Body>
            </soap:Envelope>
        """.trimIndent()
    }
}
