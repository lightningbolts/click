package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.api.ConnectionTabMessage // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 2 — C13: shared profile bottom sheet displayed when a map pin is tapped.
 *
 * Four subtabs backed by a [SecondaryTabRow] + [HorizontalPager]:
 * **Timeline · Media · Links · Files**. When [ProfileSheetState.userId] and
 * [ProfileSheetState.viewerUserId] are both provided, the Timeline subtab hydrates the
 * legacy profile rendering (interests, shared interests, availability intents, "Our
 * timeline" encounters) via [SupabaseRepository.fetchUserPublicProfile] — restoring the
 * data that was previously only available through the standalone
 * [UserProfileBottomSheet]. Media / Links / Files tabs remain empty-state placeholders
 * until the per-conversation attachment query (C15) is plumbed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    state: ProfileSheetState,
    onMessage: () -> Unit,
    onNudge: () -> Unit,
    onOpenLink: (String) -> Unit = {},
    onDownloadFile: (ProfileSheetFile) -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { ProfileSheetTab.entries.size })
    val scope = rememberCoroutineScope()

    // Hydrate legacy profile data for the Timeline subtab whenever both ids are known.
    val repository = remember { SupabaseRepository() }
    var legacyProfile by remember(state.userId, state.viewerUserId) {
        mutableStateOf<UserPublicProfile?>(null)
    }
    var legacyLoading by remember(state.userId, state.viewerUserId) { mutableStateOf(false) }
    var legacyError by remember(state.userId, state.viewerUserId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.userId, state.viewerUserId) {
        val uid = state.userId
        if (uid.isNullOrBlank()) {
            legacyProfile = null
            legacyLoading = false
            legacyError = null
            return@LaunchedEffect
        }
        legacyLoading = true
        legacyError = null
        val result = runCatching {
            withContext(Dispatchers.Default) {
                repository.fetchUserPublicProfile(state.viewerUserId, uid)
            }
        }
        legacyProfile = result.getOrNull()
        legacyError = result.exceptionOrNull()?.message
        legacyLoading = false
    }

    // BFF hydration for Media + Files subtabs (C15). We call
    // `ConnectionRepository.fetchConnectionTabs` whenever the caller supplies a
    // connection id and merge the server-owned payload with any rows that were
    // explicitly passed through state (so callers can still inject UI fixtures).
    val connectionRepository = remember { ConnectionRepository() }
    var tabMedia by remember(state.connectionId) { mutableStateOf<List<ProfileSheetMedia>>(emptyList()) }
    var tabFiles by remember(state.connectionId) { mutableStateOf<List<ProfileSheetFile>>(emptyList()) }
    LaunchedEffect(state.connectionId) {
        val cid = state.connectionId?.takeIf { it.isNotBlank() } ?: run {
            tabMedia = emptyList()
            tabFiles = emptyList()
            return@LaunchedEffect
        }
        val result = runCatching {
            withContext(Dispatchers.Default) { connectionRepository.fetchConnectionTabs(cid) }
        }.getOrNull()?.getOrNull()
        if (result != null) {
            tabMedia = result.media.mapNotNull { it.toProfileSheetMedia() }
            tabFiles = result.files.mapNotNull { it.toProfileSheetFile() }
        }
    }

    // Local E2EE cache: derive Media / Files / Links from the locally-decrypted
    // messages when available, falling back to BFF results or caller-supplied data.
    val localMedia = remember(state.localMessages) {
        state.localMessages
            .filter { it.messageType == "image" || it.messageType == "audio" }
            .mapNotNull { msg ->
                val meta = msg.metadata as? JsonObject ?: return@mapNotNull null
                val url = METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
                    meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                } ?: return@mapNotNull null
                ProfileSheetMedia(
                    id = msg.id,
                    imageUrl = url,
                    captionedAt = msg.content.takeIf { it.isNotBlank() && !it.startsWith("ccx:v1:") },
                )
            }
    }
    val localFiles = remember(state.localMessages) {
        state.localMessages
            .filter { it.messageType == "file" || it.content.startsWith("ccx:v1:") }
            .map { msg ->
                val meta = msg.metadata as? JsonObject
                ProfileSheetFile(
                    id = msg.id,
                    fileName = meta?.get("file_name")?.jsonPrimitive?.contentOrNull
                        ?: meta?.get("filename")?.jsonPrimitive?.contentOrNull
                        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: msg.content.takeIf { it.isNotBlank() && !it.startsWith("ccx:v1:") }
                        ?: "Attachment",
                    sizeBytes = meta?.get("file_size")?.jsonPrimitive?.longOrNull
                        ?: meta?.get("size_bytes")?.jsonPrimitive?.longOrNull
                        ?: 0L,
                    mimeType = meta?.get("mime_type")?.jsonPrimitive?.contentOrNull
                        ?: meta?.get("content_type")?.jsonPrimitive?.contentOrNull
                        ?: "application/octet-stream",
                    timestamp = msg.timestamp,
                )
            }
    }
    val localLinks = remember(state.localMessages) {
        extractLinksFromLocalMessages(state.localMessages)
    }

    val effectiveMedia = remember(state.media, tabMedia, localMedia) {
        when {
            localMedia.isNotEmpty() -> localMedia
            state.media.isNotEmpty() -> state.media
            else -> tabMedia
        }
    }
    val effectiveFiles = remember(state.files, tabFiles, localFiles) {
        when {
            localFiles.isNotEmpty() -> localFiles
            state.files.isNotEmpty() -> state.files
            else -> tabFiles
        }
    }
    val effectiveLinks = remember(state.links, state.decryptedTextMessages, localLinks) {
        when {
            localLinks.isNotEmpty() -> localLinks
            state.links.isNotEmpty() -> state.links
            else -> extractLinksFromDecrypted(state.decryptedTextMessages)
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
            ProfileSheetTab.entries.forEachIndexed { index, tab ->
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
            when (ProfileSheetTab.entries[pageIndex]) {
                ProfileSheetTab.Timeline -> TimelinePanel(
                    items = state.timeline,
                    legacyProfile = legacyProfile,
                    legacyLoading = legacyLoading,
                    legacyError = legacyError,
                    showLegacy = !state.userId.isNullOrBlank(),
                )
                ProfileSheetTab.Media -> MediaPanel(items = effectiveMedia)
                ProfileSheetTab.Links -> LinksPanel(items = effectiveLinks, onOpen = onOpenLink)
                ProfileSheetTab.Files -> FilesPanel(items = effectiveFiles, onDownload = onDownloadFile)
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
    /**
     * Connection / chat id — when non-blank, the sheet pulls the Media and Files
     * subtab payloads from the click-web BFF (`GET /api/connections/{id}/tabs`) and
     * merges them with any `media` / `files` explicitly passed via state.
     */
    val connectionId: String? = null,
    /**
     * Locally-decrypted text messages (plus timestamp) to scan client-side for
     * http(s) URLs. Kept here because [content] is E2EE on the wire — the BFF
     * intentionally doesn't parse links.
     */
    val decryptedTextMessages: List<ProfileSheetDecryptedMessage> = emptyList(),
    /**
     * All locally-decrypted chat messages with type metadata. Used to populate
     * the Media / Files / Links tabs from the local E2EE cache instead of making
     * a server round-trip (message content is encrypted on the wire).
     */
    val localMessages: List<ProfileSheetLocalMessage> = emptyList(),
)

/**
 * A single locally-decrypted text message used as input for the Links subtab's
 * client-side http(s) filter. Only the fields the sheet needs to render a link row.
 */
data class ProfileSheetDecryptedMessage(
    val id: String,
    val content: String,
    val timestamp: String,
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
    val imageUrl: String,
    val captionedAt: String? = null,
)

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
private fun MediaPanel(items: List<ProfileSheetMedia>) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.Image,
            title = "No shared photos",
            body = "Photos you exchange in chat will appear here.",
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.id }) { media ->
            AsyncImage(
                model = media.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
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

/**
 * Map a BFF `messages` row (`image` / `audio`) into the shape [MediaPanel] renders.
 * The attachment URL is looked up through common metadata keys (`url`, `storage_url`,
 * `image_url`) because the `messages.metadata` JSON shape is authored by a few
 * different upload paths. Returns `null` when no renderable URL is present so the
 * caller can filter the row out silently.
 */
private fun ConnectionTabMessage.toProfileSheetMedia(): ProfileSheetMedia? {
    val meta = metadata as? JsonObject ?: return null
    val url = METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
        meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    } ?: return null
    return ProfileSheetMedia(
        id = id,
        imageUrl = url,
        captionedAt = content.takeIf { it.isNotBlank() && !it.startsWith("ccx:v1:") },
    )
}

/**
 * Map a BFF `messages` row of type `file` into the shape [FilesPanel] renders. Falls
 * back gracefully when metadata fields are absent so we still list the row rather
 * than hiding it. Timestamp is intentionally the raw epoch ms / seconds as a string
 * because higher-level formatting lives at the call site in the existing codebase.
 */
private fun ConnectionTabMessage.toProfileSheetFile(): ProfileSheetFile? {
    val meta = metadata as? JsonObject
    val fileName = meta?.get("file_name")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("filename")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("name")?.jsonPrimitive?.contentOrNull
        ?: content.takeIf { it.isNotBlank() && !it.startsWith("ccx:v1:") }
        ?: "Attachment"
    val size = meta?.get("file_size")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size_bytes")?.jsonPrimitive?.longOrNull
        ?: meta?.get("size")?.jsonPrimitive?.longOrNull
        ?: 0L
    val mime = meta?.get("mime_type")?.jsonPrimitive?.contentOrNull
        ?: meta?.get("content_type")?.jsonPrimitive?.contentOrNull
        ?: "application/octet-stream"
    return ProfileSheetFile(
        id = id,
        fileName = fileName,
        sizeBytes = size,
        mimeType = mime,
        timestamp = timeCreated.toString(),
    )
}

private val METADATA_URL_KEYS = listOf("url", "storage_url", "image_url", "audio_url", "media_url")

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
private fun extractLinksFromDecrypted(
    messages: List<ProfileSheetDecryptedMessage>,
): List<ProfileSheetLink> {
    if (messages.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ProfileSheetLink>()
    messages.forEach { msg ->
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

/**
 * Extract http(s) URLs from the full [ProfileSheetLocalMessage] list (E2EE
 * local cache). Only considers text messages so image/file metadata URLs are
 * not surfaced in the Links tab.
 */
private fun extractLinksFromLocalMessages(
    messages: List<ProfileSheetLocalMessage>,
): List<ProfileSheetLink> {
    if (messages.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ProfileSheetLink>()
    messages.filter { it.messageType == "text" && it.content.contains("http") }.forEach { msg ->
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
    val cached: User? = connectedUsers[userId]

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
            localMessages = localMessages,
        )
    }

    AdaptiveBottomSheet(
        onDismissRequest = onDismiss,
        adaptiveSheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
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
