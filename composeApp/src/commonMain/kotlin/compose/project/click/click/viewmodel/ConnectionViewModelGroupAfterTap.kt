package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionGroupAfterTapGateway // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.GroupAfterTap // pragma: allowlist secret
import compose.project.click.click.domain.GroupAfterTapPeer // pragma: allowlist secret
import compose.project.click.click.domain.GroupAfterTapResult // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun List<User>.toGroupAfterTapPeers(): List<GroupAfterTapPeer> = map { GroupAfterTapPeer(id = it.id, name = it.name) }

internal fun List<UserProfile>.profilesToGroupAfterTapPeers(): List<GroupAfterTapPeer> =
    map { GroupAfterTapPeer(id = it.id, name = it.displayName) }

/**
 * Runs [GroupAfterTap] (find-or-create the verified group chat, then reconcile its epoch) for a
 * group Click. Every post-connect group result calls this; repeated calls for the same member set
 * while one is in flight are no-ops.
 */
internal fun ConnectionViewModel.prepareGroupAfterTap(
    currentUserId: String,
    groupMemberIds: List<String>,
    peers: List<GroupAfterTapPeer>,
) {
    if (currentUserId.isBlank()) return
    val members = GroupAfterTap.memberIds(groupMemberIds, peers.map { it.id }, currentUserId)
    if (members.size < 3) return
    val key = members.joinToString(",")
    if (!groupAfterTapKeys.add(key)) return
    _groupAfterTap.value = GroupAfterTapUiState.Preparing(members)
    viewModelScope.launch {
        val result =
            withContext(Dispatchers.Default) {
                val gateway =
                    ConnectionGroupAfterTapGateway(
                        repository = repository,
                        chatRepository = SupabaseChatRepository(tokenStorage = createTokenStorage()),
                    )
                GroupAfterTap.prepare(
                    viewerId = currentUserId,
                    groupMemberIds = members,
                    peers = peers.filter { it.id != currentUserId },
                    gateway = gateway,
                )
            }
        groupAfterTapKeys.remove(key)
        when (result) {
            is GroupAfterTapResult.Ready -> {
                AppDataManager.bumpChatListRefresh()
                _groupAfterTap.value = GroupAfterTapUiState.Ready(members, result.group.groupId, result.group.chatId)
            }
            is GroupAfterTapResult.Failed -> {
                _groupAfterTap.value = GroupAfterTapUiState.Failed(members, result.message)
                _transientNotice.tryEmit(result.message)
            }
            GroupAfterTapResult.Skipped -> {
                _groupAfterTap.value = GroupAfterTapUiState.Idle
            }
        }
    }
}

/** The post-connect group chat (iOS `PostConnectModel.GroupState`). */
sealed class GroupAfterTapUiState {
    object Idle : GroupAfterTapUiState()

    data class Preparing(
        val memberUserIds: List<String>,
    ) : GroupAfterTapUiState()

    data class Ready(
        val memberUserIds: List<String>,
        val groupId: String,
        val chatId: String?,
    ) : GroupAfterTapUiState()

    data class Failed(
        val memberUserIds: List<String>,
        val message: String,
    ) : GroupAfterTapUiState()
}
