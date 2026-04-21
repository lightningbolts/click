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
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.data.models.mediaUrlLooksLikePlaintextWebChatMediaUpload
import compose.project.click.click.getPlatform
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

private val ShellShape = RoundedCornerShape(chatBubbleScaledDp(24f))
private val TrackShape = RoundedCornerShape(chatBubbleScaledDp(6f))

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
    /** From message metadata (`original_mime_type`) — used for iOS WebM/Opus web voices. */
    mimeTypeHint: String? = null,
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
            !localFilePathForPlayback.isNullOrBlank() -> "file://${localFilePathForPlayback.trim()}"
            mediaUrl.isNotBlank() -> mediaUrl
            else -> "audio-empty"
        }
    }
    val widthModifier = when (effectiveChrome) {
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
                modifier = Modifier
                    .size(chatBubbleScaledDp(60f))
                    .clip(CircleShape)
                    .background(palette.playFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(chatBubbleScaledDp(33f)),
                    strokeWidth = chatBubbleScaledDp(2f),
                    color = palette.playIcon,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chatBubbleScaledDp(54f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Decrypting…",
                    style = chatBubbleReplyLabelStyle(),
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
                    .size(chatBubbleScaledDp(60f))
                    .clip(CircleShape)
                    .border(1.dp, palette.playBorder, CircleShape)
                    .background(palette.playFill, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            pendingAutoPlayAfterDecrypt = true
                            onRequestDecrypt()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Decrypt and play",
                    tint = palette.playIcon,
                    modifier = Modifier.size(chatBubbleScaledDp(30f)),
                )
            }
            Column(Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chatBubbleScaledDp(12f))
                        .clip(TrackShape)
                        .background(palette.trackBg),
                )
                Spacer(Modifier.height(chatBubbleScaledDp(9f)))
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

    if (
        shouldUseIosSafariForWebVoice(
            isEncrypted = isEncrypted,
            localFilePathForPlayback = localFilePathForPlayback,
            mediaUrl = mediaUrl,
            mimeTypeHint = mimeTypeHint,
        )
    ) {
        SafariBackedWebVoiceRow(
            palette = palette,
            widthModifier = widthModifier,
            mediaUrl = mediaUrl,
            totalLabel = totalLabel,
        )
        return
    }

    val player = rememberChatAudioPlayer(
        mediaUrl = playbackUrl,
        durationHintMs = hintMs,
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
                .size(chatBubbleScaledDp(60f))
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
                modifier = Modifier.size(chatBubbleScaledDp(30f)),
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

/**
 * AVPlayer on iOS does not decode WebM/Opus from the web; Safari can play the same HTTPS URL.
 * Signed Supabase URLs often omit `*.webm` and MIME metadata, so we also treat **plaintext
 * click-web** chat-media objects as Safari-backed when AVPlayer is unlikely to handle them.
 */
private fun shouldUseIosSafariForWebVoice(
    isEncrypted: Boolean,
    localFilePathForPlayback: String?,
    mediaUrl: String,
    mimeTypeHint: String?,
): Boolean {
    if (isEncrypted) return false
    if (!localFilePathForPlayback.isNullOrBlank()) return false
    val m = mediaUrl.trim()
    if (m.isBlank()) return false
    if (!getPlatform().name.contains("ios", ignoreCase = true)) return false
    val pathOnly = m.substringBefore('?').lowercase()
    val mime = mimeTypeHint?.lowercase().orEmpty()
    if (avPlayerLikelySupportsRemoteAudio(pathOnly, mime)) return false
    val webmish =
        pathOnly.endsWith(".webm") ||
            "webm" in mime ||
            mime.contains("codecs=opus") ||
            ("opus" in mime && "audio" in mime)
    if (webmish) return true
    return mediaUrlLooksLikePlaintextWebChatMediaUpload(m)
}

/** Conservative: when true, keep in-app AVPlayer instead of opening Safari. */
private fun avPlayerLikelySupportsRemoteAudio(pathOnly: String, mime: String): Boolean {
    if (pathOnly.endsWith(".mp3") || pathOnly.endsWith(".m4a") || pathOnly.endsWith(".aac") ||
        pathOnly.endsWith(".wav") || pathOnly.endsWith(".caf")
    ) {
        return true
    }
    if (mime.contains("mpeg") && !mime.contains("opus")) return true
    if (mime.contains("aac")) return true
    if (mime.contains("wav")) return true
    if (mime.contains("x-m4a") || mime.contains("m4a")) return true
    if (mime.contains("mp4") && "audio" in mime) return true
    return false
}

@Composable
private fun SafariBackedWebVoiceRow(
    palette: VoiceChromePalette,
    widthModifier: Modifier,
    mediaUrl: String,
    totalLabel: String,
) {
    val uriHandler = LocalUriHandler.current
    val safeUrl = remember(mediaUrl) { mediaUrl.trim() }
    VoiceNoteChromeShell(palette, widthModifier) {
        Box(
            modifier = Modifier
                .size(chatBubbleScaledDp(60f))
                .clip(CircleShape)
                .border(1.dp, palette.playBorder, CircleShape)
                .background(palette.playFill, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { runCatching { uriHandler.openUri(safeUrl) } },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play voice (opens in browser)",
                tint = palette.playIcon,
                modifier = Modifier.size(chatBubbleScaledDp(30f)),
            )
        }
        Column(Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chatBubbleScaledDp(12f))
                    .clip(TrackShape)
                    .background(palette.trackBg),
            )
            Spacer(Modifier.height(chatBubbleScaledDp(6f)))
            Text(
                text = "Tap play to open in Safari (web audio)",
                style = chatBubbleReplyLabelStyle(),
                color = palette.timeColor.copy(alpha = 0.88f),
                maxLines = 2,
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
    onSeekFraction: (Float) -> Unit,
) {
    val seekHandler = rememberUpdatedState(onSeekFraction)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(chatBubbleScaledDp(12f))
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
