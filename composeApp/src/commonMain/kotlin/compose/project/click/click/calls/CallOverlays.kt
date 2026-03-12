package compose.project.click.click.calls

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlin.math.roundToInt

@Composable
fun CallPreviewOverlay(
    overlayState: CallOverlayState,
    currentUserId: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onDismissEnded: () -> Unit,
) {
    val invite = when (overlayState) {
        is CallOverlayState.Outgoing -> overlayState.invite
        is CallOverlayState.Incoming -> overlayState.invite
        is CallOverlayState.Connecting -> overlayState.invite
        is CallOverlayState.Ended -> overlayState.invite
        CallOverlayState.Idle -> null
    }
    val otherUserName = invite?.counterpartName(currentUserId) ?: "Connection"
    val isVideoCall = invite?.videoEnabled == true

    val transition = rememberInfiniteTransition(label = "call_preview")
    val pulseOuter by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "call_preview_outer",
    )
    val pulseInner by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "call_preview_inner",
    )
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topInset + 10.dp, bottom = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 324.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF08101F).copy(alpha = 0.94f),
            tonalElevation = 12.dp,
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (overlayState) {
                        is CallOverlayState.Outgoing -> if (isVideoCall) "Starting video ring" else "Starting voice ring"
                        is CallOverlayState.Incoming -> if (isVideoCall) "Incoming video call" else "Incoming voice call"
                        is CallOverlayState.Connecting -> if (isVideoCall) "Joining video call" else "Joining voice call"
                        is CallOverlayState.Ended -> overlayState.reason
                        CallOverlayState.Idle -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue)))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(36.dp))
                        .alpha(pulseInner),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size((92.dp * pulseOuter))
                            .clip(RoundedCornerShape(46.dp))
                            .background(PrimaryBlue.copy(alpha = 0.08f))
                    )
                    Text(
                        text = otherUserName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = otherUserName,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (isVideoCall) "Video call" else "Voice call",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center,
                )

                if (overlayState is CallOverlayState.Connecting) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CircularProgressIndicator(color = LightBlue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (overlayState) {
                    is CallOverlayState.Outgoing,
                    is CallOverlayState.Connecting,
                    -> {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "Cancel call",
                                tint = Color.White,
                            )
                        }
                    }

                    is CallOverlayState.Incoming -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onDecline,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Decline call",
                                    tint = Color.White,
                                )
                            }
                            IconButton(
                                onClick = onAccept,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue)))
                            ) {
                                Icon(
                                    imageVector = if (isVideoCall) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "Accept call",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    is CallOverlayState.Ended -> {
                        IconButton(
                            onClick = onDismissEnded,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue)))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                            )
                        }
                    }

                    CallOverlayState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    callManager: CallManager,
    otherUserName: String,
    state: CallState,
    onEndCall: () -> Unit,
) {
    val isMuted = (state as? CallState.Connected)?.microphoneEnabled == false
    val isSpeakerEnabled = (state as? CallState.Connected)?.speakerEnabled == true
    val isVideoEnabled = (state as? CallState.Connected)?.cameraEnabled == true
    val isVideoCall = when (state) {
        is CallState.Connecting -> state.videoRequested
        is CallState.Connected -> state.videoRequested
        else -> false
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hasRemoteVideo = (state as? CallState.Connected)?.remoteVideoAvailable == true
    val hasLocalVideo = (state as? CallState.Connected)?.localVideoAvailable == true
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val density = LocalDensity.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxHorizontalOffset = with(density) { ((maxWidth - 220.dp) / 2).toPx().coerceAtLeast(0f) }
        val maxVerticalOffset = with(density) { (maxHeight / 2).toPx().coerceAtLeast(0f) }

        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.94f)
                .offset {
                    IntOffset(
                        x = dragOffsetX.roundToInt(),
                        y = dragOffsetY.roundToInt(),
                    )
                }
                .pointerInput(maxHorizontalOffset, maxVerticalOffset) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxHorizontalOffset, maxHorizontalOffset)
                        dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(0f, maxVerticalOffset)
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF050A16).copy(alpha = 0.94f),
            tonalElevation = 12.dp,
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (state) {
                        is CallState.Connecting -> if (state.videoRequested) "Connecting video…" else "Connecting…"
                        is CallState.Connected -> if (state.hasVideo) "Video call" else "Voice call"
                        is CallState.Ended -> state.reason ?: "Call ended"
                        CallState.Idle -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    text = otherUserName,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (isVideoCall) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp)
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CallVideoSurface(
                            callManager = callManager,
                            isLocal = false,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!hasRemoteVideo) {
                            Text(
                                text = if (isIOS) "Waiting for remote video…" else "Waiting for remote video…",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(width = 96.dp, height = 136.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CallVideoSurface(
                                callManager = callManager,
                                isLocal = true,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (!hasLocalVideo) {
                                Text(
                                    text = "Local preview",
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = otherUserName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (state is CallState.Connecting) "Connecting audio…" else "Voice call in progress",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { callManager.setMicrophoneEnabled(isMuted) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { callManager.setSpeakerEnabled(!isSpeakerEnabled) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SpeakerPhone,
                            contentDescription = if (isSpeakerEnabled) "Turn speaker off" else "Turn speaker on",
                            tint = if (isSpeakerEnabled) LightBlue else Color.White,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { callManager.setCameraEnabled(!isVideoEnabled) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            contentDescription = if (isVideoEnabled) "Turn camera off" else "Turn camera on",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "End call",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}