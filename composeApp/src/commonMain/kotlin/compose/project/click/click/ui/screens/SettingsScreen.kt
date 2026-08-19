@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
    "ktlint:standard:indent",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.HomeLayoutMode // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.platformForegroundTickFlow // pragma: allowlist secret
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenScaffold // pragma: allowlist secret
import compose.project.click.click.ui.components.AvailabilitySheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderBackIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackContainer // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformBackHandler // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeNavigationBarSwipeReveal // pragma: allowlist secret
import compose.project.click.click.ui.components.SavedEventsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberInteractiveBackHostState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberMicrophonePermissionRequester // pragma: allowlist secret
import compose.project.click.click.utils.LocationPermissionDisplayState // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.utils.readLocationPermissionDisplayState // pragma: allowlist secret
import compose.project.click.click.viewmodel.AvailabilityViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapViewModel // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class SettingsPage {
    Hub,
    Availability,
    Alerts,
    Privacy,
    Interests,
    Personality,
    Saved,
    Appearance,
}

internal fun SettingsPage.title(): String =
    when (this) {
        SettingsPage.Hub -> "Settings"
        SettingsPage.Availability -> "Availability"
        SettingsPage.Alerts -> "Alerts"
        SettingsPage.Privacy -> "Privacy & data"
        SettingsPage.Interests -> "Interests"
        SettingsPage.Personality -> "Personality"
        SettingsPage.Saved -> "Saved events"
        SettingsPage.Appearance -> "Appearance"
    }

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSignOut: () -> Unit = {},
    onOpenSearch: (() -> Unit)? = null,
    onSubpageOpenChanged: (Boolean) -> Unit = {},
    availabilityViewModel: AvailabilityViewModel =
        viewModel(key = "settings-availability") { AvailabilityViewModel() },
    mapViewModel: MapViewModel,
    onShareBeaconToChats: ((MapBeacon, List<String>, String?) -> Unit)? = null,
) {
    val currentAvailability by availabilityViewModel.currentAvailability.collectAsState()
    val activeAvailabilityIntents by availabilityViewModel.activeAvailabilityIntents.collectAsState()
    val loadingActiveAvailabilityIntents by availabilityViewModel.loadingActiveAvailabilityIntents.collectAsState()
    val hasResolvedActiveAvailabilityIntents by availabilityViewModel.hasResolvedActiveAvailabilityIntents.collectAsState()
    val intentListFeedback by availabilityViewModel.intentListFeedback.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val notificationPreferences by AppDataManager.notificationPreferences.collectAsState()
    val locationPreferences by AppDataManager.locationPreferences.collectAsState()
    val ghostModeEnabled by AppDataManager.ghostModeEnabled.collectAsState()

    val tokenStorage = remember { createTokenStorage() }
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val locationService = remember { LocationService() }
    val requestMicrophonePermissionThen = rememberMicrophonePermissionRequester()
    val requestLocationPermissionThen = rememberLocationPermissionRequester()
    val settingsScope = rememberCoroutineScope()
    val toastState = rememberUnifiedToastState()
    var avatarUploading by remember { mutableStateOf(false) }
    val authRepoForAvatar = remember(tokenStorage) { AuthRepository(tokenStorage = tokenStorage) }
    val supabaseRepository = remember { SupabaseRepository() }
    val mediaPickers =
        rememberChatMediaPickers(
            onImagePicked = { bytes, mime ->
                settingsScope.launch {
                    avatarUploading = true
                    try {
                        authRepoForAvatar.uploadProfilePicture(bytes, mime).fold(
                            onSuccess = { url ->
                                AppDataManager.applyProfilePictureUrl(url)
                                toastState.show(settingsScope, "Profile photo updated")
                            },
                            onFailure = { e ->
                                val msg =
                                    e.message
                                        ?.lines()
                                        ?.firstOrNull()
                                        ?.take(180)
                                        ?: "Could not update profile photo"
                                toastState.show(settingsScope, msg)
                            },
                        )
                    } finally {
                        avatarUploading = false
                    }
                }
            },
            onAudioPicked = { _, _, _ -> },
            onMediaAccessBlocked = { msg ->
                toastState.show(settingsScope, msg)
            },
        )

    val foregroundSyncTick by platformForegroundTickFlow().collectAsState()

    var ambientNoiseOptIn by remember { mutableStateOf(false) }
    var micPermissionBump by remember { mutableIntStateOf(0) }
    var locationPermissionBump by remember { mutableIntStateOf(0) }
    var microphoneGranted by remember { mutableStateOf(ambientNoiseMonitor.hasPermission) }
    var locationPermissionState by remember {
        mutableStateOf(locationService.readLocationPermissionDisplayState())
    }

    LaunchedEffect(micPermissionBump, foregroundSyncTick) {
        microphoneGranted = ambientNoiseMonitor.hasPermission
    }

    LaunchedEffect(locationPermissionBump, foregroundSyncTick) {
        locationPermissionState = locationService.readLocationPermissionDisplayState()
        if (locationPermissionState != LocationPermissionDisplayState.Granted) {
            repeat(4) {
                delay(250)
                locationPermissionState = locationService.readLocationPermissionDisplayState()
                if (locationPermissionState == LocationPermissionDisplayState.Granted) {
                    return@LaunchedEffect
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser?.id != null) {
            availabilityViewModel.refreshActiveAvailabilityIntents()
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var newFirstName by remember { mutableStateOf("") }
    var newLastName by remember { mutableStateOf("") }
    var showAvailabilityIntentSheet by remember { mutableStateOf(false) }
    var seedAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }
    var pendingDeleteAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }
    var showPermissionsHub by remember { mutableStateOf(false) }
    var settingsPage by remember { mutableStateOf(SettingsPage.Hub) }
    val savedEventBookmarks by AppDataManager.cachedEventBookmarks.collectAsState()
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    var selectedSavedEventBeacon by remember { mutableStateOf<MapBeacon?>(null) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    // One host state means tap-back and swipe-back share a single animation and the hub behind gets
    // the same parallax either way, instead of tap-back using a separate slide-out with no underlay.
    val backHost = rememberInteractiveBackHostState()
    PlatformNativeNavigationBarSwipeReveal(backHost.dragOffsetPx)
    val backScope = rememberCoroutineScope()
    var subpageClosing by remember { mutableStateOf(false) }

    fun closeSettingsSubpage() {
        if (subpageClosing) return
        subpageClosing = true
        backScope.launch {
            backHost.dismiss()
            settingsPage = SettingsPage.Hub
            subpageClosing = false
        }
    }

    LaunchedEffect(settingsPage) {
        if (settingsPage == SettingsPage.Hub) backHost.reset()
        onSubpageOpenChanged(settingsPage != SettingsPage.Hub)
        if (settingsPage != SettingsPage.Availability && showAvailabilityIntentSheet) {
            showAvailabilityIntentSheet = false
            seedAvailabilityIntent = null
            availabilityViewModel.resetAvailabilityIntentSheet()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSubpageOpenChanged(false)
            availabilityViewModel.resetAvailabilityIntentSheet()
        }
    }

    PlatformBackHandler(enabled = settingsPage != SettingsPage.Hub && !isIOS) {
        // pragma: allowlist secret
        closeSettingsSubpage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
                AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                    AppScreenScaffold(
                    title = "Settings",
                    onOpenSearch = onOpenSearch,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    // The hub stays mounted under the subpage and parallaxes as it is pushed away.
                    // Keep native hub chrome bound (clipped, not unbound) so it does not pop in
                    // after swipe-back the way Clicks stays bound under chat.
                    modifier = Modifier.interactiveSwipeBackUnderlay(backHost),
                ) {
                    item {
                            SettingsProfileHeader(
                                user = currentUser,
                                avatarUploading = avatarUploading,
                                onChangePhoto = { mediaPickers.openPhotoLibrary() },
                                onEditProfile = {
                                    val (f, l) = namePartsForEditor(currentUser)
                                    newFirstName = f
                                    newLastName = l
                                    showNameDialog = true
                                },
                            )
                        }

                        item {
                            SettingsHubNavCard(onOpen = { settingsPage = it })
                        }

                        item {
                            SettingsSignOutButton(onSignOut = onSignOut)
                        }
                }

                val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                val fadeSpec = tween<Float>(220, easing = LinearOutSlowInEasing)
                AnimatedVisibility(
                    visible = settingsPage != SettingsPage.Hub,
                    modifier = Modifier.fillMaxSize().zIndex(8f),
                    enter =
                        slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                            fadeIn(animationSpec = fadeSpec),
                    // Both back paths animate through the shared host offset, so this must not run a
                    // second competing exit transition.
                    exit = ExitTransition.None,
                    label = "settings_subpage",
                ) {
                    InteractiveSwipeBackContainer(
                        enabled = true,
                        opaquePreviousBackground = false,
                        externalDragOffsetPx = backHost.dragOffsetPx,
                        onBehindLayersVisibleChanged = { backHost.behindLayersVisible = it },
                        previousContent = {},
                        onBack = { settingsPage = SettingsPage.Hub },
                        currentContent = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                            ) {
                            AppScreenScaffold(
                                title = settingsPage.title(),
                                onOpenSearch = null,
                                onNavigateBack = { closeSettingsSubpage() },
                                navigationIcon = {
                                    HeaderBackIconButton(
                                        onClick = { closeSettingsSubpage() },
                                        contentDescription = "Back to settings",
                                    )
                                },
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                when (settingsPage) {
                                    SettingsPage.Hub -> Unit
                                    SettingsPage.Availability -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Availability")
                                AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SettingsToggleRow(
                                            icon = Icons.Default.EventAvailable,
                                            iconTint =
                                                if (currentAvailability?.isFreeThisWeek == true) {
                                                    PrimaryBlue
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            title = "Free currently",
                                            checked = currentAvailability?.isFreeThisWeek ?: false,
                                            onCheckedChange = { availabilityViewModel.toggleFreeThisWeek() },
                                        )
                                        SettingsDivider()
                                        ClickButton(
                                            onClick = {
                                                availabilityViewModel.resetAvailabilityIntentSheet()
                                                seedAvailabilityIntent = null
                                                showAvailabilityIntentSheet = true
                                            },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                            variant = ClickButtonVariant.Accent,
                                        ) {
                                            Text("Share intent & timeframe")
                                        }
                                        SettingsDivider()
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 72.dp)
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "Active availability post",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            intentListFeedback?.let { fb ->
                                                Text(
                                                    text = fb,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            when {
                                                loadingActiveAvailabilityIntents &&
                                                    activeAvailabilityIntents.isEmpty() &&
                                                    !hasResolvedActiveAvailabilityIntents -> {
                                                    Text(
                                                        text = "Loading…",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                activeAvailabilityIntents.isEmpty() -> {
                                                    Text(
                                                        text = "Nothing active yet. Post above to show connections what you’re up for and for how long.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                else -> {
                                                    activeAvailabilityIntents.forEachIndexed { index, row ->
                                                        if (index > 0) {
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(vertical = 4.dp),
                                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                            )
                                                        }
                                                        Text(
                                                            text =
                                                                row.intentTag
                                                                    ?.trim()
                                                                    .orEmpty()
                                                                    .ifEmpty { "—" },
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        val timeframe = row.timeframe?.trim().orEmpty()
                                                        val until = row.activeUntilLabel()
                                                        val detail =
                                                            buildString {
                                                                if (timeframe.isNotEmpty()) {
                                                                    append(timeframe)
                                                                    append(" · ")
                                                                }
                                                                append(until)
                                                            }
                                                        Text(
                                                            text = detail,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                        if (!row.id.isNullOrBlank()) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.End,
                                                                verticalAlignment = Alignment.CenterVertically,
                                                            ) {
                                                                TextButton(
                                                                    onClick = {
                                                                        availabilityViewModel.beginEditAvailabilityIntent(row)
                                                                        seedAvailabilityIntent = row
                                                                        showAvailabilityIntentSheet = true
                                                                    },
                                                                ) {
                                                                    Text("Edit")
                                                                }
                                                                TextButton(
                                                                    onClick = {
                                                                        pendingDeleteAvailabilityIntent = row
                                                                    },
                                                                    colors =
                                                                        ButtonDefaults.textButtonColors(
                                                                            contentColor = MaterialTheme.colorScheme.error,
                                                                        ),
                                                                ) {
                                                                    Text("Remove")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.Alerts -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Alerts")
                                AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Message notifications",
                                            checked = notificationPreferences.messagePushEnabled,
                                            onCheckedChange = { AppDataManager.setMessageNotificationsEnabled(it) },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.PhoneInTalk,
                                            title = "Call alerts",
                                            checked = notificationPreferences.callPushEnabled,
                                            onCheckedChange = { AppDataManager.setCallNotificationsEnabled(it) },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Event reminders",
                                            subtitle = "Day-of and 30-minutes-before alerts for events you created.",
                                            checked = notificationPreferences.eventReminderPushEnabled,
                                            onCheckedChange = { AppDataManager.setEventReminderNotificationsEnabled(it) },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.EventAvailable,
                                            title = "Availability matches",
                                            subtitle = "When a connection posts a matching intent and overlapping timeframe.",
                                            checked = notificationPreferences.availabilityMatchPushEnabled,
                                            onCheckedChange = { AppDataManager.setAvailabilityMatchNotificationsEnabled(it) },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Hub messages",
                                            subtitle = "Community hub chat alerts when you are a participant.",
                                            checked = notificationPreferences.hubMessagePushEnabled,
                                            onCheckedChange = { AppDataManager.setHubMessageNotificationsEnabled(it) },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Mic,
                                            title = "Ambient sound enrichment",
                                            subtitle = "Short mic sample at connect time for a noise category only. No recordings stored.",
                                            checked = ambientNoiseOptIn,
                                            onCheckedChange = { enabled ->
                                                settingsScope.launch {
                                                    ambientNoiseOptIn = enabled
                                                    tokenStorage.saveAmbientNoiseOptIn(enabled)
                                                    if (enabled && !ambientNoiseMonitor.hasPermission) {
                                                        requestMicrophonePermissionThen { micPermissionBump++ }
                                                    }
                                                }
                                            },
                                        )
                                        if (ambientNoiseOptIn && !microphoneGranted) {
                                            Text(
                                                text = "Microphone access is off — enable it in system settings to use ambient enrichment.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.Privacy -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Privacy & data")
                                AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        YourDataLocationRows(
                                            locationPreferences = locationPreferences,
                                            ghostModeEnabled = ghostModeEnabled,
                                            locationPermissionState = locationPermissionState,
                                            onConnectionSnapCheckedChange = { enabled ->
                                                settingsScope.launch {
                                                    AppDataManager.setConnectionSnapEnabled(enabled)
                                                    if (enabled && !locationService.hasLocationPermission()) {
                                                        requestLocationPermissionThen { locationPermissionBump++ }
                                                    }
                                                }
                                            },
                                        )
                                        SettingsDivider()
                                        val chevronRotation by animateFloatAsState(
                                            targetValue = if (showPermissionsHub) 180f else 0f,
                                            label = "permissions_hub_chevron",
                                        )
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showPermissionsHub = !showPermissionsHub }
                                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Shield,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = PrimaryBlue,
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Permissions Hub",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                                Text(
                                                    "Review & fix microphone, location, and Bluetooth access.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = if (showPermissionsHub) "Collapse" else "Expand",
                                                modifier = Modifier.rotate(chevronRotation),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        AnimatedVisibility(
                                            visible = showPermissionsHub,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut(),
                                        ) {
                                            InlinePermissionsPanel(
                                                microphoneGranted = microphoneGranted,
                                                locationStatus = locationPermissionState.toHubStatus(),
                                                isRequestingMic = false,
                                                isRequestingLocation = false,
                                                onRequestMicrophone = {
                                                    requestMicrophonePermissionThen { micPermissionBump++ }
                                                },
                                                onRequestLocation = {
                                                    requestLocationPermissionThen { locationPermissionBump++ }
                                                },
                                                onOpenSystemSettings = {
                                                    compose.project.click.click.ui.utils // pragma: allowlist secret
                                                        .openApplicationSystemSettings()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsPage.Interests -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Interests")
                                SettingsInterestsCard(
                                    userId = currentUser?.id,
                                    supabaseRepository = supabaseRepository,
                                    onFeedback = { msg -> toastState.show(settingsScope, msg) },
                                )
                            }
                        }
                    }

                    SettingsPage.Personality -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Personality")
                                SettingsPersonalityCard(
                                    userId = currentUser?.id,
                                    onFeedback = { msg -> toastState.show(settingsScope, msg) },
                                )
                            }
                        }
                    }

                    SettingsPage.Saved -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Saved events")
                                if (savedEventBookmarks.isEmpty()) {
                                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "No saved events yet. Bookmark events from Home or the map.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    }
                                } else {
                                    SavedEventsSection( // pragma: allowlist secret
                                        bookmarks = savedEventBookmarks,
                                        onBookmarkClick = { bookmark ->
                                            selectedSavedEventBeacon =
                                                resolveSavedEventBeacon(
                                                    bookmark = bookmark,
                                                    mapBeacons = mapBeacons,
                                                    prefetchedBeacons = prefetchedBeacons,
                                                )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SettingsPage.Appearance -> {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Appearance")
                                AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                    SettingsToggleRow(
                                        icon = Icons.Default.DarkMode,
                                        title = "Dark mode",
                                        checked = isDarkMode,
                                        onCheckedChange = { onToggleDarkMode() },
                                    )
                                    SettingsDivider()
                                    val pileMode by AppDataManager.homeLayoutMode.collectAsState()
                                    SettingsToggleRow(
                                        icon = Icons.Default.Star,
                                        title = "Photo pile home",
                                        subtitle = "Show each home section as a swipeable stack of photos. Turn off for a linear list (better with TalkBack / VoiceOver).",
                                        checked = pileMode == HomeLayoutMode.PILE,
                                        onCheckedChange = { enabled ->
                                            AppDataManager.setHomeLayoutMode(
                                                if (enabled) HomeLayoutMode.PILE else HomeLayoutMode.LINEAR,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                                }
                            }
                            }
                        },
                    )
                }
            }
        }

        if (showAvailabilityIntentSheet) {
            AvailabilitySheet(
                viewModel = availabilityViewModel,
                seedIntent = seedAvailabilityIntent,
                onDismiss = {
                    showAvailabilityIntentSheet = false
                    seedAvailabilityIntent = null
                    availabilityViewModel.resetAvailabilityIntentSheet()
                },
            )
        }

        SavedEventDetailSheet(
            beacon = selectedSavedEventBeacon,
            mapViewModel = mapViewModel,
            currentUserId = currentUser?.id,
            onDismiss = { selectedSavedEventBeacon = null },
            onShareBeaconToChats = onShareBeaconToChats,
        )

        val pendingDelete = pendingDeleteAvailabilityIntent
        if (pendingDelete != null) {
            GlassAlertDialog(
                onDismissRequest = { pendingDeleteAvailabilityIntent = null },
                title = { Text("Remove availability?") },
                text = {
                    val label =
                        pendingDelete.intentTag
                            ?.trim()
                            .orEmpty()
                            .ifEmpty { "this intent" }
                    Text("Stop showing \"$label\" as your active availability.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete.id?.let { id ->
                                availabilityViewModel.deleteAvailabilityIntent(id)
                            }
                            pendingDeleteAvailabilityIntent = null
                        },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteAvailabilityIntent = null }) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted())
                    }
                },
            )
        }

        if (showNameDialog) {
            val nameDialogFieldColors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GlassSheetTokens.OnOled(),
                    unfocusedTextColor = GlassSheetTokens.OnOled(),
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                    cursorColor = PrimaryBlue,
                    focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                    unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                )
            GlassAlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Edit name") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ClickOutlinedTextField(
                            value = newFirstName,
                            onValueChange = { newFirstName = it },
                            label = { Text("First name", color = GlassSheetTokens.OnOledMuted()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = nameDialogFieldColors,
                        )
                        ClickOutlinedTextField(
                            value = newLastName,
                            onValueChange = { newLastName = it },
                            label = { Text("Last name", color = GlassSheetTokens.OnOledMuted()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = nameDialogFieldColors,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newFirstName.isNotBlank()) {
                                AppDataManager.updateProfileName(newFirstName, newLastName)
                                showNameDialog = false
                            }
                        },
                    ) {
                        Text("Save", color = GlassSheetTokens.OnOled())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel", color = GlassSheetTokens.OnOledMuted())
                    }
                },
            )
        }

        UnifiedToastHost(
            state = toastState,
            opaque = true,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = rememberBottomChromePadding() + 8.dp),
        )
    }
}
