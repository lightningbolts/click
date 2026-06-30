package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionEncounterMergeTest {
    @Test
    fun mergeRichestEncounterEventsCombinesRowsForSameConnectionTimestamp() {
        val sparse = ConnectionEncounter(
            id = "sparse",
            connectionId = "conn-1",
            encounteredAt = "2026-06-30T05:03:01.450Z",
            displayLocation = "Seattle, Washington",
            contextTags = listOf("Met Face-to-Face"),
        )
        val rich = ConnectionEncounter(
            id = "rich",
            connectionId = "conn-1",
            encounteredAt = "2026-06-30T05:03:01.450Z",
            gpsLat = 47.655,
            gpsLon = -122.303,
            weatherSnapshot = WeatherSnapshot(condition = "Cloudy", temperatureCelsius = 17.0),
            noiseLevel = "QUIET",
            exactNoiseLevelDb = 51.0,
            exactBarometricElevationM = 14.0,
            luxLevel = 150.0,
            motionVariance = 0.08,
            compassAzimuth = 188.0,
            batteryLevel = 80,
            contextTags = listOf("Extended Hangout"),
        )

        val merged = listOf(sparse, rich).mergeRichestEncounterEvents()

        assertEquals(1, merged.size)
        val event = merged.single()
        assertEquals("Seattle, Washington", event.displayLocation)
        assertEquals(47.655, event.gpsLat)
        assertEquals(-122.303, event.gpsLon)
        assertEquals("Cloudy", event.weatherSnapshot?.condition)
        assertEquals("QUIET", event.noiseLevel)
        assertEquals(51.0, event.exactNoiseLevelDb)
        assertEquals(14.0, event.exactBarometricElevationM)
        assertEquals(150.0, event.luxLevel)
        assertEquals(0.08, event.motionVariance)
        assertEquals(188.0, event.compassAzimuth)
        assertEquals(80, event.batteryLevel)
        assertEquals(listOf("Met Face-to-Face", "Extended Hangout"), event.contextTags)
    }
}
