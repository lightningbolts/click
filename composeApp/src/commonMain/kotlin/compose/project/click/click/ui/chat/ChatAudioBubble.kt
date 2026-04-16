package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.media.rememberChatAudioPlayer

/**
 * Inline audio bubble content: play/pause button, scrub slider, and
 * `mm:ss / mm:ss` time label. Handles E2EE loading/error gating via
 * [secureLoading]/[secureError] before instantiating the audio
 * player.
 *
 * Extracted verbatim from ConnectionsScreen.kt; no behavior change.
 */
@Composable
internal fun ChatAudioBubbleRow(
    mediaUrl: String,
    durationSeconds: Int?,
    contentColor: Color,
    accentColor: Color,
    localFilePathForPlayback: String? = null,
    secureLoading: Boolean = false,
    secureError: String? = null,
) {
    val hintMs = remember(durationSeconds) {
        durationSeconds?.takeIf { it > 0 }?.times(1000L) ?: 0L
    }
    if (secureLoading) {
        Box(
            modifier = Modifier
                .widthIn(min = 0.dp, max = 280.dp)
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }
    if (!secureError.isNullOrBlank()) {
        Text(
            text = secureError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        return
    }
    val player = rememberChatAudioPlayer(
        mediaUrl = mediaUrl,
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
    var draggingSlider by remember(mediaUrl) { mutableStateOf(false) }
    var sliderValue by remember(mediaUrl) { mutableFloatStateOf(0f) }
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
    val timeLabel = "${formatChatAudioPositionMs(positionDisplayMs)} / ${formatChatAudioDuration(durationMs, durationSeconds)}"
    Row(
        modifier = Modifier.widthIn(min = 0.dp, max = 280.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { player.togglePlayPause() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (player.isPlaying) "Pause" else "Play",
                tint = accentColor,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    draggingSlider = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    player.seekTo((sliderValue * durationMs).toLong().coerceIn(0L, durationMs))
                    draggingSlider = false
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = contentColor.copy(alpha = 0.28f),
                ),
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}
