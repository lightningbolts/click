package compose.project.click.click.domain

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.repository.ChatRepository

/**
 * Server-side [create_verified_clique] expects each member's row sealed with the 1:1 channel
 * to the key anchor peer (same rules as [compose.project.click.click.viewmodel.ChatViewModel]).
 */
data class VerifiedCliqueCreateResult(
    val groupId: String,
    /** Raw 32-byte symmetric group master (cache locally for decrypting group messages). */
    val masterKey32: ByteArray,
)

object VerifiedCliqueCreation {

    private fun findActiveEdge(a: String, b: String, connections: List<Connection>): Connection? =
        connections.firstOrNull { c ->
            c.user_ids.size == 2 &&
                c.user_ids.contains(a) &&
                c.user_ids.contains(b) &&
                c.normalizedConnectionStatus() in setOf("active", "kept")
        }

    private fun findActiveGroup(memberUserIds: List<String>, connections: List<Connection>): Connection? {
        val members = memberUserIds.distinct().sorted()
        if (members.size < 3) return null
        return connections.firstOrNull { c ->
            c.user_ids.distinct().sorted() == members &&
                c.normalizedConnectionStatus() in setOf("active", "kept") &&
                (c.isGroup || c.user_ids.size >= 3)
        }
    }

    suspend fun createVerifiedCliqueWithWrappedKeys(
        chatRepository: ChatRepository,
        connections: List<Connection>,
        currentUserId: String,
        memberUserIds: List<String>,
        initialGroupName: String = "Clique",
    ): Result<VerifiedCliqueCreateResult> {
        val members = memberUserIds.distinct().sorted()
        if (members.size < 2) {
            return Result.failure(IllegalArgumentException("Pick at least one other person"))
        }
        if (currentUserId !in members) {
            return Result.failure(IllegalStateException("Caller must be included in members"))
        }
        val creator = currentUserId
        val anchor = members.first { it != creator }
        val groupConnection = findActiveGroup(members, connections)
        val master = MessageCrypto.generateGroupMasterKeyAsync()
        val encrypted = runCatching {
            MessageCrypto.wrapGroupMasterKeyForMembers(
                masterKey32 = master,
                members = members,
                creator = creator,
                anchor = anchor,
                groupConnectionId = groupConnection?.id,
                groupMemberIds = members,
                resolveEdge = { member, wrapPeer ->
                    val conn = findActiveEdge(member, wrapPeer, connections) ?: return@wrapGroupMasterKeyForMembers null
                    conn.id to listOf(member, wrapPeer).sorted()
                },
            )
        }.getOrElse {
            return Result.failure(
                it as? Exception ?: IllegalStateException(it.message ?: "Missing verified connection for a member"),
            )
        }
        val label = initialGroupName.trim().ifBlank { "Clique" }
        return chatRepository.createVerifiedClique(members, encrypted, label).map { groupId ->
            VerifiedCliqueCreateResult(groupId = groupId, masterKey32 = master)
        }
    }
}
