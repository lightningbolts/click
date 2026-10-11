package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** G4-4 / G4-5 review helpers (copy from iOS `TapConnectModel.finishAfterOtherConfirmed`). */
class ReviewWatchTest {
    @Test
    fun noticeNamesWhoIsStillInTheGroup() {
        assertNull(otherPhoneConfirmedNotice(emptyList()))
        assertEquals(
            "Another phone confirmed this group first, so Sam is still in it. You can leave the group from its settings.",
            otherPhoneConfirmedNotice(listOf(User(id = "s", name = "Sam Lee"))),
        )
        assertEquals(
            "Another phone confirmed this group first, so Sam and Ada are still in it. You can leave the group from its settings.",
            otherPhoneConfirmedNotice(listOf(User(id = "s", name = "Sam"), User(id = "a", name = "Ada"))),
        )
    }

    @Test
    fun lateJoinersAreNewCandidatesOnlyAndNeverTheViewer() {
        val added =
            lateJoiners(
                known = listOf("a", "b"),
                candidates = listOf(User(id = "a"), User(id = "me"), User(id = "c"), User(id = "c")),
                currentUserId = "me",
            )
        assertEquals(listOf("c"), added.map { it.id })
    }
}
