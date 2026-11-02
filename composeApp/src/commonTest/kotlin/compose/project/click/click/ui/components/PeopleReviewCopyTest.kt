package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** G4-4 review copy (iOS `PeopleReviewView.primaryTitle` / `subtitle`). */
class PeopleReviewCopyTest {
    @Test
    fun primaryTitle() {
        assertEquals("Skip this Click", peopleReviewPrimaryTitle(emptyList(), existingGroup = true, removedCount = 3))
        assertEquals("Save this Click", peopleReviewPrimaryTitle(listOf("Ada", "Ben"), existingGroup = true, removedCount = 0))
        assertEquals("Connect with Ada", peopleReviewPrimaryTitle(listOf("Ada Byron"), existingGroup = true, removedCount = 1))
        assertEquals("Connect with 2 people", peopleReviewPrimaryTitle(listOf("Ada", "Ben"), existingGroup = false, removedCount = 0))
    }

    @Test
    fun subtitleAndSections() {
        assertEquals("You're already a group. Remove anyone who isn't here, then save this Click.", peopleReviewSubtitle(true))
        assertEquals("Remove anyone you don't want to connect with.", peopleReviewSubtitle(false))
        assertEquals("Connecting with · 2", peopleReviewConnectingTitle(2))
        assertEquals("Connecting with", peopleReviewConnectingTitle(0))
    }
}
