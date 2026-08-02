package compose.project.click.click.calls

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.components.StateCardTransition
import compose.project.click.click.ui.components.rememberWaitingPulse
import compose.project.click.click.ui.theme.BackgroundDark
import compose.project.click.click.ui.theme.BorderHardDark
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.theme.SurfaceDark
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
    /** Fade labels/controls only — never put TextureView under alpha. */
    chromeAlpha: Float = 1f,
    connectedAtMs: Long? = null,
) {
    val isMuted = (state as? CallState.Connected)?.microphoneEnabled == false
    val isSpeakerEnabled = (state as? CallState.Connected)?.speakerEnabled == true
    val isVideoEnabled = (state as? CallState.Connected)?.cameraEnabled == true
    var heldVideoLayout by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (state is CallState.Connecting) {
        heldVideoLayout = state.videoRequested
    } else if (state is CallState.Connected) {
        heldVideoLayout = state.videoRequested
    }
    val isVideoCall = when (state) {
        is CallState.Connecting -> state.videoRequested
        is CallState.Connected -> state.videoRequested
        is CallState.Ended -> heldVideoLayout
        else -> false
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val blankVideoSurfaces = state is CallState.Ended
    val connected = state as? CallState.Connected
    val participants = connected?.participants.orEmpty()
    val roster = if (participants.isNotEmpty()) {
        participants
    } else {
        listOf(
            CallParticipant(
                identity = "local",
                displayName = "You",
                isLocal = true,
                isMuted = isMuted,
                isSpeaking = false,
                cameraEnabled = isVideoEnabled,
                hasVideo = connected?.localVideoAvailable == true,
            ),
            CallParticipant(
                identity = "remote",
                displayName = otherUserName,
                isLocal = false,
                isMuted = false,
                isSpeaking = false,
                cameraEnabled = connected?.remoteVideoAvailable == true,
                hasVideo = connected?.remoteVideoAvailable == true,
            ),
        )
    }
    val activeSpeaker = CallLayoutPolicy.pickActiveSpeaker(roster)
    var manualOverride by remember { androidx.compose.runtime.mutableStateOf<CallLayoutMode?>(null) }
    var overrideAtCount by remember { androidx.compose.runtime.mutableStateOf(0) }
    val layoutMode = CallLayoutPolicy.resolveMode(
        participantCount = roster.size,
        manualOverride = manualOverride,
        overrideAtCount = overrideAtCount,
    )
    val useMultiLayout = isVideoCall || roster.size > 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(top = topInset),
    ) {
        if (useMultiLayout) {
            Column(modifier = Modifier.fillMaxSize()) {
                CallActiveHeader(
                    title = otherUserName,
                    connectedAtMs = connectedAtMs,
                    participantCount = roster.size,
                    layoutMode = layoutMode,
                    onToggleLayout = {
                        val next = if (layoutMode == CallLayoutMode.Grid) {
                            CallLayoutMode.Speaker
                        } else {
                            CallLayoutMode.Grid
                        }
                        manualOverride = next
                        overrideAtCount = roster.size
                    },
                    chromeAlpha = chromeAlpha,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 96.dp),
                ) {
                    when (layoutMode) {
                        CallLayoutMode.Grid -> CallGridLayout(
                            callManager = callManager,
                            participants = roster,
                            activeSpeakerId = activeSpeaker?.identity,
                            blankVideo = blankVideoSurfaces,
                            modifier = Modifier.fillMaxSize(),
                        )
                        CallLayoutMode.Speaker -> CallSpeakerLayout(
                            callManager = callManager,
                            participants = roster,
                            activeSpeaker = activeSpeaker,
                            blankVideo = blankVideoSurfaces,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } else {
            CompactVoiceCallBody(
                otherUserName = otherUserName,
                state = state,
                chromeAlpha = chromeAlpha,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        CallControlBar(
            isMuted = isMuted,
            isSpeakerEnabled = isSpeakerEnabled,
            isCameraEnabled = isVideoEnabled,
            onToggleMic = { callManager.setMicrophoneEnabled(isMuted) },
            onToggleSpeaker = { callManager.setSpeakerEnabled(!isSpeakerEnabled) },
            onToggleCamera = { callManager.setCameraEnabled(!isVideoEnabled) },
            onEndCall = onEndCall,
            chromeAlpha = chromeAlpha,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun CompactVoiceCallBody(
    otherUserName: String,
    state: CallState,
    chromeAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(0.94f)
            .background(BackgroundDark, RoundedCornerShape(28.dp))
            .border(1.dp, BorderHardDark, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .graphicsLayer { alpha = chromeAlpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (state) {
                is CallState.Connecting -> when {
                    state.reconnecting -> "Reconnecting…"
                    else -> "Connecting…"
                }
                is CallState.Connected -> "Voice call"
                is CallState.Ended -> state.reason ?: "Call ended"
                CallState.Idle -> ""
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(
            text = otherUserName,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(14.dp))
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
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = otherUserName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = when (state) {
                    is CallState.Connecting -> if (state.reconnecting) "Reconnecting…" else "Connecting audio…"
                    is CallState.Ended -> state.reason ?: "Call ended"
                    else -> "Voice call in progress"
                },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
