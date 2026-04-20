package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.ui.chat.ChatDeliveryReceiptIcon
import compose.project.click.click.ui.chat.ChatMessageBubble
import compose.project.click.click.ui.chat.ChatMessageRowWithTimestampGutter
import compose.project.click.click.ui.chat.ChatInterMessageHubBaseCompact
import compose.project.click.click.ui.chat.chatHubMessageRowTopPadding
import compose.project.click.click.ui.chat.chatHubReceiptRowTopPadding
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle
import compose.project.click.click.ui.chat.rememberChatMediaPickers
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.LocationResult
import compose.project.click.click.viewmodel.HubChatViewModel
import kotlinx.coroutines.Job

data class HubChatNavArgs(
    val hubId: String,
    val realtimeChannel: String,
    val hubTitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubChatScreen(
    args: HubChatNavArgs,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    resolveHubGatekeeperLocation: suspend () -> LocationResult? = { null },
    /**
     * When true, timestamp peek is driven by the parent `InteractiveSwipeBackContainer` horizontal
     * drag (register callbacks with [onRegisterSwipeBackRightToLeftPeek]).
     */
    integrateTimestampPeekWithSwipeBackContainer: Boolean = false,
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit = {},
) {
    val viewModel: HubChatViewModel = viewModel(key = args.realtimeChannel) {
        HubChatViewModel(
            hubId = args.hubId,
            realtimeChannelName = args.realtimeChannel,
            hubTitle = args.hubTitle,
            currentUserId = currentUserId,
            hubLocationResolver = resolveHubGatekeeperLocation,
        )
    }

    val messages by viewModel.messages.collectAsState()
    val occupantCount by viewModel.occupantCount.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val secureMediaLoadMap by viewModel.secureChatMediaLoadState.collectAsState()

    val mediaPickers = rememberChatMediaPickers(
        onImagePicked = { bytes, mime -> viewModel.sendHubImageFromPicker(bytes, mime) },
        onAudioPicked = { _, _, _ -> },
        onMediaAccessBlocked = { },
    )

    val hubIdForSecureMedia = remember(args.hubId) { args.hubId }
    val hubPeekScope = rememberCoroutineScope()

    val inLobby = occupantCount < 3

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (inLobby) {
                                "$occupantCount ${if (occupantCount == 1) "person" else "people"} here"
                            } else {
                                "$occupantCount people in this hub"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(WindowInsets.ime)
                .imePadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryBlue,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = "See someone interesting? Go tap phones to make a permanent connection.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            if (inLobby) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "You're the first one here! We'll ping you when others join.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            val rawTimestampPeekTravelPx = remember { mutableFloatStateOf(0f) }
            val displayTimestampPeekVisualPx = remember { mutableFloatStateOf(0f) }
            val timestampPeekSettleJob = remember { mutableStateOf<Job?>(null) }
            val peekRevealPx = rememberTimestampPeekRevealPx()
            val timestampPeekSoftKneePx = rememberTimestampPeekSoftKneePx()
            DisposableEffect(
                integrateTimestampPeekWithSwipeBackContainer,
                peekRevealPx,
                timestampPeekSoftKneePx,
                hubPeekScope,
            ) {
                val integrate = integrateTimestampPeekWithSwipeBackContainer
                if (integrate) {
                    val integration = InteractiveSwipeBackRightToLeftPeek(
                        onGestureStart = {
                            timestampPeekSettleJob.value?.cancel()
                            timestampPeekSettleJob.value = null
                            restoreTimestampPeekRawFromDisplay(
                                rawLeftPx = rawTimestampPeekTravelPx,
                                displayVisualPx = displayTimestampPeekVisualPx,
                                maxRevealPx = peekRevealPx,
                                softKneePx = timestampPeekSoftKneePx,
                            )
                        },
                        onLeftDragDelta = { dLeft ->
                            applyTimestampPeekDragStep(
                                rawLeftPx = rawTimestampPeekTravelPx,
                                displayVisualPx = displayTimestampPeekVisualPx,
                                maxRevealPx = peekRevealPx,
                                softKneePx = timestampPeekSoftKneePx,
                                dLeftPx = dLeft,
                            )
                        },
                        onLeftDragEnd = {
                            hubPeekScope.launchTimestampPeekReplyStyleSettle(
                                rawLeftPx = rawTimestampPeekTravelPx,
                                displayVisualPx = displayTimestampPeekVisualPx,
                                settleJobHolder = timestampPeekSettleJob,
                            )
                        },
                        onRightDragFromRest = {
                            timestampPeekSettleJob.value?.cancel()
                            timestampPeekSettleJob.value = null
                            rawTimestampPeekTravelPx.floatValue = 0f
                            displayTimestampPeekVisualPx.floatValue = 0f
                        },
                    )
                    onRegisterSwipeBackRightToLeftPeek(integration)
                }
                onDispose {
                    timestampPeekSettleJob.value?.cancel()
                    timestampPeekSettleJob.value = null
                    if (integrate) {
                        onRegisterSwipeBackRightToLeftPeek(null)
                    }
                }
            }

            val newestSentMessage = remember(messages) {
                messages.asSequence().filter { it.isSent }.maxByOrNull { it.message.timeCreated }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (!integrateTimestampPeekWithSwipeBackContainer) {
                            Modifier.chatTimestampPeekOnSwipeLeft(
                                maxRevealPx = peekRevealPx,
                                softKneePx = timestampPeekSoftKneePx,
                                rawLeftPx = rawTimestampPeekTravelPx,
                                displayVisualPx = displayTimestampPeekVisualPx,
                                scope = hubPeekScope,
                                settleJobHolder = timestampPeekSettleJob,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(
                        count = messages.size,
                        key = { messages[it].message.id },
                    ) { index ->
                        val mwu = messages[index]
                        val isCallLog = mwu.message.messageType == "call_log"
                        Column(
                            Modifier.padding(
                                top = chatHubMessageRowTopPadding(index, messages, ChatInterMessageHubBaseCompact),
                            ),
                        ) {
                            ChatMessageRowWithTimestampGutter(
                                isCallLog = isCallLog,
                                isSent = mwu.isSent,
                                timeCreated = mwu.message.timeCreated,
                                stripVisualPx = displayTimestampPeekVisualPx,
                                maxRevealPx = peekRevealPx,
                            ) {
                                ChatMessageBubble(
                                    messageWithUser = mwu,
                                    currentUserId = currentUserId,
                                    reactions = emptyList(),
                                    onToggleReaction = {},
                                    onForward = {},
                                    onLongPress = {},
                                    onSwipeReply = {},
                                    showPeerAvatarInGroup = true,
                                    secureMediaHost = viewModel,
                                    secureMediaState = secureMediaLoadMap[mwu.message.id],
                                    activeChatId = hubIdForSecureMedia,
                                    enableMessageContextMenu = false,
                                )
                            }
                        }
                    }
                    if (newestSentMessage != null) {
                        val receiptM = newestSentMessage
                        items(
                            listOf(receiptM),
                            key = { _ -> "hub-outbound-delivery-receipt" },
                        ) { mwu ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = chatHubReceiptRowTopPadding(ChatInterMessageHubBaseCompact),
                                        end = 10.dp,
                                    ),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                ChatDeliveryReceiptIcon(
                                    messageWithUser = mwu,
                                    baseTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    readTint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            sendError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            HubChatInputBar(
                viewModel = viewModel,
                inLobby = inLobby,
                onOpenPhotoLibrary = { mediaPickers.openPhotoLibrary() },
            )
        }
    }
}

@Composable
private fun HubChatInputBar(
    viewModel: HubChatViewModel,
    inLobby: Boolean,
    onOpenPhotoLibrary: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(isSending, draft) {
        if (wasSending && !isSending && draft.isEmpty()) {
            focusRequester.requestFocus()
        }
        wasSending = isSending
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = viewModel::updateDraft,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            enabled = !inLobby && !isSending,
            placeholder = {
                Text(
                    if (inLobby) "Chat unlocks when 3+ people are here"
                    else "Message the hub…",
                )
            },
            singleLine = false,
            minLines = 1,
            maxLines = 10,
            shape = RoundedCornerShape(20.dp),
        )
        IconButton(
            onClick = onOpenPhotoLibrary,
            enabled = !inLobby && !isSending,
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = "Attach image",
                tint = if (!inLobby && !isSending) {
                    PrimaryBlue
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
        IconButton(
            onClick = {
                viewModel.sendMessage()
                focusRequester.requestFocus()
            },
            enabled = !inLobby && draft.isNotBlank() && !isSending,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (!inLobby && draft.isNotBlank() && !isSending) {
                    PrimaryBlue
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
