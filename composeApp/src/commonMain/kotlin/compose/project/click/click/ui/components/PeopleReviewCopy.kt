package compose.project.click.click.ui.components

/*
 * Copy for the "Who's in this Click?" review of a group tap (07 §6 G4-4).
 * iOS: `Click/Features/AddClick/TapConnectView.swift` (`PeopleReviewView`) and
 * `TapConnectModel.PeopleReview.isExistingGroup`.
 */

/** The tap matched a group that already exists and nobody was removed: confirming saves this Click on it. */
fun isExistingGroupReview(
    existingGroup: Boolean,
    removedCount: Int,
): Boolean = existingGroup && removedCount == 0

fun peopleReviewSubtitle(existingGroup: Boolean): String =
    if (existingGroup) {
        "You're already a group. Remove anyone who isn't here, then save this Click."
    } else {
        "Remove anyone you don't want to connect with."
    }

/** "Connecting with · 3", or "Connecting with" once everyone is removed. */
fun peopleReviewConnectingTitle(includedCount: Int): String =
    if (includedCount == 0) "Connecting with" else "Connecting with · $includedCount"

const val PEOPLE_REVIEW_NOT_CONNECTING_TITLE: String = "Not connecting"
const val PEOPLE_REVIEW_EVERYONE_REMOVED: String = "Everyone's removed. Add someone back, or skip this Click."
const val PEOPLE_REVIEW_REMOVED_STATUS: String = "Won't be connected with you"

/** The primary button: skip when nobody is left, save on an untouched existing group, else connect. */
fun peopleReviewPrimaryTitle(
    includedNames: List<String>,
    existingGroup: Boolean,
    removedCount: Int,
): String {
    if (includedNames.isEmpty()) return "Skip this Click"
    if (isExistingGroupReview(existingGroup, removedCount)) return "Save this Click"
    if (includedNames.size == 1) {
        val name = includedNames[0].trim()
        return "Connect with ${name.substringBefore(' ').ifEmpty { name }}"
    }
    return "Connect with ${includedNames.size} people"
}
