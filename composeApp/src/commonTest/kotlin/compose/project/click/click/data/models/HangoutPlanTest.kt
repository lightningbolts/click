package compose.project.click.click.data.models

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HangoutPlanTest {
    private val utc = TimeZone.UTC
    private val start = Instant.parse("2026-10-01T19:00:00Z").toEpochMilliseconds() // Wed
    private val hour = 3_600_000L

    @Test
    fun wireRoundTripsLikeIos() {
        val plan = HangoutPlan("Dinner", start, start + 2 * hour, "Café Allegro", 47.6, -122.3)
        val metadata = buildJsonObject { put("plan", plan.toWire()) }
        assertEquals(plan, HangoutPlan.parse(metadata))
        val wire = plan.toWire()
        assertEquals(setOf("title", "starts_at", "ends_at", "place_name", "lat", "lon"), wire.keys)
    }

    @Test
    fun endBeforeStartIsDroppedAndCoordinatesOnlyAsAPair() {
        val plan = HangoutPlan("x", start, start - 60_000, latitude = 47.6)
        assertNull(plan.validEndsAtEpochMs)
        assertEquals(setOf("title", "starts_at"), plan.toWire().keys)
        assertEquals(start + 3 * hour, plan.endsOrAssumedEndEpochMs)
    }

    @Test
    fun parsesIosPayloadAndRejectsIncompleteOnes() {
        val ios =
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"crypto_version":2,"plan":{"title":"Walk","starts_at":1790000000000,"place_name":"Gas Works"}}""",
            )
        val plan = HangoutPlan.parse(ios)!!
        assertEquals("Walk", plan.title)
        assertEquals(1_790_000_000_000L, plan.startsAtEpochMs)
        assertEquals("Gas Works", plan.placeName)
        assertNull(HangoutPlan.parse(buildJsonObject { put("plan", buildJsonObject { put("title", "no time") }) }))
        assertNull(HangoutPlan.parse(JsonObject(emptyMap())))
        assertNull(HangoutPlan.parse(null))
    }

    @Test
    fun summaryReadsWellAsPlainText() {
        val plan = HangoutPlan("Dinner", start, start + 2 * hour, "Café Allegro")
        assertEquals("📅 Dinner · Thu, Oct 1, 7:00 PM–9:00 PM · 📍 Café Allegro", plan.summary(utc))
        val overnight = HangoutPlan("Party", start, start + 6 * hour)
        assertEquals("📅 Party · Thu, Oct 1, 7:00 PM–Oct 2, 1:00 AM", overnight.summary(utc))
    }

    @Test
    fun responsesAreMutuallyExclusive() {
        fun r(
            user: String,
            emoji: String,
        ) = MessageReaction(id = "$user$emoji", messageId = "m", userId = user, reactionType = emoji, createdAt = 0)
        val responses =
            PlanResponses.from(
                listOf(
                    r("a", HangoutPlan.GOING),
                    r("b", HangoutPlan.DECLINED),
                    r("c", HangoutPlan.GOING),
                    r("c", HangoutPlan.DECLINED),
                    r("d", "❤️"),
                ),
            )
        assertEquals(listOf("a", "c"), responses.goingUserIds)
        assertEquals(listOf("b"), responses.declinedUserIds)
        assertEquals(PlanRsvp.GOING, responses.rsvpOf("c"))
        assertNull(responses.rsvpOf("d"))
    }

    @Test
    fun plannerDefaultsAndDayMoves() {
        val morning = Instant.parse("2026-10-01T09:00:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-10-01T19:00:00Z").toEpochMilliseconds(), PlanDraftRules.defaultStart(morning, utc))
        val evening = Instant.parse("2026-10-01T18:00:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-10-02T19:00:00Z").toEpochMilliseconds(), PlanDraftRules.defaultStart(evening, utc))

        val chips = PlanDraftRules.dayChips(morning, utc)
        assertEquals(7, chips.size)
        assertEquals(LocalDate(2026, 10, 1), chips.first())
        val moved = PlanDraftRules.moveToDay(start, LocalDate(2026, 10, 4), utc)
        assertEquals(Instant.parse("2026-10-04T19:00:00Z").toEpochMilliseconds(), moved)
        assertEquals(moved + 2 * hour, PlanDraftRules.shiftEnd(start, moved, start + 2 * hour))
        assertEquals(start + PlanDraftRules.MIN_LENGTH_MS, PlanDraftRules.clampEnd(start, start + 60_000))
    }

    @Test
    fun plannerValidation() {
        val now = start - hour
        assertTrue(PlanDraftRules.canSend("Dinner", start, null, now))
        assertFalse(PlanDraftRules.canSend("  ", start, null, now))
        assertFalse(PlanDraftRules.canSend("Dinner", now - 1, null, now))
        assertFalse(PlanDraftRules.canSend("Dinner", start, start + 60_000, now))
        assertFalse(PlanDraftRules.canSend("x".repeat(81), start, null, now))
        assertTrue(PlanIdeasStore.normalize("  🎳 Bowling  ") == "🎳 Bowling")
        assertNull(PlanIdeasStore.normalize("   "))
    }

    @Test
    fun metadataPlanSurvivesAlongsideCryptoFields() {
        val plan = HangoutPlan("Coffee", start)
        val metadata =
            buildJsonObject {
                put("crypto_version", 2)
                put("client_message_id", "abc")
                put("plan", plan.toWire())
            }
        assertEquals("Coffee", Message(id = "m", user_id = "u", content = "", timeCreated = 0, metadata = metadata).planOrNull()?.title)
        assertTrue(metadata["plan"]!!.jsonObject.containsKey("starts_at"))
    }
}
