package compose.project.click.click.events

import compose.project.click.click.data.models.BeaconVisibilityAudience
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class EventVisibility(
    val apiValue: String,
) {
    PUBLIC("public"),
    UNLISTED("unlisted"),
    INVITE_ONLY("invite_only"),
    ;

    companion object {
        fun fromRaw(raw: String?): EventVisibility {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when (v) {
                "unlisted" -> UNLISTED
                "invite_only", "invite-only", "inviteonly" -> INVITE_ONLY
                else -> PUBLIC
            }
        }
    }
}

enum class GuestListVisibility(
    val apiValue: String,
) {
    PUBLIC("public"),
    HOSTS_ONLY("hosts_only"),
    ;

    companion object {
        fun fromRaw(raw: String?): GuestListVisibility {
            val v = raw?.trim()?.lowercase().orEmpty()
            return when (v) {
                "hosts_only", "hosts-only", "hostsonly" -> HOSTS_ONLY
                else -> PUBLIC
            }
        }
    }
}

enum class EventRsvpRequestStatus(
    val apiValue: String,
) {
    PENDING("pending"),
    APPROVED("approved"),
    DENIED("denied"),
    WAITLISTED("waitlisted"),
    ;

    companion object {
        fun fromRaw(raw: String?): EventRsvpRequestStatus? {
            val v = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.apiValue == v }
        }
    }
}

data class EventListingOptions(
    val eventVisibility: EventVisibility = EventVisibility.PUBLIC,
    val eventCapacity: Int? = null,
    val approvalRequired: Boolean = false,
    val guestListVisibility: GuestListVisibility = GuestListVisibility.PUBLIC,
    val coverThemeId: String? = null,
)

fun defaultEventListingOptions(): EventListingOptions = EventListingOptions()

fun parseEventCapacity(raw: JsonElement?): Int? {
    if (raw == null) return null
    val n =
        when (raw) {
            is JsonPrimitive -> {
                if (raw.contentOrNull.isNullOrBlank()) return null
                raw.content.toDoubleOrNull()
            }
            else -> null
        } ?: return null
    if (!n.isFinite() || n <= 0) return null
    return n.toInt()
}

fun parseApprovalRequired(raw: JsonElement?): Boolean {
    if (raw == null) return false
    return when (raw) {
        is JsonPrimitive -> {
            raw.contentOrNull?.trim()?.lowercase()?.let { v ->
                v == "true" || v == "1"
            } == true ||
                raw.content.toBooleanStrictOrNull() == true
        }
        else -> false
    }
}

fun parseCoverThemeId(raw: JsonElement?): String? {
    val v =
        raw
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            .orEmpty()
    if (v.isEmpty()) return null
    return v
}

fun parseEventListingOptions(metadata: JsonObject?): EventListingOptions {
    if (metadata == null) return defaultEventListingOptions()

    fun key(name: String): JsonElement? = metadata[name]
    return EventListingOptions(
        eventVisibility =
            EventVisibility.fromRaw(
                key("event_visibility")?.jsonPrimitive?.contentOrNull
                    ?: key("eventVisibility")?.jsonPrimitive?.contentOrNull,
            ),
        eventCapacity =
            parseEventCapacity(key("event_capacity"))
                ?: parseEventCapacity(key("eventCapacity")),
        approvalRequired =
            parseApprovalRequired(key("approval_required")) ||
                parseApprovalRequired(key("approvalRequired")),
        guestListVisibility =
            GuestListVisibility.fromRaw(
                key("guest_list_visibility")?.jsonPrimitive?.contentOrNull
                    ?: key("guestListVisibility")?.jsonPrimitive?.contentOrNull,
            ),
        coverThemeId =
            parseCoverThemeId(key("cover_theme_id"))
                ?: parseCoverThemeId(key("coverThemeId")),
    )
}

fun EventListingOptions.toMetadataPatch(): JsonObject =
    buildJsonObject {
        put("event_visibility", eventVisibility.apiValue)
        eventCapacity?.let { put("event_capacity", it) }
        put("approval_required", approvalRequired)
        put("guest_list_visibility", guestListVisibility.apiValue)
        coverThemeId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cover_theme_id", it) }
    }

fun EventListingOptions.mapToAudience(): BeaconVisibilityAudience =
    when (eventVisibility) {
        EventVisibility.PUBLIC -> BeaconVisibilityAudience.EVERYONE
        EventVisibility.UNLISTED, EventVisibility.INVITE_ONLY -> BeaconVisibilityAudience.CONNECTIONS
    }

/** User-facing RSVP status / error copy for event detail surfaces. */
fun eventRsvpStatusMessage(
    requestStatus: EventRsvpRequestStatus?,
    errorMessage: String? = null,
): String? {
    requestStatus?.let { status ->
        when (status) {
            EventRsvpRequestStatus.PENDING -> return "Approval required — request to join"
            EventRsvpRequestStatus.WAITLISTED -> return "You're on the list"
            EventRsvpRequestStatus.APPROVED, EventRsvpRequestStatus.DENIED -> Unit
        }
    }
    return normalizeEventRsvpErrorMessage(errorMessage)
}

fun normalizeEventRsvpErrorMessage(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val lower = text.lowercase()
    return when {
        lower.contains("approval") || lower.contains("pending") ->
            "Approval required — request to join"
        lower.contains("full") && lower.contains("waitlist") ->
            "Event full — join waitlist"
        lower.contains("full") || lower.contains("capacity") ->
            "Event full — join waitlist"
        lower.contains("waitlist") || lower.contains("on the list") ->
            "You're on the list"
        else -> text
    }
}
