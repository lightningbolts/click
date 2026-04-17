package compose.project.click.click.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.data.models.parsedMediaMetadata
import compose.project.click.click.data.models.replyRef
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.viewmodel.SecureChatMediaHost
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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
) {
    val message = messageWithUser.message
    if (message.messageType == "call_log") {
        CallLogSystemRow(message = message)
        return
    }
    val isSent = messageWithUser.isSent
    val replyRef = message.replyRef()
    val mt = message.messageType.lowercase()
    val mediaUrl = message.mediaUrlOrNull()
    val audioDurSec = message.parsedMediaMetadata()?.durationSeconds
    val isImageMessage = mt == ChatMessageType.IMAGE && mediaUrl != null
    val encryptedMedia = message.isEncryptedMedia()
    val attachmentEnvelope = remember(message.id, message.content) {
        if (mt == ChatMessageType.FILE || message.content.startsWith(AttachmentCrypto.ENVELOPE_PREFIX)) {
            AttachmentCrypto.tryDecodeEnvelope(message.content)
        } else {
            null
        }
    }
    val isAttachment = attachmentEnvelope != null

    val secureSt = secureMediaState
        ?: secureMediaHost?.secureChatMediaLoadState?.collectAsState()?.value?.get(message.id)
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(message.id, mediaUrl, activeChatId, currentUserId, mt, encryptedMedia) {
        val chatId = activeChatId
        val viewer = currentUserId
        if (encryptedMedia && secureMediaHost != null && chatId != null && viewer != null) {
            when (mt) {
                ChatMessageType.IMAGE -> secureMediaHost.ensureSecureChatImageLoaded(chatId, viewer, message)
                ChatMessageType.AUDIO -> secureMediaHost.ensureSecureChatAudioLoaded(chatId, viewer, message)
            }
        }
    }

    val sentGradient = Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))

    val sentShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 5.dp)
    val receivedShape = RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)

    val reactionGroups = reactions.groupBy { it.reactionType }
        .mapValues { (_, list) -> list.size }
        .entries
        .sortedByDescending { it.value }

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 60.dp.toPx() } }
    val maxSwipeVisualPx = remember(density) { with(density) { 42.dp.toPx() } }
    val swipeSoftKneePx = remember(density) { with(density) { 5.dp.toPx() } }
    val swipeTrackGain = remember { 0.56f }
    val swipeOverflowRubberGain = remember { 0.12f }
    var rawSwipeTravelPx by remember(message.id) { mutableFloatStateOf(0f) }
    var displayVisualPx by remember(message.id) { mutableFloatStateOf(0f) }
    var swipeDragging by remember(message.id) { mutableStateOf(false) }
    val onSwipeReplyState = rememberUpdatedState(onSwipeReply)
    val messageWithUserState = rememberUpdatedState(messageWithUser)
    val scope = rememberCoroutineScope()
    var swipeSettleJob by remember(message.id) { mutableStateOf<Job?>(null) }
    var replyThresholdHapticFired by remember(message.id) { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        swipeSettleJob?.cancel()
        swipeSettleJob = null
        rawSwipeTravelPx = (rawSwipeTravelPx + delta).coerceIn(-340f, 340f)
        displayVisualPx = swipeVisualFromRawTravel(
            rawTravelPx = rawSwipeTravelPx,
            isSent = isSent,
            maxVisualPx = maxSwipeVisualPx,
            softKneePx = swipeSoftKneePx,
            trackGain = swipeTrackGain,
            overflowRubberGain = swipeOverflowRubberGain,
        )
        val directed = if (isSent) (-rawSwipeTravelPx).coerceAtLeast(0f) else rawSwipeTravelPx.coerceAtLeast(0f)
        if (directed >= swipeThresholdPx && !replyThresholdHapticFired) {
            replyThresholdHapticFired = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val swipeDragModifier = Modifier.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
            swipeSettleJob?.cancel()
            swipeSettleJob = null
            replyThresholdHapticFired = false
            swipeDragging = true
            if (displayVisualPx != 0f) {
                rawSwipeTravelPx = swipeRawTravelFromVisual(
                    visualPx = displayVisualPx,
                    isSent = isSent,
                    maxVisualPx = maxSwipeVisualPx,
                    softKneePx = swipeSoftKneePx,
                    trackGain = swipeTrackGain,
                    overflowRubberGain = swipeOverflowRubberGain,
                ).coerceIn(-340f, 340f)
            }
        },
        onDragStopped = {
            swipeDragging = false
            val raw = rawSwipeTravelPx
            val shouldReply = if (isSent) raw <= -swipeThresholdPx else raw >= swipeThresholdPx
            if (shouldReply) {
                onSwipeReplyState.value(messageWithUserState.value)
            }
            rawSwipeTravelPx = 0f
            swipeSettleJob = scope.launch {
                try {
                    animate(
                        initialValue = displayVisualPx,
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 480,
                            easing = CubicBezierEasing(0.17f, 0.88f, 0.24f, 1f),
                        ),
                    ) { v, _ ->
                        displayVisualPx = v
                    }
                } finally {
                    swipeSettleJob = null
                }
            }
        },
    )

    val messageLongPressModifier =
        if (enableMessageContextMenu) {
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

    val dragging = swipeDragging
    val rawHintP = replyDragHintProgress(rawSwipeTravelPx, isSent, swipeThresholdPx)
    val visualHintP = (abs(displayVisualPx) / maxSwipeVisualPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val hintProgress = if (dragging) maxOf(rawHintP, visualHintP) else visualHintP
    val hintAlpha = if (dragging) {
        (0.52f + 0.48f * (hintProgress * hintProgress)).coerceIn(0f, 1f)
    } else {
        (0.38f + 0.5f * (hintProgress * hintProgress)).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!isSent) {
                    ReplySwipeSideIcon(
                        hintProgress = hintProgress,
                        hintAlpha = hintAlpha,
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
                                .padding(end = 6.dp, bottom = 2.dp)
                                .size(24.dp)
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
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Column(
                        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
                        modifier = Modifier
                            .graphicsLayer { translationX = displayVisualPx }
                            .then(swipeDragModifier)
                            .then(messageLongPressModifier),
                    ) {
                if (isSent) {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
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
                                overflowTint = Color.White.copy(alpha = 0.92f),
                                onOverflow = { onLongPress(messageWithUser) },
                                useLightOverflowContrast = true,
                                borderIfReceived = false,
                            )
                            val capImg = message.content.trim()
                            if (capImg.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    ChatLinkifyText(
                                        text = capImg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        linkColor = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.End),
                            )
                            Text(
                                text = if (message.isRead) "Read" else "Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(sentShape)
                            .background(sentGradient)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        ChatMessageOverflowButton(
                            onClick = { onLongPress(messageWithUser) },
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        Column(
                            modifier = Modifier.padding(end = 22.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.55f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            when {
                                mt == ChatMessageType.AUDIO && mediaUrl != null -> {
                                    ChatAudioBubbleRow(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = Color.White,
                                        accentColor = Color.White,
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && (secureSt == null || secureSt.loading),
                                        secureError = if (encryptedMedia) secureSt?.error else null,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        SelectionContainer {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = Color.White,
                                                linkColor = Color(0xFFB7E0FF),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                                isAttachment && attachmentEnvelope != null -> {
                                    ChatAttachmentBubble(
                                        envelope = attachmentEnvelope,
                                        isSent = true,
                                        onDownload = { onDownloadAttachment(messageWithUser, attachmentEnvelope) },
                                    )
                                }
                                else -> {
                                    SelectionContainer {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = Color.White,
                                                    linkColor = Color(0xFFB7E0FF),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.End),
                            )
                            Text(
                                text = if (message.isRead) "Read" else "Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    }
                } else {
                    if (isImageMessage) {
                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
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
                                overflowTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                                onOverflow = { onLongPress(messageWithUser) },
                                useLightOverflowContrast = false,
                                borderIfReceived = true,
                            )
                            val capRx = message.content.trim()
                            if (capRx.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    ChatLinkifyText(
                                        text = capRx,
                                        color = onBody,
                                        linkColor = linkC,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    } else {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.18f), shape = receivedShape)
                            .clip(receivedShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        ChatMessageOverflowButton(
                            onClick = { onLongPress(messageWithUser) },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        Column(
                            modifier = Modifier.padding(end = 22.dp),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            replyRef?.let { r ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "Reply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = r.replyToContent.ifBlank { "Message" },
                                        style = MaterialTheme.typography.bodySmall,
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
                                    ChatAudioBubbleRow(
                                        mediaUrl = mediaUrl,
                                        durationSeconds = audioDurSec,
                                        contentColor = onBody,
                                        accentColor = linkC,
                                        localFilePathForPlayback = secureSt?.audioLocalPath,
                                        secureLoading = encryptedMedia && secureSt?.loading == true,
                                        secureError = if (encryptedMedia) secureSt?.error else null,
                                    )
                                    val cap = message.content.trim()
                                    if (cap.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        SelectionContainer {
                                            ChatLinkifyText(
                                                text = cap,
                                                color = onBody,
                                                linkColor = linkC,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                                isAttachment && attachmentEnvelope != null -> {
                                    ChatAttachmentBubble(
                                        envelope = attachmentEnvelope,
                                        isSent = false,
                                        onDownload = { onDownloadAttachment(messageWithUser, attachmentEnvelope) },
                                    )
                                }
                                else -> {
                                    SelectionContainer {
                                        Column {
                                            if (message.content.isNotBlank()) {
                                                ChatLinkifyText(
                                                    text = message.content,
                                                    color = onBody,
                                                    linkColor = linkC,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (message.timeEdited != null) {
                                Text(
                                    text = "(edited)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatMessageTime(message.timeCreated),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                    }
                }

                if (reactionGroups.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        reactionGroups.forEach { (emoji, count) ->
                            val isOwnReaction = reactions.any { it.reactionType == emoji && it.userId == currentUserId }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isOwnReaction) PrimaryBlue.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isOwnReaction) PrimaryBlue.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        PlatformHapticsPolicy.lightImpact()
                                        onToggleReaction(emoji)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (count > 1) "$emoji $count" else emoji,
                                    fontSize = 13.sp,
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
                        hintProgress = hintProgress,
                        hintAlpha = hintAlpha,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(0f),
                    )
                }
            }
        }
    }
}
