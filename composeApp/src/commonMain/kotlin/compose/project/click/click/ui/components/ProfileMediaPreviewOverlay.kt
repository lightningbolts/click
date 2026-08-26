@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioBubble // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioChromeKind // pragma: allowlist secret
import compose.project.click.click.ui.chat.fetchImageBytesFromUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveChatImageToGallery // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareDecryptedImage // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import kotlinx.coroutines.launch

/** Full-screen media preview overlay for [ProfileBottomSheet]. */
@Composable
internal fun ProfileMediaPreviewOverlay(
    mediaPreviewVisible: Boolean,
    mediaPreviewModel: ProfileSheetMedia?,
    onDismissPreview: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    connectionRepository: ConnectionRepository,
    effectiveViewerUserId: String?,
    connectionChatId: String?,
    resolvedMediaUrls: Map<String, String>,
    resolvedMediaBitmaps: Map<String, ImageBitmap>,
    resolvedAudioLocalPaths: Map<String, String>,
) {
    val previewMedia = mediaPreviewModel
    if (previewMedia != null) {
        val media = previewMedia
        val previewImageFade = remember(media.id) { Animatable(0f) }
        val bitmapForPreview = resolvedMediaBitmaps[media.id]
        LaunchedEffect(media.id, media.mediaType, mediaPreviewVisible, bitmapForPreview) {
            when (media.mediaType) {
                ProfileSheetMediaType.Image -> {
                    if (!mediaPreviewVisible) {
                        previewImageFade.animateTo(
                            0f,
                            tween(280, easing = FastOutSlowInEasing),
                        )
                    } else if (bitmapForPreview != null) {
                        previewImageFade.snapTo(0f)
                        previewImageFade.animateTo(
                            1f,
                            tween(420, easing = FastOutSlowInEasing),
                        )
                    } else {
                        previewImageFade.snapTo(0f)
                    }
                }
                else -> previewImageFade.snapTo(1f)
            }
        }
        GlassFullscreenMediaOverlay(
            visible = mediaPreviewVisible,
            onDismissRequest = { onDismissPreview() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val previewShape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .clip(previewShape)
                        .border(1.dp, GlassSheetTokens.GlassBorder(), previewShape),
                shape = previewShape,
                color = GlassSheetTokens.OledBlack(),
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (media.mediaType == ProfileSheetMediaType.Image) {
                        val bitmap = bitmapForPreview
                        val resolvedUrl = resolvedMediaUrls[media.id] ?: media.mediaUrl
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .graphicsLayer { alpha = previewImageFade.value }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        } else {
                            AsyncImage(
                                model = resolvedUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                onLoading = {
                                    scope.launch {
                                        previewImageFade.snapTo(0f)
                                    }
                                },
                                onSuccess = {
                                    scope.launch {
                                        previewImageFade.snapTo(0f)
                                        previewImageFade.animateTo(
                                            1f,
                                            tween(420, easing = FastOutSlowInEasing),
                                        )
                                    }
                                },
                                onError = {
                                    scope.launch { previewImageFade.snapTo(0f) }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 360.dp)
                                        .graphicsLayer { alpha = previewImageFade.value }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    } else {
                        val stream = resolvedMediaUrls[media.id] ?: media.mediaUrl
                        val local = resolvedAudioLocalPaths[media.id]
                        val canPlay =
                            !local.isNullOrBlank() ||
                                (stream?.isNotBlank() == true && !media.isEncrypted)
                        if (canPlay) {
                            ChatAudioBubble(
                                mediaUrl = stream.orEmpty(),
                                durationSeconds = media.durationSeconds,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                accentColor = PrimaryBlue,
                                isEncrypted = false,
                                localFilePathForPlayback = local,
                                secureLoading = false,
                                secureError = null,
                                onRequestDecrypt = {},
                                mimeTypeHint = media.mimeType,
                                modifier = Modifier.fillMaxWidth(),
                                chromeKind = ChatAudioChromeKind.ProfileSurface,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Playback unavailable",
                                tint = MaterialTheme.colorScheme.error,
                                modifier =
                                    Modifier
                                        .padding(vertical = 12.dp)
                                        .size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (media.mediaType == ProfileSheetMediaType.Image) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onDismissPreview() }) {
                                Text("Close")
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                                        if (url.isNotBlank() &&
                                            media.isEncrypted &&
                                            !connectionChatId.isNullOrBlank() &&
                                            !effectiveViewerUserId.isNullOrBlank()
                                        ) {
                                            val bytes =
                                                connectionRepository.downloadAndDecryptChatMedia(
                                                    chatId = connectionChatId!!,
                                                    viewerUserId = effectiveViewerUserId!!,
                                                    mediaUrl = url,
                                                )
                                            if (bytes != null && bytes.isNotEmpty()) {
                                                saveChatImageToGallery(
                                                    imageUrl = url,
                                                    decryptedImageBytes = bytes,
                                                    mimeTypeHint = media.mimeType,
                                                )
                                            }
                                        } else if (url.isNotBlank()) {
                                            saveChatImageToGallery(imageUrl = url)
                                        }
                                        onDismissPreview()
                                    }
                                },
                            ) {
                                Text("Save to gallery")
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                                        val ext =
                                            when {
                                                media.mimeType?.contains("png", ignoreCase = true) == true -> "png"
                                                media.mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
                                                else -> "jpg"
                                            }
                                        if (url.isNotBlank()) {
                                            if (media.isEncrypted &&
                                                !connectionChatId.isNullOrBlank() &&
                                                !effectiveViewerUserId.isNullOrBlank()
                                            ) {
                                                val bytes =
                                                    connectionRepository.downloadAndDecryptChatMedia(
                                                        chatId = connectionChatId!!,
                                                        viewerUserId = effectiveViewerUserId!!,
                                                        mediaUrl = url,
                                                    )
                                                if (bytes != null && bytes.isNotEmpty()) {
                                                    shareDecryptedImage(bytes, "click_share.$ext")
                                                }
                                            } else {
                                                val bytes = fetchImageBytesFromUrl(url)
                                                if (bytes != null && bytes.isNotEmpty()) {
                                                    shareDecryptedImage(bytes, "click_share.$ext")
                                                }
                                            }
                                        }
                                        onDismissPreview()
                                    }
                                },
                            ) {
                                Text("Share")
                            }
                        }
                    } else {
                        TextButton(onClick = { onDismissPreview() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
