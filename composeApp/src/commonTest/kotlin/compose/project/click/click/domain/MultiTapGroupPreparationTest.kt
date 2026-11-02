package compose.project.click.click.domain

import compose.project.click.click.data.models.GroupCliqueDetails
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P2-19: Multi-Tap `prepareGroup` (iOS `PostConnectModel.prepareGroup`). */
class MultiTapGroupPreparationTest {
    private val members = listOf("a", "b", "c")

    private fun group(
        id: String,
        vararg memberIds: String,
    ) = GroupCliqueDetails(
        groupId = id,
        name = "G",
        createdByUserId = memberIds.first(),
        keyAnchorUserId = memberIds.last(),
        memberUserIds = memberIds.toList(),
    )

    @Test
    fun memberIdsMergesViewerPeersAndGroupSortedAndDistinct() {
        assertEquals(
            listOf("a", "b", "c"),
            MultiTapGroupPreparation.memberIds(viewerId = "b", groupMemberIds = listOf("c", "a"), peerIds = listOf("a", " ", "c")),
        )
    }

    @Test
    fun lowestIdIsTheCreator() {
        assertTrue(MultiTapGroupPreparation.isCreator("a", members))
        assertFalse(MultiTapGroupPreparation.isCreator("b", members))
    }

    @Test
    fun matchingGroupNeedsTheExactMemberSet() {
        val groups = listOf(group("g1", "a", "b"), group("g2", "a", "b", "c", "d"), group("g3", "c", "a", "b"))
        assertEquals("g3", MultiTapGroupPreparation.matchingGroup(groups, members)?.groupId)
        assertNull(MultiTapGroupPreparation.matchingGroup(groups.take(2), members))
    }

    @Test
    fun groupNameIsSortedFirstNames() {
        assertEquals("Ada, Zoe", MultiTapGroupPreparation.groupName(listOf("Zoe Smith", "  Ada Lovelace ")))
        assertEquals("Clique", MultiTapGroupPreparation.groupName(listOf(" ")))
    }

    @Test
    fun failureMessageMatchesIosCopy() {
        assertEquals("Couldn't set up the group chat. Offline", MultiTapGroupPreparation.failureMessage(Exception("Offline")))
        assertEquals("Couldn't set up the group chat.", MultiTapGroupPreparation.failureMessage(null))
    }

    @Test
    fun shouldStartOncePerMemberSet() {
        assertTrue(MultiTapGroupPreparation.shouldStart(MultiTapGroupState.Idle, members))
        assertFalse(MultiTapGroupPreparation.shouldStart(MultiTapGroupState.Preparing(members), members))
        assertFalse(MultiTapGroupPreparation.shouldStart(MultiTapGroupState.Ready(members, "g", false), members))
        assertTrue(MultiTapGroupPreparation.shouldStart(MultiTapGroupState.Ready(listOf("a", "b", "d"), "g", false), members))
        assertTrue(MultiTapGroupPreparation.shouldStart(MultiTapGroupState.Failed(members, "x"), members))
    }

    @Test
    fun creatorCreatesImmediatelyWithoutWaiting() =
        runTest {
            val waits = mutableListOf<Long>()
            var creates = 0
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "a",
                    memberIds = members,
                    findExisting = { emptyList() },
                    create = {
                        creates++
                        Result.success("new")
                    },
                    wait = { waits += it },
                )
            assertEquals(MultiTapGroupState.Ready(members, "new", createdHere = true), result)
            assertEquals(1, creates)
            assertTrue(waits.isEmpty())
        }

    @Test
    fun existingGroupIsReusedWithoutCreating() =
        runTest {
            var creates = 0
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "a",
                    memberIds = members,
                    findExisting = { listOf(group("g", "c", "b", "a")) },
                    create = {
                        creates++
                        Result.success("new")
                    },
                    wait = {},
                )
            assertEquals(MultiTapGroupState.Ready(members, "g", createdHere = false), result)
            assertEquals(0, creates)
        }

    @Test
    fun nonCreatorWaitsForTheCreatorsGroup() =
        runTest {
            val waits = mutableListOf<Long>()
            var lookups = 0
            var creates = 0
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "b",
                    memberIds = members,
                    findExisting = {
                        lookups++
                        if (lookups >= 3) listOf(group("from-a", "a", "b", "c")) else emptyList()
                    },
                    create = {
                        creates++
                        Result.success("dup")
                    },
                    wait = { waits += it },
                )
            assertEquals(MultiTapGroupState.Ready(members, "from-a", createdHere = false), result)
            assertEquals(listOf(2_000L, 3_000L), waits)
            assertEquals(0, creates)
        }

    @Test
    fun nonCreatorCreatesAfterAllWaitsWhenNoGroupAppears() =
        runTest {
            val waits = mutableListOf<Long>()
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "c",
                    memberIds = members,
                    findExisting = { emptyList() },
                    create = { Result.success("mine") },
                    wait = { waits += it },
                )
            assertEquals(MultiTapGroupState.Ready(members, "mine", createdHere = true), result)
            assertEquals(MultiTapGroupPreparation.creatorWaitsMs, waits)
        }

    @Test
    fun lostCreateRaceRecoversTheOtherPhonesGroup() =
        runTest {
            var created = false
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "a",
                    memberIds = members,
                    findExisting = { if (created) listOf(group("winner", "a", "b", "c")) else emptyList() },
                    create = {
                        created = true
                        Result.failure(IllegalStateException("already exists"))
                    },
                    wait = {},
                )
            assertEquals(MultiTapGroupState.Ready(members, "winner", createdHere = false), result)
        }

    @Test
    fun createFailureWithNoGroupFails() =
        runTest {
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "a",
                    memberIds = members,
                    findExisting = { throw IllegalStateException("network") },
                    create = { throw IllegalStateException("Missing verified connection") },
                    wait = {},
                )
            val failed = assertIs<MultiTapGroupState.Failed>(result)
            assertEquals("Couldn't set up the group chat. Missing verified connection", failed.message)
            assertEquals(members, failed.memberUserIds)
        }

    @Test
    fun notAGroupClickIsIdle() =
        runTest {
            var calls = 0
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = "a",
                    memberIds = listOf("a", "b"),
                    findExisting = {
                        calls++
                        emptyList()
                    },
                    create = {
                        calls++
                        Result.success("x")
                    },
                    wait = {},
                )
            assertEquals(MultiTapGroupState.Idle, result)
            assertEquals(0, calls)
            assertEquals(
                MultiTapGroupState.Idle,
                MultiTapGroupPreparation.prepare("z", members, { emptyList() }, { Result.success("x") }, {}),
            )
        }
}
