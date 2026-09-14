package compose.project.click.click.events

/**
 * Pure transition helpers for event presence. The server is authoritative: a pending request
 * never changes confirmed presence, and a rejected request never synthesizes a local check-in.
 */
data class ConfirmedEventPresence(
    val checkedIn: Boolean,
    val checkedInAt: String? = null,
    val checkInCount: Int = 0,
)

sealed interface EventPresenceTransition {
    data object Pending : EventPresenceTransition

    data class Confirmed(
        val presence: ConfirmedEventPresence,
    ) : EventPresenceTransition

    data class Rejected(
        val httpStatus: Int?,
        val message: String,
    ) : EventPresenceTransition
}

fun pendingEventPresence(previous: ConfirmedEventPresence): ConfirmedEventPresence = previous

fun confirmedEventPresence(
    checkedIn: Boolean,
    checkedInAt: String?,
    checkInCount: Int,
): ConfirmedEventPresence =
    ConfirmedEventPresence(
        checkedIn = checkedIn,
        checkedInAt = checkedInAt,
        checkInCount = checkInCount.coerceAtLeast(0),
    )

fun rejectedEventPresence(
    previous: ConfirmedEventPresence,
    httpStatus: Int?,
    fallback: String?,
): Pair<ConfirmedEventPresence, EventPresenceTransition.Rejected> =
    previous to
        EventPresenceTransition.Rejected(
            httpStatus = httpStatus,
            message = beaconCheckInFailureMessage(httpStatus, fallback),
        )

/** Event-chat eligibility is RSVP/host authorization, not physical-presence state. */
fun shouldRevokeEventHubAfterCheckout(
    hasAcceptedRsvp: Boolean,
    isHost: Boolean,
): Boolean = !hasAcceptedRsvp && !isHost
