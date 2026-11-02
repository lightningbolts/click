package compose.project.click.click.platform

import compose.project.click.click.events.EventCalendarDraft

/**
 * Opens the phone's calendar app on a prefilled new event. No calendar permission is needed: the
 * user saves it there. Returns false when no calendar app can take it.
 */
expect fun openSystemCalendarInsert(draft: EventCalendarDraft): Boolean

/** Opens [geoUri] in whichever maps app the user picks. Returns false when none is installed. */
expect fun openMapsChooser(geoUri: String): Boolean
