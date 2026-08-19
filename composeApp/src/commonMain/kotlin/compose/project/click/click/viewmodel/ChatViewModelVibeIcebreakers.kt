@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.chat.attachments.ChatAttachmentValidator // pragma: allowlist secret
import compose.project.click.click.collaboration.computeClickDropRevealTtlIso
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerPrompt // pragma: allowlist secret
import compose.project.click.click.data.models.IcebreakerRepository // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.audioCacheFileExtension // pragma: allowlist secret
import compose.project.click.click.data.models.collapseOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.data.models.hasLocalMediaUri // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMessage // pragma: allowlist secret
import compose.project.click.click.data.models.toBeaconChatContent
import compose.project.click.click.data.models.toBeaconChatMetadata
import compose.project.click.click.data.models.withCoercedBeaconType
import compose.project.click.click.data.models.withDbDerivedDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator
import compose.project.click.click.data.repository.ChatMessageSubscription // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRealtimeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatSessionCaches
import compose.project.click.click.data.repository.MessageChangeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.ReactionChangeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import compose.project.click.click.network.ConnectivityMonitor
import compose.project.click.click.network.NetworkConnectivityMonitor
import compose.project.click.click.notifications.ChatPushInboxBridge
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveDecryptedAttachmentToDownloads // pragma: allowlist secret
import compose.project.click.click.ui.chat.secureChatImageBitmapCache // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetLocalMessage // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultExtensionForMessage // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneChatsByPeer
import compose.project.click.click.util.fileUriToLocalPath // pragma: allowlist secret
import compose.project.click.click.util.imageVaultFileExtension // pragma: allowlist secret
import compose.project.click.click.util.isChatMediaVaultLocalPath // pragma: allowlist secret
import compose.project.click.click.util.isOfflineNetworkFailure
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.isPersistedApiUuid
import compose.project.click.click.util.readChatMediaVaultBytesForMessage // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultLocalPathForMessage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.util.vaultCacheExtension // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

internal fun ChatViewModel.startVibeCheckTimer(
    connection: Connection,
    userId: String,
) {
    vibeCheckTimerJob?.cancel()
    if (connection.isMutuallyKept()) {
        _connectionKept.value = true
        _vibeCheckExpired.value = false
        _vibeCheckRemainingMs.value = 0L
        return
    }

    vibeCheckTimerJob =
        viewModelScope.launch {
            while (true) {
                val now = Clock.System.now().toEpochMilliseconds()
                val remainingMs = connection.getVibeCheckRemainingMs(now)
                _vibeCheckRemainingMs.value = remainingMs
                if (remainingMs == 0L) {
                    handleVibeCheckExpiry(connection, userId)
                    break
                }
                delay(1000L)
            }
        }
}

internal fun ChatViewModel.updateKeepStates(
    connection: Connection,
    userId: String,
) {
    val userIndex = connection.getUserIndex(userId)
    val otherUserIndex = if (userIndex == 0) 1 else 0
    if (userIndex != null && connection.should_continue.size >= 2) {
        _currentUserHasKept.value = connection.should_continue[userIndex]
        _otherUserHasKept.value = connection.should_continue[otherUserIndex]
    } else {
        _currentUserHasKept.value = false
        _otherUserHasKept.value = false
    }
    _connectionKept.value = connection.isMutuallyKept()
}

internal fun ChatViewModel.keepConnectionImpl() {
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val currentState = _chatMessagesState.value
    if (currentState !is ChatMessagesState.Success) return
    val connection = currentState.chatDetails.connection
    viewModelScope.launch {
        val success =
            if (vibeCheckEnabled) {
                supabaseRepository.updateUserKeepDecision(
                    connectionId = connectionId,
                    userId = userId,
                    keepConnection = true,
                    currentShouldContinue = connection.should_continue,
                    userIds = connection.user_ids,
                )
            } else {
                supabaseRepository.updateConnectionExpiryState(connectionId, "kept")
            }

        if (success) {
            _currentUserHasKept.value = true
            updateConnectionState(connectionId) { it.copy(expiry_state = "kept", status = "kept") }
            if (!vibeCheckEnabled) {
                _connectionKept.value = true
                vibeCheckTimerJob?.cancel()
                loadChats(isForced = true)
                return@launch
            }

            val otherUserIndex = if (connection.getUserIndex(userId) == 0) 1 else 0
            if (connection.should_continue.getOrNull(otherUserIndex) == true) {
                _connectionKept.value = true
                vibeCheckTimerJob?.cancel()
                supabaseRepository.updateConnectionExpiryState(connectionId, "kept")
            }
            refreshConnectionState(connectionId, userId)
        }
    }
}

internal suspend fun ChatViewModel.refreshConnectionState(
    chatId: String,
    userId: String,
) {
    val connection = supabaseRepository.fetchConnectionById(chatId) ?: return
    updateKeepStates(connection, userId)
    if (connection.isMutuallyKept()) {
        _connectionKept.value = true
        vibeCheckTimerJob?.cancel()
    }
}

internal suspend fun ChatViewModel.handleVibeCheckExpiry(
    connection: Connection,
    userId: String,
) {
    _vibeCheckExpired.value = true
    val latestConnection = supabaseRepository.fetchConnectionById(connection.id)
    if (latestConnection != null && latestConnection.isMutuallyKept()) {
        _connectionKept.value = true
    } else {
        _connectionKept.value = false
    }
}

/**
 * Refresh chats after a connection disappears from the client (e.g. archived server-side).
 * Idle archival is handled by [expire-connections] and [connection_archives].
 */
internal fun ChatViewModel.handleExpiredConnectionDismissImpl() {
    if (_currentUserId.value == null) return
    viewModelScope.launch {
        loadChats(isForced = true)
    }
}

internal fun ChatViewModel.resetVibeCheckState() {
    vibeCheckTimerJob?.cancel()
    vibeCheckTimerJob = null
    _vibeCheckRemainingMs.value = 0L
    _currentUserHasKept.value = false
    _otherUserHasKept.value = false
    _vibeCheckExpired.value = false
    _connectionKept.value = false
}

internal fun ChatViewModel.loadIcebreakerPrompts(contextTag: String?) {
    _icebreakerPrompts.value = IcebreakerRepository.getPromptsForContext(contextTag, count = 3)
}

internal fun ChatViewModel.icebreakerCooldownRemainingSecCeil(endEpochMs: Long): Int =
    ((endEpochMs - Clock.System.now().toEpochMilliseconds() + 999L) / 1000L).toInt().coerceAtLeast(0)

/** 15s cooldown after a successful in-chat prompt refresh or list icebreaker send. */

/** 15s cooldown after a successful in-chat prompt refresh or list icebreaker send. */
internal fun ChatViewModel.armIcebreakerCooldown() {
    icebreakerCooldownTickerJob?.cancel()
    val end = Clock.System.now().toEpochMilliseconds() + 15_000L
    _icebreakerCooldownRemainingSec.value = icebreakerCooldownRemainingSecCeil(end)
    icebreakerCooldownTickerJob =
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val rem = icebreakerCooldownRemainingSecCeil(end)
                _icebreakerCooldownRemainingSec.value = rem
                if (rem <= 0) break
            }
            _icebreakerCooldownRemainingSec.value = 0
        }
}

internal fun ChatViewModel.refreshIcebreakerPromptsImpl() {
    val currentState = _chatMessagesState.value
    if (currentState !is ChatMessagesState.Success) return
    if (_icebreakerCooldownRemainingSec.value > 0) return
    val now = Clock.System.now().toEpochMilliseconds()
    if (now - lastIcebreakerRefreshInvokedMs < 350L) return
    lastIcebreakerRefreshInvokedMs = now
    loadIcebreakerPrompts(currentState.chatDetails.connection.context_tag)
    armIcebreakerCooldown()
}

internal fun ChatViewModel.useIcebreakerPromptImpl(prompt: IcebreakerPrompt) {
    _messageInput.value = prompt.text
    _showIcebreakerPanel.value = false
}

internal fun ChatViewModel.dismissIcebreakerPanelImpl() {
    _showIcebreakerPanel.value = false
}

/**
 * Send a nudge message to the current chat.
 * Works from any screen that has access to the chat details.
 */
internal fun ChatViewModel.sendNudgeImpl() {
    val currentState = _chatMessagesState.value
    if (currentState !is ChatMessagesState.Success) return
    val connectionId = currentState.chatDetails.connection.id
    val userId = _currentUserId.value ?: return
    val currentUser = compose.project.click.click.data.AppDataManager.currentUser.value ?: return
    val otherUserName = currentState.chatDetails.otherUser.name ?: "them"
    viewModelScope.launch {
        val chatId = resolveOrCreateApiChatId(connectionId) ?: return@launch
        val msg =
            chatRepository.sendMessage(
                chatId = chatId,
                userId = userId,
                content = "👋 ${currentUser.name ?: "Someone"} nudged you!",
            )
        _nudgeResult.value = if (msg != null) "Nudge sent to $otherUserName! 👋" else "Failed to send nudge"
    }
}

/**
 * Send a nudge to an explicit chat — usable from Home or Connections list
 * without needing to open the full chat view.
 */
internal fun ChatViewModel.sendNudgeToChatImpl(
    chatId: String,
    otherUserName: String,
) {
    val userId = _currentUserId.value ?: return
    val currentUser = compose.project.click.click.data.AppDataManager.currentUser.value ?: return
    viewModelScope.launch {
        val msg =
            chatRepository.sendMessage(
                chatId = chatId,
                userId = userId,
                content = "👋 ${currentUser.name ?: "Someone"} nudged you!",
            )
        _nudgeResult.value = if (msg != null) "Nudge sent to $otherUserName! 👋" else "Failed to send nudge"
    }
}

/**
 * Send one contextual icebreaker from the Clicks list archive banner (matches home poll-pair behavior).
 */
internal fun ChatViewModel.sendArchiveBannerIcebreakerImpl(
    connectionId: String,
    otherDisplayName: String,
) {
    val userId = _currentUserId.value ?: return
    val name = otherDisplayName.trim().ifBlank { "them" }
    viewModelScope.launch {
        archiveBannerIcebreakerMutex.withLock {
            if (_icebreakerCooldownRemainingSec.value > 0) {
                _nudgeResult.value = "Icebreaker on cooldown — ${_icebreakerCooldownRemainingSec.value}s"
            } else {
                try {
                    val details = chatRepository.fetchChatWithDetails(connectionId, userId)
                    val chatId = details?.chat?.id ?: resolveOrCreateApiChatId(connectionId)
                    if (chatId == null) {
                        _nudgeResult.value = "Couldn't open chat"
                    } else {
                        val contextTag = details?.connection?.context_tag
                        val prompt =
                            IcebreakerRepository.getPromptsForContext(contextTag, count = 1).firstOrNull()
                                ?: IcebreakerRepository.getRandomPrompt()
                        val msg = chatRepository.sendMessage(chatId, userId, prompt.text)
                        if (msg != null) {
                            _nudgeResult.value = "Icebreaker sent to $name!"
                            armIcebreakerCooldown()
                        } else {
                            _nudgeResult.value = "Failed to send icebreaker"
                        }
                    }
                } catch (_: Exception) {
                    _nudgeResult.value = "Failed to send icebreaker"
                }
            }
        }
    }
}

internal fun ChatViewModel.clearNudgeResultImpl() {
    _nudgeResult.value = null
}

internal fun ChatViewModel.resetIcebreakerState() {
    icebreakerCooldownTickerJob?.cancel()
    icebreakerCooldownTickerJob = null
    _icebreakerCooldownRemainingSec.value = 0
    lastIcebreakerRefreshInvokedMs = 0L
    _icebreakerPrompts.value = emptyList()
    _showIcebreakerPanel.value = true
}
