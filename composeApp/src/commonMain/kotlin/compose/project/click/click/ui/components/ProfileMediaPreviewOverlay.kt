@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
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
    val previewMedia = mediaPreviewModel ?: return
    val media = previewMedia
    val bitmapForPreview = resolvedMediaBitmaps[media.id]
    val isIOS = LocalPlatformStyle.current.isIOS
    val isImage = media.mediaType == ProfileSheetMediaType.Image
    val inboxRows by AppDataManager.inboxFeedChats.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    val sourceChat =
        remember(inboxRows, connectionChatId) {
            val chatId = connectionChatId?.trim().orEmpty()
            if (chatId.isBlank()) {
                null
            } else {
                inboxRows.firstOrNull { row ->
                    row.chat.id == chatId || row.connection.chat.id == chatId
                }
            }
        }
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
        }
    }

    PlatformOverlayAbovePresentedSheets(
        liftAbovePresentedSheets = isIOS && mediaPreviewVisible,
    ) {
        GlassFullscreenMediaOverlay(
            visible = mediaPreviewVisible,
            onDismissRequest = { onDismissPreview() },
            modifier = Modifier.fillMaxSize(),
            scrimAlpha = 1f,
            nativeTrailingActions =
                if (!isIOS && isImage) {
                    mediaLightboxShareActions(onSave = saveImage, onShare = shareImage)
                } else {
                    emptyList()
                },
            useNativeChrome = !isIOS,
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
                if (isIOS) {
                    ProfileMediaPortalTopChrome(
                        sourceChat = sourceChat,
                        onlineUsers = onlineUsers,
                        onClose = onDismissPreview,
                        onSave = saveImage,
                        onShare = shareImage,
                        showActions = isImage,
                    )
                } else {
                    MediaLightboxTopChrome(
                        onClose = { onDismissPreview() },
                        showClose = true,
                        trailing = {
                            if (isImage) {
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
}

/**
 * Profile media is portaled above a native profile sheet, so it cannot safely attach the app-global
 * UIKit nav layer. Mirror the chat media geometry inside the portal instead: close on the same
 * leading plane, chat identity in the middle, and Save/Share in one trailing capsule.
 */
@Composable
private fun ProfileMediaPortalTopChrome(
    sourceChat: ChatWithDetails?,
    onlineUsers: Set<String>,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    showActions: Boolean,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val resolvedTop = maxOf(statusTop, safeTop).takeIf { it > 0.dp } ?: 59.dp
    val buttonSize = NativeHeaderMetrics.ChromeButtonSizePt.dp
    val topGutter =
        ((NativeHeaderMetrics.CompactBarHeightPt - NativeHeaderMetrics.ChromeButtonSizePt) / 2.0).dp
    val isGroup = sourceChat?.groupClique != null
    val title =
        if (isGroup) {
            sourceChat?.groupClique?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Group"
        } else {
            sourceChat?.otherUser?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Connection"
        }
    val subtitle =
        if (isGroup) {
            val count = sourceChat?.groupMemberUsers?.size ?: 0
            "$count ${if (count == 1) "person" else "people"}"
        } else {
            val peerId = sourceChat?.otherUser?.id
            if (peerId != null && peerId in onlineUsers) "Online" else "Offline"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .zIndex(3f)
                .padding(
                    start = NativeHeaderMetrics.LeadingInsetPt.dp,
                    end = NativeHeaderMetrics.TrailingInsetPt.dp,
                    top = resolvedTop + topGutter,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClickCircularGlassIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
            size = buttonSize,
            tint = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        if (sourceChat != null) {
            if (isGroup) {
                GroupAvatar(
                    members = sourceChat.groupMemberUsers,
                    avatarSize = 40.dp,
                    avatarUrl = sourceChat.groupClique?.avatarUrl,
                )
            } else {
                ConnectionListUserAvatarFace(
                    displayName = sourceChat.otherUser.name,
                    email = sourceChat.otherUser.email,
                    avatarUrl = sourceChat.otherUser.image,
                    userId = sourceChat.otherUser.id,
                    modifier = Modifier.size(40.dp),
                    useCompactTypography = true,
                )
            }
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showActions) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier =
                    Modifier
                        .height(buttonSize)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClickCircularIconButton(
                    icon = Icons.Outlined.Download,
                    contentDescription = "Save",
                    onClick = onSave,
                    size = buttonSize - 4.dp,
                    iconSize = 22.dp,
                    tint = Color.White,
                )
                ClickCircularIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Share",
                    onClick = onShare,
                    size = buttonSize - 4.dp,
                    iconSize = 22.dp,
                    tint = Color.White,
                )
            }
        }
    }
}
