@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.FORWARD_MAX_TARGETS // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetPrimaryButton // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret

/** Display name for a forward target: group name or the other person. */
internal fun ChatWithDetails.forwardTargetName(): String =
    groupClique?.name?.trim()?.ifBlank { null }
        ?: otherUser.name?.trim()?.ifBlank { null }
        ?: "Connection"

internal fun ChatWithDetails.forwardKey(): String = chat.id ?: connection.id

/** Targets for the picker: every chat except [sourceChatId], filtered by [query]. */
internal fun forwardTargets(
    chats: List<ChatWithDetails>,
    sourceChatId: String?,
    query: String,
): List<ChatWithDetails> {
    val q = query.trim()
    return chats
        .filter { it.chat.id == null || it.chat.id != sourceChatId }
        .filter { q.isEmpty() || it.forwardTargetName().contains(q, ignoreCase = true) }
        .distinctBy { it.forwardKey() }
}

/** Multi-select Clicks and groups to forward to (max [FORWARD_MAX_TARGETS]). */
@Composable
internal fun ChatTargetPicker(
    chats: List<ChatWithDetails>,
    sourceChatId: String?,
    onDismiss: () -> Unit,
    onSend: (List<ChatWithDetails>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    val options = remember(chats, sourceChatId, query) { forwardTargets(chats, sourceChatId, query) }
    ClickFormBottomSheet(onDismissRequest = onDismiss, fillBody = true) {
        ClickSheetChrome(
            title = "Forward to…",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (options.isEmpty()) {
                Text("No chats to forward to", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
            }
            options.forEach { chat ->
                val key = chat.forwardKey()
                val isSelected = key in selected
                val atLimit = selected.size >= FORWARD_MAX_TARGETS
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isSelected || !atLimit) {
                                selected = if (isSelected) selected - key else selected + key
                            }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = null, enabled = isSelected || !atLimit)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        chat.forwardTargetName(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected.size >= FORWARD_MAX_TARGETS) {
                Text(
                    "You can forward to up to $FORWARD_MAX_TARGETS chats at once.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            ClickSheetPrimaryButton(
                text = if (selected.isEmpty()) "Send" else "Send (${selected.size})",
                enabled = selected.isNotEmpty(),
                onClick = { onSend(chats.filter { it.forwardKey() in selected }.distinctBy { it.forwardKey() }) },
            )
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }
}
