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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF050816).copy(alpha = 0.96f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((260.dp * pulseOuter))
                    .clip(RoundedCornerShape(130.dp))
                    .background(PrimaryBlue.copy(alpha = 0.12f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((210.dp * pulseInner))
                    .clip(RoundedCornerShape(105.dp))
                    .background(LightBlue.copy(alpha = 0.12f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
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
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(75.dp))
                        .background(Brush.linearGradient(listOf(PrimaryBlue, LightBlue)))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(75.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = otherUserName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = otherUserName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isVideoCall) "Video call" else "Voice call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                )
                Spacer(modifier = Modifier.height(30.dp))

                if (overlayState is CallOverlayState.Connecting) {
                    CircularProgressIndicator(color = LightBlue)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                when (overlayState) {
                    is CallOverlayState.Outgoing,
                    is CallOverlayState.Connecting,
                    -> {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
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
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(36.dp))
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
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(36.dp))
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
                                .size(72.dp)
                                .clip(RoundedCornerShape(36.dp))
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
    val isVideoEnabled = (state as? CallState.Connected)?.cameraEnabled == true
    val isVideoCall = when (state) {
        is CallState.Connecting -> state.videoRequested
        is CallState.Connected -> state.videoRequested
        else -> false
    }
    val hasRemoteVideo = (state as? CallState.Connected)?.remoteVideoAvailable == true
    val hasLocalVideo = (state as? CallState.Connected)?.localVideoAvailable == true
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.94f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(70.dp))
                        .background(Brush.linearGradient(colors = listOf(PrimaryBlue, LightBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = otherUserName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = otherUserName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (isVideoCall) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                                .padding(16.dp)
                                .size(width = 110.dp, height = 160.dp)
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
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { callManager.setMicrophoneEnabled(isMuted) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { callManager.setCameraEnabled(!isVideoEnabled) },
                        modifier = Modifier.size(56.dp)
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
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
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