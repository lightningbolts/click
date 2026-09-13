package compose.project.click.click.events

/**
 * Event-hub membership policy.
 *
 * Event chat is part of the RSVP experience: an RSVP member should be able to coordinate before
 * arriving at the venue, so the shipped policy requires RSVP rather than an active check-in.
 * Hosts always bypass both requirements. The flags remain configurable for tests/future event
 * modes where a stricter policy is intentional.
 */
data class EventHubAccessPolicy(
    val requireCheckIn: Boolean = false,
    val requireRsvp: Boolean = true,
)

val EVENT_HUB_ACCESS = EventHubAccessPolicy()

const val EVENT_HUB_TTL_AFTER_END_MS = 24L * 60L * 60L * 1000L

fun eventHubExpiresAtEpochMs(eventEndEpochMs: Long): Long = eventEndEpochMs + EVENT_HUB_TTL_AFTER_END_MS

fun evaluateEventHubAccess(
    userId: String,
    hubCreatorId: String?,
    eventCreatorId: String?,
    hasActiveCheckIn: Boolean,
    hasRsvp: Boolean,
    policy: EventHubAccessPolicy = EVENT_HUB_ACCESS,
): Boolean {
    val id = userId.trim()
    if (id.isEmpty()) return false
    if (!hubCreatorId.isNullOrBlank() && id == hubCreatorId) return true
    if (!eventCreatorId.isNullOrBlank() && id == eventCreatorId) return true
    if (policy.requireCheckIn && !hasActiveCheckIn) return false
    if (policy.requireRsvp && !hasRsvp) return false
    return true
}

fun isEventLinkedHubCategory(category: String?): Boolean = category?.trim()?.equals("event", ignoreCase = true) == true

fun canOpenEventHub(
    hubId: String?,
    isCreator: Boolean,
    checkedIn: Boolean,
    hasRsvp: Boolean = false,
    policy: EventHubAccessPolicy = EVENT_HUB_ACCESS,
): Boolean {
    if (hubId.isNullOrBlank()) return false
    if (isCreator) return true
    return evaluateEventHubAccess(
        userId = "guest",
        hubCreatorId = null,
        eventCreatorId = null,
        hasActiveCheckIn = checkedIn,
        hasRsvp = hasRsvp,
        policy = policy,
    )
}
