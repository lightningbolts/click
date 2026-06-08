package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.disposableRollCollaborationTtlIso
import compose.project.click.click.data.models.isDisposableRollLocked
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.util.LruMemoryCache
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.utils.toImageBitmap
import compose.project.click.click.viewmodel.SecureChatMediaLoadState

private val chatPhotoAttachmentShape = RoundedCornerShape(16.dp)

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
    mediaUrl: String?,
    message: Message,
    isEncrypted: Boolean,
    secureState: SecureChatMediaLoadState?,
    modifier: Modifier = Modifier,
    borderIfReceived: Boolean = false,
    onPhotoClick: (() -> Unit)? = null,
    onPhotoLongPress: (() -> Unit)? = null,
) {
    val rollLocked = message.isDisposableRollLocked()
    val canExpand = onPhotoClick != null && !rollLocked
    val photoGestureModifier = when {
        canExpand && onPhotoLongPress != null -> {
            Modifier.combinedClickable(
                indication = null,
                interactionSource = remember(message.id) { MutableInteractionSource() },
                onClick = onPhotoClick!!,
                onLongClick = onPhotoLongPress,
            )
        }
        canExpand -> {
            Modifier.combinedClickable(
                indication = null,
                interactionSource = remember(message.id) { MutableInteractionSource() },
                onClick = onPhotoClick!!,
            )
        }
        onPhotoLongPress != null -> {
            Modifier.combinedClickable(
                indication = null,
                interactionSource = remember(message.id) { MutableInteractionSource() },
                onClick = {},
                onLongClick = onPhotoLongPress,
            )
        }
        else -> Modifier
    }
    val countdownLabel = remember(message.id, rollLocked) {
        if (!rollLocked) return@remember null
        val ttlIso = message.disposableRollCollaborationTtlIso() ?: return@remember "Locked"
        val ttl = runCatching { Instant.parse(ttlIso) }.getOrNull() ?: return@remember "Locked"
        val remainMs = (ttl.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
        val totalMin = remainMs / 60_000L
        val hours = totalMin / 60L
        val mins = totalMin % 60L
        if (hours > 0) "Reveals in ${hours}h ${mins}m" else "Reveals in ${mins}m"
    }
    Box(modifier = modifier.fillMaxWidth()) {
        val localPreviewBytes = secureState?.imageBytes
        val cachedBitmap = remember(message.id) { secureChatImageBitmapCache.get(message.id) }
        when {
            cachedBitmap != null -> {
                PhotoBitmapContent(
                    bitmap = cachedBitmap,
                    rollLocked = rollLocked,
                    countdownLabel = countdownLabel,
                    borderIfReceived = borderIfReceived,
                    photoGestureModifier = photoGestureModifier,
                    uploadProgress = secureState?.uploadProgress,
                )
            }
            localPreviewBytes != null -> {
                val displayBitmap = remember(message.id) {
                    runCatching { localPreviewBytes.toImageBitmap() }
                        .onFailure { e ->
                            println("ChatBubblePhotoContent: failed to decode local preview for message=${message.id}: ${e.redactedRestMessage()}")
                        }
                        .getOrNull()
                        ?.also { bmp -> secureChatImageBitmapCache.put(message.id, bmp) }
                }
                if (displayBitmap != null) {
                    PhotoBitmapContent(
                        bitmap = displayBitmap,
                        rollLocked = rollLocked,
                        countdownLabel = countdownLabel,
                        borderIfReceived = borderIfReceived,
                        photoGestureModifier = photoGestureModifier,
                        uploadProgress = secureState?.uploadProgress,
                    )
                } else {
                    SecurePhotoLoadingPlaceholder()
                }
            }
            isEncrypted && secureState?.loading == true -> {
                SecurePhotoLoadingPlaceholder()
            }
            isEncrypted && secureState?.error?.isNotBlank() == true -> {
                Text(
                    text = secureState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(chatBubbleScaledDp(18f)),
                )
            }
            isEncrypted -> SecurePhotoLoadingPlaceholder()
            !mediaUrl.isNullOrBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = chatBubbleScaledDp(330f)),
                ) {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(photoGestureModifier)
                            .then(
                                if (borderIfReceived) {
                                    Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), chatPhotoAttachmentShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clip(chatPhotoAttachmentShape)
                            .then(if (rollLocked) Modifier.blur(25.dp) else Modifier),
                    )
                    if (rollLocked && countdownLabel != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(chatBubbleScaledDp(24f)))
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = countdownLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            else -> SecurePhotoLoadingPlaceholder()
        }
    }
}

@Composable
private fun SecurePhotoLoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = chatBubbleScaledDp(120f), max = chatBubbleScaledDp(220f))
            .clip(chatPhotoAttachmentShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = PrimaryBlue,
        )
    }
}

@Composable
private fun PhotoBitmapContent(
    bitmap: ImageBitmap,
    rollLocked: Boolean,
    countdownLabel: String?,
    borderIfReceived: Boolean,
    photoGestureModifier: Modifier,
    uploadProgress: Float?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = chatBubbleScaledDp(330f)),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .then(photoGestureModifier)
                .then(
                    if (borderIfReceived) {
                        Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), chatPhotoAttachmentShape)
                    } else {
                        Modifier
                    },
                )
                .clip(chatPhotoAttachmentShape)
                .then(if (rollLocked) Modifier.blur(25.dp) else Modifier),
        )
        if (rollLocked && countdownLabel != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(chatPhotoAttachmentShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
        val up = uploadProgress
        if (up != null && up < 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(chatPhotoAttachmentShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)),
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
}
