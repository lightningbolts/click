package compose.project.click.click.calls

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.StateCardTransition
import compose.project.click.click.ui.components.rememberWaitingPulse
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.BorderHardDark
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.SurfaceDark
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sign
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator

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

    val pulseActive = overlayState !is CallOverlayState.Ended
    val pulse = rememberWaitingPulse(
        active = pulseActive,
        durationMillis = 1_200,
        scaleMax = 1.08f,
        alphaMin = 0.94f,
    )
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topInset + 10.dp, bottom = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        StateCardTransition(visible = true) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 324.dp)
                    .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = BackgroundDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHardDark),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
                        .background(PrimaryBlue)
                        .border(1.dp, BorderHardDark, RoundedCornerShape(36.dp))
                        .alpha(pulse.alpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size((92.dp * pulse.scale))
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
                    AdaptiveCircularProgressIndicator(color = LightBlue, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (overlayState) {
                    is CallOverlayState.Outgoing,
                    is CallOverlayState.Connecting,
                    -> {
                        IconButton(
                            onClick = {
                                PlatformHapticsPolicy.heavyImpact()
                                onCancel()
                            },
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
                                onClick = {
                                    PlatformHapticsPolicy.heavyImpact()
                                    onDecline()
                                },
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
                                onClick = {
                                    PlatformHapticsPolicy.lightImpact()
                                    onAccept()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(PrimaryBlue)
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
                            onClick = {
                                PlatformHapticsPolicy.lightImpact()
                                onDismissEnded()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(PrimaryBlue)
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
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val maxHorizontalOffset = with(density) { ((maxWidth - 220.dp) / 2).toPx().coerceAtLeast(0f) }
        val maxVerticalOffset = with(density) { (maxHeight / 2).toPx().coerceAtLeast(0f) }
        val renderedOffsetX by animateFloatAsState(
            targetValue = dragOffsetX,
            animationSpec = spring(
                dampingRatio = if (dragging) 0.9f else Spring.DampingRatioMediumBouncy,
                stiffness = if (dragging) Spring.StiffnessHigh else Spring.StiffnessMediumLow,
            ),
            label = "active_call_drag_x",
        )
        val renderedOffsetY by animateFloatAsState(
            targetValue = dragOffsetY,
            animationSpec = spring(
                dampingRatio = if (dragging) 0.9f else Spring.DampingRatioMediumBouncy,
                stiffness = if (dragging) Spring.StiffnessHigh else Spring.StiffnessMediumLow,
            ),
            label = "active_call_drag_y",
        )

        StateCardTransition(visible = true) {
            Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.94f)
                .offset {
                    IntOffset(
                        x = renderedOffsetX.roundToInt(),
                        y = renderedOffsetY.roundToInt(),
                    )
                }
                .pointerInput(maxHorizontalOffset, maxVerticalOffset) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            dragOffsetX = if (
                                maxHorizontalOffset > 0f &&
                                abs(dragOffsetX) >= maxHorizontalOffset * 0.35f
                            ) {
                                sign(dragOffsetX) * maxHorizontalOffset
                            } else {
                                0f
                            }
                        },
                        onDragCancel = { dragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val damping = 0.86f
                            dragOffsetX = (dragOffsetX + dragAmount.x * damping)
                                .coerceIn(-maxHorizontalOffset, maxHorizontalOffset)
                            dragOffsetY = (dragOffsetY + dragAmount.y * damping)
                                .coerceIn(0f, maxVerticalOffset)
                        },
                    )
                },
            shape = RoundedCornerShape(28.dp),
            color = BackgroundDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHardDark),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
                            .background(SurfaceDark)
                            .border(1.dp, BorderHardDark, RoundedCornerShape(28.dp)),
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
                                .background(SurfaceDark)
                                .border(1.dp, BorderHardDark, RoundedCornerShape(20.dp)),
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
                            .background(SurfaceDark)
                            .border(1.dp, BorderHardDark, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(PrimaryBlue),
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

                val micInteraction = remember { MutableInteractionSource() }
                val speakerInteraction = remember { MutableInteractionSource() }
                val cameraInteraction = remember { MutableInteractionSource() }
                val endInteraction = remember { MutableInteractionSource() }
                val micPressed by micInteraction.collectIsPressedAsState()
                val speakerPressed by speakerInteraction.collectIsPressedAsState()
                val cameraPressed by cameraInteraction.collectIsPressedAsState()
                val endPressed by endInteraction.collectIsPressedAsState()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            PlatformHapticsPolicy.lightImpact()
                            callManager.setMicrophoneEnabled(isMuted)
                        },
                        interactionSource = micInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (micPressed) 0.94f else 1f),
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { callManager.setSpeakerEnabled(!isSpeakerEnabled) },
                        interactionSource = speakerInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (speakerPressed) 0.94f else 1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SpeakerPhone,
                            contentDescription = if (isSpeakerEnabled) "Turn speaker off" else "Turn speaker on",
                            tint = if (isSpeakerEnabled) LightBlue else Color.White,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { callManager.setCameraEnabled(!isVideoEnabled) },
                        interactionSource = cameraInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (cameraPressed) 0.94f else 1f),
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            contentDescription = if (isVideoEnabled) "Turn camera off" else "Turn camera on",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            PlatformHapticsPolicy.heavyImpact()
                            onEndCall()
                        },
                        interactionSource = endInteraction,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(if (endPressed) 0.92f else 1f)
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
}