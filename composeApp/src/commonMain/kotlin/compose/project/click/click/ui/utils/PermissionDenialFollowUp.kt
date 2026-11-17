package compose.project.click.click.ui.utils

/**
 * A denial that comes back faster than this means Android showed no prompt: location is off
 * for good. A person cannot read and answer a shown prompt this fast.
 */
const val NO_PROMPT_DENIAL_MAX_MS = 300L

/**
 * Whether a denied location request should explain how to allow it in Settings.
 *
 * Mirrors click-ios 6a8310d: a "Don't allow" answer to the system prompt ends there, and nothing
 * opens Settings on its own. Only a request that Android denies without a prompt (location is
 * off for good) shows a notice with an Open Settings button the user chooses.
 *
 * The elapsed time is the signal, because the rationale flags look the same for a dismissed
 * prompt and for a permanent denial, and an app update has no record of earlier requests.
 */
fun shouldExplainDeniedLocation(
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
    elapsedMs: Long,
): Boolean = !rationaleBefore && !rationaleAfter && elapsedMs in 0L until NO_PROMPT_DENIAL_MAX_MS

const val LOCATION_OFF_NOTICE_TITLE = "Location is off for Click"
const val LOCATION_OFF_NOTICE_BODY =
    "Click works without it. To center the map on you and see what's nearby, you can allow location for Click in Settings."
