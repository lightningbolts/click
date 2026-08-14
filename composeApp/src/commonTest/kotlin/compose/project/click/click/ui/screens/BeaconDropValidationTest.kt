package compose.project.click.click.ui.screens // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BeaconDropValidationTest {
    /**
     * Photos are optional for **every** category. This used to be enforced for all kinds except
     * soundtrack; the test exists so the requirement cannot creep back in.
     */
    @Test
    fun missingPhotoNeverBlocksSubmission() {
        BeaconDropCategory.entries.forEach { category ->
            val error =
                beaconDropValidationError(
                    category = category,
                    title = "Coffee run",
                    soundtrackUrl = "https://open.spotify.com/track/1",
                    hasEventLocation = true,
                )
            assertNull(error, "$category rejected a submission with no photo: $error")
        }
    }

    @Test
    fun titleIsRequiredExceptForSoundtracks() {
        assertEquals(
            "Please add a title.",
            beaconDropValidationError(
                category = BeaconDropCategory.HAZARD,
                title = "   ",
                soundtrackUrl = null,
                hasEventLocation = true,
            ),
        )
        assertNull(
            beaconDropValidationError(
                category = BeaconDropCategory.SOUNDTRACK,
                title = "",
                soundtrackUrl = "https://music.apple.com/song/1",
                hasEventLocation = true,
            ),
        )
    }

    @Test
    fun soundtrackNeedsALinkAndEventsNeedALocation() {
        assertEquals(
            "Please add a music link.",
            beaconDropValidationError(
                category = BeaconDropCategory.SOUNDTRACK,
                title = "",
                soundtrackUrl = " ",
                hasEventLocation = true,
            ),
        )
        assertEquals(
            "Set an event location (search an address or use my location).",
            beaconDropValidationError(
                category = BeaconDropCategory.EVENT,
                title = "Launch party",
                soundtrackUrl = null,
                hasEventLocation = false,
            ),
        )
    }
}
