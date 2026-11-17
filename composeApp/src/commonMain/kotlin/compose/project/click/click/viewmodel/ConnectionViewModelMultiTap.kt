package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.MultiTapGroupPreparation // pragma: allowlist secret
import compose.project.click.click.domain.MultiTapGroupState // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What [startMultiTapGroupPreparation] was last called with, so "Try again" can rerun it. */
internal data class MultiTapGroupArgs(
    val currentUserId: String,
    val groupMemberIds: List<String>,
    val peers: List<UserProfile>,
)

/** True while the post-connect result of a group Click is on screen (where its setup state shows). */
internal fun ConnectionViewModel.isGroupResultShown(): Boolean {
    val tagging = _connectionState.value as? ConnectionState.TaggingContext ?: return false
    return !tagging.requiresSelection && tagging.isGroup && tagging.newConnections.isNotEmpty()
}

/**
 * Starts group chat setup when a new group Click's result is shown (iOS `PostConnectView` runs
 * `prepareGroup` as it appears), independent of tag saving. A failure for the same people is not
 * retried on its own; the result offers "Try again".
 */
internal fun ConnectionViewModel.startGroupSetupForShownResult(state: ConnectionState) {
    val tagging = state as? ConnectionState.TaggingContext ?: return
    if (tagging.requiresSelection || !tagging.isGroup || !tagging.isNewConnection) return
    if (tagging.newConnections.isEmpty() || tagging.newConnections.any { it.isPendingSync() }) return
    val currentUserId =
        AppDataManager.currentUser.value
            ?.id
            ?.takeIf { it.isNotBlank() } ?: return
    val peers = tagging.targetUsers.filter { it.id.isNotBlank() && it.id != currentUserId }
    val members =
        MultiTapGroupPreparation.memberIds(
            viewerId = currentUserId,
            groupMemberIds = tagging.memberUserIds,
            peerIds = peers.map { it.id },
        )
    if (!MultiTapGroupPreparation.shouldAutoStart(_multiTapGroupState.value, members)) return
    startMultiTapGroupPreparation(
        currentUserId = currentUserId,
        groupMemberIds = tagging.memberUserIds,
        peers = peers,
    )
}

/** The group chat setup to show on [tagging]'s result: Idle for a 1:1, a review, or another Click's setup. */
internal fun groupSetupStateFor(
    state: MultiTapGroupState,
    tagging: ConnectionState.TaggingContext,
    currentUserId: String,
): MultiTapGroupState {
    if (!tagging.isGroup || tagging.requiresSelection || currentUserId.isBlank()) return MultiTapGroupState.Idle
    val members =
        MultiTapGroupPreparation.memberIds(
            viewerId = currentUserId,
            groupMemberIds = tagging.memberUserIds,
            peerIds = tagging.targetUsers.filter { it.id.isNotBlank() && it.id != currentUserId }.map { it.id },
        )
    return MultiTapGroupPreparation.stateFor(state, members)
}

/** "Try again" on the result after group chat setup failed. */
fun ConnectionViewModel.retryMultiTapGroupPreparation() {
    if (_multiTapGroupState.value !is MultiTapGroupState.Failed) return
    val args = multiTapGroupArgs ?: return
    startMultiTapGroupPreparation(args.currentUserId, args.groupMemberIds, args.peers)
}

/** "Open group chat" on the result: resolves the ready group's chat and hands its ID to [open]. */
fun ConnectionViewModel.openMultiTapGroupChat(open: (chatId: String) -> Unit) {
    val ready = _multiTapGroupState.value as? MultiTapGroupState.Ready ?: return
    viewModelScope.launch {
        val chatId =
            try {
                SupabaseChatRepository(tokenStorage = createTokenStorage()).resolveChatIdForGroupId(ready.groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        if (chatId.isNullOrBlank()) {
            _transientNotice.tryEmit(MULTI_TAP_GROUP_CHAT_NOT_LOADED)
        } else {
            open(chatId)
        }
    }
}

internal const val MULTI_TAP_GROUP_CHAT_NOT_LOADED: String =
    "The group was created but couldn't be loaded yet. Find it in Chats."

/**
 * Multi-Tap `prepareGroup` (P2-19): starts finding or creating the verified group chat for a group
 * Click as soon as it is confirmed, on every member's phone, without blocking the post-connect
 * screen. The lowest member ID creates at once; the others wait 2/3/4 s for that group first
 * (iOS `PostConnectModel.prepareGroup`). Runs once per member set. A failure shows "Try again" on
 * the result while it is on screen; otherwise it falls back to the prefilled group sheet so the
 * person can still create it by hand.
 *
 * @param peers the other people in the Click, for the group name and the fallback sheet.
 */
internal fun ConnectionViewModel.startMultiTapGroupPreparation(
    currentUserId: String,
    groupMemberIds: List<String>,
    peers: List<UserProfile>,
) {
    val members =
        MultiTapGroupPreparation.memberIds(
            viewerId = currentUserId,
            groupMemberIds = groupMemberIds,
            peerIds = peers.map { it.id },
        )
    if (currentUserId.isBlank() || members.size < MultiTapGroupPreparation.MIN_MEMBERS) return
    if (!MultiTapGroupPreparation.shouldStart(_multiTapGroupState.value, members)) return
    multiTapGroupJob?.cancel()
    multiTapGroupArgs = MultiTapGroupArgs(currentUserId, groupMemberIds, peers)
    _multiTapGroupState.value = MultiTapGroupState.Preparing(members)
    multiTapGroupJob =
        viewModelScope.launch {
            val chatRepo = SupabaseChatRepository(tokenStorage = createTokenStorage())
            val result =
                MultiTapGroupPreparation.prepare(
                    viewerId = currentUserId,
                    memberIds = members,
                    findExisting = {
                        chatRepo.fetchGroupUserChatsWithDetails(currentUserId).mapNotNull { it.groupClique }
                    },
                    create = {
                        // Read the viewer's connections from the server (iOS `pairConnectionIDs`):
                        // the 1:1 connections this Click just made are usually not cached yet.
                        val fresh =
                            AppDataManager.supabaseRepository
                                .fetchUserConnectionsSnapshot(currentUserId, runStaleSweep = false)
                                .connections
                        VerifiedCliqueCreation
                            .createVerifiedCliqueWithWrappedKeys(
                                chatRepository = chatRepo,
                                connections = fresh + AppDataManager.connections.value,
                                currentUserId = currentUserId,
                                memberUserIds = members,
                                initialGroupName = MultiTapGroupPreparation.groupName(peers.map { it.displayName }),
                            ).map { created ->
                                try {
                                    chatRepo.resolveChatIdForGroupId(created.groupId)?.let { chatId ->
                                        chatRepo.cacheGroupMasterKey(chatId, created.masterKey32)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    // The key is unwrapped from the server when the chat opens.
                                }
                                created.groupId
                            }
                    },
                )
            _multiTapGroupState.value = result
            when (result) {
                is MultiTapGroupState.Ready -> AppDataManager.bumpChatListRefresh()
                is MultiTapGroupState.Failed -> {
                    // The result on screen shows the failure with "Try again"; otherwise fall back
                    // to the prefilled group sheet.
                    if (!isGroupResultShown()) {
                        _transientNotice.tryEmit(result.message)
                        val others = members.filter { it != currentUserId }
                        _verifiedCliqueFromProximity.emit(
                            VerifiedCliqueProximityIntent(
                                preselectFriendIds = others.take(PROXIMITY_HOST_SELECTION_MAX_PEERS),
                                matchedUsers =
                                    peers.map { p ->
                                        User(id = p.id, name = p.displayName, image = p.avatarUrl, createdAt = 0L)
                                    },
                            ),
                        )
                    }
                }
                else -> Unit
            }
        }
}
