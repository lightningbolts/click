package compose.project.click.click.data.models

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Friendship levels, spots, streaks and hangout highlights, derived only from real encounters
 * (`connection_encounters`). Port of iOS `Core/Connections/FriendshipStats.swift` and
 * `GroupHangout.clusters`; both platforms must agree, so keep the rules in sync.
 */

/** The encounter facts friendship stats need, decoupled from the wire model. */
data class FriendshipEncounter(
    val id: String,
    val at: Instant,
    /** Venue name, else the first component of the stored label (never a full address). */
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val neighbourhood: String? = null,
    val city: String? = null,
    val temperatureCelsius: Double? = null,
)

private val semanticJson = Json { ignoreUnknownKeys = true }

private fun JsonObject.firstString(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

private fun validCoordinate(
    lat: Double?,
    lon: Double?,
): Pair<Double, Double>? {
    if (lat == null || lon == null) return null
    if (!lat.isFinite() || !lon.isFinite()) return null
    if (abs(lat) > 90.0 || abs(lon) > 180.0) return null
    if (lat == 0.0 && lon == 0.0) return null
    return lat to lon
}

/** Maps a wire encounter; returns null when its timestamp can't be parsed. */
fun ConnectionEncounter.toFriendshipEncounter(): FriendshipEncounter? {
    val at = encounteredAtInstant() ?: return null
    val semantic =
        semanticLocation
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?.let { runCatching { semanticJson.parseToJsonElement(it).jsonObject }.getOrNull() }
    val address = semantic?.get("address")?.let { runCatching { it.jsonObject }.getOrNull() }
    val place = locationName?.trim()?.takeIf { it.isNotEmpty() } ?: displayLocation?.trim()?.takeIf { it.isNotEmpty() }
    val venue = semantic?.firstString("name")
    val coordinate = validCoordinate(gpsLat, gpsLon)
    return FriendshipEncounter(
        id = id,
        at = at,
        placeName = venue ?: place?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() },
        latitude = coordinate?.first,
        longitude = coordinate?.second,
        neighbourhood = address?.firstString("neighbourhood", "neighborhood", "suburb"),
        city = address?.firstString("city", "town", "village"),
        temperatureCelsius = weatherSnapshot?.temperatureCelsius,
    )
}

/** A named step in a friendship, earned by hangouts together (both people see the same level). */
data class FriendshipLevel(
    val rank: Int,
    val name: String,
    /** Hangouts needed to reach it. */
    val threshold: Int,
) {
    companion object {
        val all: List<FriendshipLevel> =
            listOf(
                FriendshipLevel(1, "New Click", 1),
                FriendshipLevel(2, "Familiar", 3),
                FriendshipLevel(3, "Regulars", 6),
                FriendshipLevel(4, "Close", 12),
                FriendshipLevel(5, "Inseparable", 25),
            )

        fun forHangouts(count: Int): FriendshipLevel = all.lastOrNull { count >= it.threshold } ?: all.first()
    }
}

/** A place you've met at least once (several encounters at one venue are one spot). */
data class FriendshipSpot(
    val id: String,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val firstVisit: Instant,
    val visits: Int,
)

enum class FriendshipTimeOfDay(
    val label: String,
) {
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night"),
    ;

    companion object {
        fun forHour(hour: Int): FriendshipTimeOfDay =
            when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT
            }
    }
}

/** Everything the profile, souvenir and "Your story" say about a friendship. */
data class FriendshipStats(
    val hangouts: Int,
    val firstMet: FriendshipEncounter?,
    val lastMet: FriendshipEncounter?,
    /** Distinct spots, in the order you first met there. */
    val spots: List<FriendshipSpot>,
    val neighborhoods: Int,
    val level: FriendshipLevel,
    val nextLevel: FriendshipLevel?,
    /** Hangouts still needed for [nextLevel]. */
    val toNextLevel: Int,
    /** Consecutive weeks with a hangout, counting this week (or last week while this one is open). */
    val weekStreak: Int,
    val longestWeekStreak: Int,
    val favoriteTime: FriendshipTimeOfDay?,
    val coldest: FriendshipEncounter?,
    val warmest: FriendshipEncounter?,
) {
    val isEmpty: Boolean get() = hangouts == 0

    /** The most-visited spot, only when it was visited more than once. */
    val topSpot: FriendshipSpot? get() = spots.maxByOrNull { it.visits }?.takeIf { it.visits > 1 }

    /** Progress from the current level to the next (0..1; 1 at the top level). */
    val levelProgress: Double
        get() {
            val next = nextLevel ?: return 1.0
            val span = (next.threshold - level.threshold).toDouble()
            return ((hangouts - level.threshold) / span).coerceIn(0.0, 1.0)
        }

    companion object {
        /** Weeks start on Monday unless the caller passes the locale's first day. */
        val DEFAULT_FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

        /** ~110 m cells: the same café on two days is one spot. */
        fun spotKey(encounter: FriendshipEncounter): String? {
            val name = encounter.placeName?.trim()?.lowercase()
            if (!name.isNullOrEmpty()) return "name:$name"
            val lat = encounter.latitude ?: return null
            val lon = encounter.longitude ?: return null
            return "geo:${(lat * 1000).roundToLong()},${(lon * 1000).roundToLong()}"
        }

        internal fun weekStart(
            instant: Instant,
            timeZone: TimeZone,
            firstDayOfWeek: DayOfWeek,
        ): LocalDate {
            val date = instant.toLocalDateTime(timeZone).date
            val offset = (date.dayOfWeek.ordinal - firstDayOfWeek.ordinal + 7) % 7
            return date.minus(DatePeriod(days = offset))
        }

        fun compute(
            encounters: List<FriendshipEncounter>,
            now: Instant,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            firstDayOfWeek: DayOfWeek = DEFAULT_FIRST_DAY_OF_WEEK,
        ): FriendshipStats {
            val ordered = encounters.sortedBy { it.at }

            val spots = mutableListOf<FriendshipSpot>()
            val spotIndex = mutableMapOf<String, Int>()
            for (encounter in ordered) {
                val key = spotKey(encounter) ?: continue
                val index = spotIndex[key]
                if (index != null) {
                    spots[index] = spots[index].copy(visits = spots[index].visits + 1)
                } else {
                    spotIndex[key] = spots.size
                    spots +=
                        FriendshipSpot(
                            id = key,
                            name = encounter.placeName,
                            latitude = encounter.latitude,
                            longitude = encounter.longitude,
                            firstVisit = encounter.at,
                            visits = 1,
                        )
                }
            }
            val neighborhoods =
                ordered
                    .mapNotNull { (it.neighbourhood ?: it.city)?.lowercase() }
                    .toSet()
                    .size

            val level = FriendshipLevel.forHangouts(ordered.size)
            val next = FriendshipLevel.all.firstOrNull { it.rank == level.rank + 1 }

            // Week streaks.
            val week = DatePeriod(days = 7)
            val weeks = ordered.map { weekStart(it.at, timeZone, firstDayOfWeek) }.toSet()
            val thisWeek = weekStart(now, timeZone, firstDayOfWeek)
            var current = 0
            var cursor = if (thisWeek in weeks) thisWeek else thisWeek.minus(week)
            while (cursor in weeks) {
                current++
                cursor = cursor.minus(week)
            }
            var longest = 0
            for (start in weeks) {
                if (start.minus(week) in weeks) continue
                var length = 0
                var w = start
                while (w in weeks) {
                    length++
                    w = w.plus(week)
                }
                longest = maxOf(longest, length)
            }

            // When you usually meet (needs a clear pattern: at least 3 hangouts). Ties resolve in
            // enum order so the result is deterministic.
            val favorite =
                if (ordered.size >= 3) {
                    val counts =
                        ordered.groupingBy { FriendshipTimeOfDay.forHour(it.at.toLocalDateTime(timeZone).hour) }.eachCount()
                    FriendshipTimeOfDay.entries.maxByOrNull { counts[it] ?: 0 }
                } else {
                    null
                }

            val withTemperature = ordered.filter { it.temperatureCelsius != null }
            return FriendshipStats(
                hangouts = ordered.size,
                firstMet = ordered.firstOrNull(),
                lastMet = ordered.lastOrNull(),
                spots = spots.toList(),
                neighborhoods = neighborhoods,
                level = level,
                nextLevel = next,
                toNextLevel = next?.let { maxOf(0, it.threshold - ordered.size) } ?: 0,
                weekStreak = current,
                longestWeekStreak = longest,
                favoriteTime = favorite,
                coldest = if (withTemperature.size >= 2) withTemperature.minByOrNull { it.temperatureCelsius!! } else null,
                warmest = if (withTemperature.size >= 2) withTemperature.maxByOrNull { it.temperatureCelsius!! } else null,
            )
        }
    }
}

/** What the newest hangout added (for the post-tap souvenir). */
data class HangoutHighlights(
    /** 1-based count of this hangout. */
    val ordinal: Int,
    val isNewSpot: Boolean,
    /** Set when this hangout reached a new level. */
    val leveledUpTo: FriendshipLevel?,
    /** A round-number hangout (5th, 10th, 25th...). */
    val isMilestone: Boolean,
    val weekStreak: Int,
) {
    companion object {
        val milestones: Set<Int> = setOf(5, 10, 25, 50, 75, 100, 150, 200, 250, 365, 500)

        fun of(
            encounters: List<FriendshipEncounter>,
            now: Instant,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            firstDayOfWeek: DayOfWeek = FriendshipStats.DEFAULT_FIRST_DAY_OF_WEEK,
        ): HangoutHighlights? {
            val ordered = encounters.sortedBy { it.at }
            val latest = ordered.lastOrNull() ?: return null
            val earlier = ordered.dropLast(1)
            val earlierSpots = earlier.mapNotNull(FriendshipStats::spotKey).toSet()
            val before = FriendshipLevel.forHangouts(earlier.size)
            val after = FriendshipLevel.forHangouts(ordered.size)
            val latestKey = FriendshipStats.spotKey(latest)
            return HangoutHighlights(
                ordinal = ordered.size,
                isNewSpot = ordered.size > 1 && latestKey != null && latestKey !in earlierSpots,
                leveledUpTo = if (ordered.size > 1 && after.rank > before.rank) after else null,
                isMilestone = ordered.size in milestones,
                weekStreak = FriendshipStats.compute(ordered, now, timeZone, firstDayOfWeek).weekStreak,
            )
        }
    }
}

/** Your encounters with two or more group members within a short window count as one group hangout. */
data class GroupHangout(
    /** The earliest encounter, preferring one with a location (date, place, map pin). */
    val representative: FriendshipEncounter,
    val memberIds: Set<String>,
) {
    val id: String get() = representative.id

    data class Tagged(
        val userId: String,
        val encounter: FriendshipEncounter,
    )

    companion object {
        const val DEFAULT_WINDOW_MS: Long = 2 * 60 * 60 * 1000L

        fun clusters(
            tagged: List<Tagged>,
            windowMs: Long = DEFAULT_WINDOW_MS,
        ): List<GroupHangout> {
            val ordered = tagged.sortedBy { it.encounter.at }
            val result = mutableListOf<GroupHangout>()
            var current = mutableListOf<Tagged>()

            fun flush() {
                val members = current.map { it.userId }.toSet()
                val first = current.firstOrNull()?.encounter
                if (members.size < 2 || first == null) return
                val located = current.map { it.encounter }.firstOrNull { it.latitude != null } ?: first
                result += GroupHangout(representative = located, memberIds = members)
            }
            for (item in ordered) {
                val start = current.firstOrNull()?.encounter?.at
                if (start != null && (item.encounter.at - start).inWholeMilliseconds > windowMs) {
                    flush()
                    current = mutableListOf()
                }
                current += item
            }
            flush()
            return result
        }
    }
}
