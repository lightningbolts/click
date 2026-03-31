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
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle

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
fun VoiceMessageRecordDialogLayout(
    isRecording: Boolean,
    elapsedSec: Long,
    waveformSamples: List<Float>,
    onCancel: () -> Unit,
    onRecord: () -> Unit,
    onStopSend: () -> Unit,
) {
    val style = LocalPlatformStyle.current
    val m = (elapsedSec / 60).toInt()
    val s = (elapsedSec % 60).toInt()
    val timeLabel = "$m:${s.toString().padStart(2, '0')}"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (style.isIOS) 14.dp else 12.dp),
    ) {
        Text(
            text = if (isRecording) "Recording" else "New voice message",
            style = if (style.isIOS) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        VoiceRecordingWaveform(
            samples = waveformSamples,
            barColor = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (isRecording) {
                "Tap Stop & send when you're finished."
            } else {
                "Tap Record, speak clearly, then Stop & send."
            },
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
            if (!isRecording) {
                FilledTonalButton(onClick = onRecord) {
                    Text("Record")
                }
            } else {
                Button(onClick = onStopSend) {
                    Text("Stop & send")
                }
            }
        }
    }
}
