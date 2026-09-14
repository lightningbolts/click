package compose.project.click.click.events

/** Maps beacon check-in API failures to stable user-facing copy. */
internal fun beaconCheckInFailureMessage(
    httpStatus: Int?,
    fallback: String? = null,
): String =
    when (httpStatus) {
        401 -> "Please sign in again to check in"
        403 -> "Move closer to the event to check in"
        409 -> "Check-in opens when the event starts"
        400 -> "Location is required to check in"
        in 500..599 -> "Event check-in is temporarily unavailable"
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Couldn't check in"
    }

/** Labeled check-in CTA copy. Pending never implies that server validation succeeded. */
internal fun eventCheckInCtaLabel(
    checkedIn: Boolean,
    pending: Boolean,
): String =
    when {
        pending && checkedIn -> "Updating…"
        pending -> "Checking location…"
        checkedIn -> "Checked in"
        else -> "Check in here"
    }
