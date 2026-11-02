package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-4 / G4-5 review helpers (copy from iOS `TapConnectModel.finishAfterOtherConfirmed`). */
class ReviewWatchTest {
    private fun connection(
        id: String,
        vararg users: String,
        group: Boolean = false,
    ) = Connection(id = id, created = 0L, expiry = 0L, user_ids = users.toList(), isGroup = group)

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
        assertEquals("Sam, Ada, and someone", joinedFirstNames(listOf("Sam", "Ada Byron", null)))
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

    @Test
    fun lateJoinersRespectTheHostCap() {
        val known = (1..10).map { "p$it" }
        val added = lateJoiners(known, listOf(User(id = "x"), User(id = "y"), User(id = "z")), "me")
        assertEquals(listOf("x"), added.map { it.id })
    }

    @Test
    fun lateJoinersStartIncludedAndKeepRemovals() {
        val review =
            ConnectionState.TaggingContext(
                newConnections = emptyList(),
                targetUsers = listOf(UserProfile("a", "Ada"), UserProfile("b", "Ben")),
                memberUserIds = listOf("a", "b", "me"),
                selectableUsers = listOf(UserProfile("a", "Ada"), UserProfile("b", "Ben")),
                pendingHandshakeId = "ph",
                requiresSelection = true,
                selectedPeerIds = setOf("a"),
            )
        val next = review.withLateJoiners(listOf(UserProfile("c", "Cy")), existingConnectionId = "g1")
        assertEquals(listOf("a", "b", "c"), next.selectableUsers.map { it.id })
        assertEquals(setOf("a", "c"), next.selectedPeerIds)
        assertEquals(listOf("a", "b", "c", "me"), next.memberUserIds)
        assertEquals("g1", next.existingConnectionId)
        assertTrue(next.requiresSelection)
    }

    @Test
    fun groupWatchOnlyWhileTheSameResultIsShown() {
        val shown =
            ConnectionState.TaggingContext(
                newConnections = listOf(connection("c1", "me", "a")),
                targetUsers = listOf(UserProfile("a", "Ada")),
            )
        assertTrue(isShowingResult(shown, "c1"))
        assertFalse(isShowingResult(shown, "c2"))
        assertFalse(isShowingResult(shown.copy(requiresSelection = true), "c1"))
        assertFalse(isShowingResult(ConnectionState.Idle, "c1"))
    }

    @Test
    fun groupTagsAlsoGoToEachMembersPairConnection() {
        val known =
            listOf(
                connection("pair-a", "me", "a"),
                connection("pair-b", "b", "me"),
                connection("other", "a", "b"),
                connection("g", "me", "a", "b", group = true),
            )
        assertEquals(
            listOf("g", "pair-a", "pair-b"),
            groupTagTargetIds(listOf("g"), listOf("me", "a", "b", "c"), "me", known),
        )
    }
}
