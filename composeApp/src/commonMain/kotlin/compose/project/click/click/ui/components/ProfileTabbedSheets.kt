@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.contacts.ContactDiscoveryHelper // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drop-in replacement for the legacy [UserProfileBottomSheet] that surfaces the same
 * peer-profile data (name, avatar, interests, shared interests, mutual moments) via
 * the new tabbed [ProfileBottomSheet] (Timeline · Media · Links · Files).
 *
 * Wire from any list flow (e.g. the Clicks chat list) **or a map pin** by setting [userId] to the peer
 * id (and optionally [connectionId] when the caller already knows the edge). Pass `null` for [userId]
 * to keep the sheet dismissed unless [connectionId] can resolve the peer from [AppDataManager].
 * Map and Clicks must not diverge: both entry points use this wrapper so Timeline / Beacons /
 * Media / Links hydrate the same way. The sheet hydrates `user_interests.tags` for the peer via
 * [SupabaseRepository.fetchUserPublicProfile] (which queries the
 * `user_interests` Postgres `text[]`) so the Timeline tab renders interests as soon
 * as the row resolves; if the peer has no `user_interests` row the section shows the
 * standard empty state instead of being blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedUserProfileSheet(
    userId: String?,
    viewerUserId: String?,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null,
    onNudge: (() -> Unit)? = null,
    onOpenDisposableRoll: ((String) -> Unit)? = null,
    localMessages: List<ProfileSheetLocalMessage> = emptyList(),
    /**
     * When set, used instead of looking the connection up from [AppDataManager.connections].
     * Map pins pass this so Timeline / Beacons / Media / Links hydrate even if the pin's
     * cached [User] is missing.
     */
    connectionId: String? = null,
    statusBadge: ProfileSheetBadge? = null,
) {
    val connections by AppDataManager.connections.collectAsState()
    val resolvedUserId =
        remember(userId, connectionId, viewerUserId, connections) {
            resolveProfilePeerUserId(
                requestedUserId = userId,
                viewerUserId = viewerUserId,
                connectionId = connectionId,
                connections = connections,
            )
        }
    if (resolvedUserId.isNullOrBlank()) return
    val userId = resolvedUserId

    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val inboxRows by AppDataManager.inboxFeedChats.collectAsState()
    val cached: User? = connectedUsers[userId]
    val inboxHint =
        remember(inboxRows, userId) {
            inboxRows.firstOrNull { row ->
                row.otherUser.id == userId || row.groupMemberUsers.any { member -> member.id == userId }
            }
        }
    val hintedUser =
        remember(inboxHint, userId) {
            when {
                inboxHint?.otherUser?.id == userId -> inboxHint.otherUser
                else -> inboxHint?.groupMemberUsers?.firstOrNull { member -> member.id == userId }
            }
        }
    val profileConnectionId =
        remember(connections, inboxHint, userId, viewerUserId, connectionId) {
            connectionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: connections
                    .firstOrNull { conn ->
                        userId in conn.user_ids &&
                            (viewerUserId.isNullOrBlank() || viewerUserId in conn.user_ids)
                    }?.id ?: inboxHint?.connection?.id?.takeIf { it.isNotBlank() }
        }

    var resolved by remember(userId) { mutableStateOf<User?>(cached ?: hintedUser) }
    LaunchedEffect(userId, cached, hintedUser) {
        val local = AppDataManager.connectedUsers.value[userId] ?: cached ?: hintedUser
        val currentName = resolved?.name?.trim().orEmpty()
        val shouldUseLocal =
            resolved == null ||
                currentName.isBlank() ||
                currentName.equals("Member", ignoreCase = true) ||
                currentName.equals("Connection", ignoreCase = true)
        if (local != null && shouldUseLocal) {
            resolved = local
            return@LaunchedEffect
        }
        if (resolved == null) {
            runCatching {
                withContext(Dispatchers.Default) {
                    SupabaseRepository().fetchUserPublicProfile(viewerUserId, userId)?.user
                }
            }.getOrNull()?.let { resolved = it }
        }
    }

    val displayName =
        resolved?.name?.takeIf { it.isNotBlank() }
            ?: cached?.name?.takeIf { it.isNotBlank() }
            ?: hintedUser?.name?.takeIf { it.isNotBlank() }
            ?: "Connection"
    val priorConnection =
        remember(connections, profileConnectionId) {
            connections.firstOrNull { it.id == profileConnectionId && it.isPriorConnection() }
        }
    val resolvedBadge =
        if (priorConnection != null) {
            ProfileSheetBadge(ContactDiscoveryHelper.PRIOR_BADGE_LABEL, Color(0xFFF59E0B))
        } else {
            statusBadge
        }
    val state =
        remember(
            userId,
            viewerUserId,
            displayName,
            resolved?.image,
            resolved?.email,
            hintedUser?.image,
            hintedUser?.email,
            localMessages,
            profileConnectionId,
            onMessage,
            resolvedBadge,
        ) {
            ProfileSheetState(
                displayName = displayName,
                subtitle =
                    resolved?.email?.takeIf { it.isNotBlank() }
                        ?: hintedUser?.email?.takeIf { it.isNotBlank() },
                avatarUrl = resolved?.image ?: hintedUser?.image,
                statusBadge = resolvedBadge,
                canNudge = onMessage != null && !profileConnectionId.isNullOrBlank(),
                timeline = emptyList(),
                media = emptyList(),
                links = emptyList(),
                files = emptyList(),
                userId = userId,
                email =
                    resolved?.email?.takeIf { it.isNotBlank() }
                        ?: hintedUser?.email?.takeIf { it.isNotBlank() },
                viewerUserId = viewerUserId,
                connectionId = profileConnectionId,
                localMessages = localMessages,
            )
        }

    ClickFormBottomSheet(
        onDismissRequest = onDismiss,
        // Fill viewport so pager weight(1f) reaches the sheet bottom (scroll last rows).
        useUiKitScrollHost = true,
        uiKitFillViewport = true,
    ) {
        ProfileBottomSheet(
            state = state,
            onMessage = {
                onMessage?.invoke()
                onDismiss()
            },
            onNudge = {
                onNudge?.invoke()
                onDismiss()
            },
            onOpenDisposableRoll =
                profileConnectionId?.let { cid ->
                    onOpenDisposableRoll?.let { open ->
                        {
                            open(cid)
                            onDismiss()
                        }
                    }
                },
        )
    }
}

internal fun resolveProfilePeerUserId(
    requestedUserId: String?,
    viewerUserId: String?,
    connectionId: String?,
    connections: List<Connection>,
): String? {
    requestedUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val connId = connectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val viewer = viewerUserId?.trim().orEmpty()
    return connections
        .firstOrNull { it.id == connId }
        ?.user_ids
        ?.firstOrNull { it.isNotBlank() && it != viewer }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedGroupProfileSheet(
    groupName: String?,
    groupId: String?,
    chatId: String?,
    avatarUrl: String? = null,
    viewerUserId: String?,
    members: List<User>,
    groupCreatorId: String?,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null,
    onNudge: (() -> Unit)? = null,
    onOpenDisposableRoll: ((String) -> Unit)? = null,
    onAddMember: (() -> Unit)? = null,
    onRemoveMember: ((String) -> Unit)? = null,
    onMemberClick: ((String) -> Unit)? = null,
    onGroupAvatarUrlChanged: ((String) -> Unit)? = null,
    localMessages: List<ProfileSheetLocalMessage> = emptyList(),
) {
    val resolvedChatId = chatId?.trim().orEmpty()
    if (resolvedChatId.isBlank()) return
    val resolvedGroupId = groupId?.trim().orEmpty()
    val scope = rememberCoroutineScope()
    val apiClient = remember { ApiClient() }
    var groupAvatarUrl by remember(resolvedGroupId, avatarUrl) {
        mutableStateOf(avatarUrl?.trim()?.takeIf { it.isNotEmpty() })
    }
    var avatarUploading by remember { mutableStateOf(false) }
    var avatarUploadError by remember { mutableStateOf<String?>(null) }
    val mediaPickers =
        rememberChatMediaPickers(
            onImagePicked = { bytes, mime ->
                if (resolvedGroupId.isBlank() || avatarUploading) return@rememberChatMediaPickers
                scope.launch {
                    avatarUploading = true
                    avatarUploadError = null
                    try {
                        apiClient.uploadGroupAvatar(resolvedGroupId, bytes, mime).fold(
                            onSuccess = { url ->
                                groupAvatarUrl = url
                                onGroupAvatarUrlChanged?.invoke(url)
                            },
                            onFailure = { e ->
                                avatarUploadError = e.message
                                    ?.lines()
                                    ?.firstOrNull()
                                    ?.take(160)
                                    ?: "Could not update group avatar"
                            },
                        )
                    } finally {
                        avatarUploading = false
                    }
                }
            },
            onAudioPicked = { _, _, _ -> },
            onMediaAccessBlocked = { msg -> avatarUploadError = msg },
        )

    val displayName = groupName?.trim()?.takeIf { it.isNotEmpty() } ?: "Group Click"
    val subtitle =
        when (members.size) {
            0 -> "No members"
            1 -> "1 member"
            else -> "${members.size} members"
        }
    val uploadErrorBadge = avatarUploadError?.let { ProfileSheetBadge(it, MaterialTheme.colorScheme.error) }
    val state =
        remember(
            displayName,
            subtitle,
            resolvedChatId,
            viewerUserId,
            members,
            groupAvatarUrl,
            uploadErrorBadge,
            groupCreatorId,
            onAddMember,
            onRemoveMember,
            onMemberClick,
            onMessage,
            onNudge,
            onOpenDisposableRoll,
            localMessages,
        ) {
            ProfileSheetState(
                displayName = displayName,
                subtitle = subtitle,
                avatarUrl = groupAvatarUrl,
                statusBadge = uploadErrorBadge,
                canNudge = onNudge != null,
                timeline = emptyList(),
                media = emptyList(),
                links = emptyList(),
                files = emptyList(),
                userId = resolvedGroupId.takeIf { it.isNotBlank() } ?: resolvedChatId,
                viewerUserId = viewerUserId,
                connectionId = resolvedChatId,
                isGroup = true,
                groupMembers = members,
                groupCreatorId = groupCreatorId,
                onAddMember = onAddMember,
                onRemoveMember = onRemoveMember,
                onMemberClick = onMemberClick,
                onGroupAvatarUrlChanged = onGroupAvatarUrlChanged,
                localMessages = localMessages,
            )
        }

    ClickFormBottomSheet(
        onDismissRequest = onDismiss,
        useUiKitScrollHost = true,
        uiKitFillViewport = true,
    ) {
        ProfileBottomSheet(
            state = state,
            onMessage = {
                onMessage?.invoke()
                onDismiss()
            },
            onNudge = {
                onNudge?.invoke()
                onDismiss()
            },
            onOpenDisposableRoll =
                onOpenDisposableRoll?.let { open ->
                    {
                        open(resolvedChatId)
                        onDismiss()
                    }
                },
            onAvatarClick =
                if (resolvedGroupId.isNotBlank()) {
                    { mediaPickers.openPhotoLibrary() }
                } else {
                    null
                },
            avatarUploading = avatarUploading,
        )
    }
}
