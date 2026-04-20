package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Image-bubble rendering for both encrypted (E2EE) and plain photo messages.
 * Long-press / message actions are handled on the parent bubble surface.
 */

private const val SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES = 220

/**
 * Process-wide cache of decoded secure-chat image bitmaps keyed by
 * message id. Keeps scrolling back through encrypted photo threads
 * from re-running the CPU-heavy decode on every recomposition.
 */
internal val secureChatImageBitmapCache: LruMemoryCache<String, ImageBitmap> =
    LruMemoryCache(SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES)

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
 */
@Composable
internal fun ChatBubblePhotoContent(
    mediaUrl: String,
    message: Message,
    isEncrypted: Boolean,
    secureState: SecureChatMediaLoadState?,
    modifier: Modifier = Modifier,
    borderIfReceived: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isEncrypted && secureState?.loading == true -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = chatBubbleScaledDp(180f), max = chatBubbleScaledDp(330f)),
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
                    modifier = Modifier.padding(chatBubbleScaledDp(18f)),
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
                    val up = secureState?.uploadProgress
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = chatBubbleScaledDp(330f)),
                    ) {
                        Image(
                            bitmap = cachedBitmap,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (borderIfReceived) {
                                        Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(chatBubbleScaledDp(24f)))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clip(RoundedCornerShape(chatBubbleScaledDp(24f))),
                        )
                        if (up != null && up < 1f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(chatBubbleScaledDp(24f)))
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { up },
                                    modifier = Modifier.size(chatBubbleScaledDp(44f)),
                                    strokeWidth = chatBubbleScaledDp(4f),
                                    color = PrimaryBlue,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Could not render image",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(chatBubbleScaledDp(18f)),
                    )
                }
            }
            isEncrypted -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = chatBubbleScaledDp(180f), max = chatBubbleScaledDp(330f)),
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
                        .heightIn(max = chatBubbleScaledDp(330f))
                        .then(
                            if (borderIfReceived) {
                                Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), RoundedCornerShape(chatBubbleScaledDp(24f)))
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(chatBubbleScaledDp(24f))),
                )
            }
        }
    }
}
