@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ConnectionEventRecommendationDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.beaconIdFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconShareUrlFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconTitleFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.beaconTypeFromMetadata // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareUrl // pragma: allowlist secret
import compose.project.click.click.ui.chat.BeaconPreviewCard // pragma: allowlist secret
import compose.project.click.click.ui.chat.BeaconPreviewModel // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioBubble // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAudioChromeKind // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.utils.beaconTypeDisplayLabel // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun MediaPanel(
    scrollState: ScrollState,
    items: List<ProfileSheetMedia>,
    resolvedUrls: Map<String, String>,
    resolvedBitmaps: Map<String, ImageBitmap>,
    resolvedAudioLocalPaths: Map<String, String>,
    isLoading: Boolean,
    resolvingMediaIds: Set<String>,
    onEnsureMediaResolved: (ProfileSheetMedia) -> Unit,
    onOpenMedia: (ProfileSheetMedia) -> Unit,
) {
    val imageItems =
        items
            .filter { it.mediaType == ProfileSheetMediaType.Image }
            .sortedByDescending { profileMediaSortEpoch(it) }
    val audioItems =
        items
            .filter { it.mediaType == ProfileSheetMediaType.Audio }
            .sortedByDescending { profileMediaSortEpoch(it) }
    val imageRows = imageItems.chunked(3)

    if (items.isEmpty()) {
        if (isLoading) {
            Column(
                modifier =
                    Modifier
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
                            val skelShape = RoundedCornerShape(14.dp)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(96.dp)
                                        .clip(skelShape)
                                        .border(1.dp, GlassSheetTokens.GlassBorder(), skelShape)
                                        .background(GlassSheetTokens.GlassSurface()),
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

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        imageRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { media ->
                    val rollLocked = media.isDisposableRollLocked()
                    val bitmap = resolvedBitmaps[media.id]
                    val resolvedUrl = resolvedUrls[media.id] ?: media.mediaUrl
                    val thumbResolving = media.id in resolvingMediaIds
                    val thumbUnlocking = bitmap == null && thumbResolving
                    val thumbReady =
                        bitmap != null ||
                            (!media.isEncrypted && !resolvedUrl.isNullOrBlank())
                    val countdownLabel =
                        remember(media.id, rollLocked) {
                            media.disposableRollCountdownLabel()
                        }
                    LaunchedEffect(media.id) {
                        if (!thumbReady) onEnsureMediaResolved(media)
                    }
                    val thumbReveal by animateFloatAsState(
                        targetValue =
                            if (thumbReady) {
                                1f
                            } else if (thumbUnlocking) {
                                0.55f
                            } else {
                                0.38f
                            },
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
                    val thumbShape = RoundedCornerShape(14.dp)
                    val thumbBorder = GlassSheetTokens.GlassBorder()
                    val thumbBg = GlassSheetTokens.GlassSurface()
                    val thumbModifier =
                        Modifier
                            .weight(1f)
                            .height(110.dp)
                            .graphicsLayer {
                                scaleX = thumbScale
                                scaleY = thumbScale
                                alpha = thumbReveal
                            }.clip(thumbShape)
                            .border(1.dp, thumbBorder, thumbShape)
                            .background(thumbBg)
                            .clickable(
                                interactionSource = thumbInteraction,
                                indication = ripple(bounded = true, radius = 52.dp),
                                enabled = thumbReady && !rollLocked,
                            ) { onOpenMedia(media) }
                    Box(modifier = thumbModifier, contentAlignment = Alignment.Center) {
                        when {
                            bitmap != null -> {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(if (rollLocked) Modifier.blur(25.dp) else Modifier),
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
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(if (rollLocked) Modifier.blur(25.dp) else Modifier),
                                )
                            }
                        }
                        if (rollLocked && countdownLabel != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .clip(thumbShape)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = countdownLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(110.dp),
                    )
                }
            }
        }

        if (audioItems.isNotEmpty()) {
            audioItems.forEach { media ->
                val stream = resolvedUrls[media.id] ?: media.mediaUrl
                val local = resolvedAudioLocalPaths[media.id]
                val canPlay =
                    !local.isNullOrBlank() ||
                        (stream?.isNotBlank() == true && !media.isEncrypted)
                val resolvingAudio = media.id in resolvingMediaIds
                LaunchedEffect(media.id) {
                    if (!canPlay) onEnsureMediaResolved(media)
                }
                val unlockingAudio = local.isNullOrBlank() && resolvingAudio
                val failedEncryptedAudio =
                    media.isEncrypted && local.isNullOrBlank() && !resolvingAudio
                val rowReveal by animateFloatAsState(
                    targetValue = if (canPlay || unlockingAudio || failedEncryptedAudio) 1f else 0.4f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "media_audio_${media.id}",
                )
                Column(
                    modifier =
                        Modifier
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
                            val lockShape = RoundedCornerShape(14.dp)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(lockShape)
                                        .border(1.dp, GlassSheetTokens.GlassBorder(), lockShape)
                                        .background(GlassSheetTokens.GlassSurface())
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
                                modifier =
                                    Modifier
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
internal fun BeaconsPanel(
    scrollState: ScrollState,
    messages: List<ProfileSheetLocalMessage>,
    connectionId: String?,
    isGroup: Boolean,
    onOpenBeacon: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var recommendation by remember(connectionId) {
        mutableStateOf<ConnectionEventRecommendationDto?>(null)
    }
    var recommendationDismissed by remember(connectionId) { mutableStateOf(false) }
    var rsvpInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(connectionId, isGroup) {
        if (isGroup) return@LaunchedEffect
        val connId = connectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        recommendation =
            withContext(Dispatchers.Default) {
                runCatching {
                    ApiClient()
                        .getConnectionEventRecommendation(connId)
                        .getOrNull()
                        ?.recommendation
                }.getOrNull()
            }
    }

    val previews =
        remember(messages) {
            messages
                .mapNotNull { msg ->
                    val id =
                        beaconIdFromMetadata(msg.metadata)
                            ?: return@mapNotNull null
                    val meta = msg.metadata as? kotlinx.serialization.json.JsonObject
                    val title =
                        beaconTitleFromMetadata(msg.metadata)
                            ?: msg.content
                                .removePrefix("Beacon:")
                                .trim()
                                .ifBlank { "Beacon" }
                    val typeRaw =
                        beaconTypeFromMetadata(msg.metadata)
                    val kind =
                        MapBeaconKind.fromRaw(typeRaw)
                    val schedule =
                        meta
                            ?.get("schedule_label")
                            ?.let {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            }?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    val description =
                        meta
                            ?.get("description")
                            ?.let {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            }?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    BeaconPreviewModel(
                        beaconId = id,
                        title = title,
                        kindLabel =
                            beaconTypeDisplayLabel(typeRaw, kind),
                        kind = kind,
                        description = description,
                        scheduleLabel = schedule,
                        shareUrl =
                            beaconShareUrlFromMetadata(msg.metadata)
                                ?: buildEventShareUrl(id),
                        albumArtUrl =
                            meta?.get("album_art_url")?.let {
                                (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                            },
                    )
                }.distinctBy { it.beaconId }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isGroup) {
            val rec = recommendation
            if (rec != null && !recommendationDismissed) {
                ConnectionEventRecommendationCard(
                    recommendation = rec,
                    rsvpInProgress = rsvpInProgress,
                    onRsvp = {
                        rsvpInProgress = true
                        scope.launch(Dispatchers.Default) {
                            runCatching {
                                ApiClient()
                                    .postBeaconRsvp(rec.beaconId)
                            }
                            withContext(Dispatchers.Main) {
                                recommendationDismissed = true
                                rsvpInProgress = false
                                onOpenBeacon(rec.beaconId)
                            }
                        }
                    },
                    onDismiss = { recommendationDismissed = true },
                )
            }
        }

        if (previews.isEmpty()) {
            EmptyTabState(
                icon = Icons.Outlined.Place,
                title = "No shared beacons",
                body = "Events from handshakes and map pins shared in this chat show up here.",
            )
        } else {
            previews.forEach { model ->
                BeaconPreviewCard(
                    model = model,
                    onClick = { onOpenBeacon(model.beaconId) },
                )
            }
        }
    }
}

@Composable
internal fun LinksPanel(
    scrollState: ScrollState,
    items: List<ProfileSheetLink>,
    onOpen: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.Link,
            title = "No shared links",
            body = "URLs shared in chat show up here.",
        )
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { link ->
            val linkShape = RoundedCornerShape(14.dp)
            val cardBorder = GlassSheetTokens.GlassBorder()
            val cardBg = GlassSheetTokens.GlassSurface()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(linkShape)
                        .border(1.dp, cardBorder, linkShape)
                        .background(cardBg)
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
internal fun MembersPanel(
    scrollState: ScrollState,
    members: List<User>,
    viewerUserId: String?,
    groupCreatorId: String?,
    onAddMember: (() -> Unit)?,
    onRemoveMember: ((String) -> Unit)?,
    onMemberClick: ((String) -> Unit)?,
) {
    val isGroupAdmin = !viewerUserId.isNullOrBlank() && viewerUserId == groupCreatorId
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onAddMember != null) {
            TextButton(onClick = onAddMember) {
                Text("Add member")
            }
        }
        if (members.isEmpty()) {
            EmptyTabState(
                icon = Icons.Outlined.People,
                title = "No members yet",
                body = "People in this group will show up here.",
            )
        } else {
            members.forEach { user ->
                val label = user.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Member"
                val canRemove =
                    isGroupAdmin &&
                        onRemoveMember != null &&
                        !viewerUserId.isNullOrBlank() &&
                        user.id != viewerUserId
                ListItem(
                    headlineContent = {
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    },
                    supportingContent =
                        user.email?.trim()?.takeIf { it.isNotEmpty() }?.let { email ->
                            {
                                Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    leadingContent = {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            ConnectionListUserAvatarFace(
                                displayName = user.name,
                                email = user.email,
                                avatarUrl = user.image,
                                userId = user.id,
                                modifier = Modifier.fillMaxSize(),
                                useCompactTypography = false,
                            )
                        }
                    },
                    trailingContent =
                        if (canRemove) {
                            {
                                TextButton(onClick = { onRemoveMember?.invoke(user.id) }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            null
                        },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onMemberClick?.invoke(user.id) },
                )
            }
        }
    }
}

@Composable
internal fun FilesPanel(
    scrollState: ScrollState,
    items: List<ProfileSheetFile>,
    openingFileIds: Set<String>,
    onDownload: (ProfileSheetFile) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyTabState(
            icon = Icons.Outlined.AttachFile,
            title = "No shared files",
            body = "Attachments sent in chat will appear here.",
        )
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxSize()
                // Always Compose-scroll inside the pager page (UIKit host sheetBodyScroll is a
                // no-op; fillMaxSize + verticalScroll keeps tabs scrollable and Metal-safe).
                .verticalScroll(scrollState)
                .padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { file ->
            val opening = file.id in openingFileIds
            val fileShape = RoundedCornerShape(14.dp)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(fileShape)
                        .border(1.dp, GlassSheetTokens.GlassBorder(), fileShape)
                        .background(GlassSheetTokens.GlassSurface())
                        .clickable(enabled = !opening) { onDownload(file) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (opening) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = PrimaryBlue,
                    )
                }
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
                        if (opening) {
                            "Opening..."
                        } else {
                            "${formatFileSize(file.sizeBytes)} · ${file.mimeType}"
                        },
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
internal fun EmptyTabState(
    icon: ImageVector,
    title: String,
    body: String,
) {
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
