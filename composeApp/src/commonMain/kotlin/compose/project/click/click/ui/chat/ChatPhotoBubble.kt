package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.Message
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.util.LruMemoryCache
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.utils.toImageBitmap
import compose.project.click.click.viewmodel.SecureChatMediaLoadState

/**
 * Image-bubble rendering for both encrypted (E2EE) and plain photo
 * messages, plus the three-dot overflow button that sits on top of them.
 *
 * Extracted verbatim from ConnectionsScreen.kt so the large and
 * branchy render logic has a dedicated home. Behavior is unchanged —
 * the decoded-bitmap cache, the error/loading branches, and the
 * overflow-button overlay all match the original.
 */

private const val SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES = 220

/**
 * Process-wide cache of decoded secure-chat image bitmaps keyed by
 * message id. Keeps scrolling back through encrypted photo threads
 * from re-running the CPU-heavy decode on every recomposition.
 */
internal val secureChatImageBitmapCache: LruMemoryCache<String, ImageBitmap> =
    LruMemoryCache(SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES)

/** Three-dot overflow affordance shown on chat message bubbles. */
@Composable
internal fun ChatMessageOverflowButton(
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(30.dp),
    ) {
        Icon(
            Icons.Outlined.MoreVert,
            contentDescription = "Message actions",
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Renders the photo portion of a chat message bubble, routing between
 * the encrypted (E2EE) and plain paths:
 *
 * - E2EE loading — spinner
 * - E2EE error — inline error text
 * - E2EE bytes available — decode-and-cache via
 *   [secureChatImageBitmapCache], render with [Image]
 * - E2EE bytes not yet available — spinner
 * - Plain — [AsyncImage] directly from [mediaUrl]
 *
 * On top of the image, a semi-transparent disc hosts the overflow
 * button so the affordance stays readable on any photo background.
 */
@Composable
internal fun ChatBubblePhotoContent(
    mediaUrl: String,
    message: Message,
    isEncrypted: Boolean,
    secureState: SecureChatMediaLoadState?,
    modifier: Modifier = Modifier,
    overflowTint: Color,
    onOverflow: () -> Unit,
    useLightOverflowContrast: Boolean,
    borderIfReceived: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isEncrypted && secureState?.loading == true -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            isEncrypted && secureState?.error?.isNotBlank() == true -> {
                Text(
                    text = secureState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
            isEncrypted && secureState?.imageBytes != null -> {
                val cachedBitmap = remember(message.id, secureState.imageBytes) {
                    secureChatImageBitmapCache.get(message.id) ?: run {
                        runCatching { secureState.imageBytes!!.toImageBitmap() }
                            .onFailure { e ->
                                println("ChatBubblePhotoContent: failed to decode decrypted image for message=${message.id}: ${e.redactedRestMessage()}")
                            }
                            .getOrNull()
                            ?.also { bmp -> secureChatImageBitmapCache.put(message.id, bmp) }
                    }
                }
                if (cachedBitmap != null) {
                    Image(
                        bitmap = cachedBitmap,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .then(
                                if (borderIfReceived) {
                                    Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Text(
                        text = "Could not render image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            isEncrypted -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .then(
                            if (borderIfReceived) {
                                Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (useLightOverflowContrast) Color.Black.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.22f),
                    )
                    .align(Alignment.Center),
            )
            ChatMessageOverflowButton(
                onClick = onOverflow,
                tint = overflowTint,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
