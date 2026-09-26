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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.appVersionLabel // pragma: allowlist secret
import compose.project.click.click.calendar.CalendarProvider // pragma: allowlist secret
import compose.project.click.click.calendar.DeviceCalendar // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.IdentityCache // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.api.BlockedUserDto // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.api.PROFILE_BIO_MAX_LENGTH
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.HangoutPresence
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.deeplink.AppDeepLink // pragma: allowlist secret
import compose.project.click.click.deeplink.AppDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import compose.project.click.click.openInAppBrowser // pragma: allowlist secret
import compose.project.click.click.platformForegroundTickFlow // pragma: allowlist secret
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.rememberBarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
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
import compose.project.click.click.ui.components.SavedEventsSection // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.interactiveSwipeBackUnderlay // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberInteractiveBackHostState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberUnifiedToastState // pragma: allowlist secret
import compose.project.click.click.ui.theme.AppAppearance // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppPermission // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppPermissionState // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberAppPermissionController // pragma: allowlist secret
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
    Permissions,
    Blocked,
    Calendar,
    Interests,
    Personality,
    Saved,
}

internal fun SettingsPage.title(): String =
    when (this) {
        SettingsPage.Hub -> "Me"
        SettingsPage.Availability -> "Availability"
        SettingsPage.Alerts -> "Alerts"
        SettingsPage.Privacy -> "Privacy & data"
        SettingsPage.Permissions -> "Permissions"
        SettingsPage.Blocked -> "Blocked people"
        SettingsPage.Calendar -> "Calendar"
        SettingsPage.Interests -> "Interests"
        SettingsPage.Personality -> "Personality"
        SettingsPage.Saved -> "Saved events"
    }

/** Pages pushed from Privacy go back to Privacy; everything else goes back to Me. */
internal fun SettingsPage.parent(): SettingsPage =
    when (this) {
        SettingsPage.Permissions, SettingsPage.Blocked -> SettingsPage.Privacy
        else -> SettingsPage.Hub
    }

/** Account deletion finishes on the web, signed in there (iOS `MeView.openAccountDeletion`, spec §65.14). */
internal const val ACCOUNT_DELETION_URL = "https://joinclick.co/?tab=settings"
internal const val WEB_DASHBOARD_URL = "https://joinclick.co"

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    /** Core strip "View": the Clicks list. */
    onOpenClicks: () -> Unit = {},
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
    val connections by AppDataManager.connections.collectAsState()
    val coreConnectionIds by AppDataManager.coreConnectionIds.collectAsState()
    val hiddenConnectionIds by AppDataManager.hiddenConnectionIds.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val appearanceMode by AppAppearance.mode.collectAsState()
    val notificationPreferencesSaving by AppDataManager.notificationPreferencesSaving.collectAsState()
    val identities by IdentityCache.identities.collectAsState()

    val tokenStorage = remember { createTokenStorage() }
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val barometricHeightMonitor = rememberBarometricHeightMonitor()
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
    var barometricContextOptIn by remember { mutableStateOf(false) }
    var hangoutDetectionOptIn by remember { mutableStateOf(false) }
    val relationshipApi = remember { ApiClient() }
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
        ambientNoiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: false
        barometricContextOptIn = tokenStorage.getBarometricContextOptIn() ?: false
        hangoutDetectionOptIn = tokenStorage.getHangoutDetectionOptIn() ?: false
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser?.id != null) {
            availabilityViewModel.refreshActiveAvailabilityIntents()
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var newBio by remember { mutableStateOf("") }
    var loadedBio by remember { mutableStateOf<String?>(null) }
    // Shown under the name on Me; refreshed after an edit.
    var profileBio by remember { mutableStateOf<String?>(null) }
    var profileBioEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentUser?.id, profileBioEpoch) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        relationshipApi.getUserProfile(uid).onSuccess {
            profileBio =
                it.user
            ?.bio
            ?.trim()
            ?.takeIf { bio -> bio.isNotEmpty() }
        }
    }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    var removingPhoto by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var pendingAlertKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notificationPreferencesSaving) {
        if (!notificationPreferencesSaving) pendingAlertKey = null
    }

    fun setAlert(
        key: String,
        apply: () -> Unit,
    ) {
        if (notificationPreferencesSaving) return
        pendingAlertKey = key
        apply()
    }

    val permissionController = rememberAppPermissionController()
    var permissionStates by remember { mutableStateOf<Map<AppPermission, AppPermissionState>>(emptyMap()) }
    var permissionBump by remember { mutableIntStateOf(0) }
    val currentSettingsPage = remember { mutableStateOf(SettingsPage.Hub) }
    LaunchedEffect(permissionBump, foregroundSyncTick, currentSettingsPage.value) {
        permissionStates = AppPermission.entries.associateWith { permissionController.state(it) }
    }

    fun requestPermission(permission: AppPermission) {
        permissionController.request(permission) { permissionBump++ }
    }

    var blockedLoad by remember { mutableStateOf<PageLoad<List<BlockedUserDto>>>(PageLoad.Loading) }
    var blockedEpoch by remember { mutableIntStateOf(0) }
    var unblocking by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedError by remember { mutableStateOf<String?>(null) }

    var calendarConnected by remember { mutableStateOf(true) }
    var deviceCalendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    val calendarProvider = remember { CalendarProvider() }

    var savedRefresh by remember { mutableStateOf<PageLoad<Unit>>(PageLoad.Loading) }
    var savedEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(showNameDialog, currentUser?.id) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        if (!showNameDialog) return@LaunchedEffect
        val bio =
            relationshipApi
            .getUserProfile(uid)
            .getOrNull()
            ?.user
            ?.bio
            .orEmpty()
        loadedBio = bio
        newBio = bio
    }
    var newFirstName by remember { mutableStateOf("") }
    var newLastName by remember { mutableStateOf("") }
    var showAvailabilityIntentSheet by remember { mutableStateOf(false) }
    var seedAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }
    var pendingDeleteAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }
    var showPermissionsHub by remember { mutableStateOf(false) }
    var settingsPage by currentSettingsPage

    LaunchedEffect(settingsPage, blockedEpoch) {
        if (settingsPage != SettingsPage.Blocked) return@LaunchedEffect
        if (blockedLoad !is PageLoad.Loaded) blockedLoad = PageLoad.Loading
        relationshipApi.getBlockedUsers().fold(
            onSuccess = { items ->
                blockedLoad = PageLoad.Loaded(items)
                IdentityCache.request(items.map { it.blockedId })
            },
            onFailure = { if (blockedLoad !is PageLoad.Loaded) blockedLoad = PageLoad.Failed },
        )
    }
    LaunchedEffect(settingsPage, permissionStates[AppPermission.Calendar], calendarConnected) {
        if (settingsPage != SettingsPage.Calendar) return@LaunchedEffect
        calendarConnected = !tokenStorage.getCalendarDisconnected()
        deviceCalendars =
            if (permissionStates[AppPermission.Calendar] == AppPermissionState.Granted) calendarProvider.listCalendars() else emptyList()
    }
    LaunchedEffect(settingsPage, savedEpoch) {
        if (settingsPage != SettingsPage.Saved) return@LaunchedEffect
        savedRefresh = PageLoad.Loading
        savedRefresh =
            relationshipApi.getMyEventBookmarks(limit = 50).fold(
                onSuccess = {
                    AppDataManager.updateCachedEventBookmarks(it.bookmarks)
                    PageLoad.Loaded(Unit)
                },
                onFailure = { PageLoad.Failed },
            )
    }
    val savedEventBookmarks by AppDataManager.cachedEventBookmarks.collectAsState()
    val mapBeacons by mapViewModel.mapBeacons.collectAsState()
    val prefetchedBeacons by AppDataManager.prefetchedMapBeacons.collectAsState()
    var selectedSavedEventBeacon by remember { mutableStateOf<MapBeacon?>(null) }
    // One host state means tap-back and swipe-back share a single animation and the hub behind gets
    // the same parallax either way, instead of tap-back using a separate slide-out with no underlay.
    val backHost = rememberInteractiveBackHostState()
    val backScope = rememberCoroutineScope()
    var subpageClosing by remember { mutableStateOf(false) }

    fun closeSettingsSubpage() {
        if (subpageClosing) return
        val parent = settingsPage.parent()
        // Permissions / Blocked people swap back to Privacy in place; only Me gets the slide-out.
        if (parent != SettingsPage.Hub) {
            settingsPage = parent
            return
        }
        subpageClosing = true
        backScope.launch {
            backHost.dismiss()
            settingsPage = parent
            subpageClosing = false
        }
    }

    LaunchedEffect(settingsPage) {
        // Also after a swipe-back from a Privacy child page, so Privacy shows at rest.
        backHost.reset()
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

    PlatformBackHandler(enabled = settingsPage != SettingsPage.Hub) {
        // pragma: allowlist secret
        closeSettingsSubpage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
                AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                    AppScreenScaffold(
                    title = "Me",
                    onOpenSearch = onOpenSearch,
                    actions = {
                        IconButton(onClick = { AppDeepLinkRouter.open(AppDeepLink.MyQr) }) {
                            Icon(Icons.Default.QrCode, contentDescription = "My QR")
                        }
                        IconButton(onClick = {
                            val (f, l) = namePartsForEditor(currentUser)
                            newFirstName = f
                            newLastName = l
                            showNameDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                        }
                    },
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    // The hub stays mounted under the subpage and parallaxes as it is pushed away.
                    // Keep native hub chrome bound (clipped, not unbound) so it does not pop in
                    // after swipe-back the way Clicks stays bound under chat.
                    modifier = Modifier.interactiveSwipeBackUnderlay(backHost),
                ) {
                    item {
                        MeIdentityHeader(
                            user = currentUser,
                            bio = profileBio,
                            statusLabel = availabilityStatusLabel(activeAvailabilityIntents),
                            clicksLabel = clicksCountLabel(connections, hiddenConnectionIds),
                            avatarUploading = avatarUploading || removingPhoto,
                            onChangePhoto = { mediaPickers.openPhotoLibrary() },
                            onStatusClick = { settingsPage = SettingsPage.Availability },
                        )
                    }
                    val coreItems =
                        meCoreItems(connections, coreConnectionIds, hiddenConnectionIds, connectedUsers, currentUser?.id)
                    if (coreItems.isNotEmpty()) {
                        item {
                            MeCoreStrip(
                                items = coreItems,
                                onOpenProfile = { ChatDeepLinkManager.setPendingProfile(it) },
                                onViewAll = onOpenClicks,
                            )
                        }
                    }
                    item {
                        Column {
                            SettingsToggleRow(
                                icon = Icons.Default.WbSunny,
                                title = "Free currently",
                                subtitle = "Your Clicks can see you're up for plans",
                                checked = currentAvailability?.isFreeThisWeek ?: false,
                                onCheckedChange = { availabilityViewModel.toggleFreeThisWeek() },
                            )
                            SettingsDivider()
                            MeNavRow(
                                icon = Icons.Default.EventAvailable,
                                title = "Availability post",
                                value = availabilitySummary(activeAvailabilityIntents),
                                onClick = { settingsPage = SettingsPage.Availability },
                            )
                            SettingsDivider()
                            MeNavRow(
                                icon = Icons.Default.Bookmark,
                                title = "Saved events",
                                value =
                                    savedEventSections(savedEventBookmarks, nowEpochMs())
                                        .upcoming.size
                                        .takeIf { savedEventBookmarks.isNotEmpty() }
                                        ?.toString(),
                                onClick = { settingsPage = SettingsPage.Saved },
                            )
                            SettingsDivider()
                            MeNavRow(
                                icon = Icons.Default.CalendarMonth,
                                title = "Calendar",
                                onClick = { settingsPage = SettingsPage.Calendar },
                            )
                        }
                    }
                    item {
                        Column {
                            MeNavRow(Icons.Default.Notifications, "Alerts", onClick = { settingsPage = SettingsPage.Alerts })
                            SettingsDivider()
                            MeNavRow(Icons.Default.PrivacyTip, "Privacy & data", onClick = { settingsPage = SettingsPage.Privacy })
                            SettingsDivider()
                            MeNavRow(Icons.Default.Shield, "Permissions", onClick = { settingsPage = SettingsPage.Permissions })
                            SettingsDivider()
                            MeNavRow(
                                icon = Icons.Default.Star,
                                title = "Interests",
                                value =
                                    currentUser
                                    ?.tags
                                    ?.size
                                    ?.takeIf { it > 0 }
                                    ?.toString(),
                                    onClick = { settingsPage = SettingsPage.Interests },
                            )
                            SettingsDivider()
                            MeNavRow(
                                icon = Icons.Default.Person,
                                title = "Personality",
                                value = currentUser?.personalityTags?.size?.let { "$it of 5" },
                                onClick = { settingsPage = SettingsPage.Personality },
                            )
                        }
                    }
                    item {
                        Column {
                            AppearanceSegmentedRow(
                                mode = appearanceMode,
                                onSelect = { mode -> settingsScope.launch { AppAppearance.set(mode, tokenStorage) } },
                            )
                            SettingsDivider()
                            MeExternalLinkRow(
                                icon = Icons.Default.Language,
                                title = "Web dashboard",
                                onClick = { openInAppBrowser(WEB_DASHBOARD_URL) },
                            )
                        }
                    }
                    item {
                        MeAccountRows(
                            signingOut = signingOut,
                            onSignOut = { confirmSignOut = true },
                            onDeleteAccount = { confirmDeleteAccount = true },
                        )
                    }
                    item {
                        MeFooter(version = remember { appVersionLabel() })
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
                        onBack = { settingsPage = settingsPage.parent() },
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
                                navigationIcon = {
                                    HeaderBackIconButton(
                                        onClick = { closeSettingsSubpage() },
                                        contentDescription = "Back",
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

                    SettingsPage.Alerts -> {
                        item {
                            AlertsSystemSection(
                                state = permissionStates[AppPermission.Notifications],
                                onAllow = { requestPermission(AppPermission.Notifications) },
                                onOpenSettings = { permissionController.openSettings(AppPermission.Notifications) },
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Notify me about")
                                Column(modifier = Modifier.fillMaxWidth()) {
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Message notifications",
                                            subtitle = "New messages, archive warnings, and Click Drop reveals",
                                            checked = notificationPreferences.messagePushEnabled,
                                            enabled = !notificationPreferencesSaving,
                                            pending = pendingAlertKey == "messages",
                                            onCheckedChange = { on ->
                                                setAlert("messages") { AppDataManager.setMessageNotificationsEnabled(on) }
                                            },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Event reminders",
                                            subtitle = "An hour and 15 minutes before events you're going to or saved",
                                            checked = notificationPreferences.eventReminderPushEnabled,
                                            enabled = !notificationPreferencesSaving,
                                            pending = pendingAlertKey == "events",
                                            onCheckedChange = { on ->
                                                setAlert("events") { AppDataManager.setEventReminderNotificationsEnabled(on) }
                                            },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Relationship moments",
                                            subtitle = "Anniversaries, memories, quiet groups, reconnecting and shared events",
                                            checked = notificationPreferences.reconnectNudgePushEnabled,
                                            enabled = !notificationPreferencesSaving,
                                            pending = pendingAlertKey == "moments",
                                            onCheckedChange = { on ->
                                                setAlert("moments") { AppDataManager.setReconnectNudgeNotificationsEnabled(on) }
                                            },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.EventAvailable,
                                            title = "Availability matches",
                                            subtitle = "When a Click's plans overlap with yours",
                                            checked = notificationPreferences.availabilityMatchPushEnabled,
                                            enabled = !notificationPreferencesSaving,
                                            pending = pendingAlertKey == "availability",
                                            onCheckedChange = { on ->
                                                setAlert("availability") { AppDataManager.setAvailabilityMatchNotificationsEnabled(on) }
                                            },
                                        )
                                        SettingsDivider()
                                        SettingsToggleRow(
                                            icon = Icons.Default.Notifications,
                                            title = "Hub messages",
                                            subtitle = "Messages in community and event hubs",
                                            checked = notificationPreferences.hubMessagePushEnabled,
                                            enabled = !notificationPreferencesSaving,
                                            pending = pendingAlertKey == "hubs",
                                            onCheckedChange = { on ->
                                                setAlert("hubs") { AppDataManager.setHubMessageNotificationsEnabled(on) }
                                            },
                                        )
                                    }
                                Text(
                                    "These are saved to your account and control what Click sends to all your devices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SettingsPage.Permissions -> {
                        item {
                            PermissionsPageContent(
                                states = permissionStates,
                                onAllow = ::requestPermission,
                                onOpenSettings = { permissionController.openSettings(it) },
                            )
                        }
                    }

                    SettingsPage.Blocked -> {
                        item {
                            BlockedPeoplePageContent(
                                load = blockedLoad,
                                names = identities,
                                unblocking = unblocking,
                                errorMessage = blockedError,
                                onRetry = { blockedEpoch++ },
                                onUnblock = { userId ->
                                    unblocking = unblocking + userId
                                    settingsScope.launch {
                                        relationshipApi.unblockUser(userId).fold(
                                            onSuccess = {
                                                blockedError = null
                                                (blockedLoad as? PageLoad.Loaded)?.let { loaded ->
                                                    blockedLoad = PageLoad.Loaded(loaded.value.filterNot { it.blockedId == userId })
                                                }
                                            },
                                            onFailure = { blockedError = "Couldn't unblock. Please try again." },
                                        )
                                        unblocking = unblocking - userId
                                    }
                                },
                            )
                        }
                    }

                    SettingsPage.Calendar -> {
                        item {
                            CalendarPageContent(
                                permission = permissionStates[AppPermission.Calendar],
                                connected = calendarConnected,
                                calendars = deviceCalendars,
                                onAllow = { requestPermission(AppPermission.Calendar) },
                                onOpenSettings = { permissionController.openSettings(AppPermission.Calendar) },
                                onConnectedChange = { connect ->
                                    calendarConnected = connect
                                    settingsScope.launch { tokenStorage.saveCalendarDisconnected(!connect) }
                                },
                            )
                        }
                    }

                    SettingsPage.Privacy -> {
                        item {
                            // Encounter context (iOS Privacy -> Encounter context): optional sensor labels on new encounters.
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Encounter context")
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SettingsToggleRow(
                                        icon = Icons.Default.Mic,
                                        title = "Ambient sound",
                                        subtitle = "Adds a noise-level label to new encounters. Nothing is recorded or stored.",
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
                                            text = "Microphone access is off — enable it in system settings to use ambient sound.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp),
                                        )
                                    }
                                    SettingsDivider()
                                    SettingsToggleRow(
                                        icon = Icons.Default.Height,
                                        title = "Barometric context",
                                        subtitle =
                                            if (barometricHeightMonitor.isAvailable) {
                                                "Adds an elevation label to new encounters using this phone's barometer."
                                            } else {
                                                "This phone has no barometer, so no elevation label is added."
                                            },
                                        checked = barometricContextOptIn && barometricHeightMonitor.isAvailable,
                                        onCheckedChange = { enabled ->
                                            if (!barometricHeightMonitor.isAvailable) return@SettingsToggleRow
                                            settingsScope.launch {
                                                barometricContextOptIn = enabled
                                                tokenStorage.saveBarometricContextOptIn(enabled)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        item {
                            // Opt-in "Hanging out?" prompts (iOS Privacy -> Hangout detection).
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Hangouts")
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SettingsToggleRow(
                                        icon = Icons.Default.People,
                                        title = "Hangout detection",
                                        subtitle =
                                            "When you open Click near a Click who also turned this on, you both get a " +
                                                "“Hanging out?” prompt to log it. Your approximate location is shared only " +
                                                "with Click, never shown to anyone, and deleted within 2 hours.",
                                        checked = hangoutDetectionOptIn,
                                        onCheckedChange = { enabled ->
                                            hangoutDetectionOptIn = enabled
                                            settingsScope.launch {
                                                HangoutPresence.setEnabled(tokenStorage, relationshipApi, enabled)
                                                if (enabled && !locationService.hasLocationPermission()) {
                                                    requestLocationPermissionThen { locationPermissionBump++ }
                                                }
                                            }
                                        },
                                    )
                                    if (hangoutDetectionOptIn && locationPermissionState != LocationPermissionDisplayState.Granted) {
                                        Text(
                                            text = "Location access is off, so Click can't detect hangouts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsSectionHeader("Privacy & data")
                                Column(modifier = Modifier.fillMaxWidth()) {
                                        YourDataLocationRows(
                                            locationPreferences = locationPreferences,
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
                                        MeNavRow(Icons.Default.Shield, "Permissions", onClick = { settingsPage = SettingsPage.Permissions })
                                        SettingsDivider()
                                        MeNavRow(Icons.Default.Block, "Blocked people", onClick = { settingsPage = SettingsPage.Blocked })
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
                        val sections = savedEventSections(savedEventBookmarks, nowEpochMs())
                        val openBookmark: (EventBookmarkItemDto) -> Unit = { bookmark ->
                            selectedSavedEventBeacon =
                                resolveSavedEventBeacon(
                                    bookmark = bookmark,
                                    mapBeacons = mapBeacons,
                                    prefetchedBeacons = prefetchedBeacons,
                                )
                        }
                        if (savedRefresh is PageLoad.Failed) {
                            item {
                                TextButton(onClick = { savedEpoch++ }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        if (savedEventBookmarks.isEmpty()) {
                                            "Couldn't load your saved events. Retry"
                                        } else {
                                            "Showing saved events from earlier · Retry"
                                        },
                                    )
                                }
                            }
                        }
                        if (savedEventBookmarks.isEmpty()) {
                            item {
                                Text(
                                    text =
                                        if (savedRefresh is PageLoad.Loading) {
                                            "Loading…"
                                        } else {
                                            "No saved events yet. Bookmark events from Home or the map."
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (sections.upcoming.isNotEmpty()) {
                            item {
                                SavedEventsSection( // pragma: allowlist secret
                                    bookmarks = sections.upcoming,
                                    onBookmarkClick = openBookmark,
                                    title = "Upcoming",
                                )
                            }
                        }
                        if (sections.past.isNotEmpty()) {
                            item {
                                SavedEventsSection( // pragma: allowlist secret
                                    bookmarks = sections.past,
                                    onBookmarkClick = openBookmark,
                                    title = "Past or unavailable",
                                )
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
                title = { Text("Edit profile") },
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
                        ClickOutlinedTextField(
                            value = newBio,
                            onValueChange = { newBio = it.take(PROFILE_BIO_MAX_LENGTH) },
                            label = { Text("Bio", color = GlassSheetTokens.OnOledMuted()) },
                            supportingText = {
                                Text(
                                    "${newBio.length}/$PROFILE_BIO_MAX_LENGTH",
                                    color = GlassSheetTokens.OnOledMuted(),
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = nameDialogFieldColors,
                        )
                        if (!currentUser?.image.isNullOrBlank()) {
                            TextButton(
                                onClick = { confirmRemovePhoto = true },
                                enabled = !removingPhoto,
                            ) {
                                Text(if (removingPhoto) "Removing…" else "Remove photo", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newFirstName.isNotBlank()) {
                                AppDataManager.updateProfileName(newFirstName, newLastName)
                                val uid = currentUser?.id
                                val bio = newBio.trim()
                                if (uid != null && loadedBio != null && bio != loadedBio?.trim()) {
                                    settingsScope.launch {
                                        relationshipApi
                                            .patchUserProfile(uid, bio = bio)
                                            .onSuccess { profileBioEpoch++ }
                                            .onFailure { toastState.show(settingsScope, "Couldn't save your bio") }
                                    }
                                }
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

        if (confirmRemovePhoto) {
            GlassAlertDialog(
                onDismissRequest = { confirmRemovePhoto = false },
                title = { Text("Remove your photo?") },
                text = { Text("People will see your initials instead.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRemovePhoto = false
                            removingPhoto = true
                            settingsScope.launch {
                                relationshipApi.deleteAvatar().fold(
                                    onSuccess = { AppDataManager.clearProfilePicture() },
                                    onFailure = { toastState.show(settingsScope, "Your photo wasn't removed. Please try again.") },
                                )
                                removingPhoto = false
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove photo") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemovePhoto = false }) { Text("Cancel", color = GlassSheetTokens.OnOledMuted()) }
                },
            )
        }

        if (confirmSignOut) {
            GlassAlertDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text("Sign out of Click?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmSignOut = false
                            signingOut = true
                            onSignOut()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Sign out") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOut = false }) { Text("Cancel", color = GlassSheetTokens.OnOledMuted()) }
                },
            )
        }

        if (confirmDeleteAccount) {
            GlassAlertDialog(
                onDismissRequest = { confirmDeleteAccount = false },
                title = { Text("Delete your Click account?") },
                text = {
                    Text(
                        "Deleting your account permanently removes your profile, connections, and messages. For your " +
                            "security, you'll confirm deletion on joinclick.co while signed in there.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDeleteAccount = false
                            if (!openInAppBrowser(ACCOUNT_DELETION_URL)) {
                                toastState.show(settingsScope, "Open joinclick.co in your browser to delete your account.")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Continue on joinclick.co") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteAccount = false }) { Text("Cancel", color = GlassSheetTokens.OnOledMuted()) }
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
