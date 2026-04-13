package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.mohamedrejeb.calf.ui.toggle.AdaptiveSwitch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import compose.project.click.click.platformForegroundTickFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.AdaptiveBackground
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.AvailabilitySheet // pragma: allowlist secret
import compose.project.click.click.ui.components.PageHeader
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.proximity.rememberProximityManager
import compose.project.click.click.viewmodel.AvailabilityViewModel
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.AvailabilityIntentRow
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.sensors.rememberAmbientNoiseMonitor
import compose.project.click.click.ui.utils.rememberLocationPermissionRequester
import compose.project.click.click.ui.utils.rememberMicrophonePermissionRequester
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.launch
import compose.project.click.click.data.repository.NotificationPreferences
import compose.project.click.click.data.models.LocationPreferences
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import compose.project.click.click.ui.chat.rememberChatMediaPickers

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSignOut: () -> Unit = {},
    availabilityViewModel: AvailabilityViewModel = viewModel { AvailabilityViewModel() }
) {
    val currentAvailability by availabilityViewModel.currentAvailability.collectAsState()
    val activeAvailabilityIntents by availabilityViewModel.activeAvailabilityIntents.collectAsState()
    val loadingActiveAvailabilityIntents by availabilityViewModel.loadingActiveAvailabilityIntents.collectAsState()
    val intentListFeedback by availabilityViewModel.intentListFeedback.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val notificationPreferences by AppDataManager.notificationPreferences.collectAsState()
    val locationPreferences by AppDataManager.locationPreferences.collectAsState()
    val ghostModeEnabled by AppDataManager.ghostModeEnabled.collectAsState()

    val tokenStorage = remember { createTokenStorage() }
    val proximityManager = rememberProximityManager()
    val ambientNoiseMonitor = rememberAmbientNoiseMonitor()
    val locationService = remember { LocationService() }
    val requestMicrophonePermissionThen = rememberMicrophonePermissionRequester()
    val requestLocationPermissionThen = rememberLocationPermissionRequester()
    val settingsScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var avatarUploading by remember { mutableStateOf(false) }
    val authRepoForAvatar = remember(tokenStorage) { AuthRepository(tokenStorage = tokenStorage) }
    val mediaPickers = rememberChatMediaPickers(
        onImagePicked = { bytes, _ ->
            settingsScope.launch {
                if (bytes.size > 2_000_000) {
                    snackbarHostState.showSnackbar("Image must be under 2 MB")
                    return@launch
                }
                avatarUploading = true
                try {
                    authRepoForAvatar.uploadProfilePicture(bytes).fold(
                        onSuccess = { url ->
                            AppDataManager.applyProfilePictureUrl(url)
                            snackbarHostState.showSnackbar("Profile photo updated")
                        },
                        onFailure = { e ->
                            val msg = e.message?.lines()?.firstOrNull()?.take(180)
                                ?: "Could not update profile photo"
                            snackbarHostState.showSnackbar(msg)
                        },
                    )
                } finally {
                    avatarUploading = false
                }
            }
        },
        onAudioPicked = { _, _, _ -> },
        onMediaAccessBlocked = { msg ->
            settingsScope.launch { snackbarHostState.showSnackbar(msg) }
        },
    )

    val foregroundSyncTick by platformForegroundTickFlow().collectAsState()

    var ambientNoiseOptIn by remember { mutableStateOf(false) }
    var micPermissionBump by remember { mutableIntStateOf(0) }
    var locationPermissionBump by remember { mutableIntStateOf(0) }
    val microphoneGranted = remember(micPermissionBump, foregroundSyncTick) { ambientNoiseMonitor.hasPermission }
    val locationSnapGranted = remember(locationPermissionBump, foregroundSyncTick) {
        locationService.hasLocationPermission()
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
    var pendingDeleteAvailabilityIntent by remember { mutableStateOf<AvailabilityIntentRow?>(null) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                PageHeader(title = "Settings")
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsSectionHeader("Availability")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsToggleRow(
                                icon = Icons.Default.EventAvailable,
                                iconTint = if (currentAvailability?.isFreeThisWeek == true)
                                    PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                title = "Free currently",
                                checked = currentAvailability?.isFreeThisWeek ?: false,
                                onCheckedChange = { availabilityViewModel.toggleFreeThisWeek() }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Button(
                                onClick = {
                                    availabilityViewModel.resetAvailabilityIntentSheet()
                                    showAvailabilityIntentSheet = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Share intent & timeframe")
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    loadingActiveAvailabilityIntents && activeAvailabilityIntents.isEmpty() -> {
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
                                                text = row.intentTag?.trim().orEmpty().ifEmpty { "—" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            val timeframe = row.timeframe?.trim().orEmpty()
                                            val until = row.activeUntilLabel()
                                            val detail = buildString {
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
                                                            showAvailabilityIntentSheet = true
                                                        },
                                                    ) {
                                                        Text("Edit")
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            pendingDeleteAvailabilityIntent = row
                                                        },
                                                        colors = ButtonDefaults.textButtonColors(
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

                item {
                    SettingsSectionHeader("Notifications")
                }
                item {
                    NotificationSettingsCard(notificationPreferences = notificationPreferences)
                }

                item {
                    SettingsSectionHeader("Sound & microphone")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                }
                            )
                            if (ambientNoiseOptIn && !microphoneGranted) {
                                Text(
                                    text = "Microphone access is off — enable it in system settings to use ambient enrichment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSectionHeader("Nearby & Bluetooth")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = PrimaryBlue,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bluetooth for Connect",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Nearby handshake uses Bluetooth Low Energy plus a short in-room audio cue. " +
                                            "Keep Bluetooth on; the OS may still show a permission prompt the first time you connect.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Button(
                                onClick = { proximityManager.openRadiosSettings() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Open Bluetooth & radios")
                            }
                        }
                    }
                }

                item {
                    SettingsSectionHeader("Your Data")
                }
                item {
                    YourDataLocationCard(
                        locationPreferences = locationPreferences,
                        ghostModeEnabled = ghostModeEnabled,
                        locationSnapGranted = locationSnapGranted,
                        onConnectionSnapCheckedChange = { enabled ->
                            settingsScope.launch {
                                AppDataManager.setConnectionSnapEnabled(enabled)
                                if (enabled && !locationService.hasLocationPermission()) {
                                    requestLocationPermissionThen { locationPermissionBump++ }
                                }
                            }
                        },
                    )
                }

                item {
                    SettingsSectionHeader("Appearance")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            icon = Icons.Default.DarkMode,
                            title = "Dark mode",
                            checked = isDarkMode,
                            onCheckedChange = { onToggleDarkMode() }
                        )
                    }
                }

                item {
                    SettingsSectionHeader("Account")
                }
                item {
                    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val avatarUrl = currentUser?.image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(modifier = Modifier.size(84.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = !avatarUploading) {
                                                mediaPickers.openPhotoLibrary()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = "Profile photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(PrimaryBlue.copy(alpha = 0.45f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = profileAvatarInitials(currentUser),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            }
                                        }
                                        if (avatarUploading) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            }
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBlue)
                                            .clickable(enabled = !avatarUploading) {
                                                mediaPickers.openPhotoLibrary()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            contentDescription = "Change profile photo",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Tap to change photo · max 2 MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

                            val (shownFirst, shownLast) = namePartsForEditor(currentUser)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Name",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "First name",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        shownFirst.ifEmpty { "—" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Last name",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        shownLast.ifEmpty { "—" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val (f, l) = namePartsForEditor(currentUser)
                                        newFirstName = f
                                        newLastName = l
                                        showNameDialog = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit name",
                                        modifier = Modifier.size(20.dp),
                                        tint = PrimaryBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val sStyle = LocalPlatformStyle.current
                            Button(
                                onClick = onSignOut,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.error,
                                    contentColor = if (sStyle.isIOS) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onError
                                ),
                                elevation = if (sStyle.isIOS) ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp) else ButtonDefaults.buttonElevation(),
                                shape = RoundedCornerShape(if (sStyle.isIOS) 10.dp else 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign out")
                            }
                        }
                    }
                }
            }
        }

        if (showAvailabilityIntentSheet) {
            AvailabilitySheet(
                viewModel = availabilityViewModel,
                onDismiss = {
                    showAvailabilityIntentSheet = false
                    availabilityViewModel.resetAvailabilityIntentSheet()
                },
            )
        }

        val pendingDelete = pendingDeleteAvailabilityIntent
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteAvailabilityIntent = null },
                title = { Text("Remove availability?") },
                text = {
                    val label = pendingDelete.intentTag?.trim().orEmpty().ifEmpty { "this intent" }
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
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteAvailabilityIntent = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showNameDialog) {
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Edit name") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = newFirstName,
                            onValueChange = { newFirstName = it },
                            label = { Text("First name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newLastName,
                            onValueChange = { newLastName = it },
                            label = { Text("Last name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

private fun profileAvatarInitials(user: User?): String {
    if (user == null) return "?"
    val (f, l) = namePartsForEditor(user)
    val initials = buildString {
        f.firstOrNull()?.uppercaseChar()?.let { append(it) }
        if (length < 2) l.firstOrNull()?.uppercaseChar()?.let { append(it) }
    }
    return initials.ifEmpty { "?" }
}

/** Values shown in settings and prefilled in the editor (falls back to splitting [User.name]). */
private fun namePartsForEditor(user: User?): Pair<String, String> {
    if (user == null) return "" to ""
    val fn = user.firstName?.trim()?.takeIf { it.isNotEmpty() }
    val ln = user.lastName?.trim()?.takeIf { it.isNotEmpty() }
    if (fn != null || ln != null) {
        return (fn ?: "") to (ln ?: "")
    }
    val n = user.name?.trim().orEmpty()
    if (n.isEmpty()) return "" to ""
    val sp = n.indexOf(' ')
    return if (sp < 0) {
        n to ""
    } else {
        n.take(sp).trim() to n.substring(sp + 1).trim()
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun YourDataLocationCard(
    locationPreferences: LocationPreferences,
    ghostModeEnabled: Boolean,
    locationSnapGranted: Boolean,
    onConnectionSnapCheckedChange: (Boolean) -> Unit,
) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (ghostModeEnabled) {
                Text(
                    "Ghost mode is on — location not shared.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            SettingsToggleRow(
                icon = Icons.Default.LocationOn,
                title = "Location snap",
                subtitle = "GPS recorded at moment of tap",
                checked = locationPreferences.connectionSnapEnabled,
                onCheckedChange = onConnectionSnapCheckedChange
            )
            if (locationPreferences.connectionSnapEnabled && !locationSnapGranted) {
                Text(
                    text = "Location access is off — enable it in system settings to capture connection snaps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 4.dp)
                )
            }

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.Map,
                title = "Memory Map",
                subtitle = "Personal only, never shared",
                checked = locationPreferences.showOnMapEnabled,
                onCheckedChange = { AppDataManager.setShowOnMapEnabled(it) }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.PrivacyTip,
                title = "Business insights",
                subtitle = "Anonymized venue trends",
                checked = locationPreferences.includeInInsightsEnabled,
                onCheckedChange = { AppDataManager.setIncludeInInsightsEnabled(it) }
            )
        }
    }
}

@Composable
private fun NotificationSettingsCard(notificationPreferences: NotificationPreferences) {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                icon = Icons.Default.Notifications,
                title = "Message notifications",
                checked = notificationPreferences.messagePushEnabled,
                onCheckedChange = { AppDataManager.setMessageNotificationsEnabled(it) }
            )

            SettingsDivider()

            SettingsToggleRow(
                icon = Icons.Default.PhoneInTalk,
                title = "Call alerts",
                checked = notificationPreferences.callPushEnabled,
                onCheckedChange = { AppDataManager.setCallNotificationsEnabled(it) }
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryBlue,
                checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
            )
        )
    }
}
