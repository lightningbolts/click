package compose.project.click.click

import compose.project.click.click.data.models.FriendshipEncounter
import compose.project.click.click.data.models.FriendshipStats
import compose.project.click.click.data.models.HangoutPlan
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.upcomingPlansFrom
import compose.project.click.click.data.repository.HangoutPresence
import compose.project.click.click.data.repository.mergedEncounterTags
import compose.project.click.click.ui.chat.nonRsvpReactions
import compose.project.click.click.ui.chat.planMapsUri
import compose.project.click.click.ui.chat.shouldShowGroupRevival
import compose.project.click.click.ui.components.SouvenirPalette
import compose.project.click.click.ui.components.canUseCurrentLocationFor
import compose.project.click.click.ui.components.relationshipLine
import compose.project.click.click.ui.components.storyPages
import compose.project.click.click.utils.LocationResult
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase2HelpersTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test
    fun presenceIsThrottledAndNeedsAPreciseFix() {
        assertFalse(HangoutPresence.isThrottled(null, 1_000))
        assertTrue(HangoutPresence.isThrottled(0, HangoutPresence.MIN_INTERVAL_MS - 1))
        assertFalse(HangoutPresence.isThrottled(0, HangoutPresence.MIN_INTERVAL_MS))
        assertTrue(HangoutPresence.isUsableFix(LocationResult(47.6, -122.3, accuracyMeters = 30.0)))
        assertFalse(HangoutPresence.isUsableFix(LocationResult(47.6, -122.3, accuracyMeters = 250.0)))
        assertFalse(HangoutPresence.isUsableFix(LocationResult(47.6, -122.3, accuracyMeters = null)))
        assertFalse(HangoutPresence.isUsableFix(LocationResult(0.0, 0.0, accuracyMeters = 5.0)))
        assertFalse(HangoutPresence.isUsableFix(null))
    }

    @Test
    fun storyShowsOnlyPagesBackedByData() {
        val now = Instant.fromEpochSeconds(1_790_000_000)

        fun enc(
            id: String,
            daysAgo: Long,
            venue: String?,
            temp: Double? = null,
        ) = FriendshipEncounter(
            id,
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - daysAgo * day),
            placeName = venue,
            temperatureCelsius = temp,
        )

        val two = listOf(enc("a", 20, "Café"), enc("b", 3, "Park"))
        val pages = storyPages(FriendshipStats.compute(two, now), "Maya")
        assertEquals(listOf("Where it started", "New Click"), pages.map { it.eyebrow })
        assertEquals("You first Clicked at Café.", pages.first().detail)

        val rich = listOf(enc("a", 21, "Café", 2.0), enc("b", 14, "Café", 9.0), enc("c", 7, "Park", 15.0), enc("d", 1, "Café", 20.0))
        val richPages = storyPages(FriendshipStats.compute(rich, now), "Maya").map { it.eyebrow }
        assertTrue("Your spot" in richPages)
        assertTrue("Your rhythm" in richPages)
        assertTrue("Rain or shine" in richPages)
    }

    @Test
    fun relationshipLineUsesFirstEncounter() {
        val first = FriendshipEncounter("a", Instant.parse("2026-09-12T18:00:00Z"), placeName = "Café Allegro")
        val later = FriendshipEncounter("b", Instant.parse("2026-09-20T18:00:00Z"))
        val line = assertNotNull(relationshipLine(listOf(later, first)))
        assertTrue(line.startsWith("Clicked Sep 1"), line)
        assertTrue(line.endsWith(" at Café Allegro · 2 encounters"), line)
        assertNull(relationshipLine(emptyList()))
    }

    @Test
    fun editedTagsKeepAtEventAndDropDuplicates() {
        assertEquals(
            listOf("cafe", "study", "at_event"),
            mergedEncounterTags(listOf("cafe", " study ", "cafe", "at_event"), listOf("at_event", "party")),
        )
        assertEquals(listOf("cafe"), mergedEncounterTags(listOf("cafe", ""), listOf("party")))
    }

    @Test
    fun upcomingPlansSkipPastOnesAndCountGoing() {
        val now = 1_000 * day

        fun planMessage(
            id: String,
            startOffset: Long,
        ) = Message(
            id = id,
            user_id = "u",
            content = "",
            timeCreated = 0,
            metadata = buildJsonObject { put("plan", HangoutPlan("P$id", now + startOffset).toWire()) },
        )
        val messages =
            listOf(planMessage("late", 2 * day), planMessage("soon", hour), planMessage("past", -5 * hour), Message("x", "u", "hi", 0))
        val reactions = mapOf("soon" to listOf(MessageReaction("r", "soon", "a", HangoutPlan.GOING, 0)))
        val plans = upcomingPlansFrom("chat", messages, reactions, now)
        assertEquals(listOf("soon", "late"), plans.map { it.messageId })
        assertEquals(1, plans.first().goingCount)
    }

    @Test
    fun planCardHelpers() {
        assertEquals(
            "geo:47.6,-122.3?q=47.6,-122.3(Caf%C3%A9)",
            planMapsUri(HangoutPlan("Dinner", 0, placeName = "Café", latitude = 47.6, longitude = -122.3)),
        )
        assertTrue(planMapsUri(HangoutPlan("Dinner", 0, placeName = "Gas Works"))!!.startsWith("https://www.google.com/maps/search/"))
        assertNull(planMapsUri(HangoutPlan("Dinner", 0)))
        val reactions = listOf(HangoutPlan.GOING, "❤️", HangoutPlan.DECLINED).mapIndexed { i, e -> MessageReaction("$i", "m", "u$i", e, 0) }
        assertEquals(listOf("❤️"), nonRsvpReactions(reactions).map { it.reactionType })
    }

    @Test
    fun groupRevivalNeedsHistoryAndThreeQuietWeeks() {
        val now = 100 * day
        assertTrue(shouldShowGroupRevival(true, 10, now - 21 * day, now))
        assertFalse(shouldShowGroupRevival(true, 9, now - 30 * day, now))
        assertFalse(shouldShowGroupRevival(true, 20, now - 20 * day, now))
        assertFalse(shouldShowGroupRevival(false, 20, now - 30 * day, now))
    }

    @Test
    fun logHangoutCurrentLocationOnlyForRecentTimes() {
        val now = 10 * day
        assertTrue(canUseCurrentLocationFor(now - hour, now))
        assertFalse(canUseCurrentLocationFor(now - 4 * hour, now))
        assertFalse(canUseCurrentLocationFor(now + hour, now))
    }

    @Test
    fun souvenirPaletteIsStablePerSeed() {
        assertEquals(SouvenirPalette.colors("user-1"), SouvenirPalette.colors("user-1"))
        assertEquals(5381L, SouvenirPalette.djb2(""))
        // djb2("a") = 5381 * 33 + 97
        assertEquals(177_670L, SouvenirPalette.djb2("a"))
    }
}
