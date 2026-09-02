@file:Suppress(
    "ktlint:standard:no-consecutive-comments",
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import compose.project.click.click.ui.components.ProfileSheetLocalMessage // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal fun ChatViewModel.notifyVerifiedCliqueSelectionBlockedImpl() {
    _nudgeResult.value = "That friend isn't connected to everyone already selected."
}

/**
 * Snapshot of the current chat's locally-decrypted messages shaped for the
 * [ProfileBottomSheet] Media / Files / Links tabs. Returns an empty list
 * when no chat is loaded or messages haven't been fetched yet.
 */
internal fun ChatViewModel.currentChatLocalMessagesImpl(): List<ProfileSheetLocalMessage> {
    val state = _chatMessagesState.value
    if (state !is ChatMessagesState.Success) return emptyList()
    return state.messages.map { mwu ->
        val m = mwu.message
        ProfileSheetLocalMessage(
            id = m.id,
            content = m.content,
            messageType = m.messageType,
            timestamp =
                kotlinx.datetime.Instant
                    .fromEpochMilliseconds(m.timeCreated)
                    .toString(),
            metadata = m.metadata,
        )
    }
}

/**
 * True when the unordered member set (including caller) forms a fully connected graph on the server.
 */
internal suspend fun ChatViewModel.memberSetSatisfiesVerifiedCliqueGraphImpl(memberUserIds: List<String>): Boolean =
    chatRepository.verifiedCliqueEdgesExist(memberUserIds)

/**
 * Which [candidateUserIds] can join [baseMemberUserIds] while preserving a verified clique.
 * Selected candidates stay enabled so users can deselect them while eligibility recalculates.
 */

/**
 * Which [candidateUserIds] can join [baseMemberUserIds] while preserving a verified clique.
 * Selected candidates stay enabled so users can deselect them while eligibility recalculates.
 */
internal suspend fun ChatViewModel.computeVerifiedCliqueAddableMaskImpl(
    baseMemberUserIds: List<String>,
    candidateUserIds: List<String>,
    selectedCandidateIds: Set<String>,
): Map<String, Boolean> =
    coroutineScope {
        candidateUserIds
            .map { candidateId ->
                async {
                    val ok =
                        if (candidateId in selectedCandidateIds) {
                            true
                        } else {
                            memberSetSatisfiesVerifiedCliqueGraph(
                                (baseMemberUserIds + selectedCandidateIds + candidateId).distinct().sorted(),
                            )
                        }
                    candidateId to ok
                }
            }.map { it.await() }
            .toMap()
    }

internal fun ChatViewModel.buildInitialVerifiedCliqueDisplayName(
    memberUserIds: List<String>,
    currentUserId: String,
): String {
    val ordered = memberUserIds.distinct().sorted()
    return ordered.joinToString(", ") { uid ->
        val u =
            when {
                uid == currentUserId -> AppDataManager.currentUser.value
                else -> AppDataManager.getConnectedUser(uid)
            }
        u?.firstName?.trim()?.takeIf { it.isNotEmpty() }
            ?: u
                ?.name
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.takeIf { it.isNotEmpty() }
            ?: "Friend"
    }
}

/**
 * Creates a verified group chat ("click") with [selectedFriendUserIds] (excluding self; self is merged in).
 */
internal fun ChatViewModel.createVerifiedCliqueImpl(
    selectedFriendUserIds: List<String>,
    onResult: (Result<String>) -> Unit,
) {
    val userId =
        _currentUserId.value ?: run {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
    val members = (selectedFriendUserIds + userId).distinct().sorted()
    if (members.size < 2) {
        onResult(Result.failure(IllegalArgumentException("Pick at least one friend")))
        return
    }
    val memberSet = members.toSet()
    val alreadyHaveClick =
        (_chatListState.value as? ChatListState.Success)?.chats?.any { chat ->
            chat.groupClique != null && chat.groupClique.memberUserIds.toSet() == memberSet
        } == true
    if (alreadyHaveClick) {
        onResult(
            Result.failure(
                IllegalStateException("verified click already exists for this member set"),
            ),
        )
        return
    }
    viewModelScope.launch {
        try {
            AppDataManager.refresh(force = true)
            // Allow the forced refresh to settle so key-wrap sees current edges.
            delay(450)
            val connections = AppDataManager.connections.value
            val initialName = buildInitialVerifiedCliqueDisplayName(members, userId)
            val rpc =
                VerifiedCliqueCreation.createVerifiedCliqueWithWrappedKeys(
                    chatRepository = chatRepository,
                    connections = connections,
                    currentUserId = userId,
                    memberUserIds = members,
                    initialGroupName = initialName,
                )
            val payload =
                rpc.getOrElse { err ->
                    onResult(Result.failure(err))
                    return@launch
                }
            val chatId = chatRepository.resolveChatIdForGroupId(payload.groupId)
            if (chatId == null) {
                loadChats(isForced = true)
                onResult(
                    Result.failure(
                        IllegalStateException(
                            "Click created but chat is not ready yet. Pull to refresh.",
                        ),
                    ),
                )
                return@launch
            }
            chatRepository.cacheGroupMasterKey(chatId, payload.masterKey32)
            loadChats(isForced = true)
            PlatformHapticsPolicy.successNotification()
            onResult(Result.success(payload.groupId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }
}

internal fun ChatViewModel.leaveVerifiedCliqueImpl(
    groupId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        chatRepository.clearChatListLocalCaches()
        val ok = chatRepository.leaveClique(groupId).isSuccess
        if (ok) {
            if (currentConnectionId == groupId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            _nudgeResult.value = "You left the group"
        } else {
            _nudgeResult.value = "Could not leave group"
        }
        onComplete(ok)
    }
}

internal fun ChatViewModel.deleteVerifiedCliqueImpl(
    groupId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        chatRepository.clearChatListLocalCaches()
        val ok = chatRepository.deleteClique(groupId).isSuccess
        if (ok) {
            if (currentConnectionId == groupId) {
                leaveChatRoom()
            }
            loadChats(isForced = true)
            _nudgeResult.value = "Group deleted"
        } else {
            _nudgeResult.value = "Could not delete group"
        }
        onComplete(ok)
    }
}

internal fun ChatViewModel.addMemberToVerifiedCliqueImpl(
    groupId: String,
    newMemberUserId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    addMembersToVerifiedClique(groupId, listOf(newMemberUserId), onComplete = { ok, _ -> onComplete(ok) })
}

internal fun ChatViewModel.addMembersToVerifiedCliqueImpl(
    groupId: String,
    newMemberUserIds: List<String>,
    onComplete: (Boolean, Int) -> Unit = { _, _ -> },
) {
    viewModelScope.launch {
        if (groupId.isBlank() || newMemberUserIds.isEmpty()) {
            onComplete(false, 0)
            return@launch
        }
        var added = 0
        var lastError: String? = null
        for (memberId in newMemberUserIds.distinct().filter { it.isNotBlank() }) {
            val result = chatRepository.addCliqueMember(groupId, memberId)
            if (result.isSuccess) {
                added++
            } else {
                lastError = formatAddCliqueMemberError(result.exceptionOrNull()?.message)
            }
        }
        if (added > 0) {
            PlatformHapticsPolicy.successNotification()
            chatRepository.clearChatListLocalCaches()
            loadChats(isForced = true)
            val state = _chatMessagesState.value as? ChatMessagesState.Success
            val connectionId =
                state
                    ?.chatDetails
                    ?.groupClique
                    ?.takeIf { it.groupId == groupId }
                    ?.let { state.chatDetails.connection.id }
                    ?: groupId
            loadChatMessages(connectionId)
            _nudgeResult.value =
                when {
                    added == 1 && newMemberUserIds.size == 1 -> "Member added"
                    lastError != null -> "Added $added member(s); some could not be added"
                    added == 1 -> "Member added"
                    else -> "Added $added members"
                }
            onComplete(true, added)
        } else {
            _nudgeResult.value = lastError ?: "Could not add members"
            onComplete(false, 0)
        }
    }
}

internal fun ChatViewModel.removeMemberFromVerifiedCliqueImpl(
    groupId: String,
    memberUserId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        if (groupId.isBlank() || memberUserId.isBlank()) {
            onComplete(false)
            return@launch
        }
        val ok = chatRepository.removeCliqueMember(groupId, memberUserId).isSuccess
        if (ok) {
            chatRepository.clearChatListLocalCaches()
            loadChats(isForced = true)
            val state = _chatMessagesState.value as? ChatMessagesState.Success
            val connectionId =
                state
                    ?.chatDetails
                    ?.groupClique
                    ?.takeIf { it.groupId == groupId }
                    ?.let { state.chatDetails.connection.id }
                    ?: groupId
            loadChatMessages(connectionId)
            _nudgeResult.value = "Member removed"
        } else {
            _nudgeResult.value = "Could not remove member"
        }
        onComplete(ok)
    }
}

internal fun ChatViewModel.renameVerifiedCliqueImpl(
    groupId: String,
    newName: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            onComplete(false)
            return@launch
        }
        val ok = chatRepository.renameClique(groupId, trimmed).isSuccess
        if (ok) {
            val cur = _chatMessagesState.value as? ChatMessagesState.Success
            val gc = cur?.chatDetails?.groupClique
            if (cur != null && gc != null && gc.groupId == groupId) {
                _chatMessagesState.value =
                    cur.copy(
                        chatDetails =
                            cur.chatDetails.copy(
                                groupClique = gc.copy(name = trimmed),
                                otherUser = cur.chatDetails.otherUser.copy(name = trimmed),
                            ),
                    )
            }
            loadChats(isForced = true)
            _nudgeResult.value = "Group renamed"
        } else {
            _nudgeResult.value = "Could not rename group"
        }
        onComplete(ok)
    }
}

internal fun ChatViewModel.fetchActiveHubDetailsImpl(
    hubId: String,
    onComplete: (Result<ChatApiClient.HubDetailsDto>) -> Unit,
) {
    viewModelScope.launch(chatMediaDispatcher) {
        val jwt = EnsureFreshAccessToken.get(tokenStorage) // pragma: allowlist secret
        if (jwt == null) {
            onComplete(Result.failure(IllegalStateException("Please sign in again.")))
            return@launch
        }
        val result = chatApi.getHubDetails(hubId = hubId, authToken = jwt)
        result.onSuccess { details ->
            AppDataManager.updateActiveHubDetails(
                hubId = hubId,
                name = details.name,
                category = details.category,
                creatorId = details.creatorId,
                isEventHub = !details.eventBeaconId.isNullOrBlank(),
            )
        }
        onComplete(result)
    }
}

internal fun ChatViewModel.updateActiveHubImpl(
    hubId: String,
    name: String,
    category: String,
    onComplete: (Boolean) -> Unit = {},
) {
    val trimmedName = name.trim().take(80)
    val trimmedCategory = category.trim().take(40)
    if (trimmedName.isEmpty() || trimmedCategory.isEmpty()) {
        _nudgeResult.value = "Hub name and category are required"
        onComplete(false)
        return
    }
    viewModelScope.launch(chatMediaDispatcher) {
        val jwt = EnsureFreshAccessToken.get(tokenStorage) // pragma: allowlist secret
        if (jwt == null) {
            _nudgeResult.value = "Please sign in again"
            onComplete(false)
            return@launch
        }
        val ok =
            chatApi
                .updateHub(
                    hubId = hubId,
                    name = trimmedName,
                    category = trimmedCategory,
                    authToken = jwt,
                ).isSuccess
        if (ok) {
            AppDataManager.updateActiveHubDetails(hubId, trimmedName, trimmedCategory)
            _nudgeResult.value = "Hub updated"
        } else {
            _nudgeResult.value = "Could not update hub"
        }
        onComplete(ok)
    }
}

internal fun ChatViewModel.leaveActiveHubImpl(
    hubId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch(chatMediaDispatcher) {
        val jwt = EnsureFreshAccessToken.get(tokenStorage) // pragma: allowlist secret
        if (jwt == null) {
            _nudgeResult.value = "Please sign in again"
            onComplete(false)
            return@launch
        }
        val result = chatApi.leaveHub(hubId = hubId, authToken = jwt)
        if (result.isSuccess) {
            AppDataManager.removeActiveHub(hubId)
            _nudgeResult.value = "You left the hub"
        } else {
            _nudgeResult.value = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                ?: "Could not leave hub"
        }
        onComplete(result.isSuccess)
    }
}

internal fun ChatViewModel.deleteActiveHubImpl(
    hubId: String,
    onComplete: (Boolean) -> Unit = {},
) {
    viewModelScope.launch(chatMediaDispatcher) {
        val jwt = EnsureFreshAccessToken.get(tokenStorage) // pragma: allowlist secret
        if (jwt == null) {
            _nudgeResult.value = "Please sign in again"
            onComplete(false)
            return@launch
        }
        val result = chatApi.deleteHub(hubId = hubId, authToken = jwt)
        if (result.isSuccess) {
            AppDataManager.dismissCommunityHub(hubId)
            _nudgeResult.value = "Hub deleted"
        } else {
            _nudgeResult.value = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                ?: "Could not delete hub"
        }
        onComplete(result.isSuccess)
    }
}
