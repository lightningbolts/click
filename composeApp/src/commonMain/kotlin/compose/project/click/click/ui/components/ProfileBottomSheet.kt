package compose.project.click.click.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.api.ConnectionTabMessage
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.ui.chat.ChatAudioBubble
import compose.project.click.click.ui.chat.ChatAudioChromeKind
import compose.project.click.click.ui.chat.fetchImageBytesFromUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveChatImageToGallery // pragma: allowlist secret
import compose.project.click.click.ui.chat.shareDecryptedImage // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile
import compose.project.click.click.ui.chat.saveDecryptedAttachmentToDownloads // pragma: allowlist secret
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Phase 2 — C13: shared profile bottom sheet displayed when a map pin is tapped.
 *
 * Four subtabs backed by a [SecondaryTabRow] + [HorizontalPager]:
 * **Timeline · Media · Links · Files**. When [ProfileSheetState.userId] and
 * [ProfileSheetState.viewerUserId] are both provided, the Timeline subtab hydrates the
 * legacy profile rendering (interests, shared interests, availability intents, "Our
 * timeline" encounters) via [SupabaseRepository.fetchUserPublicProfile] — restoring the
 * data that was previously only available through the standalone
 * [UserProfileBottomSheet]. Media / Links / Files are derived client-side from
 * [ProfileSheetState.localMessages] because chat message content is E2EE on the wire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    state: ProfileSheetState,
    onMessage: () -> Unit,
    onNudge: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
    onDownloadFile: ((ProfileSheetFile) -> Unit)? = null,
) {
    val visibleTabs = remember {
        listOf(
            ProfileSheetTab.Timeline,
            ProfileSheetTab.Media,
            ProfileSheetTab.Links,
            ProfileSheetTab.Files,
        )
    }
    val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var selectedMediaForPreview by remember { mutableStateOf<ProfileSheetMedia?>(null) }
    var mediaPreviewVisible by remember { mutableStateOf(false) }
    var mediaPreviewModel by remember { mutableStateOf<ProfileSheetMedia?>(null) }

    LaunchedEffect(selectedMediaForPreview) {
        when (val m = selectedMediaForPreview) {
            null -> {
                if (mediaPreviewModel != null) {
                    mediaPreviewVisible = false
                    delay(360)
                    if (selectedMediaForPreview == null) {
                        mediaPreviewModel = null
                    }
                }
            }
            else -> {
                mediaPreviewModel = m
                mediaPreviewVisible = true
            }
        }
    }
    val selectedUserId = state.userId?.trim().orEmpty()
    val connectionRepository = remember { ConnectionRepository() }
    val appViewerUserId = AppDataManager.currentUser.collectAsState().value?.id?.trim()
    val effectiveViewerUserId = state.viewerUserId?.trim().takeIf { !it.isNullOrBlank() } ?: appViewerUserId
    var connectionLocalMessages by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<List<ProfileSheetLocalMessage>>(emptyList())
    }
    var connectionChatId by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<String?>(null)
    }
    var connectionTabMedia by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<List<ProfileSheetMedia>>(emptyList())
    }
    var connectionTabFiles by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<List<ProfileSheetFile>>(emptyList())
    }
    var resolvedMediaUrls by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var resolvedMediaBitmaps by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    }
    var resolvedAudioLocalPaths by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var profileTabsHydrating by remember { mutableStateOf(false) }
    var profileMediaResolving by remember { mutableStateOf(false) }

    LaunchedEffect(selectedUserId, state.connectionId, effectiveViewerUserId) {
        profileTabsHydrating = true
        try {
        val connectionId = state.connectionId?.trim().orEmpty()
        if (connectionId.isBlank()) {
            connectionLocalMessages = emptyList()
            connectionChatId = null
            connectionTabMedia = emptyList()
            connectionTabFiles = emptyList()
            return@LaunchedEffect
        }

        val fetched = if (!effectiveViewerUserId.isNullOrBlank()) {
            runCatching {
                connectionRepository.fetchDecryptedMessagesForProfileConnection(
                    connectionId = connectionId,
                    viewerUserId = effectiveViewerUserId,
                )
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        connectionLocalMessages = fetched
            .sortedBy { it.timeCreated }
            .map { msg ->
                ProfileSheetLocalMessage(
                    id = msg.id,
                    content = msg.content,
                    messageType = msg.messageType,
                    timestamp = Instant.fromEpochMilliseconds(msg.timeCreated).toString(),
                    metadata = msg.metadata,
                )
            }

        val tabsPayload = runCatching {
            connectionRepository.fetchConnectionTabs(connectionId).getOrNull()
        }.getOrNull()
        connectionChatId = tabsPayload?.chatId

        connectionTabMedia = tabsPayload?.media
            ?.mapNotNull { it.toProfileSheetMediaFromTab() }
            .orEmpty()
        connectionTabFiles = tabsPayload?.files
            ?.map { it.toProfileSheetFileFromTab() }
            .orEmpty()
        } finally {
            profileTabsHydrating = false
        }
    }

    val profileLocalMessages = remember(state.localMessages, connectionLocalMessages) {
        val chosen = if (connectionLocalMessages.isNotEmpty()) connectionLocalMessages else state.localMessages
        chosen.filterNot { it.content.isLikelyWireEncrypted() }
    }

    // Hydrate legacy profile data for the Timeline subtab whenever both ids are known.
    val repository = remember { SupabaseRepository() }
    var legacyProfile by remember(state.userId, state.viewerUserId) {
        mutableStateOf<UserPublicProfile?>(null)
    }
    var legacyLoading by remember(state.userId, state.viewerUserId) { mutableStateOf(false) }
    var legacyError by remember(state.userId, state.viewerUserId) { mutableStateOf<String?>(null) }
    val cachedLegacyProfile by remember(state.userId) {
        repository.observeCachedUserPublicProfile(state.userId.orEmpty())
    }.collectAsState(initial = repository.getCachedUserPublicProfile(state.userId.orEmpty()))

    LaunchedEffect(cachedLegacyProfile) {
        if (cachedLegacyProfile != null) {
            legacyProfile = cachedLegacyProfile
            legacyLoading = false
            legacyError = null
        }
    }

    LaunchedEffect(state.userId, state.viewerUserId) {
        val uid = state.userId?.trim()
        if (uid.isNullOrBlank()) {
            legacyProfile = null
            legacyLoading = false
            legacyError = null
            return@LaunchedEffect
        }
        legacyProfile = repository.getCachedUserPublicProfile(uid)
        legacyLoading = legacyProfile == null
        legacyError = null
        val result = runCatching {
            withContext(Dispatchers.Default) {
                repository.refreshUserPublicProfile(state.viewerUserId, uid)
            }
        }
        val refreshed = result.getOrNull()
        if (refreshed != null) {
            legacyProfile = refreshed
            legacyError = null
        } else if (legacyProfile == null) {
            legacyError = result.exceptionOrNull()?.message
        }
        legacyLoading = false
    }

    val localMediaMessages = remember(profileLocalMessages) {
        profileLocalMessages.filter {
            val type = it.messageType.lowercase()
            type == "image" ||
                type == "audio" ||
                it.hasMetadataMediaUrl()
        }
    }
    val localFileMessages = remember(profileLocalMessages) {
        profileLocalMessages.filter {
            val type = it.messageType.lowercase()
            type == "file" ||
                it.hasMetadataAttachmentV1() ||
                it.content.startsWith(AttachmentCrypto.ENVELOPE_PREFIX)
        }
    }
    val localLinkMessages = remember(profileLocalMessages) {
        profileLocalMessages.filter {
            it.messageType == "text" &&
                (it.content.contains("http://") || it.content.contains("https://"))
        }
    }

    val effectiveMedia = remember(localMediaMessages, connectionTabMedia, state.media) {
        mergeProfileMedia(
            localMediaMessages.mapNotNull { it.toProfileSheetMedia() } + connectionTabMedia + state.media,
        )
    }
    val effectiveFiles = remember(localFileMessages, connectionTabFiles, state.files) {
        mergeProfileFiles(
            localFileMessages.map { it.toProfileSheetFile() } + connectionTabFiles + state.files,
        )
    }
    val effectiveLinks = remember(localLinkMessages, state.links) {
        mergeProfileLinks(extractLinksFromLocalMessages(localLinkMessages) + state.links)
    }

    LaunchedEffect(effectiveMedia, connectionChatId, effectiveViewerUserId) {
        profileMediaResolving = true
        resolvedMediaUrls = emptyMap()
        resolvedMediaBitmaps = emptyMap()
        resolvedAudioLocalPaths = emptyMap()
        try {
            effectiveMedia.forEach { media ->
                val direct = media.mediaUrl?.trim().orEmpty()
                val fromPath = media.storagePath?.trim().orEmpty()
                val url = when {
                    direct.isNotBlank() -> direct
                    fromPath.isNotBlank() -> connectionRepository.getSignedChatAttachmentUrl(fromPath).orEmpty()
                    else -> ""
                }
                if (url.isBlank()) {
                    delay(10)
                    return@forEach
                }

                if (media.isEncrypted && !connectionChatId.isNullOrBlank() && !effectiveViewerUserId.isNullOrBlank()) {
                    val bytes = connectionRepository.downloadAndDecryptChatMedia(
                        chatId = connectionChatId.orEmpty(),
                        viewerUserId = effectiveViewerUserId,
                        mediaUrl = url,
                    )
                    if (bytes != null && bytes.isNotEmpty()) {
                        if (media.mediaType == ProfileSheetMediaType.Image) {
                            val bitmap = runCatching { bytes.toImageBitmap() }.getOrNull()
                            if (bitmap != null) {
                                resolvedMediaBitmaps = resolvedMediaBitmaps + (media.id to bitmap)
                                delay(14)
                                return@forEach
                            }
                        } else {
                            val ext = extensionFromMimeType(media.mimeType)
                            val localPath = writeSecureChatAudioTempFile(media.id, bytes, ext)
                            if (!localPath.isNullOrBlank()) {
                                resolvedAudioLocalPaths = resolvedAudioLocalPaths + (media.id to localPath)
                                delay(14)
                                return@forEach
                            }
                        }
                    }
                }

                if (media.isEncrypted) {
                    delay(12)
                    return@forEach
                }

                resolvedMediaUrls = resolvedMediaUrls + (media.id to url)
                delay(14)
            }
        } finally {
            profileMediaResolving = false
        }
    }

    val handleOpenLink: (String) -> Unit = remember(onOpenLink, uriHandler) {
        { url ->
            val normalized = normalizeExternalUri(url)
            runCatching { uriHandler.openUri(normalized) }
            onOpenLink?.invoke(normalized)
        }
    }
    val handleDownloadFile: (ProfileSheetFile) -> Unit = remember(onDownloadFile, connectionRepository) {
        { file ->
            scope.launch {
                var handled = false
                val path = file.attachmentPath?.trim().orEmpty()
                val key = file.attachmentKeyBase64?.trim().orEmpty()
                val sha = file.attachmentSha256Base64?.trim().orEmpty()

                if (path.isNotBlank() && key.isNotBlank() && sha.isNotBlank()) {
                    val plain = connectionRepository.downloadAttachmentPlaintext(
                        path = path,
                        fileMasterKeyBase64 = key,
                        expectedSha256Base64 = sha,
                    )
                    if (plain != null) {
                        handled = saveDecryptedAttachmentToDownloads(
                            bytes = plain,
                            fileName = file.fileName,
                            mimeType = file.mimeType,
                        ) != null
                    }
                }

                if (!handled) {
                    val directUrl = file.downloadUrl?.trim().orEmpty()
                    if (directUrl.isNotBlank()) {
                        runCatching { uriHandler.openUri(directUrl) }
                        handled = true
                    }
                }

                if (!handled && path.isNotBlank()) {
                    val signed = connectionRepository.getSignedChatAttachmentUrl(path)
                    if (!signed.isNullOrBlank()) {
                        runCatching { uriHandler.openUri(signed) }
                        handled = true
                    }
                }

                if (!handled) {
                    onDownloadFile?.invoke(file)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        ProfileSheetHeader(
            displayName = state.displayName,
            subtitle = state.subtitle,
            avatarUrl = state.avatarUrl,
            statusBadge = state.statusBadge,
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onMessage,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Message, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Message", fontWeight = FontWeight.SemiBold)
            }
            if (state.canNudge) {
                OutlinedButton(
                    onClick = onNudge,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Nudge", fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            visibleTabs.forEachIndexed { index, tab ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    selectedContentColor = PrimaryBlue,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 1.dp,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { pageIndex ->
            when (visibleTabs[pageIndex]) {
                ProfileSheetTab.Timeline -> TimelinePanel(
                    items = state.timeline,
                    legacyProfile = legacyProfile,
                    legacyLoading = legacyLoading,
                    legacyError = legacyError,
                    showLegacy = !state.userId.isNullOrBlank(),
                )
                ProfileSheetTab.Media -> MediaPanel(
                    items = effectiveMedia,
                    resolvedUrls = resolvedMediaUrls,
                    resolvedBitmaps = resolvedMediaBitmaps,
                    resolvedAudioLocalPaths = resolvedAudioLocalPaths,
                    isLoading = profileTabsHydrating || (effectiveMedia.isEmpty() && profileMediaResolving),
                    isResolvingMedia = profileMediaResolving,
                    onOpenMedia = { selectedMediaForPreview = it },
                )
                ProfileSheetTab.Links -> LinksPanel(items = effectiveLinks, onOpen = handleOpenLink)
                ProfileSheetTab.Files -> FilesPanel(items = effectiveFiles, onDownload = handleDownloadFile)
            }
        }

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
            Dialog(
                onDismissRequest = { selectedMediaForPreview = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val reveal by animateFloatAsState(
                    targetValue = if (mediaPreviewVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "profile_media_preview",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f * reveal.coerceIn(0f, 1f)))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selectedMediaForPreview = null },
                    contentAlignment = Alignment.Center,
                ) {
                    val previewShape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .graphicsLayer {
                                val t = reveal.coerceIn(0f, 1f)
                                scaleX = 0.88f + 0.12f * t
                                scaleY = 0.88f + 0.12f * t
                                alpha = t
                            }
                            .clip(previewShape)
                            .border(1.dp, GlassSheetTokens.GlassBorder, previewShape),
                        shape = previewShape,
                        color = GlassSheetTokens.OledBlack,
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
                                        modifier = Modifier
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
                                        modifier = Modifier
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
                                val canPlay = !local.isNullOrBlank() ||
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
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .size(40.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (media.mediaType == ProfileSheetMediaType.Image) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { selectedMediaForPreview = null }) {
                                        Text("Close")
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                                                if (url.isNotBlank() && media.isEncrypted &&
                                                    !connectionChatId.isNullOrBlank() &&
                                                    !effectiveViewerUserId.isNullOrBlank()
                                                ) {
                                                    val bytes = connectionRepository.downloadAndDecryptChatMedia(
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
                                                selectedMediaForPreview = null
                                            }
                                        },
                                    ) {
                                        Text("Save to gallery")
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val url = (resolvedMediaUrls[media.id] ?: media.mediaUrl)?.trim().orEmpty()
                                                val ext = when {
                                                    media.mimeType?.contains("png", ignoreCase = true) == true -> "png"
                                                    media.mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
                                                    else -> "jpg"
                                                }
                                                if (url.isNotBlank()) {
                                                    if (media.isEncrypted &&
                                                        !connectionChatId.isNullOrBlank() &&
                                                        !effectiveViewerUserId.isNullOrBlank()
                                                    ) {
                                                        val bytes = connectionRepository.downloadAndDecryptChatMedia(
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
                                                selectedMediaForPreview = null
                                            }
                                        },
                                    ) {
                                        Text("Share")
                                    }
                                    if (!media.isEncrypted) {
                                        TextButton(
                                            onClick = {
                                                val target = resolvedMediaUrls[media.id] ?: media.mediaUrl
                                                if (!target.isNullOrBlank()) {
                                                    handleOpenLink(target)
                                                }
                                                selectedMediaForPreview = null
                                            },
                                        ) {
                                            Text("Open in browser")
                                        }
                                    }
                                }
                            } else {
                                TextButton(onClick = { selectedMediaForPreview = null }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Immutable snapshot the sheet renders. Callers rebuild this when underlying data changes. */
data class ProfileSheetState(
    val displayName: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val statusBadge: ProfileSheetBadge? = null,
    val canNudge: Boolean = true,
    val timeline: List<ProfileSheetTimelineItem> = emptyList(),
    val media: List<ProfileSheetMedia> = emptyList(),
    val links: List<ProfileSheetLink> = emptyList(),
    val files: List<ProfileSheetFile> = emptyList(),
    /** Peer user id — when non-blank, Timeline subtab hydrates interests / encounters. */
    val userId: String? = null,
    /** Viewer user id — needed to compute shared interests + mutual connection. */
    val viewerUserId: String? = null,
    /** Optional connection/chat id retained for callers that want contextual actions. */
    val connectionId: String? = null,
    /**
     * All locally-decrypted chat messages with type metadata. Used to populate
     * the Media / Files / Links tabs from the local E2EE cache instead of making
     * a server round-trip (message content is encrypted on the wire).
     */
    val localMessages: List<ProfileSheetLocalMessage> = emptyList(),
)

/**
 * A locally-decrypted chat message carrying its [messageType] so the profile sheet
 * can populate Media / Files / Links tabs entirely from the local E2EE cache
 * without making a server round-trip (message content is end-to-end encrypted on
 * the wire, so the BFF cannot parse it).
 */
data class ProfileSheetLocalMessage(
    val id: String,
    val content: String,
    val messageType: String,
    val timestamp: String,
    val metadata: JsonElement? = null,
)

data class ProfileSheetBadge(
    val label: String,
    /** 0xAARRGGBB packed color — rendered via [Color]`(value)`. */
    val tint: Color,
)

data class ProfileSheetTimelineItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val timestamp: String,
)

data class ProfileSheetMedia(
    val id: String,
    val mediaUrl: String? = null,
    val storagePath: String? = null,
    val mimeType: String? = null,
    val isEncrypted: Boolean = false,
    val mediaType: ProfileSheetMediaType = ProfileSheetMediaType.Image,
    val captionedAt: String? = null,
    /** Voice-note length from message metadata when available. */
    val durationSeconds: Int? = null,
)

enum class ProfileSheetMediaType {
    Image,
    Audio,
}

data class ProfileSheetLink(
    val id: String,
    val url: String,
    val title: String?,
    val timestamp: String,
)

data class ProfileSheetFile(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val timestamp: String,
    /** Signed/public URL fallback used when tuple decryption fields are unavailable. */
    val downloadUrl: String? = null,
    /** `chat-attachments` object path for encrypted file downloads. */
    val attachmentPath: String? = null,
    /** Base64 32-byte per-file master key from the `ccx:v1` envelope. */
    val attachmentKeyBase64: String? = null,
    /** Base64 SHA-256 checksum for plaintext integrity verification. */
    val attachmentSha256Base64: String? = null,
)

enum class ProfileSheetTab(val label: String, val icon: ImageVector) {
    Timeline("Timeline", Icons.Outlined.History),
    Media("Media", Icons.Outlined.Image),
    Links("Links", Icons.Outlined.Link),
    Files("Files", Icons.Outlined.AttachFile),
}

@Composable
private fun ProfileSheetHeader(
    displayName: String,
    subtitle: String?,
    avatarUrl: String?,
    statusBadge: ProfileSheetBadge?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(LightBlue, PrimaryBlue))),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(CircleShape),
                )
            } else {
                Text(
                    displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (statusBadge != null) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBadge.tint.copy(alpha = 0.14f),
                ) {
                    Text(
                        statusBadge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusBadge.tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelinePanel(
    items: List<ProfileSheetTimelineItem>,
    legacyProfile: UserPublicProfile?,
    legacyLoading: Boolean,
    legacyError: String?,
    showLegacy: Boolean,
) {
    val hasTimelineItems = items.isNotEmpty()
    if (!showLegacy && !hasTimelineItems) {
        EmptyTabState(
            icon = Icons.Outlined.History,
            title = "No timeline yet",
            body = "Shared moments will show up here as you connect.",
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasTimelineItems) {
            items.forEach { TimelineRow(item = it) }
        }
        if (showLegacy) {
            if (hasTimelineItems) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(Modifier.height(6.dp))
            }
            ProfileLegacyTimelineContent(
                profile = legacyProfile,
                loading = legacyLoading,
                error = legacyError,
            )
        }
    }
}

@Composable
private fun TimelineRow(item: ProfileSheetTimelineItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimaryBlue)
                .padding(top = 6.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun MediaPanel(
    items: List<ProfileSheetMedia>,
    resolvedUrls: Map<String, String>,
    resolvedBitmaps: Map<String, ImageBitmap>,
    resolvedAudioLocalPaths: Map<String, String>,
    isLoading: Boolean,
    isResolvingMedia: Boolean,
    onOpenMedia: (ProfileSheetMedia) -> Unit,
) {
    val imageItems = items.filter { it.mediaType == ProfileSheetMediaType.Image }
    val audioItems = items.filter { it.mediaType == ProfileSheetMediaType.Audio }
    val imageRows = imageItems.chunked(3)

    if (items.isEmpty()) {
        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                repeat(4) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            return
        }
        EmptyTabState(
            icon = Icons.Outlined.Image,
            title = "No shared media",
            body = "Photos and voice notes you exchange in chat will appear here.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(imageRows, key = { row -> row.firstOrNull()?.id ?: "row" }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { media ->
                    val bitmap = resolvedBitmaps[media.id]
                    val resolvedUrl = resolvedUrls[media.id] ?: media.mediaUrl
                    val thumbUnlocking = media.isEncrypted && bitmap == null && isResolvingMedia
                    val thumbReady = bitmap != null ||
                        (!media.isEncrypted && !resolvedUrl.isNullOrBlank())
                    val thumbReveal by animateFloatAsState(
                        targetValue = if (thumbReady) 1f else if (thumbUnlocking) 0.55f else 0.38f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "media_thumb_${media.id}",
                    )
                    val thumbInteraction = remember(media.id) { MutableInteractionSource() }
                    val thumbPressed by thumbInteraction.collectIsPressedAsState()
                    val thumbScale by animateFloatAsState(
                        targetValue = if (thumbPressed) 0.94f else 1f,
                        animationSpec = tween(140, easing = FastOutSlowInEasing),
                        label = "media_thumb_press",
                    )
                    val thumbModifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                            alpha = thumbReveal
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            interactionSource = thumbInteraction,
                            indication = ripple(bounded = true, radius = 52.dp),
                            enabled = thumbReady,
                        ) { onOpenMedia(media) }
                    Box(modifier = thumbModifier, contentAlignment = Alignment.Center) {
                        when {
                            bitmap != null -> {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            thumbUnlocking -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                )
                            }
                            !resolvedUrl.isNullOrBlank() && !media.isEncrypted -> {
                                AsyncImage(
                                    model = resolvedUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp),
                    )
                }
            }
        }

        if (audioItems.isNotEmpty()) {
            items(audioItems, key = { it.id }) { media ->
                val stream = resolvedUrls[media.id] ?: media.mediaUrl
                val local = resolvedAudioLocalPaths[media.id]
                val canPlay = !local.isNullOrBlank() ||
                    (stream?.isNotBlank() == true && !media.isEncrypted)
                val unlockingAudio = media.isEncrypted && local.isNullOrBlank() && isResolvingMedia
                val failedEncryptedAudio =
                    media.isEncrypted && local.isNullOrBlank() && !isResolvingMedia
                val rowReveal by animateFloatAsState(
                    targetValue = if (canPlay || unlockingAudio || failedEncryptedAudio) 1f else 0.4f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "media_audio_${media.id}",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = rowReveal },
                ) {
                    when {
                        canPlay -> {
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
                        }
                        unlockingAudio -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryBlue,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    text = "Unlocking voice note…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        failedEncryptedAudio -> {
                            Text(
                                text = "This voice note could not be unlocked for playback.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Voice note unavailable",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksPanel(items: List<ProfileSheetLink>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.Link,
            title = "No shared links",
            body = "URLs shared in chat show up here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onOpen(link.url) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = PrimaryBlue)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        link.title ?: link.url,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        link.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(items: List<ProfileSheetFile>, onDownload: (ProfileSheetFile) -> Unit) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.AttachFile,
            title = "No shared files",
            body = "Attachments sent in chat will appear here.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onDownload(file) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = PrimaryBlue,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatFileSize(file.sizeBytes)} · ${file.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        file.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024L * 1_024 -> "${bytes / 1_024} KB"
    else -> "${(bytes * 10 / (1_024L * 1_024)) / 10.0} MB"
}

private fun ProfileSheetLocalMessage.toProfileSheetMedia(): ProfileSheetMedia? {
    val meta = metadata as? JsonObject ?: return null
    val url = METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
        meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    val path = METADATA_PATH_KEYS.firstNotNullOfOrNull { key ->
        meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    if (url == null && path == null) return null
    val lowerType = messageType.lowercase()
    val mediaType = if (lowerType == "audio") ProfileSheetMediaType.Audio else ProfileSheetMediaType.Image
    return ProfileSheetMedia(
        id = id,
        mediaUrl = url,
        storagePath = path,
        mimeType = meta.stringAt("original_mime_type")
            ?: meta.stringAt("mime_type")
            ?: meta.stringAt("content_type"),
        isEncrypted = Message(
            id = id,
            user_id = "",
            content = content.trim().ifBlank { " " },
            timeCreated = 0L,
            messageType = lowerType,
            metadata = metadata,
        ).isEncryptedMedia(),
        mediaType = mediaType,
        captionedAt = content.takeUnless { it.isLikelyWireEncrypted() || it.startsWith("ccx:v1:") }
            ?.takeIf { it.isNotBlank() },
        durationSeconds = meta.intAt("duration_seconds")
            ?: meta.intAt("durationSeconds")
            ?: meta["duration"]?.jsonPrimitive?.intOrNull,
    )
}

private fun ConnectionTabMessage.toProfileSheetMediaFromTab(): ProfileSheetMedia? {
    val lowerType = messageType.lowercase()
    if (lowerType != "image" && lowerType != "audio") return null
    val meta = metadata as? JsonObject
    val url = METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
        meta?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    val path = METADATA_PATH_KEYS.firstNotNullOfOrNull { key ->
        meta?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    if (url == null && path == null) return null
    return ProfileSheetMedia(
        id = id,
        mediaUrl = url,
        storagePath = path,
        mimeType = meta?.stringAt("original_mime_type")
            ?: meta?.stringAt("mime_type")
            ?: meta?.stringAt("content_type"),
        isEncrypted = Message(
            id = id,
            user_id = userId,
            content = content.trim().ifBlank { " " },
            timeCreated = timeCreated,
            messageType = lowerType,
            metadata = metadata,
        ).isEncryptedMedia(),
        mediaType = if (lowerType == "audio") ProfileSheetMediaType.Audio else ProfileSheetMediaType.Image,
        captionedAt = content.takeUnless { it.isLikelyWireEncrypted() || it.startsWith("ccx:v1:") }
            ?.takeIf { it.isNotBlank() },
        durationSeconds = meta?.let { m ->
            m.intAt("duration_seconds")
                ?: m.intAt("durationSeconds")
                ?: m["duration"]?.jsonPrimitive?.intOrNull
        },
    )
}

private fun ProfileSheetLocalMessage.toProfileSheetFile(): ProfileSheetFile {
    val envelope = AttachmentCrypto.tryDecodeEnvelope(content)
    val meta = metadata as? JsonObject
    val fileName = envelope?.name?.takeIf { it.isNotBlank() }
        ?: meta?.get("file_name")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("filename")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
        ?: content.takeUnless { it.isLikelyWireEncrypted() || it.startsWith("ccx:v1:") }
            ?.takeIf { it.isNotBlank() }
        ?: "Attachment"
    val size = envelope?.size
        ?: meta?.get("file_size")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size_bytes")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size")?.jsonPrimitive?.longOrNull
        ?: 0L
    val mime = envelope?.mime?.takeIf { it.isNotBlank() }
        ?: meta?.get("mime_type")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("content_type")?.jsonPrimitive?.contentOrNull
        ?: "application/octet-stream"
    val downloadUrl = meta?.stringAt("signed_url")
        ?: meta?.stringAt("public_url")
        ?: meta?.stringAt("url")
        ?: meta?.stringAt("storage_url")
        ?: meta?.stringAt("media_url")
    val attachmentPath = envelope?.path?.takeIf { it.isNotBlank() }
        ?: meta?.stringAt("path")
        ?: meta?.stringAt("storage_path")
        ?: meta?.stringAt("object_path")
        ?: meta?.stringAt("media_path")
    val attachmentKeyBase64 = envelope?.key?.takeIf { it.isNotBlank() }
        ?: meta?.stringAt("key")
        ?: meta?.stringAt("file_key")
        ?: meta?.stringAt("file_master_key")
    val attachmentSha256Base64 = envelope?.sha256?.takeIf { it.isNotBlank() }
        ?: meta?.stringAt("sha256")
        ?: meta?.stringAt("sha256_base64")
    return ProfileSheetFile(
        id = id,
        fileName = fileName,
        sizeBytes = size,
        mimeType = mime,
        timestamp = timestamp,
        downloadUrl = downloadUrl,
        attachmentPath = attachmentPath,
        attachmentKeyBase64 = attachmentKeyBase64,
        attachmentSha256Base64 = attachmentSha256Base64,
    )
}

private fun ConnectionTabMessage.toProfileSheetFileFromTab(): ProfileSheetFile {
    val meta = metadata as? JsonObject
    val fileName = meta?.stringAt("file_name")
        ?: meta?.stringAt("filename")
        ?: meta?.stringAt("name")
        ?: content.takeUnless { it.isLikelyWireEncrypted() || it.startsWith("ccx:v1:") }
            ?.takeIf { it.isNotBlank() }
        ?: "Attachment"
    val size = meta?.get("file_size")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size_bytes")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size")?.jsonPrimitive?.longOrNull
        ?: 0L
    val mime = meta?.stringAt("mime_type")
        ?: meta?.stringAt("content_type")
        ?: "application/octet-stream"
    val downloadUrl = meta?.stringAt("signed_url")
        ?: meta?.stringAt("public_url")
        ?: meta?.stringAt("url")
        ?: meta?.stringAt("storage_url")
        ?: meta?.stringAt("media_url")
    val attachmentPath = meta?.stringAt("path")
        ?: meta?.stringAt("storage_path")
        ?: meta?.stringAt("object_path")
        ?: meta?.stringAt("media_path")
    val attachmentKeyBase64 = meta?.stringAt("key")
        ?: meta?.stringAt("file_key")
        ?: meta?.stringAt("file_master_key")
    val attachmentSha256Base64 = meta?.stringAt("sha256")
        ?: meta?.stringAt("sha256_base64")

    return ProfileSheetFile(
        id = id,
        fileName = fileName,
        sizeBytes = size,
        mimeType = mime,
        timestamp = Instant.fromEpochMilliseconds(timeCreated).toString(),
        downloadUrl = downloadUrl,
        attachmentPath = attachmentPath,
        attachmentKeyBase64 = attachmentKeyBase64,
        attachmentSha256Base64 = attachmentSha256Base64,
    )
}

private val METADATA_URL_KEYS = listOf(
    "signed_url",
    "public_url",
    "url",
    "storage_url",
    "image_url",
    "audio_url",
    "media_url",
)

private val METADATA_PATH_KEYS = listOf(
    "path",
    "storage_path",
    "object_path",
    "media_path",
)

private fun ProfileSheetLocalMessage.hasMetadataMediaUrl(): Boolean {
    val meta = metadata as? JsonObject ?: return false
    return METADATA_URL_KEYS.any { key ->
        meta[key]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    }
}

private fun ProfileSheetLocalMessage.hasMetadataAttachmentV1(): Boolean {
    val meta = metadata as? JsonObject ?: return false
    val raw = meta["attachment_v"]?.jsonPrimitive ?: return false
    val asInt = raw.intOrNull ?: raw.contentOrNull?.toIntOrNull()
    if (asInt == 1) return true
    return raw.contentOrNull?.equals("true", ignoreCase = true) == true
}

private fun JsonObject.stringAt(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.intAt(key: String): Int? {
    val raw = this[key]?.jsonPrimitive ?: return null
    raw.intOrNull?.let { return it }
    raw.contentOrNull?.trim()?.toIntOrNull()?.let { return it }
    return null
}

private fun JsonObject.booleanAt(key: String): Boolean? {
    val raw = this[key]?.jsonPrimitive ?: return null
    raw.contentOrNull?.trim()?.lowercase()?.let { text ->
        if (text == "true" || text == "1") return true
        if (text == "false" || text == "0") return false
    }
    return null
}

private fun String.isLikelyWireEncrypted(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    return text.startsWith("e2e:", ignoreCase = true)
}

private fun extensionFromMimeType(mimeType: String?): String {
    val mt = mimeType?.trim()?.lowercase().orEmpty()
    return when {
        "wav" in mt -> "wav"
        "webm" in mt -> "webm"
        "ogg" in mt -> "ogg"
        "mpeg" in mt || "mp3" in mt -> "mp3"
        "aac" in mt -> "aac"
        else -> "m4a"
    }
}

private fun normalizeExternalUri(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return value
    return when {
        "://" in value -> value
        value.startsWith("/") -> "file://$value"
        else -> value
    }
}

/**
 * Regex matching bare `http://` / `https://` URLs in locally-decrypted text. Keep
 * this simple + conservative — we don't try to resolve punctuation-adjacent URLs
 * perfectly; the Links tab is a lightweight preview, not a full URL parser.
 */
private val URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

/**
 * Extract http(s) URLs from a list of already-decrypted text messages. Runs
 * client-side because message `content` is E2EE on the server and the BFF
 * intentionally does not parse links.
 */
private fun extractLinksFromLocalMessages(
    messages: List<ProfileSheetLocalMessage>,
): List<ProfileSheetLink> {
    if (messages.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ProfileSheetLink>()
    messages.filter {
        it.messageType == "text" &&
            (it.content.contains("http://") || it.content.contains("https://"))
    }.forEach { msg ->
        URL_REGEX.findAll(msg.content).forEach { match ->
            val url = match.value.trimEnd('.', ',', ')', ']', '}', ';', ':')
            if (url.isNotBlank() && seen.add(url)) {
                out += ProfileSheetLink(
                    id = "${msg.id}:$url",
                    url = url,
                    title = null,
                    timestamp = msg.timestamp,
                )
            }
        }
    }
    return out
}

private fun mergeProfileMedia(items: List<ProfileSheetMedia>): List<ProfileSheetMedia> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetMedia>()
    items.forEach { media ->
        val prev = merged[media.id]
        if (prev == null) {
            merged[media.id] = media
            return@forEach
        }
        merged[media.id] = prev.copy(
            mediaUrl = media.mediaUrl ?: prev.mediaUrl,
            storagePath = media.storagePath ?: prev.storagePath,
            mimeType = media.mimeType ?: prev.mimeType,
            isEncrypted = media.isEncrypted || prev.isEncrypted,
            mediaType = if (prev.mediaType == ProfileSheetMediaType.Audio) prev.mediaType else media.mediaType,
            captionedAt = media.captionedAt ?: prev.captionedAt,
            durationSeconds = media.durationSeconds ?: prev.durationSeconds,
        )
    }
    return merged.values.toList()
}

private fun mergeProfileFiles(items: List<ProfileSheetFile>): List<ProfileSheetFile> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetFile>()
    items.forEach { file ->
        val prev = merged[file.id]
        if (prev == null) {
            merged[file.id] = file
            return@forEach
        }
        merged[file.id] = prev.copy(
            fileName = if (file.fileName != "Attachment") file.fileName else prev.fileName,
            sizeBytes = if (file.sizeBytes > 0) file.sizeBytes else prev.sizeBytes,
            mimeType = if (file.mimeType != "application/octet-stream") file.mimeType else prev.mimeType,
            timestamp = if (file.timestamp.isNotBlank()) file.timestamp else prev.timestamp,
            downloadUrl = file.downloadUrl ?: prev.downloadUrl,
            attachmentPath = file.attachmentPath ?: prev.attachmentPath,
            attachmentKeyBase64 = file.attachmentKeyBase64 ?: prev.attachmentKeyBase64,
            attachmentSha256Base64 = file.attachmentSha256Base64 ?: prev.attachmentSha256Base64,
        )
    }
    return merged.values.toList()
}

private fun mergeProfileLinks(items: List<ProfileSheetLink>): List<ProfileSheetLink> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetLink>()
    items.forEach { link ->
        val key = link.url.trim().lowercase()
        if (key.isNotBlank()) merged[key] = link
    }
    return merged.values.toList()
}

/**
 * Drop-in replacement for the legacy [UserProfileBottomSheet] that surfaces the same
 * peer-profile data (name, avatar, interests, shared interests, mutual moments) via
 * the new tabbed [ProfileBottomSheet] (Timeline · Media · Links · Files).
 *
 * Wire from any list flow (e.g. the Clicks chat list) by setting [userId] to the peer
 * id and providing the signed-in viewer id; pass `null` for [userId] to keep the sheet
 * dismissed. The sheet hydrates `user_interests.tags` for [userId] via
 * [SupabaseRepository.fetchUserPublicProfile] (which queries the
 * `user_interests` Postgres `text[]`) so the Timeline tab renders interests as soon
 * as the row resolves; if the peer has no `user_interests` row the section shows the
 * standard empty state instead of being blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedUserProfileSheet(
    userId: String?,
    viewerUserId: String?,
    onDismiss: () -> Unit,
    onMessage: (() -> Unit)? = null,
    localMessages: List<ProfileSheetLocalMessage> = emptyList(),
) {
    if (userId.isNullOrBlank()) return

    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val connections by AppDataManager.connections.collectAsState()
    val cached: User? = connectedUsers[userId]
    val profileConnectionId = remember(connections, userId, viewerUserId) {
        connections.firstOrNull { conn ->
            userId in conn.user_ids &&
                (viewerUserId.isNullOrBlank() || viewerUserId in conn.user_ids)
        }?.id
    }

    var resolved by remember(userId) { mutableStateOf<User?>(cached) }
    LaunchedEffect(userId, cached) {
        if (resolved == null) {
            val fromCache = AppDataManager.connectedUsers.value[userId]
            if (fromCache != null) {
                resolved = fromCache
            } else {
                runCatching {
                    withContext(Dispatchers.Default) {
                        SupabaseRepository().fetchUserPublicProfile(viewerUserId, userId)?.user
                    }
                }.getOrNull()?.let { resolved = it }
            }
        }
    }

    val displayName = resolved?.name?.takeIf { it.isNotBlank() }
        ?: cached?.name?.takeIf { it.isNotBlank() }
        ?: "Member"
    val state = remember(userId, viewerUserId, displayName, resolved?.image, resolved?.email, localMessages) {
        ProfileSheetState(
            displayName = displayName,
            subtitle = resolved?.email?.takeIf { it.isNotBlank() },
            avatarUrl = resolved?.image,
            statusBadge = null,
            canNudge = onMessage != null,
            timeline = emptyList(),
            media = emptyList(),
            links = emptyList(),
            files = emptyList(),
            userId = userId,
            viewerUserId = viewerUserId,
            connectionId = profileConnectionId,
            localMessages = localMessages,
        )
    }

    GlassAdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
    ) {
        OledSheetTheme {
            ProfileBottomSheet(
                state = state,
                onMessage = {
                    onMessage?.invoke()
                    onDismiss()
                },
                onNudge = {
                    onDismiss()
                },
            )
        }
    }
}
