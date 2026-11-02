package compose.project.click.click.domain

import compose.project.click.click.data.models.GroupCliqueDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Where this phone is in finding or creating the verified group chat of a group Click. */
sealed interface MultiTapGroupState {
    data object Idle : MultiTapGroupState

    data class Preparing(
        val memberUserIds: List<String>,
    ) : MultiTapGroupState

    /** [groupId] is the group found or created; [createdHere] is true when this phone created it. */
    data class Ready(
        val memberUserIds: List<String>,
        val groupId: String,
        val createdHere: Boolean,
    ) : MultiTapGroupState

    data class Failed(
        val memberUserIds: List<String>,
        val message: String,
    ) : MultiTapGroupState
}

/**
 * Multi-Tap `prepareGroup`: finds or creates the verified group chat for a group Click (3+ people).
 *
 * Every member's phone runs this, so the member with the lowest user ID creates right away and
 * the others first wait for that group to appear (2 s, 3 s, then 4 s) before creating it
 * themselves. The server RPC `create_verified_clique` owns membership and rejects a second group
 * for the same members, so a create that loses the race is recovered by looking again.
 *
 * Mirrors iOS `Click/Features/Connect/PostConnectModel.swift` (`prepareGroup`, `creatorWaits`,
 * `groupMemberIDs`). The client only lists the viewer's groups and calls the create RPC.
 */
object MultiTapGroupPreparation {
    /** How long a non-creating member waits for the creator's group before creating it itself. */
    val creatorWaitsMs: List<Long> = listOf(2_000L, 3_000L, 4_000L)

    const val MIN_MEMBERS: Int = 3

    /** iOS `PostConnectView` failure copy, before the error detail. */
    const val CREATE_FAILED_PREFIX: String = "Couldn't set up the group chat."

    /** Everyone in this group Click, including the viewer: distinct, blank-free and sorted. */
    fun memberIds(
        viewerId: String,
        groupMemberIds: List<String>,
        peerIds: List<String>,
    ): List<String> =
        (groupMemberIds + peerIds + viewerId)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    /** The lowest member ID creates the group; everyone else waits for it first. */
    fun isCreator(
        viewerId: String,
        sortedMemberIds: List<String>,
    ): Boolean = sortedMemberIds.firstOrNull() == viewerId

    /** The viewer's existing group whose members are exactly [memberIds] (order ignored). */
    fun matchingGroup(
        groups: List<GroupCliqueDetails>,
        memberIds: List<String>,
    ): GroupCliqueDetails? {
        val wanted = memberIds.toSet()
        return groups.firstOrNull { it.memberUserIds.toSet() == wanted }
    }

    /** The new group's name: the other members' first names, sorted, joined by ", ". */
    fun groupName(peerDisplayNames: List<String>): String =
        peerDisplayNames
            .map { name ->
                val trimmed = name.trim()
                trimmed.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() } ?: trimmed
            }.filter { it.isNotEmpty() }
            .sorted()
            .joinToString(", ")
            .ifBlank { "Clique" }

    fun failureMessage(error: Throwable?): String {
        val detail = error?.message?.trim().orEmpty()
        return if (detail.isEmpty()) CREATE_FAILED_PREFIX else "$CREATE_FAILED_PREFIX $detail"
    }

    /** True when a new run may start for [memberIds]: nothing is in flight or done for that set. */
    fun shouldStart(
        current: MultiTapGroupState,
        memberIds: List<String>,
    ): Boolean =
        when (current) {
            is MultiTapGroupState.Preparing -> current.memberUserIds != memberIds
            is MultiTapGroupState.Ready -> current.memberUserIds != memberIds
            MultiTapGroupState.Idle, is MultiTapGroupState.Failed -> true
        }

    /**
     * Runs the find-or-create flow and returns the final state ([MultiTapGroupState.Idle] when
     * this isn't a group Click of 3+ people).
     *
     * @param findExisting the viewer's current groups, freshly listed (a failure counts as none).
     * @param create calls `create_verified_clique` and returns the new group ID.
     */
    suspend fun prepare(
        viewerId: String,
        memberIds: List<String>,
        findExisting: suspend () -> List<GroupCliqueDetails>,
        create: suspend () -> Result<String>,
        wait: suspend (Long) -> Unit = { delay(it) },
    ): MultiTapGroupState {
        val members = memberIds.distinct().sorted()
        if (viewerId.isBlank() || viewerId !in members || members.size < MIN_MEMBERS) return MultiTapGroupState.Idle

        suspend fun existing(): GroupCliqueDetails? {
            val groups =
                try {
                    findExisting()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            return matchingGroup(groups, members)
        }

        fun ready(
            groupId: String,
            createdHere: Boolean,
        ) = MultiTapGroupState.Ready(members, groupId, createdHere)

        existing()?.let { return ready(it.groupId, createdHere = false) }
        if (!isCreator(viewerId, members)) {
            for (ms in creatorWaitsMs) {
                wait(ms)
                existing()?.let { return ready(it.groupId, createdHere = false) }
            }
        }
        val created =
            try {
                create()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        created.getOrNull()?.takeIf { it.isNotBlank() }?.let { return ready(it, createdHere = true) }
        // Another member's phone may have created it between our check and our create.
        existing()?.let { return ready(it.groupId, createdHere = false) }
        return MultiTapGroupState.Failed(members, failureMessage(created.exceptionOrNull()))
    }
}
