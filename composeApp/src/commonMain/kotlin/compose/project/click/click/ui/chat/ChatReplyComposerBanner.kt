package compose.project.click.click.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.replySnippetForMetadata
import compose.project.click.click.ui.theme.LocalPlatformStyle

@Composable
internal fun ChatReplyComposerBanner(
    replyingTo: MessageWithUser?,
    editingMessageId: String?,
    onCancel: () -> Unit,
) {
    val composerStyle = LocalPlatformStyle.current
    val visible = replyingTo != null && editingMessageId == null
    var content by remember { mutableStateOf(replyingTo) }
    if (replyingTo != null && editingMessageId == null) {
        content = replyingTo
    }
    val shape = RoundedCornerShape(if (composerStyle.isIOS) 12.dp else 14.dp)

    AnimatedVisibility(
        visible = visible,
        enter =
            expandVertically(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)),
        exit =
            shrinkVertically(
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        label = "replyComposerBanner",
    ) {
        val target = content
        if (target != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color =
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = if (composerStyle.isIOS) 0.45f else 0.55f,
                        ),
                    border =
                        if (composerStyle.isIOS) {
                            BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                            )
                        } else {
                            null
                        },
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replying to ${target.user.name ?: "message"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                replySnippetForMetadata(target.message.content, maxLen = 100),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel reply",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
