@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal const val ChatSearchFocusHoldMs = 1800L

internal fun List<ChatTimelineEntry>.indexOfMessageId(messageId: String): Int {
    if (messageId.isBlank()) return -1
    return indexOfFirst { entry ->
        entry is ChatTimelineEntry.MessageEntry &&
            entry.messageWithUser.message.id == messageId
    }
}

internal suspend fun scrollChatTimelineToMessage(
    listState: LazyListState,
    suppressKeyboardDismiss: MutableState<Boolean>,
    index: Int,
) {
    if (index < 0) return
    if (listState.isScrollInProgress) return
    repeat(12) {
        if (listState.isScrollInProgress) return
        if (listState.layoutInfo.totalItemsCount > index) {
            suppressKeyboardDismiss.value = true
            try {
                listState.animateScrollToItem(index)
                delay(48)
            } finally {
                suppressKeyboardDismiss.value = false
            }
            return
        }
        delay(16)
    }
}

@Composable
internal fun ChatSearchFocusFrame(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = if (active) 180 else 1600,
                easing = FastOutSlowInEasing,
            ),
        label = "chat_search_focus",
    )
    val shape = RoundedCornerShape(18.dp)
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(tint.copy(alpha = 0.16f * alpha))
                .border(width = 1.5.dp, color = tint.copy(alpha = 0.5f * alpha), shape = shape)
                .padding(2.dp),
    ) {
        content()
    }
}
