@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret

/** One row of the reactors list. */
internal data class ReactorRow(
    val userId: String,
    val emoji: String,
    val isMe: Boolean,
)

/** Reactors for the tab ("All" when [emoji] is null), viewer first, then oldest first. */
internal fun reactorRows(
    reactions: List<MessageReaction>,
    emoji: String?,
    viewerUserId: String?,
): List<ReactorRow> =
    reactions
        .filter { emoji == null || it.reactionType == emoji }
        .distinctBy { it.userId to it.reactionType }
        .sortedWith(compareBy<MessageReaction> { it.userId != viewerUserId }.thenBy { it.createdAt })
        .map { ReactorRow(it.userId, it.reactionType, it.userId == viewerUserId) }

/** Emoji tabs with counts, most-used first. */
internal fun reactionTabs(reactions: List<MessageReaction>): List<Pair<String, Int>> =
    reactions
        .distinctBy { it.userId to it.reactionType }
        .groupingBy { it.reactionType }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }

/**
 * Who reacted (iOS `ReactorsSheet`): an "All" tab plus one per emoji, the viewer first. Tapping your
 * own row removes that reaction; "+" adds another.
 */
@Composable
internal fun ReactorsSheet(
    reactions: List<MessageReaction>,
    viewerUserId: String?,
    nameFor: (String) -> String,
    onRemoveMine: (emoji: String) -> Unit,
    onAddReaction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tabs = remember(reactions) { reactionTabs(reactions) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val emojiFilter = tabs.getOrNull(selectedTab - 1)?.first
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(title = "Reactions", modifier = Modifier.fillMaxWidth()) {
            ScrollableTabRow(selectedTabIndex = selectedTab.coerceIn(0, tabs.size), edgePadding = 12.dp) {
                Tab(selected = selectedTab == 0, onClick = {
                    selectedTab = 0
                }, text = { Text("All ${reactions.distinctBy { it.userId to it.reactionType }.size}") })
                tabs.forEachIndexed { index, (emoji, count) ->
                    Tab(selected = selectedTab == index + 1, onClick = { selectedTab = index + 1 }, text = { Text("$emoji $count") })
                }
            }
            Column(Modifier.padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding, vertical = 8.dp)) {
                reactorRows(reactions, emojiFilter, viewerUserId).forEach { row ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = row.isMe) { onRemoveMine(row.emoji) }
                                .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (row.isMe) "You" else nameFor(row.userId), style = MaterialTheme.typography.bodyLarge)
                            if (row.isMe) {
                                Text(
                                    "Tap to remove",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(row.emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddReaction) { Text("＋ Add a reaction") }
                Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
            }
        }
    }
}
