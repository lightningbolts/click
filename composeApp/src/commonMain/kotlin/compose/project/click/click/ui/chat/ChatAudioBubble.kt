@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.media.rememberChatAudioPlayer
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay

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
    val progressColor: Color,
    val timeColor: Color,
)

@Composable
private fun rememberVoiceChromePalette(kind: ChatAudioChromeKind): VoiceChromePalette {
    val scheme = MaterialTheme.colorScheme
    val glassSurface = GlassSheetTokens.GlassSurface()
    val glassBorder = GlassSheetTokens.GlassBorder()
    val onOledMuted = GlassSheetTokens.OnOledMuted()
    return remember(
        kind,
        scheme.surface,
        scheme.surfaceContainerHigh,
        scheme.outline,
        scheme.onSurfaceVariant,
        glassSurface,
        glassBorder,
        onOledMuted,
    ) {
        when (kind) {
            ChatAudioChromeKind.SentBubble ->
                VoiceChromePalette(
                    // Sent voice notes are their own message bubble; do not depend on a second
                    // generic text bubble behind them for the sent-message color.
                    shellBg = PrimaryBlue,
                    shellBorder = Color.White.copy(alpha = 0.25f),
                    playFill = Color.White.copy(alpha = 0.20f),
                    playBorder = Color.White.copy(alpha = 0.30f),
                    playIcon = Color.White,
                    trackBg = Color.Black.copy(alpha = 0.25f),
                    progressColor = Color.White,
                    timeColor = Color.White.copy(alpha = 0.75f),
                )
            ChatAudioChromeKind.ReceivedBubble ->
                VoiceChromePalette(
                    shellBg = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    shellBorder = scheme.outline.copy(alpha = 0.28f),
                    playFill = PrimaryBlue.copy(alpha = 0.22f),
                    playBorder = PrimaryBlue.copy(alpha = 0.38f),
                    playIcon = Color(0xFFC4A8FF),
                    trackBg = Color.Black.copy(alpha = 0.45f),
                    progressColor = PrimaryBlue,
                    timeColor = scheme.onSurfaceVariant.copy(alpha = 0.92f),
                )
            ChatAudioChromeKind.ProfileSurface -> {
                val onOledSheet = scheme.surface.luminance() < 0.08f
                if (onOledSheet) {
                    VoiceChromePalette(
                        shellBg = glassSurface,
                        shellBorder = glassBorder,
                        playFill = PrimaryBlue.copy(alpha = 0.28f),
                        playBorder = PrimaryBlue.copy(alpha = 0.45f),
                        playIcon = Color.White,
                        trackBg = Color.White.copy(alpha = 0.22f),
                        progressColor = PrimaryBlue,
                        timeColor = onOledMuted,
                    )
                } else {
                    VoiceChromePalette(
                        shellBg = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        shellBorder = scheme.outline.copy(alpha = 0.28f),
                        playFill = PrimaryBlue.copy(alpha = 0.22f),
                        playBorder = PrimaryBlue.copy(alpha = 0.38f),
                        playIcon = Color(0xFFC4A8FF),
                        trackBg = Color.Black.copy(alpha = 0.45f),
                        progressColor = PrimaryBlue,
                        timeColor = scheme.onSurfaceVariant.copy(alpha = 0.92f),
                    )
                }
            }
        }
    }
}

private val ShellShape = RoundedCornerShape(chatBubbleScaledDp(24f))
private val TrackShape = RoundedCornerShape(chatBubbleScaledDp(6f))

@Composable
private fun VoiceNoteChromeShell(
    palette: VoiceChromePalette,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                // Reserve a stable shell height so decrypt/loading → ready does not jump the timeline.
                .heightIn(min = chatBubbleScaledDp(72f))
                .clip(ShellShape)
                .border(1.dp, palette.shellBorder, ShellShape)
                .background(palette.shellBg, ShellShape)
                .padding(horizontal = chatBubbleScaledDp(18f), vertical = chatBubbleScaledDp(15f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(chatBubbleScaledDp(18f)),
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
    /** Max width in chat bubbles; profile chrome ignores this (uses [fillMaxWidth]). */
    messageBubbleMaxWidth: Dp = ChatBubbleTokens.contentMaxWidth,
) {
    val effectiveChrome = if (compact) ChatAudioChromeKind.ProfileSurface else chromeKind
    val palette = rememberVoiceChromePalette(effectiveChrome)
    var pendingAutoPlayAfterDecrypt by remember(mediaUrl, isEncrypted) { mutableStateOf(false) }
    LaunchedEffect(secureError) {
        if (!secureError.isNullOrBlank()) {
            pendingAutoPlayAfterDecrypt = false
        }
    }
    val hintMs =
        remember(durationSeconds) {
            durationSeconds?.takeIf { it > 0 }?.times(1000L) ?: 0L
        }
    val totalLabel =
        remember(durationSeconds, hintMs) {
            formatChatAudioDuration(
                durationMs = if (hintMs > 0) hintMs else 0L,
                fallbackSec = durationSeconds,
            )
        }
    val needsDecryptBeforePlay = isEncrypted && localFilePathForPlayback.isNullOrBlank()
    val playbackUrl =
        remember(mediaUrl, localFilePathForPlayback, needsDecryptBeforePlay) {
            when {
                needsDecryptBeforePlay -> "secure-audio-pending"
                !localFilePathForPlayback.isNullOrBlank() -> "file://${localFilePathForPlayback.trim()}"
                mediaUrl.isNotBlank() -> mediaUrl
                else -> "audio-empty"
            }
        }
    val widthModifier =
        when (effectiveChrome) {
            ChatAudioChromeKind.ProfileSurface -> modifier.fillMaxWidth()
            else -> modifier.widthIn(max = messageBubbleMaxWidth)
        }

    if (!secureError.isNullOrBlank()) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Text(
                text = secureError,
                style = chatBubbleReplySnippetStyle(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    if (secureLoading) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(chatBubbleScaledDp(54f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Preparing voice message",
                    style = chatBubbleReplyLabelStyle(),
                    color = palette.timeColor,
                )
            }
        }
        return
    }

    if (needsDecryptBeforePlay) {
        VoiceNoteChromeShell(palette, widthModifier) {
            Column(Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(chatBubbleScaledDp(12f))
                            .clip(TrackShape)
                            .background(palette.trackBg),
                )
                Spacer(Modifier.height(chatBubbleScaledDp(9f)))
                Text(
                    text = "Preparing voice message",
                    style = chatBubbleReplyLabelStyle(),
                    color = palette.timeColor,
                    maxLines = 1,
                )
                Spacer(Modifier.height(chatBubbleScaledDp(3f)))
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

    val player =
        rememberChatAudioPlayer(
            mediaUrl = playbackUrl,
            localFilePathForPlayback = localFilePathForPlayback,
        )
    LaunchedEffect(secureLoading, localFilePathForPlayback, needsDecryptBeforePlay, pendingAutoPlayAfterDecrypt) {
        if (needsDecryptBeforePlay) return@LaunchedEffect
        if (secureLoading) return@LaunchedEffect
        if (localFilePathForPlayback.isNullOrBlank()) return@LaunchedEffect
        if (!pendingAutoPlayAfterDecrypt) return@LaunchedEffect
        pendingAutoPlayAfterDecrypt = false
        delay(72)
        if (!player.isPlaying) {
            player.togglePlayPause()
        }
    }
    val durationMs =
        remember(player.durationMs, hintMs) {
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
    val positionDisplayMs =
        if (draggingSlider) {
            (sliderValue * durationMs).toLong()
        } else {
            player.positionMs
        }
    val endTimeLabel = formatChatAudioDuration(durationMs, durationSeconds)
    val playerState = rememberUpdatedState(player)

    VoiceNoteChromeShell(palette, widthModifier) {
        val playing = player.isPlaying
        Box(
            modifier =
                Modifier
                    .size(chatBubbleScaledDp(60f))
                    .clip(CircleShape)
                    .border(1.dp, palette.playBorder, CircleShape)
                    .background(palette.playFill, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            PlatformHapticsPolicy.lightImpact()
                            player.togglePlayPause()
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = playing,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(120),
                label = "voicePlayPause",
            ) { isPlaying ->
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.playIcon,
                    modifier = Modifier.size(chatBubbleScaledDp(30f)),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            AudioSeekTrack(
                palette = palette,
                sliderValue = sliderValue,
                durationMs = durationMs,
                onSeekStart = { draggingSlider = true },
                onSeekFraction = { fraction ->
                    sliderValue = fraction
                    playerState.value.seekTo((fraction * durationMs).toLong().coerceIn(0L, durationMs))
                },
                onSeekEnd = { draggingSlider = false },
            )
            Spacer(Modifier.height(chatBubbleScaledDp(9f)))
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
private fun timeStyle(): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    return base.merge(
        TextStyle(
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Medium,
            fontSize = (base.fontSize.value * chatBubbleAudioTimeTypeScale).sp,
        ),
    )
}

@Composable
private fun AudioSeekTrack(
    palette: VoiceChromePalette,
    sliderValue: Float,
    durationMs: Long,
    onSeekStart: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val seekStartHandler = rememberUpdatedState(onSeekStart)
    val seekHandler = rememberUpdatedState(onSeekFraction)
    val seekEndHandler = rememberUpdatedState(onSeekEnd)
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(chatBubbleScaledDp(12f))
                .clip(TrackShape)
                .background(palette.trackBg)
                .pointerInput(durationMs) {
                    if (durationMs <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down =
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                        down.consume()
                        seekStartHandler.value()
                        val width = size.width.toFloat()
                        if (width > 0f) {
                            seekHandler.value((down.position.x / width).coerceIn(0f, 1f))
                        }
                        val pointerId = down.id
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                change.consume()
                                if (width > 0f) {
                                    seekHandler.value((change.position.x / width).coerceIn(0f, 1f))
                                }
                                if (!change.pressed) break
                            }
                        } finally {
                            seekEndHandler.value()
                        }
                    }
                },
    ) {
        val fillW = maxWidth * sliderValue.coerceIn(0f, 1f)
        if (fillW > 0.dp) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(fillW)
                        .clip(TrackShape)
                        .background(palette.progressColor, TrackShape),
            )
        }
    }
}
