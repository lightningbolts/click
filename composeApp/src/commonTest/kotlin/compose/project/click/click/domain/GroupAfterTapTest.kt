package compose.project.click.click.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** G0-12: the post-tap group election (port of iOS `PostConnectModel.prepareGroup`). */
class GroupAfterTapTest {
    private class FakeGateway(
        var groups: MutableList<GroupAfterTapGroup> = mutableListOf(),
        var pairs: Map<String, String> = mapOf("a" to "ca", "b" to "cb", "c" to "cc", "me" to "cme", "z" to "cz"),
        /** Group appears after this many `groups` reads (simulates another phone creating it). */
        var appearsAfterReads: Int? = null,
        var appearing: GroupAfterTapGroup? = null,
        var createFails: Boolean = false,
        var createAppearsAfterFailure: GroupAfterTapGroup? = null,
    ) : GroupAfterTapGateway {
        var reads = 0
        val created = mutableListOf<Pair<String, String>>()
        val reconciled = mutableListOf<String>()

        override suspend fun groups(viewerId: String): List<GroupAfterTapGroup> {
            reads += 1
            val appear = appearing
            if (appear != null && appearsAfterReads != null && reads >= appearsAfterReads!!) {
                if (groups.none { it.groupId == appear.groupId }) groups.add(appear)
            }
            return groups.toList()
        }

        override suspend fun pairConnectionIds(
            viewerId: String,
            peerIds: List<String>,
        ): Map<String, String> = pairs.filterKeys { it in peerIds }

        override suspend fun create(
            creatorId: String,
            pairConnectionIds: Map<String, String>,
            name: String,
        ): String {
            if (createFails) {
                createAppearsAfterFailure?.let { groups.add(it) }
                throw IllegalStateException("duplicate clique")
            }
            created += creatorId to name
            val id = "g-new"
            groups.add(GroupAfterTapGroup(id, "chat-new", (pairConnectionIds.keys + creatorId).sorted()))
            return id
        }

        override suspend fun reconcileMembershipEpoch(
            group: GroupAfterTapGroup,
            viewerId: String,
        ) {
            reconciled += group.groupId
        }
    }

    private val peers = listOf(GroupAfterTapPeer("b", "Zoe Bloggs"), GroupAfterTapPeer("c", "Adam Jones"))

    @Test
    fun membersIncludeViewerSortedAndDeduplicated() {
        assertEquals(listOf("a", "b", "me"), GroupAfterTap.memberIds(listOf("b", "me"), listOf("a", "b"), "me"))
    }

    @Test
    fun nameIsPeersFirstNamesSortedAlphabetically() {
        // Sorted by first name, not by user ID ("b" Zoe sorts before "c" Adam by id).
        assertEquals("Adam, Zoe", GroupAfterTap.groupName(peers))
    }

    @Test
    fun lowestIdCreatesImmediatelyWithoutWaiting() =
        runTest {
            val gateway = FakeGateway()
            val sleeps = mutableListOf<Long>()
            val result = GroupAfterTap.prepare("a", listOf("a", "b", "c"), peers, gateway, sleep = { sleeps += it })
            assertIs<GroupAfterTapResult.Ready>(result)
            assertTrue(result.createdHere)
            assertEquals(emptyList(), sleeps)
            assertEquals(listOf("a" to "Adam, Zoe"), gateway.created)
            assertEquals(listOf("g-new"), gateway.reconciled)
        }

    @Test
    fun otherMembersWaitTwoThreeFourSecondsThenCreate() =
        runTest {
            val gateway = FakeGateway()
            val sleeps = mutableListOf<Long>()
            val result =
                GroupAfterTap.prepare(
                    "me",
                    listOf("a", "me", "z"),
                    listOf(GroupAfterTapPeer("a", "Al"), GroupAfterTapPeer("z", "Zed")),
                    gateway,
                    sleep = { sleeps += it },
                )
            assertEquals(listOf(2_000L, 3_000L, 4_000L), sleeps)
            assertIs<GroupAfterTapResult.Ready>(result)
            assertTrue(result.createdHere)
            assertEquals(listOf("me" to "Al, Zed"), gateway.created)
        }

    @Test
    fun otherMemberFindsCreatorsGroupDuringWaits() =
        runTest {
            val creatorGroup = GroupAfterTapGroup("g1", "chat1", listOf("a", "me", "z"))
            // Read 1 is the immediate look; read 3 is after the second wait.
            val gateway = FakeGateway(appearsAfterReads = 3, appearing = creatorGroup)
            val sleeps = mutableListOf<Long>()
            val result =
                GroupAfterTap.prepare("me", listOf("a", "me", "z"), emptyList(), gateway, sleep = { sleeps += it })
            assertEquals(listOf(2_000L, 3_000L), sleeps)
            assertIs<GroupAfterTapResult.Ready>(result)
            assertEquals("g1", result.group.groupId)
            assertEquals(false, result.createdHere)
            assertEquals(emptyList(), gateway.created)
            assertEquals(listOf("g1"), gateway.reconciled)
        }

    @Test
    fun existingGroupMustMatchExactMemberSet() =
        runTest {
            val superset = GroupAfterTapGroup("g-big", "c", listOf("a", "b", "c", "d"))
            val exact = GroupAfterTapGroup("g-exact", "c2", listOf("a", "b", "c"))
            val gateway = FakeGateway(groups = mutableListOf(superset, exact))
            val result = GroupAfterTap.prepare("b", listOf("a", "b", "c"), emptyList(), gateway, sleep = {})
            assertIs<GroupAfterTapResult.Ready>(result)
            assertEquals("g-exact", result.group.groupId)
        }

    @Test
    fun failedCreateLooksAgain() =
        runTest {
            val other = GroupAfterTapGroup("g-other", "c", listOf("a", "b", "c"))
            val gateway = FakeGateway(createFails = true, createAppearsAfterFailure = other)
            val result = GroupAfterTap.prepare("a", listOf("a", "b", "c"), peers, gateway, sleep = {})
            assertIs<GroupAfterTapResult.Ready>(result)
            assertEquals("g-other", result.group.groupId)
            assertEquals(false, result.createdHere)
        }

    @Test
    fun failedCreateWithNoGroupFails() =
        runTest {
            val gateway = FakeGateway(createFails = true)
            val result = GroupAfterTap.prepare("a", listOf("a", "b", "c"), peers, gateway, sleep = {})
            assertIs<GroupAfterTapResult.Failed>(result)
        }

    @Test
    fun missingPairConnectionNeverCreatesASmallerGroup() =
        runTest {
            val gateway = FakeGateway(pairs = mapOf("b" to "cb"))
            val result = GroupAfterTap.prepare("a", listOf("a", "b", "c"), peers, gateway, sleep = {})
            assertIs<GroupAfterTapResult.Failed>(result)
            assertEquals(emptyList(), gateway.created)
        }

    @Test
    fun oneToOneIsSkipped() =
        runTest {
            val result = GroupAfterTap.prepare("a", listOf("a", "b"), emptyList(), FakeGateway(), sleep = {})
            assertIs<GroupAfterTapResult.Skipped>(result)
        }
}
