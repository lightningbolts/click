@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.nextPinnedMessageId // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret

/**
 * "Pinned" banner above the timeline (iOS pinned banner): shows one pin; each tap jumps to it and
 * moves the banner to the next, wrapping. Hidden when the chat has no pins.
 */
@Composable
internal fun PinnedMessageBanner(
    viewModel: ChatViewModel,
    messages: List<MessageWithUser>,
) {
    val pins by viewModel.pinnedMessages.collectAsState()
    if (pins.isEmpty()) return
    var shownId by remember(pins) { mutableStateOf(pins.first().messageId) }
    val current = pins.firstOrNull { it.messageId == shownId } ?: pins.first()
    val preview =
        messages
            .firstOrNull { it.message.id == current.messageId }
            ?.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("e2e") && !it.startsWith("ccx:") }
            ?: "Pinned message"
    val position = pins.indexOf(current) + 1
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.requestJumpToMessage(current.messageId)
                            nextPinnedMessageId(pins, current.messageId)?.let { shownId = it }
                        }.padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics { contentDescription = "Pinned message $position of ${pins.size}: $preview. Double tap to show it." },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (pins.size > 1) "Pinned · $position of ${pins.size}" else "Pinned",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}
