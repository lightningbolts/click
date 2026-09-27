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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.ChatMuteStore
import compose.project.click.click.data.models.FriendshipStats
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.canForward
import compose.project.click.click.data.models.toFriendshipEncounter
import compose.project.click.click.ui.chat.ChatBeaconDetailSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatExpandedPhotoPreview // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatTargetPicker
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMenuAction // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialogs // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionCapabilities // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionHandlers // pragma: allowlist secret
import compose.project.click.click.ui.chat.MessageActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.MuteDurationDialog
import compose.project.click.click.ui.chat.PlanHangoutSheet
import compose.project.click.click.ui.chat.canEditMessage // pragma: allowlist secret
import compose.project.click.click.ui.chat.canExportMessageMedia // pragma: allowlist secret
import compose.project.click.click.ui.chat.muteResultMessage
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.TetherCompassToast // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatListState
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.canDiscardFailed
import compose.project.click.click.viewmodel.canRetrySend
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
    var forwardingMessage by remember { mutableStateOf<compose.project.click.click.data.models.Message?>(null) }
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .zIndex(100f),
    ) {
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
        val selectedMessage = contextMenuMessage!!
        MessageActionSheet(
            messageWithUser = selectedMessage,
            capabilities =
                MessageActionCapabilities(
                    canReply = selectedMessage.message.messageType.lowercase() != "call_log",
                    canSaveMedia = canExportMessageMedia(selectedMessage.message),
                    canShareMedia = canExportMessageMedia(selectedMessage.message),
                    canEdit = canEditMessage(selectedMessage),
                    canForward = selectedMessage.message.canForward(),
                    canRetry = selectedMessage.message.canRetrySend(),
                    canDiscard = selectedMessage.message.canDiscardFailed(),
                    canDelete = selectedMessage.isSent && !selectedMessage.message.id.startsWith("temp-"),
                ),
            handlers =
                MessageActionHandlers(
                    onReply = viewModel::startReplyTo,
                    onReact = { messageId, reaction -> viewModel.addReaction(messageId, reaction) },
                    fetchDecryptedMediaBytes = viewModel::fetchDecryptedChatMediaBytes,
                    onEdit = { selected ->
                        viewModel.startEditMessage(selected.message.id, selected.message.content)
                    },
                    onDelete = { selected -> viewModel.deleteMessage(selected.message.id) },
                    onForward = { selected -> forwardingMessage = selected.message },
                    onRetry = { selected -> viewModel.retryFailedMessage(selected.message.id) },
                    onDiscard = { selected -> viewModel.discardFailedMessage(selected.message.id) },
                ),
            onDismiss = { contextMenuMessage = null },
        )
    }

    forwardingMessage?.let { message ->
        val inbox = viewModel.chatListState.collectAsState().value as? ChatListState.Success
        ChatTargetPicker(
            chats = inbox?.chats.orEmpty(),
            sourceChatId = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails?.chat?.id,
            onDismiss = { forwardingMessage = null },
            onSend = { targets ->
                forwardingMessage = null
                viewModel.forwardMessage(message, targets)
            },
        )
    }
    val chatMutes by ChatMuteStore.mutes.collectAsState()
    val muteScope = rememberCoroutineScope()
    var muteDialogChatId by remember { mutableStateOf<String?>(null) }
    muteDialogChatId?.let { chat ->
        MuteDurationDialog(
            onPick = { duration ->
                muteDialogChatId = null
                muteScope.launch { toastState.show(muteScope, muteResultMessage(duration, ChatMuteStore.setMuted(chat, duration))) }
            },
            onDismiss = { muteDialogChatId = null },
        )
    }
    val plannerOpen by viewModel.plannerOpen.collectAsState()
    val plannerDetails = (chatMessagesState as? ChatMessagesState.Success)?.chatDetails
    if (plannerOpen && plannerDetails != null) {
        val isGroupPlan = plannerDetails.groupClique != null
        val metSpots =
            remember(plannerDetails.connection.connectionEncounters, isGroupPlan) {
                if (isGroupPlan) {
                    emptyList()
                } else {
                    val encounters = plannerDetails.connection.connectionEncounters.mapNotNull { it.toFriendshipEncounter() }
                    FriendshipStats
                        .compute(encounters, now = Clock.System.now())
                        .spots
                        .filter { !it.name.isNullOrBlank() }
                        .sortedByDescending { it.visits }
                }
            }
        val lastSpot = plannerDetails.connection.connectionEncounters.maxByOrNull { it.encounteredAt }
        PlanHangoutSheet(
            chatName =
                if (isGroupPlan) {
                    plannerDetails.groupClique?.name?.ifBlank { null } ?: "the group"
                } else {
                    plannerDetails.otherUser.name
                        ?.substringBefore(' ')
                        ?.ifBlank { null } ?: "your Click"
                },
            metSpots = metSpots,
            nearLatitude = lastSpot?.gpsLat,
            nearLongitude = lastSpot?.gpsLon,
            onDismiss = viewModel::closePlanner,
            onSend = viewModel::sendPlan,
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
            showPlanAction = true,
            isMuted =
                successState?.chatDetails?.chat?.id?.let {
                    ChatMuteStore.isMuted(
                        chatMutes,
                        it,
                        Clock.System.now().toEpochMilliseconds(),
                    )
                },
            onDismiss = { showConnectionSheet = false },
            onMenuAction = { action ->
                val details = successState?.chatDetails
                val connId = sheetConn?.id
                when (action) {
                    ConnectionMenuAction.Nudge -> {
                        val details = successState?.chatDetails
                        if (connId != null && details != null) viewModel.waveAt(connId, details.otherUser.name ?: "them")
                    }
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
                    ConnectionMenuAction.PlanHangout -> viewModel.openPlanner()
                    ConnectionMenuAction.SearchInChat -> viewModel.openChatSearch()
                    ConnectionMenuAction.AcceptPrior, ConnectionMenuAction.DeclinePrior -> {
                        val accept = action == ConnectionMenuAction.AcceptPrior
                        if (connId != null) {
                            muteScope.launch {
                                compose.project.click.click.data.api
                                    .ApiClient()
                                    .respondPriorConnection(connId, if (accept) "accept" else "decline")
                                    .fold(
                                        onSuccess = {
                                            compose.project.click.click.data.AppDataManager
                                                .refresh(force = true)
                                        },
                                        onFailure = { toastState.show(muteScope, "Couldn't respond. Try again.") },
                                    )
                            }
                        }
                    }
                    ConnectionMenuAction.ToggleMute -> {
                        val chat = successState?.chatDetails?.chat?.id
                        if (chat != null) {
                            if (ChatMuteStore.isMuted(chat)) {
                                muteScope.launch { toastState.show(muteScope, muteResultMessage(null, ChatMuteStore.setMuted(chat, null))) }
                            } else {
                                muteDialogChatId = chat
                            }
                        }
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
