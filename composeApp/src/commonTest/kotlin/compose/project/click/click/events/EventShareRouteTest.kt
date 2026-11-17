package compose.project.click.click.events // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventShareRouteTest {
    @Test
    fun geoLabelIsEncoded() {
        val geo = eventMapsGeoUri(40.0, -73.0, "Tacos & Beer #1 (late)")
        assertTrue(geo.startsWith("geo:40.0,-73.0?q=40.0,-73.0("), geo)
        assertTrue(geo.endsWith(")"), geo)
        val label = geo.substringAfter("(").removeSuffix(")")
        assertFalse(label.contains('&') || label.contains('#') || label.contains(' ') || label.contains('('), label)
        assertTrue(label.contains("%26") && label.contains("%23"), label)
    }

    @Test
    fun geoWithoutLabelIsCoordinatesOnly() {
        assertEquals("geo:1.0,2.0?q=1.0,2.0", eventMapsGeoUri(1.0, 2.0, "  "))
        assertEquals("geo:1.0,2.0?q=1.0,2.0", eventMapsGeoUri(1.0, 2.0, null))
    }

    @Test
    fun appleMapsLabelIsEncoded() {
        val url = eventMapsAppleHttpUrl(40.0, -73.0, "Tacos & Beer #1")
        assertTrue(url.startsWith("https://maps.apple.com/?ll=40.0,-73.0&q="), url)
        val q = url.substringAfter("&q=")
        assertFalse(q.contains('&') || q.contains('#'), q)
        assertEquals("https://maps.apple.com/?ll=1.0,2.0&q=1.0,2.0", eventMapsAppleHttpUrl(1.0, 2.0, null))
    }
}
