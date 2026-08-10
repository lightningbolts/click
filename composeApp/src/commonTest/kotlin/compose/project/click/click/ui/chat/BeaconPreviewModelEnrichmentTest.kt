package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.models.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BeaconPreviewModelEnrichmentTest {

    @Test
    fun fromMessage_prefersKnownBeaconScheduleAndLocation() {
        val known = MapBeacon(
            id = "beacon-1",
            kind = MapBeaconKind.EVENT,
            latitude = 37.7,
            longitude = -122.4,
            metadata = MapBeaconMetadata(
                title = "Campus Mixer",
                description = "Meet near the quad",
                locationName = "Main Quad",
            ),
        )
        val message = Message(
            id = "m1",
            user_id = "u1",
            content = "Beacon: stub",
            timeCreated = 1L,
            metadata = JsonObject(
                mapOf(
                    "beacon_id" to JsonPrimitive("beacon-1"),
                    "title" to JsonPrimitive("stub title"),
                ),
            ),
        )
        val model = BeaconPreviewModel.fromMessage(
            message = message,
            knownBeacon = known,
            signedUp = true,
            bookmarked = true,
            checkedIn = false,
        )
        assertEquals("Campus Mixer", model.title)
        assertEquals("Main Quad", model.locationLabel)
        assertTrue(model.signedUp)
        assertTrue(model.bookmarked)
        assertEquals(false, model.checkedIn)
    }
}
