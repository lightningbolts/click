package compose.project.click.click.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G4-11 golden cases, ported from iOS `TelemetryTests.suggestions` (Tuesday 6 Oct 2026). */
class ContextTagSuggestionsTest {
    private fun at(hour: Int) = TagSignals(hourOfDay = hour, isWeekend = false)

    private fun ids(signals: TagSignals) = ContextTagTaxonomy.suggest(signals).map { it.id }

    @Test
    fun lateNightLeansToPartyThenBar() {
        assertEquals(listOf("party", "bar"), ids(at(23)).take(2))
    }

    @Test
    fun coffeeShopNameLeadsInTheMorning() {
        assertEquals("cafe", ids(at(9).copy(placeName = "Blue Bottle Coffee")).first())
    }

    @Test
    fun alwaysFourSuggestions() {
        assertEquals(4, ids(at(4)).size)
    }

    @Test
    fun cafeAtLunchPlaceLeads() {
        assertEquals(listOf("cafe", "study"), ids(at(12).copy(placeName = "Café Allegro", placeType = "cafe")).take(2))
    }

    @Test
    fun quietCampusByDayIsClassAndStudy() {
        val campus = ids(at(16).copy(placeName = "University of Washington", placeType = "university", noiseDecibels = 38.0))
        assertEquals(listOf("lecture", "study"), campus.take(2))
        assertFalse("party" in campus)
    }

    @Test
    fun movingTogetherIsTransit() {
        assertEquals("transit", ids(at(16).copy(isMoving = true)).first())
    }

    @Test
    fun atEventPutsCampusEventFirst() {
        assertEquals("event", ids(at(20).copy(atEvent = true)).first())
    }

    @Test
    fun pastTagsOutrankTheHour() {
        assertEquals("gym", ids(at(23).copy(pastTags = listOf("gym", "gym", "Extended Hangout"))).first())
    }

    @Test
    fun serverOnlyTagIsNeverSuggested() {
        assertFalse("at_event" in ids(at(20).copy(pastTags = listOf("at_event", "at_event"))))
        assertTrue(ids(at(20)).none { it == "custom" })
    }
}
