package compose.project.click.click.data.repository

import compose.project.click.click.data.api.deleteScheduledMessage
import compose.project.click.click.data.api.getScheduledMessages
import compose.project.click.click.data.api.postScheduledMessage
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.ScheduledMessage
import compose.project.click.click.util.isPersistedApiChatId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Send Later: text is encrypted now (same [OutboundWire] as a live send, so the delivered row is
 * identical to one sent at `send_at`) and delivered server-side by the per-minute cron.
 * Only `reply_to_id` rides in metadata — never a plaintext excerpt, which would sit on the server
 * for up to a year. Mirrors iOS `ChatRepository.scheduleMessage`.
 */

private fun sessionExpired() = IllegalStateException("Session expired. Sign in again.")

internal suspend fun SupabaseChatRepository.scheduleMessageImpl(
    chatId: String,
    userId: String,
    content: String,
    replyToId: String?,
    sendAtEpochMs: Long,
    connectionId: String?,
): Result<ScheduledMessage> =
    runCatching {
        val wire =
            prepareOutboundWire(
                chatId = chatId,
                userId = userId,
                content = content,
                messageType = ChatMessageType.TEXT,
                metadata = replyToId?.takeIf { it.isNotBlank() }?.let { buildJsonObject { put("reply_to_id", it) } },
            )
        val token = ensureFreshJwtForChat() ?: throw sessionExpired()
        val resolvedConnectionId = connectionId ?: ChatSessionCaches.peekConnectionIdForChat(chatId)

        suspend fun post() =
            apiClient.postScheduledMessage(
                chatId = chatId,
                connectionId = resolvedConnectionId,
                userId = userId,
                content = wire.content,
                messageType = ChatMessageType.TEXT,
                metadata = wire.metadata,
                sendAtMs = sendAtEpochMs,
                authToken = token,
            )
        var result = post()
        if (result.exceptionOrNull()?.isE2eeV2Required() == true) {
            wire.refreshAfterV2Required()
            result = post()
        }
        val row = result.getOrThrow()
        ScheduledMessage(id = row.id, content = content, sendAtEpochMs = row.sendAt)
    }

/**
 * The viewer's pending messages for [chatId], decrypted locally. Decryption never upgrades or
 * rotates the chat's E2EE session (listing must not have lifecycle side effects).
 */
internal suspend fun SupabaseChatRepository.fetchScheduledMessagesImpl(
    chatId: String,
    userId: String,
): Result<List<ScheduledMessage>> =
    runCatching {
        if (!isPersistedApiChatId(chatId)) return@runCatching emptyList()
        val token = ensureFreshJwtForChat() ?: throw sessionExpired()
        val rows = apiClient.getScheduledMessages(chatId, token).getOrThrow()
        if (rows.isEmpty()) return@runCatching emptyList()
        val crypto = resolveChatCrypto(chatId, userId)
        rows
            .map { row ->
                val decrypted =
                    decryptMessage(
                        Message(
                            id = row.id,
                            user_id = userId,
                            content = row.content,
                            timeCreated = row.sendAt,
                            messageType = row.messageType,
                            metadata = row.metadata,
                        ),
                        crypto,
                    )
                ScheduledMessage(id = row.id, content = decrypted.content, sendAtEpochMs = row.sendAt)
            }.sortedBy { it.sendAtEpochMs }
    }

internal suspend fun SupabaseChatRepository.cancelScheduledMessageImpl(id: String): Result<Unit> =
    runCatching {
        val token = ensureFreshJwtForChat() ?: throw sessionExpired()
        apiClient.deleteScheduledMessage(id, token).getOrThrow()
    }
