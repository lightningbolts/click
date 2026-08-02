package compose.project.click.click.ui.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.hasLocalMediaUri
import compose.project.click.click.data.models.isBeaconChatMessage
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.data.models.originalMimeTypeOrNull
import compose.project.click.click.data.models.parsedMediaMetadata
import compose.project.click.click.data.models.replyRef
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.SecureChatMediaHost
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ChatMessageBubble(
    messageWithUser: MessageWithUser,
    currentUserId: String?,
    reactions: List<MessageReaction> = emptyList(),
    onToggleReaction: (String) -> Unit = {},
    onForward: (String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit = {},
    /** Horizontal swipe toward the center of the screen starts a reply (same idea as drag L→R on incoming). */
    onSwipeReply: (MessageWithUser) -> Unit = {},
    /** Verified group / multi-member chat: show the sender’s face on incoming bubbles. */
    showPeerAvatarInGroup: Boolean = false,
    secureMediaHost: SecureChatMediaHost? = null,
    /**
     * Pre-resolved secure media load state for this message. Hoist the [SecureChatMediaHost.secureChatMediaLoadState]
     * collector out of LazyColumn items and pass only the relevant entry here to avoid every row observing the full map.
     * If null, falls back to collecting from [secureMediaHost] internally (legacy path).
     */
    secureMediaState: SecureChatMediaLoadState? = null,
    activeChatId: String? = null,
    /** When false, long-press context and its haptics are disabled (e.g. read-only hub preview). */
    enableMessageContextMenu: Boolean = true,
    /**
     * Download + decrypt handler for `message_type = file` attachments (Phase 2 — C6). The
     * ViewModel is expected to wire this to [compose.project.click.click.data.repository
     * .ChatRepository.downloadAttachmentPlaintext] plus [saveDecryptedAttachmentToDownloads];
     * default is a no-op so non-chat surfaces (read-only hub preview) keep compiling.
     */
    onDownloadAttachment: suspend (MessageWithUser, AttachmentCrypto.Envelope) -> ChatAttachmentDownloadOutcome =
        { _, _ -> ChatAttachmentDownloadOutcome.Failure("Download not available in this context.") },
    onExpandPhoto: (MessageWithUser) -> Unit = {},
    /** Opens the full map beacon detail sheet for a shared beacon card. */
    onOpenBeacon: (beaconId: String) -> Unit = {},
) {
    val message = messageWithUser.message
    if (message.messageType == "call_log") {
        CallLogSystemRow(message = message)
        return
    }
    if (message.isBeaconChatMessage()) {
        BeaconChatMessageBubble(
            messageWithUser = messageWithUser,
            onOpenBeacon = onOpenBeacon,
            onLongPress = onLongPress,
            onSwipeReply = onSwipeReply,
            enableMessageContextMenu = enableMessageContextMenu,
        )
        return
    }
    val isSent = messageWithUser.isSent
    val replyRef = message.replyRef()
    val mt = message.messageType.lowercase()
    val mediaUrl = message.mediaUrlOrNull()
    val audioDurSec = message.parsedMediaMetadata()?.durationSeconds
    val encryptedMedia = message.isEncryptedMedia()
    val secureSt = secureMediaState
        ?: secureMediaHost?.secureChatMediaLoadState?.collectAsState()?.value?.get(message.id)
    val isImageMessage = mt == ChatMessageType.IMAGE &&
        (!mediaUrl.isNullOrBlank() || secureSt?.imageBytes != null)
    val attachmentEnvelope = remember(message.id, message.content, message.metadata) {
        if (mt == ChatMessageType.FILE || message.content.startsWith(AttachmentCrypto.ENVELOPE_PREFIX)) {
            AttachmentCrypto.resolveEnvelope(message.content, message.metadata)
        } else {
            null
        }
    }
    val isAttachment = attachmentEnvelope != null

    val onRequestSecureAudio = remember(message.id, activeChatId, currentUserId, secureMediaHost) {
        {
            val scopeId = activeChatId
            val viewerId = currentUserId
            if (scopeId != null && viewerId != null) {
                secureMediaHost?.ensureSecureChatAudioLoaded(scopeId, viewerId, message)
            }
        }
    }

    LaunchedEffect(message.id, mediaUrl, encryptedMedia, secureSt?.imageBytes, activeChatId, currentUserId) {
        if (!isImageMessage) return@LaunchedEffect
        if (secureSt?.imageBytes != null) return@LaunchedEffect
        if (secureChatImageBitmapCache.get(message.id) != null) return@LaunchedEffect
        val url = mediaUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val scopeId = activeChatId ?: return@LaunchedEffect
        val viewerId = currentUserId ?: return@LaunchedEffect
        secureMediaHost?.ensureSecureChatImageLoaded(scopeId, viewerId, message)
    }

    LaunchedEffect(
        message.id,
        mediaUrl,
        encryptedMedia,
        secureSt?.audioLocalPath,
        activeChatId,
        currentUserId,
    ) {
        if (mt != ChatMessageType.AUDIO) return@LaunchedEffect
        if (secureSt?.audioLocalPath != null) return@LaunchedEffect
        if (!encryptedMedia && !message.hasLocalMediaUri()) return@LaunchedEffect
        val url = mediaUrl?.takeIf { it.isNotBlank() } ?: message.mediaUrlOrNull()?.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank() && !message.hasLocalMediaUri()) return@LaunchedEffect
        val scopeId = activeChatId ?: return@LaunchedEffect
        val viewerId = currentUserId ?: return@LaunchedEffect
        secureMediaHost?.ensureSecureChatAudioLoaded(scopeId, viewerId, message)
    }

    val bubblePillShape = RoundedCornerShape(ChatBubbleTokens.cornerMain)
    val sentShape = bubblePillShape
    val receivedShape = bubblePillShape

    val reactionGroups = reactions.groupBy { it.reactionType }
        .mapValues { (_, list) -> list.size }
        .entries
        .sortedByDescending { it.value }

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
    val maxSwipeVisualPx = swipeThresholdPx
    val swipeSoftKneePx = remember(density) { with(density) { 5.dp.toPx() } }
    val swipeTrackGain = remember { 1f }
    val swipeOverflowRubberGain = remember { 0.12f }
    // Float states — read only in graphicsLayer (draw), never in composition, or photos flicker.
    val rawSwipeTravelPx = remember(message.id) { mutableFloatStateOf(0f) }
    val displayVisualPx = remember(message.id) { mutableFloatStateOf(0f) }
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val messageWithUserState = rememberUpdatedState(messageWithUser)
    val scope = rememberCoroutineScope()
    var swipeSettleJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var replyThresholdHapticFired by remember(message.id) { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        swipeSettleJob?.cancel()
        swipeSettleJob = null
        rawSwipeTravelPx.floatValue =
            (rawSwipeTravelPx.floatValue + delta).coerceIn(-ChatGestureMotion.RawTravelCapPx, ChatGestureMotion.RawTravelCapPx)
        displayVisualPx.floatValue = swipeVisualFromRawTravel(
            rawTravelPx = rawSwipeTravelPx.floatValue,
            isSent = isSent,
            maxVisualPx = maxSwipeVisualPx,
            softKneePx = swipeSoftKneePx,
            trackGain = swipeTrackGain,
            overflowRubberGain = swipeOverflowRubberGain,
        )
        val directed =
            if (isSent) (-rawSwipeTravelPx.floatValue).coerceAtLeast(0f) else rawSwipeTravelPx.floatValue.coerceAtLeast(0f)
        if (directed >= swipeThresholdPx && !replyThresholdHapticFired) {
            replyThresholdHapticFired = true
            PlatformHapticsPolicy.heavyImpact()
        }
    }

    val swipeDragModifier = Modifier.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            swipeSettleJob?.cancel()
            swipeSettleJob = null
            replyThresholdHapticFired = false
            if (displayVisualPx.floatValue != 0f) {
                rawSwipeTravelPx.floatValue = swipeRawTravelFromVisual(
                    visualPx = displayVisualPx.floatValue,
                    isSent = isSent,
                    maxVisualPx = maxSwipeVisualPx,
                    softKneePx = swipeSoftKneePx,
                    trackGain = swipeTrackGain,
                    overflowRubberGain = swipeOverflowRubberGain,
                ).coerceIn(-ChatGestureMotion.RawTravelCapPx, ChatGestureMotion.RawTravelCapPx)
            }
        },
        onDragStopped = {
            val raw = rawSwipeTravelPx.floatValue
            val shouldReply = if (isSent) raw <= -swipeThresholdPx else raw >= swipeThresholdPx
            if (shouldReply) {
                PlatformHapticsPolicy.heavyImpact()
                onSwipeReplyState.value(messageWithUserState.value)
            }
            rawSwipeTravelPx.floatValue = 0f
            swipeSettleJob = scope.launch {
                try {
                    animate(
                        initialValue = displayVisualPx.floatValue,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ) { v, _ ->
                        displayVisualPx.floatValue = v
                    }
                } finally {
                    swipeSettleJob = null
                }
            }
        },
    )

    val messageLongPressModifier =
        if (enableMessageContextMenu && !isImageMessage) {
            Modifier.pointerInput(message.id, onLongPress) {
                detectTapGestures(
                    onLongPress = {
                        PlatformHapticsPolicy.heavyImpact()
                        onLongPress(messageWithUser)
                    },
                )
            }
        } else {
            Modifier
        }

    val imageCaptionLongPressModifier =
        if (enableMessageContextMenu && isImageMessage) {
            Modifier.pointerInput(message.id, onLongPress) {
                detectTapGestures(
                    onLongPress = {
                        PlatformHapticsPolicy.heavyImpact()
                        onLongPress(messageWithUser)
                    },
                )
            }
        } else {
            Modifier
        }

    val openPhotoContextMenu: () -> Unit = {
        PlatformHapticsPolicy.heavyImpact()
        onLongPress(messageWithUser)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChatBubbleTokens.bubbleRowHorizontalInset)
        ) {
            val bubbleContentMaxWidth =
                (maxWidth * ChatBubbleTokens.messageMaxWidthToParentFraction).coerceAtLeast(120.dp)
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!isSent) {
                    ReplySwipeSideIcon(
                        rawSwipeTravelPx = rawSwipeTravelPx,
                        displayVisualPx = displayVisualPx,
                        isSent = false,
                        swipeThresholdPx = swipeThresholdPx,
                        maxSwipeVisualPx = maxSwipeVisualPx,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .zIndex(0f),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f),
                    verticalAlignment = if (!isSent && showPeerAvatarInGroup) {
                        Alignment.Bottom
                    } else {
                        Alignment.CenterVertically
                    },
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start,
                ) {
                    if (!isSent && showPeerAvatarInGroup) {
                        val peer = messageWithUser.user
                        Box(
                            modifier = Modifier
                                .padding(end = ChatBubbleTokens.peerAvatarEndPad, bottom = ChatBubbleTokens.peerAvatarBottomPad)
                                .size(ChatBubbleTokens.peerAvatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!peer.image.isNullOrBlank()) {
                                AsyncImage(
                                    model = peer.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                            } else {
                                Text(
                                    text = peer.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                    style = chatBubbleReplyLabelStyle(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .graphicsLayer { translationX = displayVisualPx.floatValue }
                            .then(swipeDragModifier)
                            .then(messageLongPressModifier),
                    ) {
                if (isSent) {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = bubbleContentMaxWidth),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = bubbleContentMaxWidth)
                                        .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                                        .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                                        .padding(
                                            horizontal = ChatBubbleTokens.replyBlockPaddingH,
                                            vertical = ChatBubbleTokens.replyBlockPaddingV,
                                        )
                                        .then(imageCaptionLongPressModifier),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = chatBubbleReplyLabelStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = chatBubbleReplySnippetStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            ChatBubblePhotoContent(
                                mediaUrl = mediaUrl,
                                message = message,
                                isEncrypted = encryptedMedia,
                                secureState = secureSt,
                                borderIfReceived = false,
                                onPhotoClick = { onExpandPhoto(messageWithUser) },
                                onPhotoLongPress = if (enableMessageContextMenu) openPhotoContextMenu else null,
                            )
                            val capImg = message.content.trim()
                            if (capImg.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(ChatBubbleTokens.captionBelowImageSpacing))
                                ChatBubbleSelectableText(
                                    allowNativeSelection = !enableMessageContextMenu,
                                    modifier = imageCaptionLongPressModifier,
                                ) {
                                    ChatLinkifyText(
                                        text = capImg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        style = chatBubbleMessageTextStyle(),
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = chatBubbleEditedFootnoteStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleContentMaxWidth)
                            .clip(sentShape)
                            .background(PrimaryBlue)
                            .padding(
                                horizontal = ChatBubbleTokens.bubblePaddingHorizontal,
                                vertical = ChatBubbleTokens.bubblePaddingVertical,
                            ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = bubbleContentMaxWidth)
                                        .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                                        .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                                        .background(Color.Black.copy(alpha = 0.12f))
                                        .padding(
                                            horizontal = ChatBubbleTokens.replyBlockPaddingH,
                                            vertical = ChatBubbleTokens.replyBlockPaddingV,
                                        ),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = chatBubbleReplyLabelStyle(),
                                        color = Color.White.copy(alpha = 0.55f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = chatBubbleReplySnippetStyle(),
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            when {
                                mt == ChatMessageType.AUDIO && mediaUrl != null -> {
                                    ChatAudioBubble(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = Color.White,
                                        accentColor = Color.White,
                                        isEncrypted = encryptedMedia,
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && secureSt?.loading == true,
                                        secureError = if (encryptedMedia) secureSt?.error else null,
                                        onRequestDecrypt = onRequestSecureAudio,
                                        mimeTypeHint = message.originalMimeTypeOrNull(),
                                        chromeKind = ChatAudioChromeKind.SentBubble,
                                        messageBubbleMaxWidth = bubbleContentMaxWidth,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(ChatBubbleTokens.captionBelowImageSpacing))
                                        ChatBubbleSelectableText(
                                            allowNativeSelection = !enableMessageContextMenu,
                                        ) {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = Color.White,
                                                linkColor = Color(0xFFB7E0FF),
                                                style = chatBubbleMessageTextStyle(),
                                            )
                                        }
                                    }
                                }
                                isAttachment && attachmentEnvelope != null -> {
                                    ChatAttachmentBubble(
                                        envelope = attachmentEnvelope,
                                        isSent = true,
                                        onDownload = { onDownloadAttachment(messageWithUser, attachmentEnvelope) },
                                        maxCardWidth = bubbleContentMaxWidth,
                                    )
                                }
                                else -> {
                                    ChatBubbleSelectableText(
                                        allowNativeSelection = !enableMessageContextMenu,
                                    ) {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = Color.White,
                                                    linkColor = Color(0xFFB7E0FF),
                                                    style = chatBubbleMessageTextStyle(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = chatBubbleEditedFootnoteStyle(),
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                    }
                } else {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = bubbleContentMaxWidth),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = bubbleContentMaxWidth)
                                        .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                                        .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(
                                            horizontal = ChatBubbleTokens.replyBlockPaddingH,
                                            vertical = ChatBubbleTokens.replyBlockPaddingV,
                                        )
                                        .then(imageCaptionLongPressModifier),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = chatBubbleReplyLabelStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = chatBubbleReplySnippetStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val onBody = MaterialTheme.colorScheme.onSurface
                            val linkC = MaterialTheme.colorScheme.primary
                            ChatBubblePhotoContent(
                                mediaUrl = mediaUrl,
                                message = message,
                                isEncrypted = encryptedMedia,
                                secureState = secureSt,
                                borderIfReceived = true,
                                onPhotoClick = { onExpandPhoto(messageWithUser) },
                                onPhotoLongPress = if (enableMessageContextMenu) openPhotoContextMenu else null,
                            )
                            val capRx = message.content.trim()
                            if (capRx.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(ChatBubbleTokens.captionBelowImageSpacing))
                                ChatBubbleSelectableText(
                                    allowNativeSelection = !enableMessageContextMenu,
                                    modifier = imageCaptionLongPressModifier,
                                ) {
                                    ChatLinkifyText(
                                        text = capRx,
                                        color = onBody,
                                        linkColor = linkC,
                                        style = chatBubbleMessageTextStyle(),
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = chatBubbleEditedFootnoteStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = bubbleContentMaxWidth)
                            .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.18f), shape = receivedShape)
                            .clip(receivedShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .padding(
                                horizontal = ChatBubbleTokens.bubblePaddingHorizontal,
                                vertical = ChatBubbleTokens.bubblePaddingVertical,
                            ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = bubbleContentMaxWidth)
                                        .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                                        .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(
                                            horizontal = ChatBubbleTokens.replyBlockPaddingH,
                                            vertical = ChatBubbleTokens.replyBlockPaddingV,
                                        ),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = chatBubbleReplyLabelStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = chatBubbleReplySnippetStyle(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val onBody = MaterialTheme.colorScheme.onSurface
                            val linkC = MaterialTheme.colorScheme.primary
                            when {
                                mt == ChatMessageType.AUDIO && mediaUrl != null -> {
                                    ChatAudioBubble(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = onBody,
                                        accentColor = linkC,
                                        isEncrypted = encryptedMedia,
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && secureSt?.loading == true,
                                        secureError = if (encryptedMedia) secureSt?.error else null,
                                        onRequestDecrypt = onRequestSecureAudio,
                                        mimeTypeHint = message.originalMimeTypeOrNull(),
                                        chromeKind = ChatAudioChromeKind.ReceivedBubble,
                                        messageBubbleMaxWidth = bubbleContentMaxWidth,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(ChatBubbleTokens.captionBelowImageSpacing))
                                        ChatBubbleSelectableText(
                                            allowNativeSelection = !enableMessageContextMenu,
                                        ) {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = onBody,
                                                linkColor = linkC,
                                                style = chatBubbleMessageTextStyle(),
                                            )
                                        }
                                    }
                                }
                                isAttachment && attachmentEnvelope != null -> {
                                    ChatAttachmentBubble(
                                        envelope = attachmentEnvelope,
                                        isSent = false,
                                        onDownload = { onDownloadAttachment(messageWithUser, attachmentEnvelope) },
                                        maxCardWidth = bubbleContentMaxWidth,
                                    )
                                }
                                else -> {
                                    ChatBubbleSelectableText(
                                        allowNativeSelection = !enableMessageContextMenu,
                                    ) {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = onBody,
                                                    linkColor = linkC,
                                                    style = chatBubbleMessageTextStyle(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = chatBubbleEditedFootnoteStyle(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                    }
                }

                if (reactionGroups.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = ChatBubbleTokens.reactionRowPadH,
                            vertical = ChatBubbleTokens.reactionRowPadV,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(ChatBubbleTokens.reactionChipGap),
                    ) {
                        reactionGroups.forEach { (emoji, count) ->
                            val isOwnReaction = reactions.any { it.reactionType == emoji && it.userId == currentUserId }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ChatBubbleTokens.reactionChipCorner))
                                    .background(
                                        if (isOwnReaction) PrimaryBlue.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isOwnReaction) PrimaryBlue.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(ChatBubbleTokens.reactionChipCorner),
                                    )
                                    .clickable {
                                        PlatformHapticsPolicy.lightImpact()
                                        onToggleReaction(emoji)
                                    }
                                    .padding(
                                        horizontal = ChatBubbleTokens.reactionChipPadH,
                                        vertical = ChatBubbleTokens.reactionChipPadV,
                                    ),
                            ) {
                                Text(
                                    text = if (count > 1) "$emoji $count" else emoji,
                                    fontSize = ChatBubbleTokens.reactionFontSp.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    }
                }
                }
                if (isSent) {
                    ReplySwipeSideIcon(
                        rawSwipeTravelPx = rawSwipeTravelPx,
                        displayVisualPx = displayVisualPx,
                        isSent = true,
                        swipeThresholdPx = swipeThresholdPx,
                        maxSwipeVisualPx = maxSwipeVisualPx,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(0f),
                    )
                }
            }
        }
    }
}

/**
 * Beacon preview with the same reply-swipe + long-press affordances as normal bubbles.
 */
@Composable
private fun BeaconChatMessageBubble(
    messageWithUser: MessageWithUser,
    onOpenBeacon: (beaconId: String) -> Unit,
    onLongPress: (MessageWithUser) -> Unit,
    onSwipeReply: (MessageWithUser) -> Unit,
    enableMessageContextMenu: Boolean,
) {
    val message = messageWithUser.message
    val isSent = messageWithUser.isSent
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
    val maxSwipeVisualPx = swipeThresholdPx
    val swipeSoftKneePx = remember(density) { with(density) { 5.dp.toPx() } }
    val swipeTrackGain = remember { 1f }
    val swipeOverflowRubberGain = remember { 0.12f }
    val rawSwipeTravelPx = remember(message.id) { mutableFloatStateOf(0f) }
    val displayVisualPx = remember(message.id) { mutableFloatStateOf(0f) }
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val messageWithUserState = rememberUpdatedState(messageWithUser)
    val scope = rememberCoroutineScope()
    var swipeSettleJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var replyThresholdHapticFired by remember(message.id) { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        swipeSettleJob?.cancel()
        swipeSettleJob = null
        rawSwipeTravelPx.floatValue =
            (rawSwipeTravelPx.floatValue + delta).coerceIn(-ChatGestureMotion.RawTravelCapPx, ChatGestureMotion.RawTravelCapPx)
        displayVisualPx.floatValue = swipeVisualFromRawTravel(
            rawTravelPx = rawSwipeTravelPx.floatValue,
            isSent = isSent,
            maxVisualPx = maxSwipeVisualPx,
            softKneePx = swipeSoftKneePx,
            trackGain = swipeTrackGain,
            overflowRubberGain = swipeOverflowRubberGain,
        )
        val directed =
            if (isSent) (-rawSwipeTravelPx.floatValue).coerceAtLeast(0f) else rawSwipeTravelPx.floatValue.coerceAtLeast(0f)
        if (directed >= swipeThresholdPx && !replyThresholdHapticFired) {
            replyThresholdHapticFired = true
            PlatformHapticsPolicy.heavyImpact()
        }
    }

    val swipeDragModifier = Modifier.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            swipeSettleJob?.cancel()
            swipeSettleJob = null
            replyThresholdHapticFired = false
            if (displayVisualPx.floatValue != 0f) {
                rawSwipeTravelPx.floatValue = swipeRawTravelFromVisual(
                    visualPx = displayVisualPx.floatValue,
                    isSent = isSent,
                    maxVisualPx = maxSwipeVisualPx,
                    softKneePx = swipeSoftKneePx,
                    trackGain = swipeTrackGain,
                    overflowRubberGain = swipeOverflowRubberGain,
                ).coerceIn(-ChatGestureMotion.RawTravelCapPx, ChatGestureMotion.RawTravelCapPx)
            }
        },
        onDragStopped = {
            val raw = rawSwipeTravelPx.floatValue
            val shouldReply = if (isSent) raw <= -swipeThresholdPx else raw >= swipeThresholdPx
            if (shouldReply) {
                PlatformHapticsPolicy.heavyImpact()
                onSwipeReplyState.value(messageWithUserState.value)
            }
            rawSwipeTravelPx.floatValue = 0f
            swipeSettleJob = scope.launch {
                try {
                    animate(
                        initialValue = displayVisualPx.floatValue,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ) { v, _ ->
                        displayVisualPx.floatValue = v
                    }
                } finally {
                    swipeSettleJob = null
                }
            }
        },
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isSent) {
            ReplySwipeSideIcon(
                rawSwipeTravelPx = rawSwipeTravelPx,
                displayVisualPx = displayVisualPx,
                isSent = false,
                swipeThresholdPx = swipeThresholdPx,
                maxSwipeVisualPx = maxSwipeVisualPx,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(0f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .then(swipeDragModifier)
                .graphicsLayer {
                    translationX = displayVisualPx.floatValue
                },
        ) {
            BeaconChatCard(
                message = message,
                isSent = isSent,
                onOpenBeacon = onOpenBeacon,
                onLongPress = { onLongPress(messageWithUser) },
                enableContextMenu = enableMessageContextMenu,
            )
        }
        if (isSent) {
            ReplySwipeSideIcon(
                rawSwipeTravelPx = rawSwipeTravelPx,
                displayVisualPx = displayVisualPx,
                isSent = true,
                swipeThresholdPx = swipeThresholdPx,
                maxSwipeVisualPx = maxSwipeVisualPx,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .zIndex(0f),
            )
        }
    }
}

/**
 * Native [SelectionContainer] competes with long-press → message action sheet.
 * When the context menu is enabled, skip selection so Copy lives on the sheet only.
 */
@Composable
private fun ChatBubbleSelectableText(
    allowNativeSelection: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (allowNativeSelection) {
        SelectionContainer(modifier = modifier, content = content)
    } else {
        Box(modifier = modifier) { content() }
    }
}
