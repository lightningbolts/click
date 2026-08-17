package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassSheetGesturePhysicsTest {
    @Test
    fun dragCommitsOnlyPastHalfTravel() {
        assertFalse(
            shouldCommitVerticalDismiss(
                offsetPx = 200f,
                travelPx = 400f,
                velocityPxPerSec = 0f,
            ),
        )
        assertTrue(
            shouldCommitVerticalDismiss(
                offsetPx = 201f,
                travelPx = 400f,
                velocityPxPerSec = 0f,
            ),
        )
    }

    @Test
    fun interactiveBack_commitsPastMidpointOrOnFlick() {
        assertFalse(
            shouldCommitInteractiveBack(
                offsetPx = 200f,
                widthPx = 400f,
                velocityXPxPerSec = 0f,
            ),
        )
        assertTrue(
            shouldCommitInteractiveBack(
                offsetPx = 201f,
                widthPx = 400f,
                velocityXPxPerSec = 0f,
            ),
        )
        assertTrue(
            shouldCommitInteractiveBack(
                offsetPx = 40f,
                widthPx = 400f,
                velocityXPxPerSec = GlassGestureFlickVelocityPxPerSec + 1f,
            ),
        )
    }

    @Test
    fun interactiveBack_settleAllowsCommitAndCancelOvershoot() {
        assertEquals(400f, clampInteractiveBackSettleOffset(400f, 400f, committing = true))
        assertEquals(
            400f * (1f + InteractiveBackCommitOvershootRatio),
            clampInteractiveBackSettleOffset(500f, 400f, committing = true),
        )
        assertEquals(0f, clampInteractiveBackSettleOffset(-8f, 400f, committing = true))
        assertEquals(
            -400f * InteractiveBackCancelOvershootRatio,
            clampInteractiveBackSettleOffset(-50f, 400f, committing = false),
        )
        assertEquals(12f, clampInteractiveBackSettleOffset(12f, 400f, committing = false))
    }

    @Test
    fun downwardFlickCommitsFromAnySheetBodyRegion() {
        assertTrue(
            shouldCommitVerticalDismiss(
                offsetPx = 1f,
                travelPx = 400f,
                velocityPxPerSec = GlassGestureFlickVelocityPxPerSec + 1f,
            ),
        )
    }

    @Test
    fun surfaceDismissAllowedOnlyAtTopWithoutPriorScrollOrKeyboardBlock() {
        assertTrue(
            shouldAllowSheetSurfaceDismiss(
                atTop = true,
                contentScrolledThisGesture = false,
                surfaceDragActive = true,
                blockSurfaceDrag = false,
            ),
        )
        assertFalse(
            shouldAllowSheetSurfaceDismiss(
                atTop = false,
                contentScrolledThisGesture = false,
                surfaceDragActive = true,
                blockSurfaceDrag = false,
            ),
        )
        assertFalse(
            shouldAllowSheetSurfaceDismiss(
                atTop = true,
                contentScrolledThisGesture = true,
                surfaceDragActive = true,
                blockSurfaceDrag = false,
            ),
        )
        assertFalse(
            shouldAllowSheetSurfaceDismiss(
                atTop = true,
                contentScrolledThisGesture = false,
                surfaceDragActive = true,
                blockSurfaceDrag = true,
            ),
        )
        assertFalse(
            shouldAllowSheetSurfaceDismiss(
                atTop = true,
                contentScrolledThisGesture = false,
                surfaceDragActive = false,
                blockSurfaceDrag = false,
            ),
        )
    }
}
