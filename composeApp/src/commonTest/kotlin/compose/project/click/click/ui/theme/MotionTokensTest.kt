package compose.project.click.click.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

class MotionTokensTest {
    @Test
    fun durationTokens_matchSharedScale() {
        assertEquals(120, MotionTokens.Duration.Instant)
        assertEquals(160, MotionTokens.Duration.Fast)
        assertEquals(240, MotionTokens.Duration.Standard)
        assertEquals(360, MotionTokens.Duration.Deliberate)
        assertEquals(500, MotionTokens.Duration.Emphasized)
    }

    @Test
    fun pressScale_isOneValueForEveryControl() {
        assertEquals(0.97f, MotionTokens.PressScale.Scale)
        assertEquals(0.97f, MotionTokens.PressScale.IconPressedScale)
        assertEquals(0.97f, MotionTokens.PressScale.ButtonPressedScale)
        assertEquals(0.97f, MotionTokens.PressScale.CardPressedScale)
    }

    @Test
    fun pressSpring_matchesIosClickMotionPress() {
        assertEquals(0.7f, MotionTokens.PressScale.DampingRatio)
        val response = MotionTokens.PressScale.IosResponseSeconds
        val expectedStiffness = (2.0 * PI / response).pow(2).toFloat()
        assertEquals(expectedStiffness, MotionTokens.PressScale.Stiffness, absoluteTolerance = 0.5f)
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
