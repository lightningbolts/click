@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.disposableRollRevealAtIso
import compose.project.click.click.data.models.isDisposableRollLocked
import compose.project.click.click.data.repository.GatedDropOriginals
import compose.project.click.click.data.repository.isGatedDropAwaitingOriginal
import compose.project.click.click.platform.rememberReduceMotionEnabled
import compose.project.click.click.ui.drops.ClickDropDevelopEffect
import compose.project.click.click.ui.drops.chatClickDropLabel
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.util.LruMemoryCache
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.utils.softBlurredForLockedDrop
import compose.project.click.click.utils.toChatDisplayImageBitmap
import compose.project.click.click.viewmodel.SecureChatMediaLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private val chatPhotoAttachmentShape = RoundedCornerShape(16.dp)

/**
 * Image-bubble rendering for both encrypted (E2EE) and plain photo messages.
 * Long-press / message actions are handled on the parent bubble surface.
 */

private const val SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES = 120
private const val LOCKED_DROP_BLUR_CACHE_MAX_ENTRIES = 48

/** At most two heavy secure-image decodes at once — keeps fling/backswipe on the main thread. */
private val chatImageDecodeGate = kotlinx.coroutines.sync.Semaphore(permits = 2)

/**
 * Process-wide cache of decoded secure-chat image bitmaps keyed by
 * message id. Keeps scrolling back through encrypted photo threads
 * from re-running the CPU-heavy decode on every recomposition.
 */
internal val secureChatImageBitmapCache: LruMemoryCache<String, ImageBitmap> =
    LruMemoryCache(SECURE_CHAT_IMAGE_BITMAP_CACHE_MAX_ENTRIES)

/** Static pre-blurred locked Drop bitmaps — never use live Modifier.blur while swiping. */
private val lockedDropBlurBitmapCache: LruMemoryCache<String, ImageBitmap> =
    LruMemoryCache(LOCKED_DROP_BLUR_CACHE_MAX_ENTRIES)

/** Bump when blur strength changes so stale weak pixels are not reused. */
private const val LOCKED_DROP_BLUR_CACHE_VERSION = 5

private fun lockedDropCacheKey(
    messageId: String,
    source: ImageBitmap,
): String = "$messageId#${source.width}x${source.height}#v$LOCKED_DROP_BLUR_CACHE_VERSION"

private fun lockedDropDisplayBitmap(
    messageId: String,
    source: ImageBitmap,
): ImageBitmap {
    val key = lockedDropCacheKey(messageId, source)
    lockedDropBlurBitmapCache.get(key)?.let { return it }
    val blurred = runCatching { source.softBlurredForLockedDrop() }.getOrDefault(source)
    lockedDropBlurBitmapCache.put(key, blurred)
    return blurred
}

/** Prefer localSentAt identity so temp→server id swaps do not reset bitmap slots. */
private fun photoBitmapSlotKey(message: Message): String {
    val stamp = message.localSentAt
    return if (stamp != null) "out-${message.user_id}-$stamp" else message.id
}

/** Re-key blurred locked-drop bitmaps when optimistic temp ids become server ids. */
internal fun migrateLockedDropBlurCacheKey(
    tempId: String,
    serverMessageId: String,
) {
    val prefix = "$tempId#"
    lockedDropBlurBitmapCache.entriesSnapshot().forEach { (key, bmp) ->
        if (key.startsWith(prefix)) {
            val migrated = key.replaceFirst(tempId, serverMessageId)
            lockedDropBlurBitmapCache.put(migrated, bmp)
            lockedDropBlurBitmapCache.remove(key)
        }
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
    clickDropControls: ClickDropBubbleControls? = null,
) {
    val gatedOriginals by GatedDropOriginals.loaded.collectAsState()
    // A gated drop stays pixelated until its original is on screen, even after its reveal time.
    val awaitingOriginal = message.isGatedDropAwaitingOriginal(gatedOriginals)
    val rollLocked = message.isDisposableRollLocked() || awaitingOriginal
    val canExpand = onPhotoClick != null && !rollLocked
    // A ready drop develops on tap (iOS `ClickDropImageView.lockedImage`).
    val developClick =
        clickDropControls
            ?.takeIf { awaitingOriginal && it.state == ClickDropDevelopState.Ready && !it.developing }
            ?.onDevelop
    val expandClick = onPhotoClick.takeIf { canExpand } ?: developClick
    val tapLabel = if (developClick != null && !canExpand) "Develop Click Drop" else "Open photo"
    val photoLongPress = onPhotoLongPress
    val photoGestureModifier =
        if (expandClick != null || photoLongPress != null) {
            Modifier
                .semantics(mergeDescendants = true) {
                    if (expandClick != null) {
                        onClick(label = tapLabel) {
                            expandClick()
                            true
                        }
                    }
                    if (photoLongPress != null) {
                        onLongClick(label = "Message actions") {
                            photoLongPress()
                            true
                        }
                    }
                }.onKeyEvent { event ->
                    if (expandClick != null && (event.key == Key.Enter || event.key == Key.Spacebar)) {
                        if (event.type == KeyEventType.KeyUp) expandClick()
                        true
                    } else {
                        false
                    }
                }.focusable()
                .pointerInput(message.id, expandClick, photoLongPress) {
                    val tap = expandClick
                    val longPress = photoLongPress
                    if (longPress != null) {
                        detectTapGestures(
                            onTap = { tap?.invoke() },
                            onLongPress = { longPress.invoke() },
                        )
                    } else if (tap != null) {
                        detectTapGestures(onTap = { tap.invoke() })
                    }
                }
        } else {
            Modifier
        }
    val dropLabelState = clickDropControls?.state
    val dropDeveloping = clickDropControls?.developing == true
    val countdownLabel =
        remember(message.id, rollLocked, dropLabelState, dropDeveloping) {
            if (!rollLocked) return@remember null
            if (awaitingOriginal && dropLabelState != null) return@remember chatClickDropLabel(dropLabelState, dropDeveloping)
            val ttlIso = message.disposableRollRevealAtIso() ?: return@remember "Locked"
            val ttl = runCatching { Instant.parse(ttlIso) }.getOrNull() ?: return@remember "Locked"
            val remainMs = (ttl.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
            if (remainMs == 0L && awaitingOriginal) return@remember "Developing…"
            val totalMin = remainMs / 60_000L
            val hours = totalMin / 60L
            val mins = totalMin % 60L
            if (hours > 0) "Reveals in ${hours}h ${mins}m" else "Reveals in ${mins}m"
        }
    Box(modifier = modifier.fillMaxWidth()) {
        val localPreviewBytes = secureState?.imageBytes
        val bitmapSlotKey =
            remember(message.id, message.localSentAt, message.user_id) {
                photoBitmapSlotKey(message)
            }
        // Stable bitmap slot — keyed by localSentAt when present so temp→server id swaps
        // do not remount into a spinner (Click Drop send flicker).
        var displayBitmap by remember(bitmapSlotKey) {
            mutableStateOf(secureChatImageBitmapCache.get(message.id))
        }
        var lockedBitmap by remember(bitmapSlotKey, rollLocked) {
            val src = secureChatImageBitmapCache.get(message.id)
            val warmed =
                if (rollLocked && src != null) {
                    lockedDropBlurBitmapCache.get(lockedDropCacheKey(message.id, src))
                } else {
                    null
                }
            mutableStateOf(warmed)
        }
        // Re-read cache on every composition without resetting state when the item remounts
        // with the same slot key after a brief dispose (reply banner / back-swipe layout).
        val cachedBitmap = secureChatImageBitmapCache.get(message.id)
        val bitmap = displayBitmap ?: cachedBitmap
        LaunchedEffect(bitmapSlotKey, message.id, localPreviewBytes, rollLocked) {
            suspend fun ensureLocked(source: ImageBitmap) {
                if (!rollLocked) {
                    lockedBitmap = null
                    return
                }
                val key = lockedDropCacheKey(message.id, source)
                lockedDropBlurBitmapCache.get(key)?.let {
                    lockedBitmap = it
                    return
                }
                val blurred =
                    withContext(Dispatchers.Default) {
                        lockedDropDisplayBitmap(message.id, source)
                    }
                lockedBitmap = blurred
            }

            secureChatImageBitmapCache.get(message.id)?.let {
                if (displayBitmap !== it) displayBitmap = it
                ensureLocked(it)
                return@LaunchedEffect
            }
            val bytes = localPreviewBytes ?: return@LaunchedEffect
            // Decode + optional pixelation off the main thread so fling/back stay at 120Hz.
            val decoded =
                withContext(Dispatchers.Default) {
                    chatImageDecodeGate.withPermit {
                        runCatching { bytes.toChatDisplayImageBitmap() }
                            .onFailure { e ->
                                println(
                                    "ChatBubblePhotoContent: failed to decode local preview for message=${message.id}: ${e.redactedRestMessage()}",
                                )
                            }.getOrNull()
                    }
                } ?: return@LaunchedEffect
            secureChatImageBitmapCache.put(message.id, decoded)
            displayBitmap = decoded
            ensureLocked(decoded)
        }
        when {
            bitmap != null && rollLocked -> {
                val locked = lockedBitmap
                if (locked != null) {
                    PhotoBitmapContent(
                        bitmap = locked,
                        rollLocked = true,
                        countdownLabel = countdownLabel,
                        borderIfReceived = borderIfReceived,
                        photoGestureModifier = photoGestureModifier,
                        uploadProgress = secureState?.uploadProgress,
                    )
                } else {
                    // Keep 4:3 placeholder until pixelation finishes — never flash a clear frame.
                    SecurePhotoLoadingPlaceholder()
                }
            }
            bitmap != null -> {
                // Developed just now: the photo blooms out of its pixels once, then stays.
                // Only once the original is decoded (the cache is emptied when it arrives).
                val blooms =
                    clickDropControls?.fresh == true &&
                        !message.isGatedDropAwaitingOriginal(gatedOriginals) &&
                        cachedBitmap != null &&
                        cachedBitmap === bitmap
                val reduceMotion = rememberReduceMotionEnabled()
                PhotoBitmapContent(
                    bitmap = bitmap,
                    rollLocked = false,
                    countdownLabel = countdownLabel,
                    borderIfReceived = borderIfReceived,
                    photoGestureModifier = photoGestureModifier,
                    uploadProgress = secureState?.uploadProgress,
                    overlay =
                        if (blooms) {
                            {
                                ClickDropDevelopEffect(
                                    original = bitmap,
                                    reduceMotion = reduceMotion,
                                    modifier = Modifier.matchParentSize().clip(chatPhotoAttachmentShape),
                                    onFinished = { clickDropControls?.onShown?.invoke() },
                                )
                            }
                        } else {
                            null
                        },
                )
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
                val platformContext = LocalPlatformContext.current
                val request =
                    remember(mediaUrl) {
                        ImageRequest
                            .Builder(platformContext)
                            .data(mediaUrl)
                            .size(Size(720, 540))
                            .build()
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .heightIn(max = chatBubbleScaledDp(330f))
                            .clip(chatPhotoAttachmentShape)
                            .then(photoGestureModifier),
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (borderIfReceived) {
                                        Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), chatPhotoAttachmentShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                // No live Modifier.blur — flickers hard under reply/back translation.
                                .then(
                                    if (rollLocked) {
                                        Modifier.graphicsLayer { alpha = 0.28f }
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                    if (rollLocked && countdownLabel != null) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(chatBubbleScaledDp(24f)))
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f)),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .heightIn(min = chatBubbleScaledDp(120f), max = chatBubbleScaledDp(330f))
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
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .heightIn(max = chatBubbleScaledDp(330f))
                .clip(chatPhotoAttachmentShape)
                .then(photoGestureModifier),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Photo",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (borderIfReceived) {
                            Modifier.border(1.dp, PrimaryBlue.copy(alpha = 0.18f), chatPhotoAttachmentShape)
                        } else {
                            Modifier
                        },
                    ).then(
                        if (rollLocked) {
                            // Bitmap is already pre-blurred; only dim — no live RenderEffect.
                            Modifier.graphicsLayer { alpha = 0.92f }
                        } else {
                            Modifier
                        },
                    ),
        )
        overlay?.invoke(this)
        if (rollLocked && countdownLabel != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(chatPhotoAttachmentShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
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
                modifier =
                    Modifier
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

/**
 * A gated Click Drop bubble's develop state and actions (iOS `ClickDropControls`): the state the
 * server gave this viewer, whether a develop is in flight, and whether it was developed on this
 * screen just now (play the bloom once, then [onShown]).
 */
internal data class ClickDropBubbleControls(
    val state: ClickDropDevelopState,
    val developing: Boolean,
    val fresh: Boolean,
    val onDevelop: () -> Unit,
    val onShown: () -> Unit,
)
