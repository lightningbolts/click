@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ConnectionEventRecommendationDto // pragma: allowlist secret
import compose.project.click.click.data.api.EventTeaserDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.withPreservedEventScheduleFrom // pragma: allowlist secret
import compose.project.click.click.events.EventRsvpRequestStatus // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareText // pragma: allowlist secret
import compose.project.click.click.events.buildEventShareUrl // pragma: allowlist secret
import compose.project.click.click.events.canOpenEventHub // pragma: allowlist secret
import compose.project.click.click.events.eventCheckInCtaLabel // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventPostedAtLabel // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.events.isEnded // pragma: allowlist secret
import compose.project.click.click.events.isLive // pragma: allowlist secret
import compose.project.click.click.events.openEventMapsRoute // pragma: allowlist secret
import compose.project.click.click.events.parseEventListingOptions // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.platform.shareText // pragma: allowlist secret
import compose.project.click.click.ui.components.AnimatedClickDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDropdownMenu // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionEventRecommendationCard // pragma: allowlist secret
import compose.project.click.click.ui.components.EventDirectoryUserProfileSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.EventPeopleDirectorySection // pragma: allowlist secret
import compose.project.click.click.ui.components.EventPeopleDirectorySheetContent // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun BeaconDetailSheetContent(
    beacon: MapBeacon,
    distanceMeters: Double?,
    currentUserId: String?,
    viewModel: MapViewModel,
    onShareBeaconToChat: ((MapBeacon) -> Unit)? = null,
    onNavigateToChat: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCreator = !currentUserId.isNullOrBlank() && beacon.createdByUserId == currentUserId
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editDraft by remember(beacon.id) {
        mutableStateOf(
            beacon.metadata.description
                ?.trim()
                .orEmpty(),
        )
    }
    val openEdit: () -> Unit = {
        editDraft =
            beacon.metadata.description
                ?.trim()
                .orEmpty()
        showEditDialog = true
    }
    val openDelete: () -> Unit = { showDeleteConfirm = true }

    Column(modifier = modifier) {
        when (beacon.kind) {
            MapBeaconKind.SOUNDTRACK ->
                SoundtrackBeaconDetail(
                    beacon = beacon,
                    distanceMeters = distanceMeters,
                    viewModel = viewModel,
                    isCreator = isCreator,
                    onEdit = openEdit,
                    onDelete = openDelete,
                    onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            MapBeaconKind.EVENT ->
                EventBeaconDetail(
                    beacon = beacon,
                    distanceMeters = distanceMeters,
                    viewModel = viewModel,
                    isCreator = isCreator,
                    onEdit = openEdit,
                    onDelete = openDelete,
                    onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                    onNavigateToChat = onNavigateToChat,
                    modifier = Modifier.fillMaxWidth(),
                )
            else -> {
                CommunityBeaconDetail(
                    beacon = beacon,
                    distanceMeters = distanceMeters,
                    viewModel = viewModel,
                    isCreator = isCreator,
                    onEdit = openEdit,
                    onDelete = openDelete,
                    onShareToChat = onShareBeaconToChat?.let { cb -> { cb(beacon) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Animated (scale + fade) confirmation, matching the create/join hub popup motion.
    AnimatedClickDialog(
        visible = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = "Delete beacon?",
        confirmLabel = "Delete",
        onConfirm = {
            showDeleteConfirm = false
            viewModel.deleteOwnedBeacon(beacon.id)
        },
    ) {
        Text(
            text = "This removes the pin from the map for everyone nearby.",
            color = GlassSheetTokens.OnOledMuted(),
        )
    }

    AnimatedClickDialog(
        visible = showEditDialog,
        onDismissRequest = { showEditDialog = false },
        title = "Edit beacon",
        confirmLabel = "Save",
        onConfirm = {
            showEditDialog = false
            viewModel.updateOwnedBeaconDescription(beacon.id, editDraft)
        },
    ) {
        ClickOutlinedTextField(
            value = editDraft,
            onValueChange = { if (it.length <= 140) editDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            maxLines = 4,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GlassSheetTokens.OnOled(),
                    unfocusedTextColor = GlassSheetTokens.OnOled(),
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                    cursorColor = PrimaryBlue,
                    focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                    unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                ),
        )
    }
}

@Composable
internal fun BeaconOwnerOverflowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val border = clickBorderColor()
    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        EventHeroIconButton(
            selected = menuExpanded,
            border = border,
            onClick = { menuExpanded = true },
            contentDescription = "More actions",
            icon = Icons.Filled.MoreVert,
        )
        BeaconOwnerDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

/** Owner overflow — shared [ClickDropdownMenu] chrome. */
@Composable
internal fun BeaconOwnerDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ClickDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        items =
            listOf(
                ClickMenuItem(
                    label = "Edit",
                    onClick = onEdit,
                    icon = Icons.Filled.Edit,
                ),
                ClickMenuItem(
                    label = "Delete",
                    onClick = onDelete,
                    icon = Icons.Filled.Delete,
                    destructive = true,
                ),
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EventBeaconDetail(
    beacon: MapBeacon,
    distanceMeters: Double?,
    viewModel: MapViewModel,
    isCreator: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareToChat: (() -> Unit)? = null,
    onNavigateToChat: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rsvpCache by viewModel.beaconRsvpById.collectAsState()
    val rsvpLoadingIds by viewModel.beaconRsvpLoadingIds.collectAsState()
    val rsvpPendingIds by viewModel.beaconRsvpPendingIds.collectAsState()
    val entry = rsvpCache[beacon.id]
    val attendees = entry?.attendees.orEmpty()
    val rsvpCacheSignedUp = entry?.currentUserSignedUp == true
    val rsvpLoading = entry == null && beacon.id in rsvpLoadingIds
    val rsvpPending = beacon.id in rsvpPendingIds
    var rsvpError by remember(beacon.id) { mutableStateOf<String?>(null) }
    val engagementCache by viewModel.beaconEngagementById.collectAsState()
    val checkInPendingIds by viewModel.beaconCheckInPendingIds.collectAsState()
    val bookmarkPendingIds by viewModel.beaconBookmarkPendingIds.collectAsState()
    val engagement = engagementCache[beacon.id]
    val bookmarked = engagement?.bookmarked == true
    val engagementCheckedIn = engagement?.checkedIn == true || engagement?.localEarlyCheckIn == true
    val checkInPending = beacon.id in checkInPendingIds
    val bookmarkPending = beacon.id in bookmarkPendingIds
    val uriHandler = LocalUriHandler.current
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val mapBeacons by viewModel.mapBeacons.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    // Direct GET override — Home saved/bookmark seeds often lack host until fetch completes.
    var networkDetail by remember(beacon.id) { mutableStateOf<MapBeacon?>(null) }
    // Key on the matching row (not the whole list) so host/posted patches invalidate remember.
    val fromMap = mapBeacons.firstOrNull { it.id == beacon.id }
    val fromPrefetch = prefetchedBeacons.firstOrNull { it.id == beacon.id }
    val displayBeacon =
        remember(beacon, fromMap, fromPrefetch, networkDetail) {
            val live = networkDetail ?: fromMap ?: fromPrefetch
            when {
                live == null -> beacon
                else -> live.withPreservedEventScheduleFrom(beacon)
            }
        }
    val schedule = displayBeacon.eventSchedule()
    val live = schedule?.isLive() == true
    val ended = schedule?.isEnded() == true
    val rsvpRequestStatus = entry?.requestStatus
    val listing = parseEventListingOptions(displayBeacon.metadata.raw)
    val pendingOrWaitlisted =
        rsvpRequestStatus == EventRsvpRequestStatus.PENDING ||
            rsvpRequestStatus == EventRsvpRequestStatus.WAITLISTED
    val distanceLabel = distanceMeters?.let { formatBeaconDistance(it) }
    val scheduleRange = schedule?.let { formatEventScheduleRange(it) }
    val categories = displayBeacon.metadata.eventCategories
    val border = clickBorderColor()
    val cardSurface = clickCardSurface()
    val hostUserId = displayBeacon.createdByUserId?.takeIf { it.isNotBlank() }
    val hostUser =
        hostUserId?.let { id ->
            if (id == currentUser?.id) currentUser else connectedUsers[id]
        }
    val hostDisplayName =
        displayBeacon.creatorDisplayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostUser?.name?.trim()?.takeIf { it.isNotEmpty() }
    val hostAvatarUrl = hostUser?.image?.trim()?.takeIf { it.isNotEmpty() }

    val directoryCache by viewModel.beaconDirectoryById.collectAsState()
    val directoryLoadingIds by viewModel.beaconDirectoryLoadingIds.collectAsState()
    val directoryEntry = directoryCache[beacon.id]
    val directoryAttendees = directoryEntry?.attendees.orEmpty()
    val directoryLoading = beacon.id in directoryLoadingIds
    // The enriched directory independently returns the viewer's RSVP/check-in state.
    // Prefer a positive answer from either cache so a stale engagement response cannot
    // hide Cancel RSVP / Check out controls.
    val currentUserSignedUp =
        rsvpCacheSignedUp || directoryEntry?.currentUserSignedUp == true
    val checkedIn =
        engagementCheckedIn || directoryEntry?.currentUserCheckedIn == true
    // Mutuals are server-authorized enrichment. Local/early check-in only changes the CTA;
    // it must not expose an old or unavailable mutual directory payload.
    val mutualsUnlocked = directoryEntry?.mutualsSectionUnlocked == true
    var showPeopleDirectory by remember(beacon.id) { mutableStateOf(false) }
    var directoryProfileUserId by remember(beacon.id) { mutableStateOf<String?>(null) }
    var pendingDirectoryProfileUserId by remember(beacon.id) { mutableStateOf<String?>(null) }
    var seedTeaser by remember(beacon.id) { mutableStateOf<EventTeaserDto?>(null) }
    var seedTeaserDismissed by remember(beacon.id) { mutableStateOf(false) }
    val seedApi = remember { ApiClient() }

    LaunchedEffect(displayBeacon.id) {
        seedTeaser = seedApi.getEventTeaser(displayBeacon.id).getOrNull()?.teaser
        viewModel.loadBeaconRsvp(displayBeacon.id, forceRefresh = true)
        viewModel.loadBeaconEngagement(displayBeacon.id, forceRefresh = true)
        viewModel.recordEventImpression(displayBeacon.id)
        // Always hydrate missing Posted / Host / creator / schedule — bookmark & proximity rows
        // often already have schedule, so the old schedule-only gate skipped host+posted forever.
        viewModel.ensureEventBeaconDetail(displayBeacon.id, seed = displayBeacon)
    }

    LaunchedEffect(displayBeacon.id, currentUserSignedUp, checkedIn) {
        viewModel.loadBeaconAttendeeDirectory(displayBeacon.id, forceRefresh = false)
    }

    LaunchedEffect(showPeopleDirectory, displayBeacon.id) {
        if (showPeopleDirectory) {
            // Prefer cache; only refresh if we never enriched this beacon.
            viewModel.loadBeaconAttendeeDirectory(
                displayBeacon.id,
                forceRefresh = directoryEntry == null,
            )
        }
    }
    // UIKit cannot present the profile sheet while the directory's dismiss animation is active.
    // Queue the presentation until the first page sheet has fully left the screen.
    LaunchedEffect(showPeopleDirectory, pendingDirectoryProfileUserId) {
        val pendingId = pendingDirectoryProfileUserId
        if (!showPeopleDirectory && pendingId != null) {
            delay(450)
            directoryProfileUserId = pendingId
            pendingDirectoryProfileUserId = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BeaconIdentityBanner(beacon = displayBeacon)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (live) {
                    EventLiveBadge()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = displayBeacon.displayDynamicTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                distanceLabel?.let { d ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EventHeroActions(
                bookmarked = bookmarked,
                bookmarkPending = bookmarkPending,
                isCreator =
                    isCreator ||
                        (!currentUser?.id.isNullOrBlank() && displayBeacon.createdByUserId == currentUser?.id),
                onShare = {
                    val shareUrl = buildEventShareUrl(displayBeacon.id)
                    viewModel.recordEventShare(displayBeacon.id, shareUrl = shareUrl)
                    shareText(
                        text = buildEventShareText(displayBeacon, scheduleRange, distanceLabel),
                        subject = displayBeacon.displayDynamicTitle(),
                    )
                },
                onShareToChat = onShareToChat,
                onToggleBookmark = {
                    if (!bookmarkPending) viewModel.toggleBeaconBookmark(displayBeacon.id)
                },
                onEdit = onEdit,
                onDelete = onDelete,
            )
        }

        schedule?.let { EventScheduleBento(schedule = it, border = border, cardSurface = cardSurface) }

        if (categories.isNotEmpty()) {
            EventCategoryChips(categories = categories, border = border, cardSurface = cardSurface)
        }

        val rawLocationLabel =
            displayBeacon.metadata.formattedAddress
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: displayBeacon.metadata.locationName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        // Legacy drops stored the literal "Current location" — never show that to viewers.
        var resolvedLocationLabel by remember(displayBeacon.id, rawLocationLabel) {
            mutableStateOf(
                rawLocationLabel?.takeUnless { it.equals("Current location", ignoreCase = true) },
            )
        }
        LaunchedEffect(displayBeacon.id, rawLocationLabel, displayBeacon.latitude, displayBeacon.longitude) {
            if (resolvedLocationLabel != null) return@LaunchedEffect
            if (
                rawLocationLabel == null ||
                rawLocationLabel.equals("Current location", ignoreCase = true)
            ) {
                val reverse =
                    withContext(Dispatchers.Default) {
                        compose.project.click.click.utils.GeocodingService.reverseGeocode( // pragma: allowlist secret
                            displayBeacon.latitude,
                            displayBeacon.longitude,
                        )
                    }
                resolvedLocationLabel = reverse?.shortLabel?.takeIf { it.isNotBlank() }
                    ?: reverse?.displayName?.takeIf { it.isNotBlank() }
            }
        }
        resolvedLocationLabel?.let { locationLabel ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(clickBorderWidth(), border, RoundedCornerShape(12.dp))
                        .background(cardSurface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (displayBeacon.showCreatorName && !hostDisplayName.isNullOrBlank()) {
            EventHostCard(
                displayName = hostDisplayName,
                userId = hostUserId ?: "host:$hostDisplayName",
                avatarUrl = hostAvatarUrl,
                border = border,
                cardSurface = cardSurface,
            )
        }

        Text(
            text =
                displayBeacon.metadata.description
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "No description" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        displayBeacon.createdAtEpochMs?.let { createdMs ->
            Text(
                text = "Posted ${formatEventPostedAtLabel(createdMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EventPeopleDirectorySection(
            attendees =
                directoryAttendees.ifEmpty {
                    attendees.map {
                        compose.project.click.click.events.DirectoryAttendee( // pragma: allowlist secret
                            userId = it.userId,
                            name = it.name,
                            avatarUrl = it.avatarUrl,
                        )
                    }
                },
            loading = directoryLoading || rsvpLoading,
            mutualsSectionUnlocked = mutualsUnlocked,
            directoryEnriched = directoryEntry != null,
            onOpenDirectory = { showPeopleDirectory = true },
        )

        if (!seedTeaserDismissed) {
            seedTeaser?.let { teaser ->
                ConnectionEventRecommendationCard(
                    recommendation =
                        ConnectionEventRecommendationDto(
                            beaconId = displayBeacon.id,
                            title = displayBeacon.displayDynamicTitle(),
                            peerName = "",
                            peerUserId = "",
                        ),
                    onRsvp = {},
                    onDismiss = { seedTeaserDismissed = true },
                    headline = teaser.headline,
                    chipLabel = "Seed a Room",
                    showRsvp = false,
                    subtitle = "Names stay private until you Click.",
                )
            }
        }

        if (
            isCreator ||
            (!currentUser?.id.isNullOrBlank() && displayBeacon.createdByUserId == currentUser?.id)
        ) {
            EventGuestListPasteCard(
                beaconId = displayBeacon.id,
                border = border,
                cardSurface = cardSurface,
            )
        }

        if (showPeopleDirectory) {
            ClickFormBottomSheet(
                onDismissRequest = { showPeopleDirectory = false },
                expandable = true,
                // Column + sheetBodyScroll — UIKit scroll-host (same as view-event; no surface-drag flicker).
                useUiKitScrollHost = true,
            ) {
                EventPeopleDirectorySheetContent(
                    attendees =
                        directoryAttendees.ifEmpty {
                            attendees.map {
                                compose.project.click.click.events.DirectoryAttendee( // pragma: allowlist secret
                                    userId = it.userId,
                                    name = it.name,
                                    avatarUrl = it.avatarUrl,
                                )
                            }
                        },
                    loading = directoryLoading,
                    mutualsSectionUnlocked = mutualsUnlocked,
                    directoryEnriched = directoryEntry != null,
                    onAttendeeClick = { attendee ->
                        pendingDirectoryProfileUserId = attendee.userId
                        showPeopleDirectory = false
                    },
                )
            }
        }

        directoryProfileUserId?.let { profileId ->
            val attendee =
                directoryAttendees.firstOrNull { it.userId == profileId }
                    ?: attendees
                        .map {
                            compose.project.click.click.events.DirectoryAttendee( // pragma: allowlist secret
                                userId = it.userId,
                                name = it.name,
                                avatarUrl = it.avatarUrl,
                            )
                        }.firstOrNull { it.userId == profileId }
            if (attendee != null) {
                val viewerId = currentUser?.id
                val canMessage =
                    compose.project.click.click.events.allowsDirectoryConnectActions( // pragma: allowlist secret
                        attendee.relationship,
                    )
                EventDirectoryUserProfileSheet(
                    attendee = attendee,
                    viewerUserId = viewerId,
                    onDismiss = { directoryProfileUserId = null },
                    onMessage =
                        if (canMessage) {
                            {
                                val conn =
                                    compose.project.click.click.data.AppDataManager.connections.value // pragma: allowlist secret
                                        .firstOrNull { c ->
                                            attendee.userId in c.user_ids &&
                                                (viewerId.isNullOrBlank() || viewerId in c.user_ids)
                                        }
                                conn?.id?.let { onNavigateToChat?.invoke(it) }
                            }
                        } else {
                            null
                        },
                )
            }
        }

        val actionShape = RoundedCornerShape(12.dp)
        val checkInLabel = eventCheckInCtaLabel(checkedIn = checkedIn, pending = checkInPending)
        Button(
            onClick = {
                if (checkInPending) return@Button
                viewModel.toggleBeaconCheckIn(displayBeacon.id)
            },
            enabled = !checkInPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = actionShape,
            border = BorderStroke(clickBorderWidth(), border),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (checkedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    contentColor =
                        if (checkedIn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (checkInPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = if (checkedIn) Icons.Filled.CheckCircle else Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(checkInLabel, fontWeight = FontWeight.SemiBold)
        }

        val eventHubId = displayBeacon.hubId ?: engagement?.hubId
        val canOpenHub =
            canOpenEventHub(
                hubId = eventHubId,
                isCreator = isCreator,
                checkedIn = checkedIn,
                hasRsvp = currentUserSignedUp,
            )
        if (!eventHubId.isNullOrBlank()) {
            if (canOpenHub) {
                Button(
                    onClick = {
                        ChatDeepLinkManager.setPendingEventHub(
                            hubId = eventHubId,
                            title = displayBeacon.displayDynamicTitle(),
                            creatorId = displayBeacon.createdByUserId,
                        )
                        viewModel.clearSelection()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = actionShape,
                    border = BorderStroke(clickBorderWidth(), border),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open event hub", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "Check in to join the event hub",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }

        Button(
            onClick = {
                openEventMapsRoute(
                    openUri = { uriHandler.openUri(it) },
                    latitude = displayBeacon.latitude,
                    longitude = displayBeacon.longitude,
                    label = displayBeacon.displayDynamicTitle(),
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = actionShape,
            border = BorderStroke(clickBorderWidth(), border),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Directions,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Join Event Route", fontWeight = FontWeight.SemiBold)
        }

        if (ended) {
            EventEndedBanner()
        }

        if (!ended) {
            if (currentUserSignedUp || pendingOrWaitlisted) {
                Button(
                    onClick = {
                        if (rsvpPending) return@Button
                        rsvpError = null
                        viewModel.cancelRsvpToBeacon(displayBeacon.id) { ok ->
                            if (!ok) {
                                rsvpError = viewModel.engagementSnackbar.value
                                    ?: "Could not update RSVP. Please try again."
                            }
                        }
                    },
                    enabled = !rsvpPending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = actionShape,
                    border = BorderStroke(clickBorderWidth(), MaterialTheme.colorScheme.error),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f),
                        ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        text =
                            if (rsvpPending) {
                                "Updating…"
                            } else if (currentUserSignedUp) {
                                "Cancel RSVP"
                            } else {
                                "Withdraw request"
                            },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (rsvpPending) return@OutlinedButton
                        rsvpError = null
                        viewModel.rsvpToBeacon(displayBeacon.id) { ok ->
                            if (!ok) {
                                rsvpError = viewModel.engagementSnackbar.value
                                    ?: "Could not update RSVP. Please try again."
                            }
                        }
                    },
                    enabled = !rsvpPending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = actionShape,
                    border = BorderStroke(clickBorderWidth(), border),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        text =
                            if (rsvpPending) {
                                "Updating…"
                            } else if (listing.approvalRequired) {
                                "Request to join"
                            } else {
                                "RSVP / Sign Up"
                            },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (rsvpPending) {
                Text(
                    text = "Saving in the background...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        EventRsvpStatusLine(
            requestStatus = rsvpRequestStatus,
            errorMessage = rsvpError,
        )
    }
}
