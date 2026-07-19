package compose.project.click.click.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeContinuityPolicyTest {
    @Test
    fun nonHomeRouteOwnsGestureUnderlay() {
        assertTrue(shouldRenderHomeSwipeUnderlay(NavigationItem.Map.route))
        assertTrue(shouldRenderHomeSwipeUnderlay(NavigationItem.Settings.route))
        assertTrue(shouldRenderHomeSwipeUnderlay(NavigationItem.AddClick.route))
    }

    @Test
    fun committedHomeRouteReleasesOutgoingUnderlay() {
        assertFalse(shouldRenderHomeSwipeUnderlay(NavigationItem.Home.route))
    }
}
