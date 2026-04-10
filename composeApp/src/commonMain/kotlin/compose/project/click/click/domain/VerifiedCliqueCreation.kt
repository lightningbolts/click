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

    suspend fun createVerifiedCliqueWithWrappedKeys(
        chatRepository: ChatRepository,
        connections: List<Connection>,
        currentUserId: String,
        memberUserIds: List<String>,
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
        val master = MessageCrypto.generateGroupMasterKey()
        val b64 = MessageCrypto.encodeGroupMasterKeyBase64(master)
        val encrypted = mutableMapOf<String, String>()
        for (m in members) {
            val wrapPeer = if (m == creator) anchor else creator
            val conn = findActiveEdge(m, wrapPeer, connections) ?: run {
                return Result.failure(IllegalStateException("Missing verified connection for a member"))
            }
            val keys = MessageCrypto.deriveKeysForConnection(
                conn.id,
                listOf(m, wrapPeer).sorted(),
            )
            encrypted[m] = MessageCrypto.encryptContent(b64, keys)
        }
        return chatRepository.createVerifiedClique(members, encrypted).map { groupId ->
            VerifiedCliqueCreateResult(groupId = groupId, masterKey32 = master)
        }
    }
}
