@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.ChatMuteDuration // pragma: allowlist secret
import compose.project.click.click.data.ChatMuteStore // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.util.formatShortDateTime // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/** "Muted until Oct 4, 7:00 PM" / "Muted" / null when not muted. */
internal fun muteStatusLine(
    mutedUntilEpochMs: Long?,
    nowEpochMs: Long,
): String? =
    when {
        mutedUntilEpochMs == null || mutedUntilEpochMs <= nowEpochMs -> null
        mutedUntilEpochMs == ChatMuteStore.FOREVER -> "Muted"
        else -> "Muted until ${formatShortDateTime(mutedUntilEpochMs)}"
    }

/** Pick how long to mute a conversation (1 hour, 8 hours, 1 week, until turned back on). */
@Composable
internal fun MuteDurationDialog(
    onPick: (ChatMuteDuration) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications") },
        text = {
            Column {
                ChatMuteDuration.entries.forEach { duration ->
                    Text(
                        duration.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(duration) }
                                .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Toast copy after a mute change (iOS ChatView `notificationsMenu` / ClicksView `muteMenu`, click-ios
 * `Click/Features/Chat/ChatView.swift`): "Notifications muted" / "Notifications on".
 */
internal fun muteResultMessage(
    duration: ChatMuteDuration?,
    ok: Boolean,
): String =
    when {
        !ok -> "Couldn't change notifications. Try again."
        duration == null -> "Notifications on"
        else -> "Notifications muted"
    }

/**
 * The ids a conversation's mute may be stored under: the chat id, plus a direct chat's connection
 * id (iOS `MuteMenu(chatID:aliases:)` in click-ios `Click/Features/Clicks/ConversationActions.swift`).
 */
internal fun ChatWithDetails.muteIds(): List<String?> =
    listOfNotNull(
        chat.id,
        connection.id.takeIf {
            groupClique == null && it.isNotBlank()
        },
    )

/** "Now" for mute checks that advances when a timed mute ends, so the row bell clears on time. */
@Composable
internal fun rememberMuteNowEpochMs(mutes: Map<String, Long>): Long {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(mutes) {
        while (true) {
            now = Clock.System.now().toEpochMilliseconds()
            val next = ChatMuteStore.nextExpiryEpochMs(mutes, now) ?: break
            delay(next - now + 1)
        }
    }
    return now
}
