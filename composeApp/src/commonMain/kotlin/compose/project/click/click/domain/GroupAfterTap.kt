package compose.project.click.click.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** A verified group (clique) the viewer belongs to, as far as the post-tap election needs it. */
data class GroupAfterTapGroup(
    val groupId: String,
    val chatId: String?,
    /** Every member, including the viewer. */
    val memberUserIds: List<String>,
)

/** A peer in the group Click: id plus display name (for the group's initial name). */
data class GroupAfterTapPeer(
    val id: String,
    val name: String?,
)

/** Reads and writes [GroupAfterTap] needs; the real one is backed by PostgREST + `create_verified_clique`. */
interface GroupAfterTapGateway {
    /** Every group the viewer is in. */
    suspend fun groups(viewerId: String): List<GroupAfterTapGroup>

    /**
     * The viewer's 1:1 connection with each peer (two-member rows only; active/kept first).
     * Throws when the lookup fails.
     */
    suspend fun pairConnectionIds(
        viewerId: String,
        peerIds: List<String>,
    ): Map<String, String>

    /** `create_verified_clique` with keys wrapped over [pairConnectionIds]; returns the group id. */
    suspend fun create(
        creatorId: String,
        pairConnectionIds: Map<String, String>,
        name: String,
    ): String

    /** Rotates the group's E2EE epoch to its current membership (iOS `reconcileMembershipEpoch`). */
    suspend fun reconcileMembershipEpoch(
        group: GroupAfterTapGroup,
        viewerId: String,
    )
}

sealed class GroupAfterTapResult {
    /** Not a group Click (fewer than 3 members). */
    object Skipped : GroupAfterTapResult()

    data class Ready(
        val group: GroupAfterTapGroup,
        /** True when this phone created the group. */
        val createdHere: Boolean,
    ) : GroupAfterTapResult()

    data class Failed(
        val message: String,
    ) : GroupAfterTapResult()
}

/**
 * Finds or creates the verified group chat after a group Click (port of iOS
 * `PostConnectModel.prepareGroup`). Every member's phone runs this, so the lowest member ID
 * creates right away and the others first wait for that group to appear
 * (`create_verified_clique` rejects a second group for the same members anyway).
 */
object GroupAfterTap {
    /** How long a non-creating member waits for the creator's group before creating it itself. */
    val CREATOR_WAITS_MS: List<Long> = listOf(2_000L, 3_000L, 4_000L)

    /** Everyone in the group Click, including the viewer, sorted (iOS `groupMemberIDs`). */
    fun memberIds(
        groupMemberIds: List<String>,
        peerIds: List<String>,
        viewerId: String,
    ): List<String> =
        (groupMemberIds + peerIds + viewerId)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    /** First word of a display name (iOS `HomeFeedModel.firstName`), else the whole name. */
    fun firstName(name: String?): String? {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() } ?: trimmed
    }

    /** Peers' first names sorted alphabetically and joined with ", " (not sorted by user ID). */
    fun groupName(peers: List<GroupAfterTapPeer>): String =
        peers
            .distinctBy { it.id }
            .map { firstName(it.name) ?: it.name?.trim().orEmpty() }
            .sorted()
            .joinToString(", ")

    suspend fun prepare(
        viewerId: String,
        groupMemberIds: List<String>,
        peers: List<GroupAfterTapPeer>,
        gateway: GroupAfterTapGateway,
        sleep: suspend (Long) -> Unit = { delay(it) },
    ): GroupAfterTapResult {
        if (viewerId.isBlank()) return GroupAfterTapResult.Skipped
        val members = memberIds(groupMemberIds, peers.map { it.id }, viewerId)
        if (members.size < 3) return GroupAfterTapResult.Skipped
        val memberSet = members.toSet()

        suspend fun existing(): GroupAfterTapGroup? {
            val groups =
                try {
                    gateway.groups(viewerId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            return groups.firstOrNull { (it.memberUserIds + viewerId).toSet() == memberSet }
        }

        suspend fun open(
            group: GroupAfterTapGroup,
            createdHere: Boolean,
        ): GroupAfterTapResult {
            try {
                gateway.reconcileMembershipEpoch(group, viewerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort (iOS `try?`): the next send rotates if this couldn't.
            }
            return GroupAfterTapResult.Ready(group, createdHere)
        }

        existing()?.let { return open(it, createdHere = false) }
        if (members.first() != viewerId) {
            for (wait in CREATOR_WAITS_MS) {
                sleep(wait)
                existing()?.let { return open(it, createdHere = false) }
            }
        }

        val peerIds = members.filter { it != viewerId }
        try {
            val pairs = gateway.pairConnectionIds(viewerId, peerIds)
            // A missing pair would silently create a smaller group; treat it as a failed create.
            check(pairs.keys.containsAll(peerIds)) { "Missing verified connection for a member" }
            gateway.create(creatorId = viewerId, pairConnectionIds = pairs, name = groupName(peers))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Another member's phone may have created it between our check and our create.
            existing()?.let { return open(it, createdHere = false) }
            return GroupAfterTapResult.Failed(
                "Couldn't set up the group chat." + (e.message?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""),
            )
        }
        return existing()?.let { open(it, createdHere = true) }
            ?: GroupAfterTapResult.Failed("The group was created but couldn't be loaded yet. Pull to refresh Groups.")
    }
}
