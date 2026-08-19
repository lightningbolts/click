@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.calls.CallSessionManager // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatBeaconDetailSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatExpandedPhotoPreview // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMenuAction // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialogs // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.TetherCompassToast // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.ChatViewOverlays(
    viewModel: ChatViewModel,
    chatMessagesState: ChatMessagesState,
    currentUserId: String?,
    archivedConnectionIds: Set<String>,
    coreConnectionIds: Set<String>,
    topInset: Dp,
    edgeBottomInset: Dp,
    toastState: GlassToastState,
    shareableBeacons: List<compose.project.click.click.data.models.MapBeacon>,
    mapViewModel: compose.project.click.click.viewmodel.MapViewModel?,
    onShareBeaconToChats: (
        (
            beacon: compose.project.click.click.data.models.MapBeacon,
            chatIds: List<String>,
            openConnectionId: String?,
        ) -> Unit
    )?,
    onBackPressed: () -> Unit,
    tetherToastMessageState: MutableState<String?>,
    tetherSenderAckState: MutableState<String?>,
    expandedPhotoTargetState: MutableState<MessageWithUser?>,
    openBeaconDetailIdState: MutableState<String?>,
    openBeaconDetailFallbackState: MutableState<compose.project.click.click.data.models.MapBeacon?>,
    openBeaconDetailMetadataState: MutableState<kotlinx.serialization.json.JsonObject?>,
    openBeaconDetailContentState: MutableState<String?>,
    contextMenuMessageState: MutableState<MessageWithUser?>,
    showConnectionSheetState: MutableState<Boolean>,
    showRenameGroupDialogState: MutableState<Boolean>,
    renameGroupDraftState: MutableState<String>,
) {
    var tetherToastMessage by tetherToastMessageState
    var tetherSenderAck by tetherSenderAckState
    var expandedPhotoTarget by expandedPhotoTargetState
    var openBeaconDetailId by openBeaconDetailIdState
    var openBeaconDetailFallback by openBeaconDetailFallbackState
    var openBeaconDetailMetadata by openBeaconDetailMetadataState
    var openBeaconDetailContent by openBeaconDetailContentState
    var contextMenuMessage by contextMenuMessageState
    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState
    var renameGroupDraft by renameGroupDraftState

    TetherCompassToast(
        message = tetherToastMessage,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(60f)
                .padding(top = topInset + 64.dp),
        onDismissed = { tetherToastMessage = null },
    )

    TetherCompassToast(
        message = tetherSenderAck,
        visibleDurationMs = 2_400L,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(61f)
                .padding(top = topInset + 64.dp),
        onDismissed = { tetherSenderAck = null },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ChatExpandedPhotoPreview(
            target = expandedPhotoTarget,
            secureMediaHost = viewModel,
            onDismiss = { expandedPhotoTarget = null },
        )
    }

    val beaconDetailId = openBeaconDetailId
    val mapVm = mapViewModel
    if (beaconDetailId != null) {
        if (mapVm != null) {
            ChatBeaconDetailSheet(
                beaconId = beaconDetailId,
                mapViewModel = mapVm,
                knownBeacons = shareableBeacons,
                onDismissRequest = {
                    openBeaconDetailId = null
                    openBeaconDetailFallback = null
                    openBeaconDetailMetadata = null
                    openBeaconDetailContent = null
                },
                onShareBeaconToChats = onShareBeaconToChats,
                messageFallback = openBeaconDetailFallback,
                messageMetadata = openBeaconDetailMetadata,
                messageContent = openBeaconDetailContent,
            )
        } else {
            LaunchedEffect(beaconDetailId) {
                compose.project.click.click.deeplink.EventDeepLinkRouter
                    .setPendingBeaconId(beaconDetailId)
                openBeaconDetailId = null
            }
        }
    }

    GlassToastHost(
        state = toastState,
        opaque = true,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .zIndex(50f)
                .padding(end = 20.dp, bottom = edgeBottomInset + 16.dp),
    )

    // Message long-press context sheet
    if (contextMenuMessage != null) {
        MessageActionSheet(
            messageWithUser = contextMenuMessage!!,
            viewModel = viewModel,
            onDismiss = { contextMenuMessage = null },
        )
    }

    var pendingConnectionDialog by remember { mutableStateOf<ConnectionSheetDialog?>(null) }
    var dialogGroupId by remember { mutableStateOf<String?>(null) }

    // Connection action sheet
    if (showConnectionSheet) {
        val successState = chatMessagesState as? ChatMessagesState.Success
        val sheetConn = successState?.chatDetails?.connection
        ConnectionActionSheet(
            chatDetails = successState?.chatDetails,
            currentUserId = currentUserId,
            isArchived = sheetConn != null && sheetConn.id in archivedConnectionIds,
            isServerLifecycleArchived = sheetConn?.isServerLifecycleArchived() == true,
            isCore = sheetConn != null && sheetConn.id in coreConnectionIds,
            onDismiss = { showConnectionSheet = false },
            onMenuAction = { action ->
                val details = successState?.chatDetails
                val connId = sheetConn?.id
                when (action) {
                    ConnectionMenuAction.Nudge -> viewModel.sendNudge()
                    ConnectionMenuAction.Archive -> {
                        viewModel.archiveConnection { success ->
                            if (success) onBackPressed()
                        }
                    }
                    ConnectionMenuAction.Unarchive -> {
                        if (connId != null) viewModel.unarchiveConnection(connId)
                    }
                    ConnectionMenuAction.AddToCore -> {
                        if (connId != null) viewModel.addConnectionToCore(connId)
                    }
                    ConnectionMenuAction.RemoveFromCore -> {
                        if (connId != null) viewModel.removeConnectionFromCore(connId)
                    }
                    ConnectionMenuAction.MarkUnread -> {
                        if (connId != null) viewModel.markConversationUnread(connId)
                    }
                    ConnectionMenuAction.RequestRemove -> {
                        pendingConnectionDialog = ConnectionSheetDialog.Remove
                    }
                    ConnectionMenuAction.RequestReport -> {
                        pendingConnectionDialog = ConnectionSheetDialog.Report()
                    }
                    ConnectionMenuAction.RequestBlock -> {
                        pendingConnectionDialog = ConnectionSheetDialog.Block
                    }
                    ConnectionMenuAction.RequestLeaveGroup -> {
                        dialogGroupId = details?.groupClique?.groupId
                        pendingConnectionDialog = ConnectionSheetDialog.LeaveGroup
                    }
                    ConnectionMenuAction.RequestDeleteGroup -> {
                        dialogGroupId = details?.groupClique?.groupId
                        pendingConnectionDialog = ConnectionSheetDialog.DeleteGroup
                    }
                }
            },
        )
    }

    ConnectionSheetDialogs(
        dialog = pendingConnectionDialog,
        onDismiss = {
            pendingConnectionDialog = null
            dialogGroupId = null
        },
        onConfirmRemove = {
            viewModel.deleteConnectionPermanently { success ->
                if (success) onBackPressed()
            }
        },
        onConfirmBlock = {
            viewModel.blockUser { success ->
                if (success) onBackPressed()
            }
        },
        onConfirmReport = { reason ->
            viewModel.reportConnection(reason) { }
        },
        onConfirmLeaveGroup = {
            dialogGroupId?.let { gid ->
                viewModel.leaveVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
            }
        },
        onConfirmDeleteGroup = {
            dialogGroupId?.let { gid ->
                viewModel.deleteVerifiedClique(gid) { ok -> if (ok) onBackPressed() }
            }
        },
    )

    val renameGroupId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.groupClique?.groupId
    UnifiedPopupFormDialog(
        visible = showRenameGroupDialog,
        onDismissRequest = { showRenameGroupDialog = false },
        title = "Rename group",
        confirmLabel = "Save",
        onConfirm = {
            if (renameGroupDraft.isBlank()) return@UnifiedPopupFormDialog
            renameGroupId?.let { gid ->
                viewModel.renameVerifiedClique(gid, renameGroupDraft) { }
            }
            showRenameGroupDialog = false
        },
        body = {
            ClickOutlinedTextField(
                value = renameGroupDraft,
                onValueChange = { renameGroupDraft = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Group name", color = GlassSheetTokens.OnOledMuted()) },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GlassSheetTokens.OnOled(),
                        unfocusedTextColor = GlassSheetTokens.OnOled(),
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                        cursorColor = PrimaryBlue,
                        focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                        unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                    ),
            )
        },
    )
}

internal fun startOutgoingChatCall(
    details: ChatMessagesState.Success,
    videoEnabled: Boolean,
) {
    val clique = details.chatDetails.groupClique
    if (clique != null) {
        val groupId = clique.groupId
        val threadId = details.chatDetails.chat.id
        if (!groupId.isNullOrBlank() && !threadId.isNullOrBlank()) {
            CallSessionManager.startOutgoingGroupCall(
                groupId = groupId,
                chatId = threadId,
                memberIds = clique.memberUserIds,
                videoEnabled = videoEnabled,
            )
        }
        return
    }
    CallSessionManager.startOutgoingCall(
        connectionId = details.chatDetails.connection.id,
        otherUserId = details.chatDetails.otherUser.id,
        otherUserName = details.chatDetails.otherUser.name ?: "Connection",
        videoEnabled = videoEnabled,
    )
}
