package compose.project.click.click.data.repository

import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.domain.GroupAfterTapGateway // pragma: allowlist secret
import compose.project.click.click.domain.GroupAfterTapGroup // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PairConnectionRow(
    val id: String,
    @SerialName("user_ids") val userIds: List<String> = emptyList(),
    val status: String? = null,
)

/**
 * Picks the viewer's 1:1 connection from `connections?user_ids=cs.{viewer,peer}` rows (iOS
 * `GroupRepository.pairConnectionIDs`). A group connection also contains the pair but is never a
 * pairwise key, so only two-member rows count; active/kept win over a still-pending fresh Click.
 */
internal fun bestPairConnectionId(
    rows: List<PairConnectionRow>,
    viewerId: String,
    peerId: String,
): String? {
    val pairs =
        rows.filter { row ->
            row.userIds.distinct().size == 2 && viewerId in row.userIds && peerId in row.userIds
        }
    val best = pairs.firstOrNull { it.status in setOf("active", "kept") } ?: pairs.firstOrNull()
    return best?.id?.takeIf { it.isNotBlank() }
}

/** The viewer's 1:1 connection with each peer. Throws when a lookup fails (never partial on error). */
internal suspend fun ConnectionRepository.fetchPairConnectionIdsImpl(
    viewerId: String,
    peerIds: List<String>,
): Map<String, String> {
    val result = mutableMapOf<String, String>()
    for (peer in peerIds.map { it.trim() }.filter { it.isNotEmpty() && it != viewerId }.distinct()) {
        val rows =
            supabase
                .from("connections")
                .select(columns = Columns.list("id", "user_ids", "status")) {
                    filter { contains("user_ids", listOf(viewerId, peer)) }
                    limit(10)
                }.decodeList<PairConnectionRow>()
        bestPairConnectionId(rows, viewerId, peer)?.let { result[peer] = it }
    }
    return result
}

/** [GroupAfterTapGateway] over the chat repository (groups, clique create, epoch) and PostgREST pairs. */
internal class ConnectionGroupAfterTapGateway(
    private val repository: ConnectionRepository,
    private val chatRepository: SupabaseChatRepository,
) : GroupAfterTapGateway {
    override suspend fun groups(viewerId: String): List<GroupAfterTapGroup> =
        chatRepository.fetchGroupUserChatsWithDetails(viewerId).mapNotNull { details ->
            val clique = details.groupClique ?: return@mapNotNull null
            GroupAfterTapGroup(
                groupId = clique.groupId,
                chatId = details.chat.id?.takeIf { it.isNotBlank() },
                memberUserIds = clique.memberUserIds,
            )
        }

    override suspend fun pairConnectionIds(
        viewerId: String,
        peerIds: List<String>,
    ): Map<String, String> = repository.fetchPairConnectionIds(viewerId, peerIds)

    override suspend fun create(
        creatorId: String,
        pairConnectionIds: Map<String, String>,
        name: String,
    ): String {
        // Wrap edges come from the resolved pairs (a fresh tap's pairs may not be cached yet).
        val now = 0L
        val pairEdges =
            pairConnectionIds.map { (peer, connectionId) ->
                Connection(
                    id = connectionId,
                    created = now,
                    expiry = now,
                    user_ids = listOf(creatorId, peer).sorted(),
                    status = "active",
                    expiry_state = "active",
                )
            }
        val members = (pairConnectionIds.keys + creatorId).distinct().sorted()
        val created =
            VerifiedCliqueCreation
                .createVerifiedCliqueWithWrappedKeys(
                    chatRepository = chatRepository,
                    connections = pairEdges + AppDataManager.connections.value,
                    currentUserId = creatorId,
                    memberUserIds = members,
                    initialGroupName = name,
                ).getOrThrow()
        chatRepository.resolveChatIdForGroupId(created.groupId)?.let { chatId ->
            chatRepository.cacheGroupMasterKey(chatId, created.masterKey32)
        }
        return created.groupId
    }

    override suspend fun reconcileMembershipEpoch(
        group: GroupAfterTapGroup,
        viewerId: String,
    ) {
        val chatId = group.chatId ?: chatRepository.resolveChatIdForGroupId(group.groupId) ?: return
        chatRepository.secureGroupAfterMembershipChange(chatId, viewerId).getOrThrow()
    }
}
