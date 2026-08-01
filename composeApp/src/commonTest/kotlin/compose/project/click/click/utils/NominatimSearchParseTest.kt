package compose.project.click.click.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NominatimSearchParseTest {
    @Test
    fun parseNominatimSearchResults_mapsLatLonAndLabels() {
        val body = """
            [
              {
                "lat": "47.6553",
                "lon": "-122.3035",
                "display_name": "Red Square, University of Washington, Seattle, Washington, USA",
                "address": {
                  "amenity": "Red Square",
                  "city": "Seattle",
                  "state": "Washington"
                }
              },
              {
                "lat": "47.6062",
                "lon": "-122.3321",
                "display_name": "Pike Place Market, Seattle, WA, USA",
                "address": {
                  "road": "Pike Place",
                  "city": "Seattle"
                }
              }
            ]
        """.trimIndent()
        val places = parseNominatimSearchResults(body, limit = 5)
        assertEquals(2, places.size)
        assertEquals(47.6553, places[0].latitude)
        assertEquals(-122.3035, places[0].longitude)
        assertEquals("Red Square", places[0].shortLabel)
        assertTrue(places[1].displayName.contains("Pike Place"))
    }

    @Test
    fun parseNominatimSearchResults_emptyOrInvalid_returnsEmpty() {
        assertTrue(parseNominatimSearchResults("").isEmpty())
        assertTrue(parseNominatimSearchResults("not-json").isEmpty())
        assertTrue(parseNominatimSearchResults("[]").isEmpty())
    }

    @Test
    fun parseNominatimSearchResults_respectsLimit() {
        val body = """
            [
              {"lat":"1.0","lon":"2.0","display_name":"A"},
              {"lat":"3.0","lon":"4.0","display_name":"B"},
              {"lat":"5.0","lon":"6.0","display_name":"C"}
            ]
        """.trimIndent()
        assertEquals(1, parseNominatimSearchResults(body, limit = 1).size)
    }

    @Test
    fun rankGeocodedPlaces_prefersWordMatchThenNearest() {
        val farMatch = GeocodedPlace(
            latitude = 40.0,
            longitude = -74.0,
            displayName = "Red Square, New York, USA",
            shortLabel = "Red Square",
        )
        val nearPartial = GeocodedPlace(
            latitude = 47.66,
            longitude = -122.30,
            displayName = "University Bookstore, Seattle, WA",
            shortLabel = "University Bookstore",
        )
        val nearExact = GeocodedPlace(
            latitude = 47.655,
            longitude = -122.303,
            displayName = "Red Square, University of Washington, Seattle, WA",
            shortLabel = "Red Square",
        )
        val ranked = rankGeocodedPlaces(
            places = listOf(farMatch, nearPartial, nearExact),
            query = "red square",
            nearLat = 47.655,
            nearLon = -122.303,
            limit = 3,
        )
        assertEquals("Red Square, University of Washington, Seattle, WA", ranked.first().displayName)
        assertTrue(ranked.any { it.shortLabel == "University Bookstore" }.not() || ranked.size == 3)
        // Exact word match + nearest should beat distant exact label.
        assertEquals(nearExact.latitude, ranked.first().latitude)
    }
}
