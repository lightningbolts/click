@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.ChatDropReply
import compose.project.click.click.ui.drops.ClickDropPixelatedImage
import compose.project.click.click.ui.drops.rememberDecodedBitmap
import compose.project.click.click.util.readChatMediaVaultBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Replied to your drop" above a story reply (sheet 07 G2-8; iOS `DropReplyViews.swift`). The drop
 * is referenced by ID only: its thumbnail comes from this device's own shared-drop cache (the
 * original once developed, else the pixelated teaser), or a camera glyph when it has neither.
 */
@Composable
internal fun ChatDropReplyQuote(
    reply: ChatDropReply,
    mine: Boolean,
    maxWidth: Dp,
) {
    val cached by produceState<Pair<ByteArray?, ByteArray?>>(null to null, reply.dropId) {
        value =
            withContext(Dispatchers.Default) {
                val original = readChatMediaVaultBytes("shared-drop-${reply.dropId}-original", "jpg")
                original to if (original == null) readChatMediaVaultBytes("shared-drop-${reply.dropId}-preview", "jpg") else null
            }
    }
    val photo = rememberDecodedBitmap(cached.first, 160)
    val teaser = rememberDecodedBitmap(cached.second, 160)
    val tint = if (mine) Color.White else Color.Unspecified
    Row(
        modifier =
            Modifier
                .widthIn(max = maxWidth)
                .padding(bottom = ChatBubbleTokens.replyAboveMediaSpacing)
                .clip(RoundedCornerShape(ChatBubbleTokens.replyBlockCorner))
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp, 48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            when {
                photo != null -> Image(photo, null, Modifier.size(36.dp, 48.dp), contentScale = ContentScale.Crop)
                teaser != null -> ClickDropPixelatedImage(teaser, Modifier.size(36.dp, 48.dp), contentDescription = null)
                else -> Icon(Icons.Filled.PhotoCamera, null, tint = tint.takeIf { it != Color.Unspecified } ?: Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = reply.label(mine),
            style = chatBubbleReplyLabelStyle(),
            color = if (mine) Color.White.copy(alpha = 0.75f) else Color.Unspecified,
        )
    }
}
