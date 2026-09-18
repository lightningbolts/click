package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.EVENT_HUB_ACCESS_DENIED_MARKER // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun HubChatViewModel.startHubReplyImpl(target: MessageWithUser) {
    _editingMessageId.value = null
    _replyingTo.value = target
}

internal fun HubChatViewModel.cancelHubReplyImpl() {
    _replyingTo.value = null
}

internal fun HubChatViewModel.startHubEditImpl(target: MessageWithUser) {
    if (!target.isSent || target.message.messageType.lowercase() != ChatMessageType.TEXT) return
    _replyingTo.value = null
    _editingMessageId.value = target.message.id
    _draft.value = target.message.content
}

internal fun HubChatViewModel.cancelHubEditImpl() {
    _editingMessageId.value = null
    _draft.value = ""
}

internal fun HubChatViewModel.toggleHubReactionImpl(
    messageId: String,
    reactionType: String,
) {
    val trimmedId = messageId.trim()
    val emoji = reactionType.trim()
    if (trimmedId.isEmpty() || trimmedId.startsWith("temp-") || emoji.isEmpty()) return

    val before = _messageReactions.value[trimmedId].orEmpty()
    val existing = before.firstOrNull { it.userId == currentUserId && it.reactionType == emoji }

    var optimisticId: String? = null
    if (existing != null) {
        _messageReactions.value =
            _messageReactions.value.toMutableMap().apply {
                this[trimmedId] = before.filterNot { it.id == existing.id }
            }
    } else {
        val optimistic =
            MessageReaction(
                id = "temp-$trimmedId-$emoji",
                messageId = trimmedId,
                userId = currentUserId,
                reactionType = emoji,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        optimisticId = optimistic.id
        _messageReactions.value =
            _messageReactions.value.toMutableMap().apply {
                this[trimmedId] =
                    before.filterNot { it.userId == currentUserId && it.reactionType == emoji } + optimistic
            }
    }

    viewModelScope.launch {
        try {
            val location = resolveGatekeeperLocationOrThrow()
            val jwt = requireFreshHubJwt()
            if (existing != null) {
                chatApi
                    .removeHubReaction(
                        hubId,
                        trimmedId,
                        emoji,
                        location.latitude,
                        location.longitude,
                        jwt,
                    ).getOrThrow()
            } else {
                val canonical =
                    chatApi
                        .addHubReaction(
                            hubId,
                            trimmedId,
                            emoji,
                            location.latitude,
                            location.longitude,
                            jwt,
                        ).getOrThrow()
                if (canonical != null) {
                    val reaction = canonical.toMessageReaction()
                    val current = _messageReactions.value[trimmedId].orEmpty()
                    _messageReactions.value =
                        _messageReactions.value.toMutableMap().apply {
                            this[trimmedId] =
                                current
                                    .filterNot {
                                        it.userId == reaction.userId &&
                                            it.reactionType == reaction.reactionType
                                    } + reaction
                        }
                }
            }
            persistHubMessagesToDisk(_messages.value)
        } catch (e: Exception) {
            val current = _messageReactions.value[trimmedId].orEmpty()
            val rolledBack =
                rollbackHubReactionMutation(
                    current = current,
                    optimisticId = optimisticId,
                    removedExisting = existing,
                    restoreRemovedExisting =
                        existing == null || existing.id !in realtimeDeletedReactionIds,
                )
            _messageReactions.value =
                _messageReactions.value.toMutableMap().apply {
                    if (rolledBack.isEmpty()) remove(trimmedId) else this[trimmedId] = rolledBack
                }
            persistHubMessagesToDisk(_messages.value)
            handleHubInteractionFailure(e, "Could not update reaction")
        }
    }
}

internal fun HubChatViewModel.confirmHubEditImpl(messageId: String) {
    if (_isSending.value) return
    val target =
        _messages.value.firstOrNull {
            it.message.id == messageId &&
                it.isSent &&
                it.message.messageType.lowercase() == ChatMessageType.TEXT
        } ?: return
    val newContent = _draft.value.trim()
    if (newContent.isEmpty()) return

    viewModelScope.launch {
        _isSending.value = true
        _sendError.value = null
        try {
            val location = resolveGatekeeperLocationOrThrow()
            val jwt = requireFreshHubJwt()
            withHubE2eeV2SendSession { e2ee ->
                val clientMessageId = e2ee?.let { MessageCryptoV2.generateClientMessageId() }
                val outgoingBody =
                    if (e2ee != null) {
                        MessageCryptoV2.encryptMessage(
                            metadata =
                                MessageCryptoV2.MessageMetadata(
                                    chatId = hubId,
                                    epoch = e2ee.epoch,
                                    senderDeviceId = e2ee.senderDeviceId,
                                    clientMessageId = clientMessageId!!,
                                ),
                            epochKey = e2ee.epochKey,
                            plaintext = newContent,
                            replayGuard = e2ee.replayGuard,
                        )
                    } else {
                        newContent
                    }
                val existingMetadata = target.message.metadata as? JsonObject
                val staleCryptoKeys =
                    setOf(
                        "crypto_version",
                        "cryptoVersion",
                        "epoch",
                        "sender_device_id",
                        "senderDeviceId",
                        "client_message_id",
                        "clientMessageId",
                    )
                val metadata =
                    buildJsonObject {
                        existingMetadata?.forEach { (key, value) ->
                            if (e2ee != null || key !in staleCryptoKeys) put(key, value)
                        }
                        if (e2ee != null) {
                            put("crypto_version", MessageCryptoV2.CRYPTO_VERSION)
                            put("epoch", e2ee.epoch)
                            put("sender_device_id", e2ee.senderDeviceId)
                            put("client_message_id", clientMessageId!!)
                        }
                    }.takeIf { it.isNotEmpty() }

                val dto =
                    chatApi
                        .editHubMessage(
                            hubId = hubId,
                            messageId = messageId,
                            body = outgoingBody,
                            metadata = metadata,
                            userLat = location.latitude,
                            userLong = location.longitude,
                            authToken = jwt,
                        ).getOrThrow()
                val refreshed = rowToMessageWithUser(dto.toHubMessageRow())
                val next =
                    _messages.value.map { current ->
                        if (current.message.id == messageId) {
                            refreshed.copy(user = current.user, isSent = true)
                        } else {
                            current
                        }
                    }
                _messages.value = next
                persistHubMessagesToDisk(next)
            }
            _editingMessageId.value = null
            _draft.value = ""
        } catch (e: Exception) {
            handleHubInteractionFailure(e, "Could not edit message")
        } finally {
            _isSending.value = false
        }
    }
}

internal fun HubChatViewModel.deleteHubMessageImpl(messageId: String) {
    val id = messageId.trim()
    if (id.isEmpty() || id.startsWith("temp-")) return
    val beforeMessages = _messages.value
    val removedIndex = beforeMessages.indexOfFirst { it.message.id == id }
    val removed = beforeMessages.getOrNull(removedIndex) ?: return
    if (!removed.isSent) return
    pendingHubMessageDeletes[id] =
        PendingHubMessageDelete(
            removed = removed,
            index = removedIndex,
        )
    _messages.value = beforeMessages.filterNot { it.message.id == id }
    if (_replyingTo.value?.message?.id == id) _replyingTo.value = null
    if (_editingMessageId.value == id) cancelHubEditImpl()
    persistHubMessagesToDisk(_messages.value)

    viewModelScope.launch {
        try {
            val location = resolveGatekeeperLocationOrThrow()
            val jwt = requireFreshHubJwt()
            chatApi
                .deleteHubMessage(
                    hubId = hubId,
                    messageId = id,
                    userLat = location.latitude,
                    userLong = location.longitude,
                    authToken = jwt,
                ).getOrThrow()
            pendingHubMessageDeletes.remove(id)
            _messageReactions.value = _messageReactions.value - id
            persistHubMessagesToDisk(_messages.value)
        } catch (e: Exception) {
            val pending = pendingHubMessageDeletes.remove(id)
            if (pending != null && _messages.value.none { it.message.id == id }) {
                val restored = _messages.value.toMutableList()
                restored.add(
                    pending.index.coerceIn(0, restored.size),
                    pending.rollbackMessage(),
                )
                _messages.value = restored
            }
            persistHubMessagesToDisk(_messages.value)
            handleHubInteractionFailure(e, "Could not delete message")
        }
    }
}

internal fun HubChatViewModel.handleHubInteractionFailure(
    error: Throwable,
    fallback: String,
) {
    val raw = error.message.orEmpty()
    when {
        HubChatViewModel.isHubExpired(error) ||
            raw.contains(EVENT_HUB_ACCESS_DENIED_MARKER) ||
            raw.contains("NOT_A_PARTICIPANT") -> {
            participantDenied = true
            _sendError.value =
                if (HubChatViewModel.isHubExpired(error)) HubChatViewModel.HUB_EXPIRED_MESSAGE else HUB_ACCESS_REVOKED_MESSAGE
            _realtimeState.value = HubRealtimeState.Error(_sendError.value ?: fallback)
            clearLocalHubState(clearDiskCache = true)
            navigationEventChannel.trySend(HubChatNavigationEvent.PopBackToConnections)
        }
        HubChatViewModel.isHubOutOfRange(error) && !_isEventHub.value -> {
            _outOfBounds.value = true
            _sendError.value = HubChatViewModel.HUB_OUT_OF_RANGE_MESSAGE
        }
        else -> {
            _sendError.value = error.redactedRestMessage().ifBlank { fallback }
        }
    }
}

private const val HUB_ACCESS_REVOKED_MESSAGE = "You no longer have access to this hub."
