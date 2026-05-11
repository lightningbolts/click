package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground
import compose.project.click.click.ui.chat.ChatChannelLoadingView
import compose.project.click.click.ui.chat.ChatComposerChromeFadeUnderlay
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag
import compose.project.click.click.ui.chat.chatSpringPressScale
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
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.LocationResult
import compose.project.click.click.viewmodel.HubChatViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class HubChatNavArgs(
    val hubId: String,
    val realtimeChannel: String,
    val hubTitle: String,
)

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
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hubNavBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Loading state: show the same loading view as regular chats until initial messages arrive.
    var channelReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2500)
        channelReady = true
    }
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) channelReady = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChatAmbientMeshBackground(
            connection = null,
            isHubNeutral = true,
            modifier = Modifier.fillMaxSize(),
        )

        if (!channelReady && messages.isEmpty()) {
            ChatChannelLoadingView(
                topInset = topInset,
                onBackPressed = onNavigateBack,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Glass header ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topInset)
                        .height(56.dp)
                        .padding(horizontal = 12.dp)
                        .testTag(ChatGlassHeaderPlateTestTag),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
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
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = PrimaryBlue.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = "See someone interesting? Go tap phones to make a permanent connection.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }

                if (inLobby) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 0.dp,
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

                // ── Timestamp peek ──────────────────────────────────────────
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

                // ── Message list ────────────────────────────────────────────
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
                        contentPadding = PaddingValues(
                            start = 6.dp,
                            end = 6.dp,
                            top = 12.dp,
                            bottom = hubNavBottomDp + 72.dp,
                        ),
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
                                    meshConnection = null,
                                    useHubNeutralMesh = true,
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

                // ── Composer (matches ConnectionChatMessageComposer layout) ─
                Box(modifier = Modifier.fillMaxWidth()) {
                    ChatComposerChromeFadeUnderlay(modifier = Modifier.matchParentSize())
                    HubChatInputBar(
                        viewModel = viewModel,
                        inLobby = inLobby,
                        onOpenPhotoLibrary = { mediaPickers.openPhotoLibrary() },
                    )
                }
            }
        }
    }
}

/**
 * Hub composer strip modeled exactly on [ConnectionChatMessageComposer]:
 * `+` attachment button (left) → BasicTextField (center) → gradient send button (right).
 * Same sizes, shapes, and spring press animations.
 */
@Composable
private fun HubChatInputBar(
    viewModel: HubChatViewModel,
    inLobby: Boolean,
    onOpenPhotoLibrary: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val composerFocusRequester = remember { FocusRequester() }
    var hadSubmitInFlight by remember { mutableStateOf(false) }

    LaunchedEffect(isSending) {
        if (isSending) {
            hadSubmitInFlight = true
            return@LaunchedEffect
        }
        if (!hadSubmitInFlight) return@LaunchedEffect
        hadSubmitInFlight = false
        composerFocusRequester.requestFocus()
    }

    val composerStyle = LocalPlatformStyle.current
    val auxButtonSize = if (composerStyle.isIOS) 44.dp else 52.dp
    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
    val composerRowHPad = 8.dp
    val attachIconSize = if (composerStyle.isIOS) 24.dp else 26.dp
    val sendIconSize = if (composerStyle.isIOS) 22.dp else 20.dp
    val fieldCorner = if (composerStyle.isIOS) 20.dp else 12.dp
    val composerGap = if (composerStyle.isIOS) 6.dp else 8.dp
    val fieldSideInset = auxButtonSize + composerGap

    val attachInteraction = remember { MutableInteractionSource() }
    val sendInteraction = remember { MutableInteractionSource() }
    val composerStripInteraction = remember { MutableInteractionSource() }
    val composerFieldInteraction = remember { MutableInteractionSource() }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val canSend = !inLobby && draft.trim().isNotEmpty() && !isSending
    val enabled = !inLobby && !isSending
    val attachTint = PrimaryBlue.copy(alpha = 0.92f)
    val sendGradient = Brush.linearGradient(
        colors = if (canSend) {
            listOf(PrimaryBlue, LightBlue)
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    )
    val composerInputTextStyle = MaterialTheme.typography.bodyMedium
    val composerTextStyleCentered = composerInputTextStyle.merge(
        TextStyle(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryBlue.copy(alpha = if (composerStyle.isIOS) 0.50f else 0.65f),
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (composerStyle.isIOS) 0.08f else 0.12f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.30f else 0.4f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (composerStyle.isIOS) 0.18f else 0.25f),
    )
    val fieldShape = RoundedCornerShape(fieldCorner)
    val approxLineBodyDp = 24.dp
    val innerVerticalPad = ((auxButtonSize - approxLineBodyDp) / 2).coerceIn(6.dp, 12.dp)
    val innerHorizontalPad = 12.dp
    val fieldDecorPadding = PaddingValues(
        start = innerHorizontalPad,
        end = innerHorizontalPad,
        top = innerVerticalPad,
        bottom = innerVerticalPad,
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(
                    indication = null,
                    interactionSource = composerStripInteraction,
                ) {},
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = composerRowHPad, vertical = composerRowVPad),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = auxButtonSize),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = fieldSideInset, end = fieldSideInset)
                        .heightIn(min = auxButtonSize)
                        .align(Alignment.BottomCenter)
                        .focusRequester(composerFocusRequester),
                    enabled = enabled,
                    textStyle = composerTextStyleCentered.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.None,
                    ),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 10,
                    interactionSource = composerFieldInteraction,
                    cursorBrush = SolidColor(PrimaryBlue),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = draft,
                            innerTextField = innerTextField,
                            enabled = enabled,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = composerFieldInteraction,
                            placeholder = {
                                Text(
                                    if (inLobby) "Chat unlocks when 3+ join"
                                    else "Message the hub…",
                                    style = composerTextStyleCentered,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            colors = fieldColors,
                            contentPadding = fieldDecorPadding,
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = enabled,
                                    isError = false,
                                    interactionSource = composerFieldInteraction,
                                    modifier = Modifier,
                                    colors = fieldColors,
                                    shape = fieldShape,
                                )
                            },
                        )
                    },
                )

                // ── Attach button (left, same as ConnectionChatMessageComposer) ─
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(auxButtonSize)
                        .zIndex(4f)
                        .focusProperties { canFocus = false },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = if (isSending || inLobby) 0.06f else 0.12f))
                            .chatSpringPressScale(attachInteraction)
                            .clickable(
                                interactionSource = attachInteraction,
                                indication = null,
                                enabled = enabled,
                                onClick = { attachmentMenuExpanded = true },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Attach",
                            tint = if (enabled) attachTint else attachTint.copy(alpha = 0.35f),
                            modifier = Modifier.size(attachIconSize),
                        )
                    }
                    DropdownMenu(
                        expanded = attachmentMenuExpanded,
                        onDismissRequest = { attachmentMenuExpanded = false },
                        shape = RoundedCornerShape(if (composerStyle.isIOS) 14.dp else 12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo library", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                onOpenPhotoLibrary()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Take photo", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = PrimaryBlue.copy(alpha = 0.9f),
                                )
                            },
                            onClick = {
                                attachmentMenuExpanded = false
                                onOpenPhotoLibrary()
                            },
                        )
                    }
                }

                // ── Send button (right, gradient pill, same as ConnectionChatMessageComposer) ─
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(auxButtonSize)
                        .zIndex(4f)
                        .focusProperties { canFocus = false }
                        .chatSpringPressScale(sendInteraction)
                        .clip(if (composerStyle.isIOS) CircleShape else RoundedCornerShape(fieldCorner))
                        .background(sendGradient)
                        .clickable(
                            interactionSource = sendInteraction,
                            indication = null,
                            enabled = canSend,
                            onClick = {
                                viewModel.sendMessage()
                                composerFocusRequester.requestFocus()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(sendIconSize),
                    )
                }
            }
        }
    }
}
