package compose.project.click.click.ui.theme // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.requiresAttachedImage // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardVisualTest {
    @Test
    fun sameIdIsDeterministic() {
        val a = generateCardVisual("event-42")
        val b = generateCardVisual("event-42")
        assertEquals(a.hash, b.hash)
        assertEquals(a.gradient, b.gradient)
        assertEquals(a.pattern, b.pattern)
        assertEquals(a.purpleDominant, b.purpleDominant)
        assertEquals(a.pinShape, b.pinShape)
    }

    @Test
    fun differentIdsDiverge() {
        val a = generateCardVisual("event-1")
        val b = generateCardVisual("event-2")
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun purpleBlueRatioStaysInBand() {
        val samples = (0 until 800).map { generateCardVisual("id-$it") }
        val purple = samples.count { it.purpleDominant }
        val ratio = purple.toDouble() / samples.size
        assertTrue(ratio in 0.58..0.68, "purple ratio was $ratio")
    }

    @Test
    fun beaconShapesAreTypeStable() {
        assertEquals(
            BeaconPinShape.CIRCLE,
            generateCardVisual("a", MapBeaconKind.SOUNDTRACK).pinShape,
        )
        assertEquals(
            BeaconPinShape.TRIANGLE,
            generateCardVisual("a", MapBeaconKind.HAZARD).pinShape,
        )
        assertEquals(
            BeaconPinShape.DIAMOND,
            generateCardVisual("a", MapBeaconKind.SOS).pinShape,
        )
        assertEquals(
            BeaconPinShape.HEXAGON,
            generateCardVisual("a", MapBeaconKind.UTILITY).pinShape,
        )
        assertEquals(
            BeaconPinShape.ROUNDED_SQUARE,
            generateCardVisual("a", MapBeaconKind.EVENT).pinShape,
        )
        assertEquals(
            BeaconPinShape.ROUNDED_RECT,
            generateCardVisual("a", MapBeaconKind.STUDY).pinShape,
        )
    }

    @Test
    fun pileSlotsAreSeededNotChaotic() {
        val a = pileSlotForCluster("saved", 0, 6)
        val b = pileSlotForCluster("saved", 0, 6)
        assertEquals(a, b)
        val other = pileSlotForCluster("reconnect", 0, 6)
        assertTrue(a.rotationDeg != other.rotationDeg || a.xFrac != other.xFrac)
    }

    @Test
    fun contentAlwaysUsesLightOnScrim() {
        val visual = generateCardVisual("contrast-check")
        assertEquals(1f, visual.onContent.alpha, 0.01f)
        assertTrue(visual.contentScrim.alpha in 0.3f..0.6f)
    }

    @Test
    fun soundtrackSkipsRequiredImage() {
        assertTrue(!MapBeaconKind.SOUNDTRACK.requiresAttachedImage())
        assertTrue(MapBeaconKind.EVENT.requiresAttachedImage())
        assertTrue(MapBeaconKind.UTILITY.requiresAttachedImage())
    }
}
