package compose.project.click.click.events

/**
 * Maps beacon check-in API failures to user-facing copy.
 * Kept pure so optimism/rollback messaging stays covered by unit tests.
 *
 * Note: HTTP 409 (not live) is handled as early check-in success in MapViewModel —
 * it must not map through this helper for rollback.
 */
internal fun beaconCheckInFailureMessage(
    httpStatus: Int?,
    fallback: String? = null,
): String {
    val msg = fallback?.trim().orEmpty()
    return when (httpStatus) {
        403 ->
            when {
                msg.contains("RSVP", ignoreCase = true) -> msg
                msg.isNotEmpty() &&
                    !msg.equals("Forbidden", ignoreCase = true) &&
                    !msg.equals("Unauthorized", ignoreCase = true) -> msg
                else -> "Move closer to the event to check in"
            }
        409 -> "Check-in opens when the event starts"
        400 -> "Location required to check in"
        else -> msg.ifEmpty { "Couldn't check in" }
    }
}

/** Labeled check-in CTA copy (not icon-only). */
internal fun eventCheckInCtaLabel(
    checkedIn: Boolean,
    pending: Boolean,
): String = when {
    pending && !checkedIn -> "Checking location…"
    pending && checkedIn -> "Updating…"
    checkedIn -> "Checked in"
    else -> "Check in here"
}

/**
 * Whether the user may attempt check-in (or undo). Hosts bypass RSVP.
 * Already-checked-in users can always check out.
 */
fun canAttemptEventCheckIn(
    hasRsvp: Boolean,
    isHost: Boolean,
    alreadyCheckedIn: Boolean,
    rsvpEnabled: Boolean = true,
): Boolean {
    if (alreadyCheckedIn) return true
    if (isHost) return true
    if (!rsvpEnabled) return true
    return hasRsvp
}
