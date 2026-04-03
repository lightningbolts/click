package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.media.rememberChatAudioPlayer
import compose.project.click.click.ui.theme.LocalPlatformStyle

enum class VoiceRecordUiPhase {
    Idle,
    Recording,
    Preview,
}

@Composable
fun VoiceRecordingWaveform(
    samples: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    inactiveAlpha: Float = 0.22f,
) {
    val n = 40
    val arr = if (samples.isEmpty()) {
        List(n) { 0.06f }
    } else {
        val tail = samples.takeLast(n)
        if (tail.size < n) List(n - tail.size) { 0.06f } + tail else tail
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        arr.forEach { raw ->
            val h = raw.coerceIn(0.05f, 1f)
            val alpha = inactiveAlpha + (1f - inactiveAlpha) * h
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((6.dp + 26.dp * h))
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun VoiceMessagePreviewPlayback(
    localMediaUrl: String,
    durationHintSec: Long,
    contentColor: Color,
    accentColor: Color,
) {
    val hintMs = durationHintSec.coerceAtLeast(0L) * 1000L
    val player = rememberChatAudioPlayer(localMediaUrl, durationHintMs = hintMs)
    val durationMs = remember(player.durationMs, hintMs) {
        when {
            player.durationMs > 0 -> player.durationMs
            hintMs > 0 -> hintMs
            else -> 1L
        }
    }
    var draggingSlider by remember(localMediaUrl) { mutableStateOf(false) }
    var sliderValue by remember(localMediaUrl) { mutableFloatStateOf(0f) }
    LaunchedEffect(player.positionMs, durationMs, player.isPlaying, draggingSlider) {
        if (!draggingSlider && durationMs > 0) {
            sliderValue = (player.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }
    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
    val positionDisplayMs = if (draggingSlider) {
        (sliderValue * durationMs).toLong()
    } else {
        player.positionMs
    }
    val timeLabel = "${formatMs(positionDisplayMs)} / ${formatMs(durationMs)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { player.togglePlayPause() },
            modifier = Modifier.width(48.dp),
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

@Composable
fun VoiceMessageRecordDialogLayout(
    phase: VoiceRecordUiPhase,
    displaySeconds: Long,
    waveformSamples: List<Float>,
    previewLocalMediaUrl: String?,
    onCancel: () -> Unit,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onReRecord: () -> Unit,
    onSend: () -> Unit,
) {
    val style = LocalPlatformStyle.current
    val m = (displaySeconds / 60).toInt()
    val s = (displaySeconds % 60).toInt()
    val timeLabel = "$m:${s.toString().padStart(2, '0')}"
    val onBody = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    val title = when (phase) {
        VoiceRecordUiPhase.Recording -> "Recording"
        VoiceRecordUiPhase.Preview -> "Review voice message"
        VoiceRecordUiPhase.Idle -> "New voice message"
    }
    val hint = when (phase) {
        VoiceRecordUiPhase.Recording ->
            "Tap Stop when you're finished. You can listen before sending."
        VoiceRecordUiPhase.Preview ->
            "Play to review, then Send or Re-record."
        VoiceRecordUiPhase.Idle ->
            "Tap Record, speak clearly, then tap Stop when done."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (style.isIOS) 14.dp else 12.dp),
    ) {
        Text(
            text = title,
            style = if (style.isIOS) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge,
            color = onBody,
        )
        VoiceRecordingWaveform(
            samples = waveformSamples,
            barColor = accent,
        )
        if (phase == VoiceRecordUiPhase.Preview && previewLocalMediaUrl != null) {
            VoiceMessagePreviewPlayback(
                localMediaUrl = previewLocalMediaUrl,
                durationHintSec = displaySeconds,
                contentColor = onBody,
                accentColor = accent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            when (phase) {
                VoiceRecordUiPhase.Idle -> {
                    FilledTonalButton(onClick = onRecord) {
                        Text("Record")
                    }
                }
                VoiceRecordUiPhase.Recording -> {
                    Button(onClick = onStopRecording) {
                        Text("Stop")
                    }
                }
                VoiceRecordUiPhase.Preview -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onReRecord) {
                            Text("Re-record")
                        }
                        Button(onClick = onSend) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}
