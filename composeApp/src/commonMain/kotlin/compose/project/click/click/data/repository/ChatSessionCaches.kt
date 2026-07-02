package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.models.Message
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide chat session caches shared by every [SupabaseChatRepository] instance so
 * RealtimeCoordinator inserts see the same routing, crypto, and hot timelines as the UI repo.
 */
object ChatSessionCaches {
    sealed class ResolvedChatCrypto {
        data class Pairwise(val keys: MessageCrypto.DerivedKeys) : ResolvedChatCrypto()
        data class GroupMaster(val masterKey: ByteArray) : ResolvedChatCrypto()
    }

    private val chatCryptoMutex = Mutex()
    private val chatCryptoCache = mutableMapOf<String, ResolvedChatCrypto>()

    private val routingMutex = Mutex()
    private val chatIdToConnectionId = mutableMapOf<String, String>()
    private val chatIdToGroupId = mutableMapOf<String, String>()

    val messageTimelineCache = ChatTimelineCache()

    suspend fun seedConnectionRouting(chatId: String, connectionId: String) {
        if (chatId.isBlank() || connectionId.isBlank()) return
        routingMutex.withLock {
            chatIdToConnectionId[chatId] = connectionId
            chatIdToGroupId.remove(chatId)
        }
    }

    suspend fun seedGroupRouting(chatId: String, groupId: String) {
        if (chatId.isBlank() || groupId.isBlank()) return
        routingMutex.withLock {
            chatIdToGroupId[chatId] = groupId
            chatIdToConnectionId.remove(chatId)
        }
    }

    suspend fun rememberConnectionRouting(chatId: String, connectionId: String) {
        seedConnectionRouting(chatId, connectionId)
    }

    suspend fun rememberGroupRouting(chatId: String, groupId: String) {
        seedGroupRouting(chatId, groupId)
    }

    suspend fun peekConnectionIdForChat(chatId: String): String? = routingMutex.withLock {
        chatIdToConnectionId[chatId]
    }

    suspend fun peekGroupIdForChat(chatId: String): String? = routingMutex.withLock {
        chatIdToGroupId[chatId]
    }

    suspend fun peekListKeyForChat(chatId: String): String? =
        peekConnectionIdForChat(chatId) ?: peekGroupIdForChat(chatId)

    suspend fun clearRouting() {
        routingMutex.withLock {
            chatIdToConnectionId.clear()
            chatIdToGroupId.clear()
        }
    }

    suspend fun getCrypto(chatId: String): ResolvedChatCrypto? =
        chatCryptoMutex.withLock { chatCryptoCache[chatId] }

    suspend fun putPairwiseCrypto(chatId: String, keys: MessageCrypto.DerivedKeys) {
        chatCryptoMutex.withLock { chatCryptoCache[chatId] = ResolvedChatCrypto.Pairwise(keys) }
    }

    suspend fun putGroupCrypto(chatId: String, masterKey: ByteArray) {
        val copy = masterKey.copyOf()
        chatCryptoMutex.withLock { chatCryptoCache[chatId] = ResolvedChatCrypto.GroupMaster(copy) }
    }

    suspend fun clearCrypto() {
        chatCryptoMutex.withLock {
            for (entry in chatCryptoCache.values) {
                if (entry is ResolvedChatCrypto.GroupMaster) {
                    entry.masterKey.fill(0)
                }
            }
            chatCryptoCache.clear()
        }
    }

    fun mergeTimeline(connectionId: String, message: Message) {
        messageTimelineCache.mergeMessage(connectionId, message)
    }

    fun clearTimelines() {
        messageTimelineCache.clear()
    }

    suspend fun clearAll() {
        clearCrypto()
        clearRouting()
        clearTimelines()
    }
}
