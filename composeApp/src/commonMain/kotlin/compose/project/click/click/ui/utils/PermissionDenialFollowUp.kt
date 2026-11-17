package compose.project.click.click.ui.utils

/**
 * Whether a denied location request should explain how to allow it in Settings.
 *
 * Mirrors click-ios 6a8310d: a "Don't allow" answer to the system prompt ends there, and nothing
 * opens Settings on its own. Only a later request, with location already off for good (the OS
 * no longer shows its prompt), shows a notice with an Open Settings button the user chooses.
 *
 * Android reports "off for good" as: asked before, and no rationale before or after the request.
 * A first denial, or a second denial from a shown prompt, has a rationale on one side and gets
 * no notice.
 */
fun shouldExplainDeniedLocation(
    askedBefore: Boolean,
    rationaleBefore: Boolean,
    rationaleAfter: Boolean,
): Boolean = askedBefore && !rationaleBefore && !rationaleAfter

const val LOCATION_OFF_NOTICE_TITLE = "Location is off for Click"
const val LOCATION_OFF_NOTICE_BODY =
    "Click works without it. To center the map on you and see what's nearby, you can allow location for Click in Settings."
