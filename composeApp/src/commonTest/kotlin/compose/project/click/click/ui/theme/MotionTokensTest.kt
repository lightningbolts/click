package compose.project.click.click.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTokensTest {
    @Test
    fun durationTokens_matchLaunchVocabulary() {
        assertEquals(90, MotionTokens.Duration.Instant)
        assertEquals(140, MotionTokens.Duration.Fast)
        assertEquals(190, MotionTokens.Duration.Standard)
        assertEquals(240, MotionTokens.Duration.Deliberate)
        assertEquals(320, MotionTokens.Duration.Emphasized)
    }

    @Test
    fun pressScale_isRestrainedByControlSize() {
        assertEquals(0.95f, MotionTokens.PressScale.IconPressedScale)
        assertEquals(0.98f, MotionTokens.PressScale.ButtonPressedScale)
        assertEquals(0.985f, MotionTokens.PressScale.CardPressedScale)
        assertTrue(MotionTokens.PressScale.CardPressedScale > MotionTokens.PressScale.ButtonPressedScale)
        assertTrue(MotionTokens.PressScale.ButtonPressedScale > MotionTokens.PressScale.IconPressedScale)
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.PressScale.DampingRatio)
    }

    @Test
    fun ordinaryContent_doesNotBounce() {
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.SoftEnter.DampingRatio)
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.SoftExit.DampingRatio)
        assertEquals(Spring.DampingRatioNoBouncy, MotionTokens.Destructive.DampingRatio)
        assertEquals(8.dp, MotionTokens.Content.EnterOffset)
    }

    @Test
    fun gesturePileSnap_staysExpressive() {
        assertEquals(Spring.DampingRatioMediumBouncy, MotionTokens.PileSnap.DampingRatio)
    }
}
