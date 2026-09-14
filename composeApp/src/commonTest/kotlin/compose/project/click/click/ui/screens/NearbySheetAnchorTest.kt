package compose.project.click.click.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals

class NearbySheetAnchorTest {
    private fun settle(
        offset: Float,
        velocity: Float = 0f,
    ) = nearbySheetSettleAnchor(offset, velocity, 0f, 400f, 800f, 700f)

    @Test
    fun slowDragSettlesAtNearestAnchor() {
        assertEquals(NearbySheetAnchor.Expanded, settle(100f))
        assertEquals(NearbySheetAnchor.Half, settle(350f))
        assertEquals(NearbySheetAnchor.Collapsed, settle(750f))
    }

    @Test
    fun flickTraversesToTheDirectionalEndEvenNearAnAnchor() {
        assertEquals(NearbySheetAnchor.Expanded, settle(790f, -900f))
        assertEquals(NearbySheetAnchor.Collapsed, settle(10f, 900f))
        assertEquals(NearbySheetAnchor.Expanded, settle(0f, -900f))
        assertEquals(NearbySheetAnchor.Collapsed, settle(800f, 900f))
    }
}
