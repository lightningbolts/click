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

    val key = HubReactionMutationKey(trimmedId, emoji)
    val state =
        hubReactionMutationStates.getOrPut(key) {
            val existing =
                _messageReactions.value[trimmedId]
                    .orEmpty()
                    .firstOrNull { it.userId == currentUserId && it.reactionType == emoji }
            HubReactionMutationState(
                desiredEnabled = existing != null,
                acknowledgedEnabled = existing != null,
                canonicalReaction = existing?.takeUnless { it.id.startsWith("temp-") },
            )
        }

    state.toggleIntent()
    reconcileHubReactionIntent(key, state)
    persistHubMessagesToDisk(_messages.value)
    ensureHubReactionMutationWorker(key)
}

internal fun HubChatViewModel.reconcileHubReactionIntent(
    key: HubReactionMutationKey,
    state: HubReactionMutationState,
) {
    val currentMap = _messageReactions.value.toMutableMap()
    val rows = currentMap[key.messageId].orEmpty()
    val currentOwn =
        rows.firstOrNull {
            it.userId == currentUserId &&
                it.reactionType == key.reactionType
        }
    val withoutOwn =
        rows.filterNot {
            it.userId == currentUserId &&
                it.reactionType == key.reactionType
        }

    val nextRows =
        if (state.desiredEnabled) {
            val reaction =
                state.canonicalReaction
                    ?: currentOwn
                    ?: MessageReaction(
                        id = "temp-${key.messageId}-${key.reactionType}-${state.generation}",
                        messageId = key.messageId,
                        userId = currentUserId,
                        reactionType = key.reactionType,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    )
            withoutOwn + reaction
        } else {
            withoutOwn
        }

    if (nextRows.isEmpty()) {
        currentMap.remove(key.messageId)
    } else {
        currentMap[key.messageId] = nextRows
    }
    _messageReactions.value = currentMap
}

internal fun HubChatViewModel.reconcilePendingHubReactionIntents() {
    hubReactionMutationStates.forEach { (key, state) ->
        reconcileHubReactionIntent(key, state)
    }
}

internal fun HubChatViewModel.reconcileHubReactionRealtimeAgainstPending(
    event: HubReactionRealtimeEvent,
): Boolean {
    val key =
        when (event) {
            is HubReactionRealtimeEvent.Upsert -> {
                if (event.reaction.userId != currentUserId) return true
                HubReactionMutationKey(event.reaction.messageId, event.reaction.reactionType)
            }
            is HubReactionRealtimeEvent.Delete -> {
                if (event.userId != currentUserId) return true
                val messageId = event.messageId ?: return true
                val reactionType = event.reactionType?.takeIf { it.isNotBlank() } ?: return true
                HubReactionMutationKey(messageId, reactionType)
            }
        }
    val state = hubReactionMutationStates[key] ?: return true

    val eventMatchesDesired =
        when (event) {
            is HubReactionRealtimeEvent.Upsert ->
                state.observeAcknowledged(
                    enabled = true,
                    canonical = event.reaction,
                )
            is HubReactionRealtimeEvent.Delete ->
                state.observeAcknowledged(
                    enabled = false,
                    canonical = null,
                )
        }

    if (!state.workerRunning && state.desiredEnabled != state.acknowledgedEnabled) {
        ensureHubReactionMutationWorker(key)
    }
    return eventMatchesDesired
}

internal fun HubChatViewModel.ensureHubReactionMutationWorker(key: HubReactionMutationKey) {
    val state = hubReactionMutationStates[key] ?: return
    if (state.workerRunning) return
    state.workerRunning = true

    viewModelScope.launch {
        try {
            while (true) {
                val currentState = hubReactionMutationStates[key] ?: break
                if (currentState.desiredEnabled == currentState.acknowledgedEnabled) break

                val targetEnabled = currentState.desiredEnabled
                val requestGeneration = currentState.generation
                val canonicalIdBeforeRequest = currentState.canonicalReaction?.id

                try {
                    val location = resolveGatekeeperLocationOrThrow()
                    val jwt = requireFreshHubJwt()
                    val canonical =
                        if (targetEnabled) {
                            chatApi
                                .addHubReaction(
                                    hubId,
                                    key.messageId,
                                    key.reactionType,
                                    location.latitude,
                                    location.longitude,
                                    jwt,
                                ).getOrThrow()
                                ?.toMessageReaction()
                        } else {
                            chatApi
                                .removeHubReaction(
                                    hubId,
                                    key.messageId,
                                    key.reactionType,
                                    location.latitude,
                                    location.longitude,
                                    jwt,
                                ).getOrThrow()
                            null
                        }

                    val latest = hubReactionMutationStates[key] ?: break
                    latest.observeAcknowledged(
                        enabled = targetEnabled,
                        canonical = canonical,
                    )
                    reconcileHubReactionIntent(key, latest)
                    persistHubMessagesToDisk(_messages.value)
                } catch (e: Exception) {
                    val latest = hubReactionMutationStates[key] ?: break
                    val realtimeConfirmed =
                        latest.acknowledgedEnabled == targetEnabled ||
                            (
                                !targetEnabled &&
                                    canonicalIdBeforeRequest != null &&
                                    canonicalIdBeforeRequest in realtimeDeletedReactionIds
                            )
                    if (realtimeConfirmed) {
                        latest.observeAcknowledged(
                            enabled = targetEnabled,
                            canonical = if (targetEnabled) latest.canonicalReaction else null,
                        )
                        reconcileHubReactionIntent(key, latest)
                        persistHubMessagesToDisk(_messages.value)
                        continue
                    }

                    val terminalFailure =
                        HubChatViewModel.isHubExpired(e) ||
                            HubChatViewModel.isHubOutOfRange(e) ||
                            e.message.orEmpty().contains(EVENT_HUB_ACCESS_DENIED_MARKER) ||
                            e.message.orEmpty().contains("NOT_A_PARTICIPANT")
                    if (latest.generation == requestGeneration || terminalFailure) {
                        latest.desiredEnabled = latest.acknowledgedEnabled
                        reconcileHubReactionIntent(key, latest)
                        persistHubMessagesToDisk(_messages.value)
                        handleHubInteractionFailure(e, "Could not update reaction")
                        break
                    }

                    // A newer tap superseded the failed request. Keep the latest
                    // optimistic intent and let the loop converge the server to it.
                    reconcileHubReactionIntent(key, latest)
                    persistHubMessagesToDisk(_messages.value)
                }
            }
        } finally {
            val latest = hubReactionMutationStates[key]
            if (latest != null) {
                latest.workerRunning = false
                if (latest.desiredEnabled == latest.acknowledgedEnabled) {
                    hubReactionMutationStates.remove(key)
                } else {
                    ensureHubReactionMutationWorker(key)
                }
            }
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
