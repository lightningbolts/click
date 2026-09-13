package compose.project.click.click.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteHistoryTest {
    @Test
    fun navigateBackReturnsEachPreviousRouteInOrder() {
        val history = RouteHistory(NavigationItem.Home.route)

        assertTrue(history.navigateTo(NavigationItem.Map.route))
        assertTrue(history.navigateTo(NavigationItem.Settings.route))
        assertEquals(NavigationItem.Map.route, history.navigateBack())
        assertEquals(NavigationItem.Home.route, history.navigateBack())
        assertNull(history.navigateBack())
    }

    @Test
    fun sameRouteDoesNotAddHistoryEntry() {
        val history = RouteHistory(NavigationItem.Home.route)

        assertTrue(history.navigateTo(NavigationItem.Map.route))
        assertFalse(history.navigateTo(NavigationItem.Map.route))
        assertEquals(NavigationItem.Home.route, history.navigateBack())
        assertNull(history.navigateBack())
    }

    @Test
    fun resetToHomeClearsHistoryForPrimaryRouteBack() {
        val history = RouteHistory(NavigationItem.Home.route)

        history.navigateTo(NavigationItem.Map.route)
        history.navigateTo(NavigationItem.Settings.route)
        history.resetTo(NavigationItem.Home.route)

        assertEquals(NavigationItem.Home.route, history.currentRoute)
        assertNull(history.navigateBack())
    }
}
