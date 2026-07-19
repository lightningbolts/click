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
): String = when (httpStatus) {
    403 -> "Move closer to the event to check in"
    409 -> "Check-in opens when the event starts"
    400 -> "Location required to check in"
    else -> fallback ?: "Couldn't check in"
}
