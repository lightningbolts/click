@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelineJournalEntry // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.ui.chat.fetchImageBytesFromUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveDecryptedAttachmentToDownloads // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.util.profileMediaVaultId // pragma: allowlist secret
import compose.project.click.click.util.profileMediaVaultLocalPath // pragma: allowlist secret
import compose.project.click.click.util.readProfileMediaVaultBytes // pragma: allowlist secret
import compose.project.click.click.util.writeProfileMediaVaultBytes // pragma: allowlist secret
import compose.project.click.click.utils.toImageBitmap // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Bounded pager height under UIKit scroll-host (Metal texture max 16384px @3x).
private val ProfileSheetPagerHeight = 560.dp // fallback when not fill-viewport

/**
 * Phase 2 — C13: shared profile bottom sheet displayed when a map pin is tapped.
 *
 * Four subtabs backed by a [SecondaryTabRow] + [HorizontalPager]:
 * **Timeline · Beacons · Media · Links · Files**. When [ProfileSheetState.userId] and
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
    onOpenDisposableRoll: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    avatarUploading: Boolean = false,
    onOpenBeacon: ((beaconId: String) -> Unit)? = null,
) {
    val visibleTabs =
        remember(state.isGroup) {
            listOf(
                ProfileSheetTab.Timeline,
                ProfileSheetTab.Beacons,
                ProfileSheetTab.Media,
                ProfileSheetTab.Links,
                ProfileSheetTab.Files,
            ) + if (state.isGroup) listOf(ProfileSheetTab.Members) else emptyList()
        }
    val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
    val timelineScroll = rememberScrollState()
    val mediaScroll = rememberScrollState()
    val linksScroll = rememberScrollState()
    val filesScroll = rememberScrollState()
    val beaconsScroll = rememberScrollState()
    val membersScroll = rememberScrollState()
    val sheetOnDismiss = LocalSheetOnDismissRequest.current
    val profileScrollAtTop =
        remember(
            pagerState,
            visibleTabs,
            timelineScroll,
            mediaScroll,
            linksScroll,
            filesScroll,
            beaconsScroll,
            membersScroll,
        ) {
            {
                when (visibleTabs.getOrNull(pagerState.currentPage)) {
                    ProfileSheetTab.Timeline -> timelineScroll.isSheetScrollAtTop()
                    ProfileSheetTab.Media -> mediaScroll.isSheetScrollAtTop()
                    ProfileSheetTab.Links -> linksScroll.isSheetScrollAtTop()
                    ProfileSheetTab.Files -> filesScroll.isSheetScrollAtTop()
                    ProfileSheetTab.Beacons -> beaconsScroll.isSheetScrollAtTop()
                    ProfileSheetTab.Members -> membersScroll.isSheetScrollAtTop()
                    null -> true
                }
            }
        }
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
                if (m.isDisposableRollLocked()) {
                    selectedMediaForPreview = null
                } else {
                    mediaPreviewModel = m
                    mediaPreviewVisible = true
                }
            }
        }
    }
    val selectedUserId = state.userId?.trim().orEmpty()
    val connectionRepository = remember { ConnectionRepository() }
    val appViewerUserId =
        AppDataManager.currentUser
            .collectAsState()
            .value
            ?.id
            ?.trim()
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
    var connectionTabBeacons by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<List<ProfileSheetLocalMessage>>(emptyList())
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
    var resolvingMediaIds by remember(state.connectionId, selectedUserId, effectiveViewerUserId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var openingFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedUserId, state.connectionId, effectiveViewerUserId) {
        profileTabsHydrating = true
        try {
            val connectionId = state.connectionId?.trim().orEmpty()
            if (connectionId.isBlank()) {
                connectionLocalMessages = emptyList()
                connectionChatId = null
                connectionTabMedia = emptyList()
                connectionTabFiles = emptyList()
                connectionTabBeacons = emptyList()
                return@LaunchedEffect
            }

            val fetched =
                if (!effectiveViewerUserId.isNullOrBlank()) {
                    runCatching {
                        if (state.isGroup) {
                            connectionRepository.fetchDecryptedMessagesForChat(
                                chatId = connectionId,
                                viewerUserId = effectiveViewerUserId,
                            )
                        } else {
                            connectionRepository.fetchDecryptedMessagesForProfileConnection(
                                connectionId = connectionId,
                                viewerUserId = effectiveViewerUserId,
                            )
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

            connectionLocalMessages =
                fetched
                    .sortedBy { it.timeCreated }
                    .map { msg ->
                        ProfileSheetLocalMessage(
                            id = msg.id,
                            content = msg.content,
                            messageType = msg.messageType,
                            timestamp = formatProfileSheetDate(msg.timeCreated),
                            metadata = msg.metadata,
                            sortEpochMs = msg.timeCreated,
                        )
                    }

            val tabsPayload =
                runCatching {
                    connectionRepository.fetchConnectionTabs(connectionId).getOrNull()
                }.getOrNull()
            connectionChatId = tabsPayload?.chatId

            connectionTabMedia =
                tabsPayload
                    ?.media
                    ?.mapNotNull { it.toProfileSheetMediaFromTab() }
                    .orEmpty()
                    .sortedByDescending { profileMediaSortEpoch(it) }
            val decryptedById = connectionLocalMessages.associateBy { it.id }
            connectionTabFiles =
                tabsPayload
                    ?.files
                    ?.mapNotNull { tab ->
                        decryptedById[tab.id]?.toProfileSheetFile()
                            ?: tab.toProfileSheetFileFromTab().takeIf { it.canOpenProfileFile() }
                    }.orEmpty()
            connectionTabBeacons =
                tabsPayload?.beacons.orEmpty().map { tab ->
                    decryptedById[tab.id] ?: ProfileSheetLocalMessage(
                        id = tab.id,
                        content = tab.content,
                        messageType = tab.messageType,
                        timestamp = formatProfileSheetDate(tab.timeCreated),
                        metadata = tab.metadata,
                        sortEpochMs = tab.timeCreated,
                    )
                }
        } finally {
            profileTabsHydrating = false
        }
    }

    val profileLocalMessages =
        remember(state.localMessages, connectionLocalMessages) {
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

    LaunchedEffect(state.userId, effectiveViewerUserId) {
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
        val result =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshUserPublicProfile(effectiveViewerUserId, uid)
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

    val proximityEncounterEpoch by AppDataManager.proximityEncounterEpoch.collectAsState()
    LaunchedEffect(proximityEncounterEpoch, state.userId, effectiveViewerUserId) {
        if (proximityEncounterEpoch <= 0L) return@LaunchedEffect
        val uid = state.userId?.trim() ?: return@LaunchedEffect
        val refreshed =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshUserPublicProfile(effectiveViewerUserId, uid)
                }
            }.getOrNull()
        if (refreshed != null) {
            legacyProfile = refreshed
            legacyError = null
            legacyLoading = false
        }
    }

    val timelineTargetType =
        if (state.isGroup) {
            "chat"
        } else if (!state.userId.isNullOrBlank()) {
            "user"
        } else {
            null
        }
    val timelineTargetId =
        if (state.isGroup) {
            state.connectionId?.trim()
        } else {
            state.userId?.trim()
        }?.takeIf { it.isNotEmpty() }
    var profileTimeline by remember(timelineTargetType, timelineTargetId) {
        mutableStateOf<ProfileTimelinePayload?>(null)
    }
    var journalText by remember(timelineTargetType, timelineTargetId) { mutableStateOf("") }
    var journalVisibility by remember(timelineTargetType, timelineTargetId) { mutableStateOf("private") }
    var journalPosting by remember(timelineTargetType, timelineTargetId) { mutableStateOf(false) }
    var journalError by remember(timelineTargetType, timelineTargetId) { mutableStateOf<String?>(null) }
    var editingJournalId by remember(timelineTargetType, timelineTargetId) { mutableStateOf<String?>(null) }
    var editingJournalText by remember(timelineTargetType, timelineTargetId) { mutableStateOf("") }
    var editingJournalVisibility by remember(timelineTargetType, timelineTargetId) { mutableStateOf("private") }
    var mutatingJournalIds by remember(timelineTargetType, timelineTargetId) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(timelineTargetType, timelineTargetId) {
        val type = timelineTargetType
        val id = timelineTargetId
        if (type == null || id == null) {
            profileTimeline = null
            return@LaunchedEffect
        }
        profileTimeline = repository.getCachedProfileTimeline(type, id)
        val refreshed =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshProfileTimeline(type, id)
                }
            }.getOrNull()
        if (refreshed != null) {
            profileTimeline = refreshed
            AppDataManager.persistProfileTimelineCaches()
        }
    }

    LaunchedEffect(proximityEncounterEpoch, timelineTargetType, timelineTargetId) {
        if (proximityEncounterEpoch <= 0L) return@LaunchedEffect
        val type = timelineTargetType ?: return@LaunchedEffect
        val id = timelineTargetId ?: return@LaunchedEffect
        val refreshed =
            runCatching {
                withContext(Dispatchers.Default) {
                    repository.refreshProfileTimeline(type, id)
                }
            }.getOrNull()
        if (refreshed != null) {
            profileTimeline = refreshed
            AppDataManager.persistProfileTimelineCaches()
        }
    }

    val submitJournalEntry: () -> Unit = {
        val type = timelineTargetType
        val id = timelineTargetId
        val text = journalText.trim()
        if (type != null && id != null && text.isNotEmpty() && !journalPosting) {
            scope.launch {
                journalPosting = true
                journalError = null
                val refreshed =
                    runCatching {
                        withContext(Dispatchers.Default) {
                            repository.createProfileTimelineJournalEntry(
                                targetType = type,
                                targetId = id,
                                body = text,
                                visibility = journalVisibility,
                            )
                        }
                    }.getOrNull()
                if (refreshed != null) {
                    profileTimeline = refreshed
                    journalText = ""
                    AppDataManager.persistProfileTimelineCaches()
                } else {
                    journalError = "Couldn't save that journal entry. Please try again."
                }
                journalPosting = false
            }
        }
    }
    val startEditJournalEntry: (ProfileTimelineJournalEntry) -> Unit = { entry ->
        editingJournalId = entry.id
        editingJournalText = entry.body
        editingJournalVisibility = entry.visibility
        journalError = null
    }
    val cancelEditJournalEntry: () -> Unit = {
        editingJournalId = null
        editingJournalText = ""
        editingJournalVisibility = "private"
    }
    val saveEditJournalEntry: (String) -> Unit = { entryId ->
        val text = editingJournalText.trim()
        if (entryId.isNotBlank() && text.isNotEmpty() && entryId !in mutatingJournalIds) {
            scope.launch {
                mutatingJournalIds = mutatingJournalIds + entryId
                journalError = null
                val refreshed =
                    runCatching {
                        withContext(Dispatchers.Default) {
                            repository.updateProfileTimelineJournalEntry(
                                id = entryId,
                                body = text,
                                visibility = editingJournalVisibility,
                            )
                        }
                    }.getOrNull()
                if (refreshed != null) {
                    profileTimeline = refreshed
                    cancelEditJournalEntry()
                    AppDataManager.persistProfileTimelineCaches()
                } else {
                    journalError = "Couldn't update that journal entry. Please try again."
                }
                mutatingJournalIds = mutatingJournalIds - entryId
            }
        }
    }
    val deleteJournalEntry: (String) -> Unit = { entryId ->
        if (entryId.isNotBlank() && entryId !in mutatingJournalIds) {
            scope.launch {
                mutatingJournalIds = mutatingJournalIds + entryId
                journalError = null
                val refreshed =
                    runCatching {
                        withContext(Dispatchers.Default) {
                            repository.deleteProfileTimelineJournalEntry(entryId)
                        }
                    }.getOrNull()
                if (refreshed != null) {
                    profileTimeline = refreshed
                    if (editingJournalId == entryId) cancelEditJournalEntry()
                    AppDataManager.persistProfileTimelineCaches()
                } else {
                    journalError = "Couldn't delete that journal entry. Please try again."
                }
                mutatingJournalIds = mutatingJournalIds - entryId
            }
        }
    }

    val localMediaMessages =
        remember(profileLocalMessages) {
            profileLocalMessages.filter {
                val type = it.messageType.lowercase()
                type == "image" ||
                    type == "audio" ||
                    it.hasMetadataMediaUrl()
            }
        }
    val localFileMessages =
        remember(profileLocalMessages) {
            profileLocalMessages.filter {
                val type = it.messageType.lowercase()
                type == "file" ||
                    it.hasMetadataAttachmentV1() ||
                    it.content.startsWith(AttachmentCrypto.ENVELOPE_PREFIX)
            }
        }
    val localLinkMessages =
        remember(profileLocalMessages) {
            profileLocalMessages.filter {
                it.messageType == "text" &&
                    (it.content.contains("http://") || it.content.contains("https://"))
            }
        }
    val localBeaconMessages =
        remember(profileLocalMessages, connectionTabBeacons) {
            val fromLocal = profileLocalMessages.filter { it.messageType.equals("beacon", ignoreCase = true) }
            val localIds = fromLocal.map { it.id }.toSet()
            (fromLocal + connectionTabBeacons.filter { it.id !in localIds })
                .distinctBy { it.id }
        }

    val effectiveMedia =
        remember(localMediaMessages, connectionTabMedia, state.media) {
            mergeProfileMedia(
                localMediaMessages.mapNotNull { it.toProfileSheetMedia() } + connectionTabMedia + state.media,
            )
        }
    val effectiveFiles =
        remember(localFileMessages, connectionTabFiles, state.files) {
            val localFiles = localFileMessages.map { it.toProfileSheetFile() }
            val localFileIds = localFiles.map { it.id }.toSet()
            mergeProfileFiles(
                localFiles +
                    connectionTabFiles.filter { it.id !in localFileIds } +
                    state.files,
            )
        }
    val effectiveLinks =
        remember(localLinkMessages, state.links) {
            mergeProfileLinks(extractLinksFromLocalMessages(localLinkMessages) + state.links)
        }

    LaunchedEffect(effectiveMedia, connectionChatId, effectiveViewerUserId) {
        resolvingMediaIds = emptySet()
        val cachedUrls = mutableMapOf<String, String>()
        val cachedBitmaps = mutableMapOf<String, ImageBitmap>()
        val cachedAudioPaths = mutableMapOf<String, String>()
        effectiveMedia.forEach { media ->
            val direct = media.mediaUrl?.trim().orEmpty()
            val path = media.storagePath?.trim().orEmpty()
            when {
                direct.isNotBlank() && !media.isEncrypted -> cachedUrls[media.id] = direct
                path.isNotBlank() && !media.isEncrypted -> {
                    profileSignedUrlCache.get(path)?.let { cachedUrls[media.id] = it }
                }
            }
            val cacheKey = profileMediaCacheKey(media, connectionChatId, effectiveViewerUserId)
            val vaultId = profileMediaVaultId(cacheKey)
            val vaultExt = profileMediaVaultExtension(media)
            profileMediaBitmapCache.get(cacheKey)?.let { cachedBitmaps[media.id] = it }
                ?: readProfileMediaVaultBytes(vaultId, vaultExt)?.let { bytes ->
                    runCatching { bytes.toImageBitmap() }.getOrNull()?.also { bitmap ->
                        profileMediaBitmapCache.put(cacheKey, bitmap)
                        cachedBitmaps[media.id] = bitmap
                    }
                }
            profileMediaAudioPathCache.get(cacheKey)?.let { cachedAudioPaths[media.id] = it }
                ?: profileMediaVaultLocalPath(vaultId, vaultExt)?.let { path ->
                    profileMediaAudioPathCache.put(cacheKey, path)
                    cachedAudioPaths[media.id] = path
                }
        }
        resolvedMediaUrls = cachedUrls
        resolvedMediaBitmaps = cachedBitmaps
        resolvedAudioLocalPaths = cachedAudioPaths
    }

    val ensureProfileMediaResolved: (ProfileSheetMedia) -> Unit = ensure@{ media ->
        if (media.id in resolvingMediaIds) return@ensure
        if (resolvedMediaUrls.containsKey(media.id) ||
            resolvedMediaBitmaps.containsKey(media.id) ||
            resolvedAudioLocalPaths.containsKey(media.id)
        ) {
            return@ensure
        }
        val cacheKey = profileMediaCacheKey(media, connectionChatId, effectiveViewerUserId)
        profileMediaBitmapCache.get(cacheKey)?.let { cached ->
            resolvedMediaBitmaps = resolvedMediaBitmaps + (media.id to cached)
            return@ensure
        }
        profileMediaAudioPathCache.get(cacheKey)?.let { cached ->
            resolvedAudioLocalPaths = resolvedAudioLocalPaths + (media.id to cached)
            return@ensure
        }
        val vaultId = profileMediaVaultId(cacheKey)
        val vaultExt = profileMediaVaultExtension(media)
        if (media.mediaType == ProfileSheetMediaType.Image) {
            readProfileMediaVaultBytes(vaultId, vaultExt)?.let { bytes ->
                runCatching { bytes.toImageBitmap() }.getOrNull()?.let { bitmap ->
                    profileMediaBitmapCache.put(cacheKey, bitmap)
                    resolvedMediaBitmaps = resolvedMediaBitmaps + (media.id to bitmap)
                    return@ensure
                }
            }
        } else {
            profileMediaVaultLocalPath(vaultId, vaultExt)?.let { path ->
                profileMediaAudioPathCache.put(cacheKey, path)
                resolvedAudioLocalPaths = resolvedAudioLocalPaths + (media.id to path)
                return@ensure
            }
        }

        scope.launch {
            if (media.id in resolvingMediaIds) return@launch
            resolvingMediaIds = resolvingMediaIds + media.id
            try {
                val url = resolveProfileMediaUrl(media, connectionRepository) ?: return@launch
                if (!media.isEncrypted) {
                    resolvedMediaUrls = resolvedMediaUrls + (media.id to url)
                    return@launch
                }
                val chatId = connectionChatId?.trim().orEmpty()
                val viewerId = effectiveViewerUserId?.trim().orEmpty()
                if (chatId.isBlank() || viewerId.isBlank()) return@launch
                val bytes =
                    connectionRepository.downloadAndDecryptChatMedia(
                        chatId = chatId,
                        viewerUserId = viewerId,
                        mediaUrl = url,
                    )
                val plaintext = bytes?.takeIf { it.isNotEmpty() } ?: return@launch
                writeProfileMediaVaultBytes(vaultId, plaintext, vaultExt)
                if (media.mediaType == ProfileSheetMediaType.Image) {
                    val bitmap = runCatching { plaintext.toImageBitmap() }.getOrNull() ?: return@launch
                    profileMediaBitmapCache.put(cacheKey, bitmap)
                    resolvedMediaBitmaps = resolvedMediaBitmaps + (media.id to bitmap)
                } else {
                    val localPath =
                        profileMediaVaultLocalPath(vaultId, vaultExt)
                            ?: writeSecureChatAudioTempFile(media.id, plaintext, vaultExt)
                    if (localPath.isNullOrBlank()) return@launch
                    profileMediaAudioPathCache.put(cacheKey, localPath)
                    resolvedAudioLocalPaths = resolvedAudioLocalPaths + (media.id to localPath)
                }
            } finally {
                resolvingMediaIds = resolvingMediaIds - media.id
            }
        }
    }

    val handleOpenLink: (String) -> Unit =
        remember(onOpenLink, uriHandler) {
            { url ->
                val normalized = normalizeExternalUri(url)
                runCatching { uriHandler.openUri(normalized) }
                onOpenLink?.invoke(normalized)
            }
        }
    val handleDownloadFile: (ProfileSheetFile) -> Unit = { file ->
        if (file.id !in openingFileIds) {
            openingFileIds = openingFileIds + file.id
            scope.launch {
                try {
                    var handled = false
                    val path = file.attachmentPath?.trim().orEmpty()
                    val key = file.attachmentKeyBase64?.trim().orEmpty()
                    val sha = file.attachmentSha256Base64?.trim().orEmpty()
                    val saveName = ensureProfileAttachmentFileName(file.fileName, file.mimeType)

                    if (path.isNotBlank() && key.isNotBlank() && sha.isNotBlank()) {
                        val plain =
                            connectionRepository.downloadAttachmentPlaintext(
                                path = path,
                                fileMasterKeyBase64 = key,
                                expectedSha256Base64 = sha,
                            )
                        if (plain != null) {
                            handled = saveDecryptedAttachmentToDownloads(
                                bytes = plain,
                                fileName = saveName,
                                mimeType = file.mimeType,
                            ) != null
                        }
                    }

                    // Encrypted chat-attachments must never be saved from a raw signed URL — that
                    // writes ciphertext to disk and Preview reports the file as corrupted.
                    val isEncryptedAttachment = path.isNotBlank()
                    if (!handled && !isEncryptedAttachment) {
                        val directUrl = file.downloadUrl?.trim().orEmpty()
                        if (directUrl.isNotBlank()) {
                            val bytes = fetchImageBytesFromUrl(directUrl)
                            if (bytes != null && bytes.isNotEmpty()) {
                                handled = saveDecryptedAttachmentToDownloads(
                                    bytes = bytes,
                                    fileName = saveName,
                                    mimeType = file.mimeType,
                                ) != null
                            }
                        }
                    }

                    if (!handled) {
                        onDownloadFile?.invoke(file)
                    }
                } finally {
                    openingFileIds = openingFileIds - file.id
                }
            }
        }
    }

    ProvideSheetSwipeDismiss(
        onDismissRequest = sheetOnDismiss,
        scrollAtTop = profileScrollAtTop,
    ) {
        val scrollOwnedByHost = LocalSheetScrollOwnedByHost.current
        Box(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(sheetPageBackground())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassSheetTokens.OnOled(),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                )
                ProfileSheetHeader(
                    displayName = state.displayName,
                    subtitle = state.subtitle,
                    avatarUrl = state.avatarUrl,
                    userId = state.userId,
                    email = state.email ?: state.subtitle,
                    statusBadge = state.statusBadge,
                    onAvatarClick = onAvatarClick,
                    avatarUploading = avatarUploading,
                )

                Spacer(Modifier.height(16.dp))

                ProfileActionGrid(
                    showNudge = state.canNudge,
                    showDisposableRoll = onOpenDisposableRoll != null && !state.connectionId.isNullOrBlank(),
                    onMessage = onMessage,
                    onNudge = onNudge,
                    onOpenDisposableRoll = onOpenDisposableRoll,
                )

                Spacer(Modifier.height(18.dp))

                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = GlassSheetTokens.OledBlack(),
                    contentColor = GlassSheetTokens.OnOled(),
                    edgePadding = 0.dp,
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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

                // Stop media preview / audio when leaving Media so tab switches never freeze the sheet.
                LaunchedEffect(pagerState.currentPage) {
                    val tab = visibleTabs.getOrNull(pagerState.currentPage)
                    if (tab != ProfileSheetTab.Media && selectedMediaForPreview != null) {
                        selectedMediaForPreview = null
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    // Fill remaining sheet height so each tab can scroll to its last row.
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalAlignment = Alignment.Top,
                    pageSpacing = 14.dp,
                    userScrollEnabled = true,
                ) { pageIndex ->
                    when (visibleTabs[pageIndex]) {
                        ProfileSheetTab.Timeline ->
                            TimelinePanel(
                                scrollState = timelineScroll,
                                items = state.timeline,
                                legacyProfile = legacyProfile,
                                legacyLoading = legacyLoading,
                                legacyError = legacyError,
                                showLegacy = !state.userId.isNullOrBlank(),
                                isGroup = state.isGroup,
                                sharedInterests = profileTimeline?.sharedInterests.orEmpty(),
                                journalEntries = profileTimeline?.journalEntries.orEmpty(),
                                journalText = journalText,
                                onJournalTextChange = { journalText = it.take(1_200) },
                                journalVisibility = journalVisibility,
                                onJournalVisibilityChange = { journalVisibility = it },
                                journalPosting = journalPosting,
                                journalError = journalError,
                                onSubmitJournalEntry = submitJournalEntry,
                                viewerUserId = effectiveViewerUserId,
                                editingJournalId = editingJournalId,
                                editingJournalText = editingJournalText,
                                onEditingJournalTextChange = { editingJournalText = it.take(1_200) },
                                editingJournalVisibility = editingJournalVisibility,
                                onEditingJournalVisibilityChange = { editingJournalVisibility = it },
                                mutatingJournalIds = mutatingJournalIds,
                                onStartEditJournalEntry = startEditJournalEntry,
                                onCancelEditJournalEntry = cancelEditJournalEntry,
                                onSaveEditJournalEntry = saveEditJournalEntry,
                                onDeleteJournalEntry = deleteJournalEntry,
                            )
                        ProfileSheetTab.Media ->
                            MediaPanel(
                                scrollState = mediaScroll,
                                items = effectiveMedia,
                                resolvedUrls = resolvedMediaUrls,
                                resolvedBitmaps = resolvedMediaBitmaps,
                                resolvedAudioLocalPaths = resolvedAudioLocalPaths,
                                isLoading = profileTabsHydrating,
                                resolvingMediaIds = resolvingMediaIds,
                                onEnsureMediaResolved = ensureProfileMediaResolved,
                                onOpenMedia = { media ->
                                    if (!media.isDisposableRollLocked()) {
                                        selectedMediaForPreview = media
                                    }
                                },
                            )
                        ProfileSheetTab.Links ->
                            LinksPanel(
                                scrollState = linksScroll,
                                items = effectiveLinks,
                                onOpen = handleOpenLink,
                            )
                        ProfileSheetTab.Files ->
                            FilesPanel(
                                scrollState = filesScroll,
                                items = effectiveFiles,
                                openingFileIds = openingFileIds,
                                onDownload = handleDownloadFile,
                            )
                        ProfileSheetTab.Beacons ->
                            BeaconsPanel(
                                scrollState = beaconsScroll,
                                messages = localBeaconMessages,
                                connectionId = state.connectionId,
                                isGroup = state.isGroup,
                                onOpenBeacon = { id ->
                                    onOpenBeacon?.invoke(id)
                                        ?: EventDeepLinkRouter.setPendingBeaconId(id)
                                },
                            )
                        ProfileSheetTab.Members ->
                            MembersPanel(
                                scrollState = membersScroll,
                                members = state.groupMembers,
                                viewerUserId = state.viewerUserId,
                                groupCreatorId = state.groupCreatorId,
                                onAddMember = state.onAddMember,
                                onRemoveMember = state.onRemoveMember,
                                onMemberClick = state.onMemberClick,
                            )
                    }
                }
            }

            ProfileMediaPreviewOverlay(
                mediaPreviewVisible = mediaPreviewVisible,
                mediaPreviewModel = mediaPreviewModel,
                onDismissPreview = { selectedMediaForPreview = null },
                scope = scope,
                connectionRepository = connectionRepository,
                effectiveViewerUserId = effectiveViewerUserId,
                connectionChatId = connectionChatId,
                resolvedMediaUrls = resolvedMediaUrls,
                resolvedMediaBitmaps = resolvedMediaBitmaps,
                resolvedAudioLocalPaths = resolvedAudioLocalPaths,
            )
        }
    }
}
