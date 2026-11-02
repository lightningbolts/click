package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.MultiTapGroupPreparation // pragma: allowlist secret
import compose.project.click.click.domain.MultiTapGroupState // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lets the forced connections refresh settle so key wrapping sees the new pair edges. */
private const val EDGE_REFRESH_SETTLE_MS = 450L

/**
 * Multi-Tap `prepareGroup` (P2-19): starts finding or creating the verified group chat for a group
 * Click as soon as it is confirmed, on every member's phone, without blocking the post-connect
 * screen. The lowest member ID creates at once; the others wait 2/3/4 s for that group first
 * (iOS `PostConnectModel.prepareGroup`). Runs once per member set; a failure falls back to the
 * prefilled group sheet so the person can still create it by hand.
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
                        AppDataManager.refresh(force = true)
                        delay(EDGE_REFRESH_SETTLE_MS)
                        VerifiedCliqueCreation
                            .createVerifiedCliqueWithWrappedKeys(
                                chatRepository = chatRepo,
                                connections = AppDataManager.connections.value,
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
                else -> Unit
            }
        }
}
