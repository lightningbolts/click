package compose.project.click.click.data.models

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Golden cases ported from iOS `Tests/ClickTests/Round7Tests.swift` so both platforms agree. */
class FriendshipStatsTest {
    /** A Monday in UTC (same fixture as iOS). */
    private val now = Instant.fromEpochSeconds(1_790_000_000)
    private val utc = TimeZone.UTC

    private fun encounter(
        id: String,
        daysAgo: Double,
        venue: String? = null,
        lat: Double? = null,
        temp: Double? = null,
        base: Instant = now,
    ) = FriendshipEncounter(
        id = id,
        at = Instant.fromEpochMilliseconds(base.toEpochMilliseconds() - (daysAgo * 86_400_000).roundToLong()),
        placeName = venue,
        latitude = lat,
        longitude = lat?.let { -122.33 },
        temperatureCelsius = temp,
    )

    private fun compute(
        list: List<FriendshipEncounter>,
        at: Instant = now,
    ) = FriendshipStats.compute(list, now = at, timeZone = utc, firstDayOfWeek = DayOfWeek.MONDAY)

    @Test
    fun levelsFollowHangoutCountsWithProgress() {
        assertEquals("New Click", FriendshipLevel.forHangouts(1).name)
        assertEquals("Familiar", FriendshipLevel.forHangouts(3).name)
        assertEquals("Regulars", FriendshipLevel.forHangouts(11).name)
        assertEquals("Inseparable", FriendshipLevel.forHangouts(40).name)
        assertEquals("New Click", FriendshipLevel.forHangouts(0).name)

        val stats = compute((0 until 4).map { encounter("e$it", daysAgo = it * 10.0) })
        assertEquals("Familiar", stats.level.name)
        assertEquals(2, stats.toNextLevel)
        assertTrue(abs(stats.levelProgress - 1.0 / 3.0) < 0.001)
    }

    @Test
    fun spotsMergeRepeatVenuesAndStreaksCountConsecutiveWeeks() {
        val list =
            listOf(
                encounter("a", daysAgo = 1.0, venue = "Café Allegro"),
                encounter("b", daysAgo = 8.0, venue = "café allegro"),
                encounter("c", daysAgo = 15.0, venue = "Gas Works Park"),
                encounter("d", daysAgo = 60.0, lat = 47.6101),
                encounter("e", daysAgo = 61.0, lat = 47.6102), // same ~110 m cell as "d"
            )
        val stats = compute(list)
        assertEquals(3, stats.spots.size)
        assertEquals(2, stats.topSpot?.visits)
        assertEquals(3, stats.weekStreak)
        assertEquals(3, stats.longestWeekStreak)

        val lapsed = compute(list, at = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 21 * 86_400_000L))
        assertEquals(0, lapsed.weekStreak)
        assertEquals(3, lapsed.longestWeekStreak)
    }

    @Test
    fun newestHangoutHighlights() {
        val two = listOf(encounter("a", 30.0, venue = "Park"), encounter("b", 0.0, venue = "Park"))
        assertEquals(false, HangoutHighlights.of(two, now, utc)?.isNewSpot)

        val three = two + encounter("c", -0.01, venue = "Bar")
        val result = assertNotNull(HangoutHighlights.of(three, now, utc))
        assertTrue(result.isNewSpot)
        assertEquals("Familiar", result.leveledUpTo?.name)
        assertEquals(3, result.ordinal)

        val five = (0 until 5).map { encounter("m$it", daysAgo = (5 - it).toDouble()) }
        assertEquals(true, HangoutHighlights.of(five, now, utc)?.isMilestone)
        assertEquals(false, HangoutHighlights.of(listOf(encounter("x", 0.0, venue = "Park")), now, utc)?.isNewSpot)
        assertNull(HangoutHighlights.of(emptyList(), now, utc))
    }

    @Test
    fun favoriteTimeNeedsThreeHangoutsAndTemperaturesNeedTwo() {
        // now is 14:13 UTC, so these land in the afternoon.
        val afternoon = (1..3).map { encounter("t$it", daysAgo = it * 7.0, temp = it * 5.0) }
        val stats = compute(afternoon)
        assertEquals(FriendshipTimeOfDay.AFTERNOON, stats.favoriteTime)
        assertEquals("t1", stats.coldest?.id)
        assertEquals("t3", stats.warmest?.id)

        val twoOnly = compute(afternoon.take(2))
        assertNull(twoOnly.favoriteTime)
        val oneTemp = compute(listOf(encounter("x", 1.0, temp = 3.0), encounter("y", 2.0)))
        assertNull(oneTemp.coldest)
    }

    @Test
    fun emptyStatsAreEmpty() {
        val stats = compute(emptyList())
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.weekStreak)
        assertNull(stats.topSpot)
        assertEquals("New Click", stats.level.name)
    }

    @Test
    fun groupHangoutsClusterTwoOrMoreMembersWithinTwoHours() {
        val base = Instant.fromEpochSeconds(1_790_000_000)

        fun at(
            id: String,
            minutes: Long,
            lat: Double? = null,
        ) = FriendshipEncounter(
            id = id,
            at = Instant.fromEpochSeconds(base.epochSeconds + minutes * 60),
            latitude = lat,
            longitude = lat?.let { -122.3 },
        )
        val result =
            GroupHangout.clusters(
                listOf(
                    GroupHangout.Tagged("a", at("1", 0)),
                    GroupHangout.Tagged("b", at("2", 30, lat = 47.6)),
                    GroupHangout.Tagged("a", at("3", 600)), // alone: not a group hangout
                    GroupHangout.Tagged("a", at("4", 2000)),
                    GroupHangout.Tagged("c", at("5", 2050)),
                ),
            )
        assertEquals(2, result.size)
        assertEquals(setOf("a", "b"), result[0].memberIds)
        assertEquals("2", result[0].representative.id) // prefers the located encounter
        assertEquals(setOf("a", "c"), result[1].memberIds)
    }

    @Test
    fun wireEncounterMapsVenueAddressAndRejectsNullIsland() {
        val wire =
            ConnectionEncounter(
                id = "enc",
                connectionId = "conn",
                encounteredAt = "2026-09-20T18:30:00Z",
                locationName = "Pike Place Market, Seattle, WA",
                semanticLocation =
                    """{"name":"Storyville Coffee","address":{"suburb":"Belltown","city":"Seattle"}}""",
                gpsLat = 0.0,
                gpsLon = 0.0,
            )
        val mapped = assertNotNull(wire.toFriendshipEncounter())
        assertEquals("Storyville Coffee", mapped.placeName)
        assertEquals("Belltown", mapped.neighbourhood)
        assertEquals("Seattle", mapped.city)
        assertNull(mapped.latitude)

        val noVenue = assertNotNull(wire.copy(semanticLocation = null, gpsLat = 47.61, gpsLon = -122.34).toFriendshipEncounter())
        assertEquals("Pike Place Market", noVenue.placeName)
        assertEquals(47.61, noVenue.latitude)

        assertNull(wire.copy(encounteredAt = "not a date").toFriendshipEncounter())
        assertFalse(
            wire
                .copy(semanticLocation = "Just a label")
                .toFriendshipEncounter()
                ?.placeName
                .isNullOrEmpty(),
        )
    }
}
