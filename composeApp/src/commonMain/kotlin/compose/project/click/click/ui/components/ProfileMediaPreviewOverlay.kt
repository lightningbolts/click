@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import compose.project.click.click.ui.chat.persistLightboxImageToGallery // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareLightboxImage // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
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
        val isIOS = LocalPlatformStyle.current.isIOS
        val isImage = media.mediaType == ProfileSheetMediaType.Image
        val saveImage: () -> Unit = {
            scope.launch {
                val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                val decrypted =
                    if (url.isNotBlank() &&
                        media.isEncrypted &&
                        !connectionChatId.isNullOrBlank() &&
                        !effectiveViewerUserId.isNullOrBlank()
                    ) {
                        connectionRepository.downloadAndDecryptChatMedia(
                            chatId = connectionChatId!!,
                            viewerUserId = effectiveViewerUserId!!,
                            mediaUrl = url,
                        )
                    } else {
                        null
                    }
                persistLightboxImageToGallery(url, decrypted, media.mimeType)
                onDismissPreview()
            }
        }
        val shareImage: () -> Unit = {
            scope.launch {
                val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                val decrypted =
                    if (url.isNotBlank() &&
                        media.isEncrypted &&
                        !connectionChatId.isNullOrBlank() &&
                        !effectiveViewerUserId.isNullOrBlank()
                    ) {
                        connectionRepository.downloadAndDecryptChatMedia(
                            chatId = connectionChatId!!,
                            viewerUserId = effectiveViewerUserId!!,
                            mediaUrl = url,
                        )
                    } else {
                        null
                    }
                shareLightboxImage(url, decrypted, media.mimeType)
                onDismissPreview()
            }
        }
        GlassFullscreenMediaOverlay(
            visible = mediaPreviewVisible,
            onDismissRequest = { onDismissPreview() },
            modifier = Modifier.fillMaxSize(),
            scrimAlpha = 1f,
            nativeTrailingActions =
                if (isImage) {
                    mediaLightboxShareActions(onSave = saveImage, onShare = shareImage)
                } else {
                    emptyList()
                },
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
                MediaLightboxTopChrome(
                    onClose = { onDismissPreview() },
                    showClose = !isIOS,
                    trailing = {
                        if (!isIOS && isImage) {
                            MediaLightboxSaveShareTrailing(
                                onSave = saveImage,
                                onShare = shareImage,
                            )
                        }
                    },
                )
            }
        }
    }
}
