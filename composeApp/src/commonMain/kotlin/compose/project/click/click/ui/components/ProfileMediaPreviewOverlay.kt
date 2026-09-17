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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioBubble // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioChromeKind // pragma: allowlist secret
import compose.project.click.click.ui.chat.persistLightboxImageToGallery // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatPresenceSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareLightboxImage // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
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
    var portalDismissing by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(mediaPreviewVisible) {
        if (mediaPreviewVisible) portalDismissing = false
    }
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
    val mediaActions =
        if (isImage) {
            mediaLightboxShareActions(onSave = saveImage, onShare = shareImage)
        } else {
            emptyList()
        }

    PlatformOverlayAbovePresentedSheets(
        liftAbovePresentedSheets = isIOS && mediaPreviewVisible,
        dismissing = isIOS && portalDismissing,
    ) {
        GlassFullscreenMediaOverlay(
            visible = mediaPreviewVisible,
            onDismissRequest = { onDismissPreview() },
            modifier = Modifier.fillMaxSize(),
            scrimAlpha = 1f,
            nativeTrailingActions = if (isIOS) emptyList() else mediaActions,
            useNativeChrome = !isIOS,
            onDismissTransitionStarted = {
                if (isIOS) portalDismissing = true
            },
            chrome = { requestDismiss ->
                if (isIOS) {
                    CompositionLocalProvider(LocalIsDarkMode provides true) {
                        ProfileMediaPortalNativeChrome(
                            sourceChat = sourceChat,
                            onlineUsers = onlineUsers,
                            fallbackChatId = connectionChatId,
                            onClose = requestDismiss,
                            trailingActions = mediaActions,
                        )
                    }
                }
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
                if (!isIOS) {
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
 * Profile media lives in a detached full-screen Compose host above the native profile sheet. Bind
 * that host into the same persistent iOS navigation layer as chat media instead of drawing a second
 * Compose approximation of Liquid Glass. The shared layer remains mounted through the lightbox exit
 * so the whole UIKit portal can fade as one surface before ownership returns to the profile sheet.
 */
@Composable
private fun ProfileMediaPortalNativeChrome(
    sourceChat: ChatWithDetails?,
    onlineUsers: Set<String>,
    fallbackChatId: String?,
    onClose: () -> Unit,
    trailingActions: List<NativeChromeAction>,
) {
    val isGroup = sourceChat?.groupClique != null
    val title =
        if (isGroup) {
            sourceChat
                ?.groupClique
                ?.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Group"
        } else {
            sourceChat
                ?.otherUser
                ?.name
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "Connection"
        }
    val peerId = if (isGroup) null else sourceChat?.otherUser?.id
    val presenceOnline = peerId?.let { it in onlineUsers }
    val identity =
        sourceChat?.let { chat ->
            NativeChromeIdentity(
                displayName = title,
                email = if (isGroup) null else chat.otherUser.email,
                avatarUrl = if (isGroup) chat.groupClique?.avatarUrl else chat.otherUser.image,
                userId =
                    if (isGroup) {
                        chat.groupClique?.groupId ?: fallbackChatId.orEmpty()
                    } else {
                        chat.otherUser.id
                    },
                onClick = {},
            )
        }

    BindPlatformNativeNavigationBar(
        title = title,
        subtitle = rememberChatPresenceSubtitle(sourceChat, isGroup),
        presenceOnline = presenceOnline,
        identity = identity,
        onNavigateBack = onClose,
        nativeTrailingActions = trailingActions,
        collapseFraction = 1f,
        leadingClose = true,
    )
}
