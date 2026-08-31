package compose.project.click.click.events

/**
 * Event-hub membership policy. Flip [requireRsvp] to also require an RSVP.
 * Host (event or hub creator) always bypasses both flags.
 */
data class EventHubAccessPolicy(
    val requireCheckIn: Boolean = true,
    val requireRsvp: Boolean = false,
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
