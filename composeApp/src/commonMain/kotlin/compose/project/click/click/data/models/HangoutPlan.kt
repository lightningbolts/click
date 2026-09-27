package compose.project.click.click.data.models

import compose.project.click.click.util.formatClockTime
import compose.project.click.click.util.formatShortDateTime
import compose.project.click.click.util.formatWeekdayDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * A proposed hangout sent into a chat as `metadata.plan` on an ordinary (E2EE) text message.
 * The message text is [summary], so clients that don't understand plans still read it. RSVPs are
 * reactions: [GOING] and [DECLINED], mutually exclusive per person.
 *
 * Wire-compatible with iOS `HangoutPlan` (`Core/Chat/ChatMessage.swift`): `title`, `starts_at` and
 * `ends_at` (epoch ms), `place_name`, and `lat`/`lon` only as a pair. Note the plan object is
 * plaintext metadata on the server (docs/parity README decision #1).
 */
data class HangoutPlan(
    val title: String,
    val startsAtEpochMs: Long,
    /** Only kept when after [startsAtEpochMs]. */
    val endsAtEpochMs: Long? = null,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val validEndsAtEpochMs: Long? get() = endsAtEpochMs?.takeIf { it > startsAtEpochMs }

    /** When the plan is over: its end, or three hours after it starts. */
    val endsOrAssumedEndEpochMs: Long get() = validEndsAtEpochMs ?: (startsAtEpochMs + ASSUMED_LENGTH_MS)

    fun isOver(nowEpochMs: Long): Boolean = nowEpochMs >= endsOrAssumedEndEpochMs

    /** "📅 Dinner · Wed, Oct 1, 7:00 PM–9:00 PM · 📍 Café Allegro". */
    fun summary(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        var whenText = formatWeekdayDateTime(startsAtEpochMs, timeZone)
        validEndsAtEpochMs?.let { end ->
            val sameDay =
                Instant.fromEpochMilliseconds(end).toLocalDateTime(timeZone).date ==
                    Instant.fromEpochMilliseconds(startsAtEpochMs).toLocalDateTime(timeZone).date
            whenText += "–" + if (sameDay) formatClockTime(end, timeZone) else formatShortDateTime(end, timeZone)
        }
        return "📅 $title · $whenText" + (placeName?.let { " · 📍 $it" } ?: "")
    }

    fun toWire(): JsonObject =
        buildJsonObject {
            put("title", title)
            put("starts_at", startsAtEpochMs)
            validEndsAtEpochMs?.let { put("ends_at", it) }
            placeName?.takeIf { it.isNotBlank() }?.let { put("place_name", it) }
            if (latitude != null && longitude != null) {
                put("lat", latitude)
                put("lon", longitude)
            }
        }

    companion object {
        const val GOING: String = "✅"
        const val DECLINED: String = "❌"
        const val TITLE_MAX: Int = 80
        const val ASSUMED_LENGTH_MS: Long = 3 * 60 * 60 * 1000L

        fun parse(metadata: JsonElement?): HangoutPlan? {
            val plan = (metadata as? JsonObject)?.get("plan") as? JsonObject ?: return null

            fun string(key: String) =
                (plan[key] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            fun number(key: String) = (plan[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            val title = string("title") ?: return null
            val starts = number("starts_at")?.toLong() ?: return null
            val lat = number("lat")
            val lon = number("lon")
            return HangoutPlan(
                title = title,
                startsAtEpochMs = starts,
                endsAtEpochMs = number("ends_at")?.toLong()?.takeIf { it > starts },
                placeName = string("place_name"),
                latitude = if (lat != null && lon != null) lat else null,
                longitude = if (lat != null && lon != null) lon else null,
            )
        }
    }
}

fun Message.planOrNull(): HangoutPlan? = HangoutPlan.parse(metadata)

/** A person's RSVP to a plan, derived from their ✅ / ❌ reactions. */
enum class PlanRsvp { GOING, DECLINED }

data class PlanResponses(
    val goingUserIds: List<String>,
    val declinedUserIds: List<String>,
) {
    fun rsvpOf(userId: String?): PlanRsvp? =
        when (userId) {
            null -> null
            in goingUserIds -> PlanRsvp.GOING
            in declinedUserIds -> PlanRsvp.DECLINED
            else -> null
        }

    companion object {
        /** A user with both reactions (a race between devices) counts as going. */
        fun from(reactions: List<MessageReaction>): PlanResponses {
            val going = reactions.filter { it.reactionType == HangoutPlan.GOING }.map { it.userId }.distinct()
            val declined =
                reactions
                    .filter { it.reactionType == HangoutPlan.DECLINED }
                    .map { it.userId }
                    .distinct()
                    .filterNot { it in going }
            return PlanResponses(going, declined)
        }
    }
}

/** A plan that hasn't ended, for profile / group "Coming up" lists. */
data class UpcomingPlan(
    val messageId: String,
    val chatId: String,
    val plan: HangoutPlan,
    val goingCount: Int,
)

/** Plans that haven't ended, soonest first (iOS `UpcomingPlans.in`). */
fun upcomingPlansFrom(
    chatId: String,
    messages: List<Message>,
    reactions: Map<String, List<MessageReaction>>,
    nowEpochMs: Long,
): List<UpcomingPlan> =
    messages
        .mapNotNull { message -> message.planOrNull()?.let { message to it } }
        .filter { (_, plan) -> !plan.isOver(nowEpochMs) }
        .sortedBy { (_, plan) -> plan.startsAtEpochMs }
        .map { (message, plan) ->
            UpcomingPlan(
                messageId = message.id,
                chatId = chatId,
                plan = plan,
                goingCount = PlanResponses.from(reactions[message.id].orEmpty()).goingUserIds.size,
            )
        }
