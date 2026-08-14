@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.calls // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.ui.theme.BackgroundDark // pragma: allowlist secret
import compose.project.click.click.ui.theme.BorderQuietDark // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.SoftBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.SurfaceDark // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

private val TileCorner = RoundedCornerShape(18.dp)
private val NameScrim = Color(0x8C000000)

@Composable
fun CallParticipantTile(
    callManager: CallManager,
    participant: CallParticipant,
    isActiveSpeaker: Boolean,
    blankVideo: Boolean,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    showVisibilityHint: Boolean = false,
) {
    val borderColor = if (isActiveSpeaker) PrimaryBlue else BorderQuietDark
    val borderWidth = if (isActiveSpeaker) 2.dp else 1.dp
    val label =
        labelOverride
            ?: if (participant.isLocal) {
                CallLayoutPolicy.selfLabel(participant.displayName)
            } else {
                participant.displayName
            }

    Box(
        modifier =
            modifier
                .background(SurfaceDark, TileCorner)
                .border(borderWidth, borderColor, TileCorner),
    ) {
        if (!blankVideo && participant.hasVideo) {
            CallVideoSurface(
                callManager = callManager,
                participantId = participant.identity,
                modifier = Modifier.fillMaxSize(),
                mirror = participant.isLocal,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2C2C)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                            .border(1.dp, BorderQuietDark, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = CallLayoutPolicy.initialsFor(participant.displayName),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (participant.isSpeaking && !participant.isMuted) PrimaryBlue else Color(0xCC101212))
                    .border(1.dp, BorderQuietDark, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (participant.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (participant.isMuted) "Muted" else "Mic on",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(NameScrim, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showVisibilityHint) {
                Text(
                    text = "•",
                    color = SoftBlue,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun CallActiveHeader(
    title: String,
    connectedAtMs: Long?,
    participantCount: Int,
    layoutMode: CallLayoutMode,
    onToggleLayout: () -> Unit,
    chromeAlpha: Float,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(connectedAtMs) {
        while (true) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(1_000)
        }
    }
    val duration = connectedAtMs?.let { CallLayoutPolicy.formatDuration(nowMs - it) } ?: "00:00"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue),
                )
                Text(
                    text = "$duration · $participantCount active",
                    color = SoftBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        IconButton(
            onClick = {
                PlatformHapticsPolicy.lightImpact()
                onToggleLayout()
            },
            modifier =
                Modifier
                    .size(40.dp)
                    .border(1.dp, BorderQuietDark, RoundedCornerShape(10.dp))
                    .background(SurfaceDark, RoundedCornerShape(10.dp)),
        ) {
            Icon(
                imageVector = if (layoutMode == CallLayoutMode.Grid) Icons.Filled.ViewAgenda else Icons.Filled.Apps,
                contentDescription = if (layoutMode == CallLayoutMode.Grid) "Speaker view" else "Grid view",
                tint = Color.White,
            )
        }
    }
}

@Composable
fun CallControlBar(
    isMuted: Boolean,
    isSpeakerEnabled: Boolean,
    isCameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: () -> Unit,
    chromeAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .graphicsLayer { alpha = chromeAlpha }
                .background(BackgroundDark, RoundedCornerShape(40.dp))
                .border(1.dp, BorderQuietDark, RoundedCornerShape(40.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallControlCircleButton(
            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (isMuted) "Unmute" else "Mute",
            onClick = onToggleMic,
            filled = Color.White,
            iconTint = Color.Black,
        )
        CallControlCircleButton(
            icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            contentDescription = if (isCameraEnabled) "Turn camera off" else "Turn camera on",
            onClick = onToggleCamera,
            filled = Color(0xFFE8E8E8),
            iconTint = Color.Black,
        )
        CallControlCircleButton(
            icon = Icons.Filled.SpeakerPhone,
            contentDescription = if (isSpeakerEnabled) "Turn speaker off" else "Turn speaker on",
            onClick = onToggleSpeaker,
            filled = if (isSpeakerEnabled) SoftBlue else Color(0xFFE8E8E8),
            iconTint = Color.Black,
        )
        CallControlCircleButton(
            icon = Icons.Filled.CallEnd,
            contentDescription = "End call",
            onClick = {
                PlatformHapticsPolicy.heavyImpact()
                onEndCall()
            },
            filled = MaterialTheme.colorScheme.error,
            iconTint = Color.White,
            size = 56.dp,
        )
    }
}

@Composable
private fun CallControlCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    filled: Color,
    iconTint: Color,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    IconButton(
        onClick = {
            PlatformHapticsPolicy.lightImpact()
            onClick()
        },
        interactionSource = interaction,
        modifier =
            Modifier
                .size(size)
                .scale(if (pressed) 0.94f else 1f)
                .clip(CircleShape)
                .background(filled)
                .border(1.dp, BorderQuietDark, CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

@Composable
fun CallGridLayout(
    callManager: CallManager,
    participants: List<CallParticipant>,
    activeSpeakerId: String?,
    blankVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = remember(participants.size) { CallLayoutPolicy.gridRowSizes(participants.size) }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var index = 0
        rows.forEach { rowSize ->
            val slice = participants.drop(index).take(rowSize)
            index += rowSize
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                slice.forEach { participant ->
                    CallParticipantTile(
                        callManager = callManager,
                        participant = participant,
                        isActiveSpeaker = participant.identity == activeSpeakerId,
                        blankVideo = blankVideo,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
fun CallSpeakerLayout(
    callManager: CallManager,
    participants: List<CallParticipant>,
    activeSpeaker: CallParticipant?,
    blankVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    val local = participants.firstOrNull { it.isLocal }
    val remotes = participants.filter { !it.isLocal }
    val primary = activeSpeaker?.takeIf { !it.isLocal } ?: remotes.firstOrNull() ?: local
    val otherRemotes = remotes.filter { it.identity != primary?.identity }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (primary != null) {
                CallParticipantTile(
                    callManager = callManager,
                    participant = primary,
                    isActiveSpeaker = true,
                    blankVideo = blankVideo,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(if (otherRemotes.isEmpty()) 1f else 1.6f),
                )
            }
            if (otherRemotes.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    otherRemotes.forEach { remote ->
                        CallParticipantTile(
                            callManager = callManager,
                            participant = remote,
                            isActiveSpeaker = remote.identity == activeSpeaker?.identity,
                            blankVideo = blankVideo,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }
        if (local != null && primary?.identity != local.identity) {
            CallParticipantTile(
                callManager = callManager,
                participant = local,
                isActiveSpeaker = false,
                blankVideo = blankVideo,
                labelOverride = CallLayoutPolicy.selfLabel(local.displayName),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .width(108.dp)
                        .height(144.dp),
            )
        }
    }
}
