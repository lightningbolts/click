@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioBubble // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioChromeKind // pragma: allowlist secret
import compose.project.click.click.ui.chat.fetchImageBytesFromUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveChatImageToGallery // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareDecryptedImage // pragma: allowlist secret
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
        val bitmapForPreview = resolvedMediaBitmaps[media.id]
        GlassFullscreenMediaOverlay(
            visible = mediaPreviewVisible,
            onDismissRequest = { onDismissPreview() },
            modifier = Modifier.fillMaxSize(),
            scrimAlpha = 1f,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (media.mediaType == ProfileSheetMediaType.Image) {
                        val bitmap = bitmapForPreview
                        val resolvedUrl = resolvedMediaUrls[media.id] ?: media.mediaUrl
                        ClickZoomableMedia {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = it,
                                )
                            } else {
                                AsyncImage(
                                    model = resolvedUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = it,
                                )
                            }
                        }
                    } else {
                        val stream = resolvedMediaUrls[media.id] ?: media.mediaUrl
                        val local = resolvedAudioLocalPaths[media.id]
                        val canPlay =
                            !local.isNullOrBlank() ||
                                (stream?.isNotBlank() == true && !media.isEncrypted)
                        Column(
                            modifier = Modifier.padding(horizontal = ClickScreenSpacing.Horizontal),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (canPlay) {
                                ChatAudioBubble(
                                    mediaUrl = stream.orEmpty(),
                                    durationSeconds = media.durationSeconds,
                                    contentColor = Color.White,
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
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onDismissPreview() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (media.mediaType == ProfileSheetMediaType.Image) {
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
                            Text("Save", color = Color.White)
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
                            Text("Share", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
