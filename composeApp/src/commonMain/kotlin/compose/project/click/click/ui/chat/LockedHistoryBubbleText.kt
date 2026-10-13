@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.LOCKED_HISTORY_BUBBLE_TEXT

/**
 * A row this device can't read yet (sheet 10 A3; iOS `MessageBubbleView.bodyText`). Not an error:
 * end-to-end encryption means a device added later can't read what came before until one of the
 * user's earlier devices shares the key, and the open chat retries on its own.
 */
@Composable
internal fun LockedHistoryBubbleText(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tint = color.copy(alpha = 0.7f)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = LOCKED_HISTORY_BUBBLE_TEXT },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = LOCKED_HISTORY_BUBBLE_TEXT,
            color = tint,
            style = chatBubbleMessageTextStyle().copy(fontStyle = FontStyle.Italic),
        )
    }
}
