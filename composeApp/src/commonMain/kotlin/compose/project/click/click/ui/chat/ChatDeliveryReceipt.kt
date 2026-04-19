package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.MessageWithUser

/** Outbound send pipeline: single / double checkmarks (shown once under the latest sent message). */
@Composable
internal fun ChatDeliveryReceiptIcon(
    messageWithUser: MessageWithUser,
    baseTint: Color,
    readTint: Color,
    modifier: Modifier = Modifier,
) {
    if (!messageWithUser.isSent) return
    val m = messageWithUser.message
    val a11y =
        when {
            m.deliveryState == MessageDeliveryState.ERROR -> "Failed to send"
            m.deliveryState == MessageDeliveryState.PENDING -> "Sending"
            m.deliveryState == MessageDeliveryState.READ || m.readAt != null || m.isRead -> "Read"
            m.deliveryState == MessageDeliveryState.DELIVERED -> "Delivered"
            else -> "Sent"
        }
    Box(
        modifier =
            modifier.semantics {
                contentDescription = a11y
            },
    ) {
        when {
            m.deliveryState == MessageDeliveryState.ERROR ->
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp),
                )
            m.deliveryState == MessageDeliveryState.PENDING ->
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = baseTint.copy(alpha = baseTint.alpha * 0.38f),
                    modifier = Modifier.size(14.dp),
                )
            m.deliveryState == MessageDeliveryState.READ || m.readAt != null || m.isRead ->
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = readTint,
                    modifier = Modifier.size(17.dp),
                )
            m.deliveryState == MessageDeliveryState.DELIVERED ->
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = baseTint,
                    modifier = Modifier.size(17.dp),
                )
            else ->
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = baseTint,
                    modifier = Modifier.size(14.dp),
                )
        }
    }
}
