package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.media.rememberChatAudioPlayer
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Visual chrome aligned with the web chat audio player: frosted pill on sent bubbles,
 * zinc-style card on received bubbles and profile surfaces.
 */
enum class ChatAudioChromeKind {
    SentBubble,
    ReceivedBubble,
    ProfileSurface,
}

private data class VoiceChromePalette(
    val shellBg: Color,
    val shellBorder: Color,
    val playFill: Color,
    val playBorder: Color,
    val playIcon: Color,
    val trackBg: Color,
    val progressBrush: Brush,
    val timeColor: Color,
)

@Composable
private fun rememberVoiceChromePalette(kind: ChatAudioChromeKind): VoiceChromePalette {
    val scheme = MaterialTheme.colorScheme
    val violet = Color(0xFF8338EC)
    return remember(kind, scheme.surfaceContainerHigh, scheme.outline, scheme.onSurfaceVariant) {
        when (kind) {
            ChatAudioChromeKind.SentBubble -> VoiceChromePalette(
                shellBg = Color.White.copy(alpha = 0.12f),
                shellBorder = Color.White.copy(alpha = 0.25f),
                playFill = Color.White.copy(alpha = 0.20f),
                playBorder = Color.White.copy(alpha = 0.30f),
                playIcon = Color.White,
                trackBg = Color.Black.copy(alpha = 0.25f),
                progressBrush = Brush.horizontalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.78f)),
                ),
                timeColor = Color.White.copy(alpha = 0.75f),
            )
            ChatAudioChromeKind.ReceivedBubble, ChatAudioChromeKind.ProfileSurface -> VoiceChromePalette(
                shellBg = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                shellBorder = scheme.outline.copy(alpha = 0.28f),
                playFill = PrimaryBlue.copy(alpha = 0.22f),
                playBorder = PrimaryBlue.copy(alpha = 0.38f),
                playIcon = Color(0xFFC4A8FF),
                trackBg = Color.Black.copy(alpha = 0.45f),
                progressBrush = Brush.horizontalGradient(colors = listOf(violet, PrimaryBlue, LightBlue)),
                timeColor = scheme.onSurfaceVariant.copy(alpha = 0.92f),
            )
        }
    }
}

private val ShellShape = RoundedCornerShape(16.dp)
private val TrackShape = RoundedCornerShape(4.dp)

@Composable
private fun VoiceNoteChromeShell(
    palette: VoiceChromePalette,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(ShellShape)
            .border(1.dp, palette.shellBorder, ShellShape)
            .background(palette.shellBg, ShellShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Inline voice-note player matching the web chat audio UI: rounded shell, circular play,
 * tap-to-seek track, split current / total times. Encrypted audio uses [onRequestDecrypt] before
 * the native player is created.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ChatAudioBubble(
    mediaUrl: String,
    durationSeconds: Int?,
    contentColor: Color,
    accentColor: Color,
    isEncrypted: Boolean,
    localFilePathForPlayback: String?,
    secureLoading: Boolean,
    secureError: String?,
    onRequestDecrypt: () -> Unit,
    modifier: Modifier = Modifier,
    /** @deprecated Use [chromeKind] instead; when true, maps to [ChatAudioChromeKind.ProfileSurface]. */
    compact: Boolean = false,
    chromeKind: ChatAudioChromeKind = ChatAudioChromeKind.ReceivedBubble,
) {
    val effectiveChrome = if (compact) ChatAudioChromeKind.ProfileSurface else chromeKind
    val palette = rememberVoiceChromePalette(effectiveChrome)
    val hintMs = remember(durationSeconds) {
        durationSeconds?.takeIf { it > 0 }?.times(1000L) ?: 0L
    }
    val totalLabel = remember(durationSeconds, hintMs) {
        formatChatAudioDuration(
            durationMs = if (hintMs > 0) hintMs else 0L,
            fallbackSec = durationSeconds,
        )
    }
    val needsDecryptBeforePlay = isEncrypted && localFilePathForPlayback.isNullOrBlank()
    val playbackUrl = remember(mediaUrl, localFilePathForPlayback, needsDecryptBeforePlay) {
        when {
            needsDecryptBeforePlay -> "secure-audio-pending"
            mediaUrl.isNotBlank() -> mediaUrl
            !localFilePathForPlayback.isNullOrBlank() -> "file://${localFilePathForPlayback.trim()}"
            else -> "audio-empty"
        }
    }
    val widthModifier = when (effectiveChrome) {
        ChatAudioChromeKind.ProfileSurface -> modifier.fillMaxWidth()
        else -> modifier.widthIn(min = 200.dp, max = 280.dp)
    }

    if (!secureError.isNullOrBlank()) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Text(
                text = secureError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    if (secureLoading) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.playFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = palette.playIcon,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Decrypting…",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.timeColor,
                )
            }
        }
        return
    }

    if (needsDecryptBeforePlay) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.playBorder, CircleShape)
                    .background(palette.playFill, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRequestDecrypt,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Decrypt and play",
                    tint = palette.playIcon,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(TrackShape)
                        .background(palette.trackBg),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "0:00",
                        style = timeStyle(),
                        color = palette.timeColor,
                    )
                    Text(
                        text = totalLabel,
                        style = timeStyle(),
                        color = palette.timeColor,
                    )
                }
            }
        }
        return
    }

    val player = rememberChatAudioPlayer(
        mediaUrl = playbackUrl,
        durationHintMs = hintMs,
        localFilePathForPlayback = localFilePathForPlayback,
    )
    val durationMs = remember(player.durationMs, hintMs) {
        when {
            player.durationMs > 0 -> player.durationMs
            hintMs > 0 -> hintMs
            else -> 1L
        }
    }
    var draggingSlider by remember(playbackUrl, localFilePathForPlayback) { mutableStateOf(false) }
    var sliderValue by remember(playbackUrl, localFilePathForPlayback) { mutableFloatStateOf(0f) }
    LaunchedEffect(player.positionMs, durationMs, player.isPlaying, draggingSlider) {
        if (!draggingSlider && durationMs > 0) {
            sliderValue = (player.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
    val positionDisplayMs = if (draggingSlider) {
        (sliderValue * durationMs).toLong()
    } else {
        player.positionMs
    }
    val endTimeLabel = formatChatAudioDuration(durationMs, durationSeconds)
    val playerState = rememberUpdatedState(player)

    VoiceNoteChromeShell(palette, widthModifier) {
        val playing = player.isPlaying
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, palette.playBorder, CircleShape)
                .background(palette.playFill, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { player.togglePlayPause() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = palette.playIcon,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            AudioSeekTrack(
                palette = palette,
                sliderValue = sliderValue,
                durationMs = durationMs,
                onSeekFraction = { fraction ->
                    playerState.value.seekTo((fraction * durationMs).toLong().coerceIn(0L, durationMs))
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatChatAudioPositionMs(positionDisplayMs),
                    style = timeStyle(),
                    color = palette.timeColor,
                )
                Text(
                    text = endTimeLabel,
                    style = timeStyle(),
                    color = palette.timeColor,
                )
            }
        }
    }
}

@Composable
private fun timeStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.merge(
        TextStyle(
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Medium,
        ),
    )

@Composable
private fun AudioSeekTrack(
    palette: VoiceChromePalette,
    sliderValue: Float,
    durationMs: Long,
    onSeekFraction: (Float) -> Unit,
) {
    val seekHandler = rememberUpdatedState(onSeekFraction)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(TrackShape)
            .background(palette.trackBg)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    if (w <= 0f || durationMs <= 0L) return@detectTapGestures
                    val fraction = (offset.x / w).coerceIn(0f, 1f)
                    seekHandler.value(fraction)
                }
            },
    ) {
        val fillW = maxWidth * sliderValue.coerceIn(0f, 1f)
        if (fillW > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillW)
                    .clip(TrackShape)
                    .background(palette.progressBrush, TrackShape),
            )
        }
    }
}
