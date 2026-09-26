@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import compose.project.click.click.calendar.DeviceCalendar // pragma: allowlist secret
import compose.project.click.click.data.UserIdentity // pragma: allowlist secret
import compose.project.click.click.data.api.BlockedUserDto // pragma: allowlist secret
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppPermission // pragma: allowlist secret
import compose.project.click.click.ui.utils.AppPermissionState // pragma: allowlist secret
import compose.project.click.click.ui.utils.PermissionRowAction // pragma: allowlist secret
import compose.project.click.click.ui.utils.permissionRowAction // pragma: allowlist secret
import compose.project.click.click.util.formatWeekdayDate // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Loadable list state for pages that fetch on open (no fake empty state while loading). */
internal sealed interface PageLoad<out T> {
    data object Loading : PageLoad<Nothing>

    data class Loaded<T>(
        val value: T,
    ) : PageLoad<T>

    data object Failed : PageLoad<Nothing>
}

private fun AppPermission.icon(): ImageVector =
    when (this) {
        AppPermission.Location -> Icons.Default.LocationOn
        AppPermission.Bluetooth -> Icons.AutoMirrored.Filled.BluetoothSearching
        AppPermission.Microphone -> Icons.Default.Mic
        AppPermission.Camera -> Icons.Default.PhotoCamera
        AppPermission.Photos -> Icons.Default.Photo
        AppPermission.Contacts -> Icons.Default.Contacts
        AppPermission.Calendar -> Icons.Default.CalendarMonth
        AppPermission.Notifications -> Icons.Default.Notifications
    }

@Composable
private fun PermissionActionSlot(
    state: AppPermissionState?,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (val action = state?.let(::permissionRowAction)) {
        null -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        is PermissionRowAction.Label ->
            Text(action.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionRowAction.Allow -> TextButton(onClick = onAllow) { Text("Allow") }
        PermissionRowAction.OpenSettings -> TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}

/** Me → Privacy → Permissions (iOS `PermissionsSettingsView`): one row per capability. */
@Composable
internal fun PermissionsPageContent(
    states: Map<AppPermission, AppPermissionState>,
    onAllow: (AppPermission) -> Unit,
    onOpenSettings: (AppPermission) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            AppPermission.entries.forEachIndexed { index, permission ->
                if (index > 0) SettingsDivider()
                ClickListRow(
                    title = permission.title,
                    subtitle = permission.use,
                    leading = {
                        Icon(permission.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailing = {
                        PermissionActionSlot(
                            state = states[permission],
                            onAllow = { onAllow(permission) },
                            onOpenSettings = { onOpenSettings(permission) },
                        )
                    },
                    showDivider = false,
                )
            }
        }
        Text(
            "Click asks for access only when you use a feature that needs it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Alerts → system status (iOS `AlertsSettingsView.systemSection`). Nothing when allowed. */
@Composable
internal fun AlertsSystemSection(
    state: AppPermissionState?,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (button, footer, action) =
        when (state) {
            AppPermissionState.Blocked ->
                Triple(
                    "Turn on notifications in Settings",
                    "Notifications are off for Click in Android Settings, so nothing below can reach this phone.",
                    onOpenSettings,
                )
            AppPermissionState.CanRequest ->
                Triple("Allow notifications", "Allow notifications so the alerts you choose can reach this phone.", onAllow)
            else -> return
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(button) }
        Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Me → Privacy → Blocked people (iOS `BlockedUsersView`). */
@Composable
internal fun BlockedPeoplePageContent(
    load: PageLoad<List<BlockedUserDto>>,
    names: Map<String, UserIdentity>,
    unblocking: Set<String>,
    errorMessage: String?,
    onUnblock: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (load) {
        PageLoad.Loading ->
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
            }
        PageLoad.Failed ->
            TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Couldn't load blocked people. Retry") }
        is PageLoad.Loaded ->
            if (load.value.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No one blocked", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "People you block can't message you or see you on Click.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    load.value.forEachIndexed { index, item ->
                        if (index > 0) SettingsDivider()
                        val identity = names[item.blockedId]
                        val name = identity?.name ?: "Click user"
                        ClickListRow(
                            title = name,
                            subtitle = blockedOnLabel(item.blockedAt),
                            leading = {
                                ConnectionListUserAvatarFace(
                                    displayName = identity?.name,
                                    email = null,
                                    avatarUrl = identity?.avatarUrl,
                                    userId = item.blockedId,
                                    modifier = Modifier.size(36.dp),
                                    useCompactTypography = true,
                                )
                            },
                            trailing = {
                                OutlinedButton(
                                    onClick = { onUnblock(item.blockedId) },
                                    enabled = item.blockedId !in unblocking,
                                ) { Text("Unblock") }
                            },
                            showDivider = false,
                        )
                    }
                    if (errorMessage != null) {
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
    }
}

internal fun blockedOnLabel(blockedAtIso: String?): String? {
    val instant = blockedAtIso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return "Blocked ${formatWeekdayDate(instant.toEpochMilliseconds())}"
}

/** Me → Calendar (iOS `CalendarSettingsView`), plus the device calendars and a Click-only disconnect. */
@Composable
internal fun CalendarPageContent(
    permission: AppPermissionState?,
    connected: Boolean,
    calendars: List<DeviceCalendar>,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onConnectedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            ClickListRow(
                title = "Calendar access",
                trailing = { PermissionActionSlot(permission, onAllow, onOpenSettings) },
                showDivider = false,
            )
            if (permission == AppPermissionState.Granted) {
                SettingsDivider()
                ClickListRow(
                    title = if (connected) "Connected" else "Disconnected",
                    subtitle =
                        if (connected) {
                            "Click reads busy and free times from the calendars below."
                        } else {
                            "Click won't read your calendar until you connect it again."
                        },
                    trailing = {
                        TextButton(onClick = { onConnectedChange(!connected) }) {
                            Text(if (connected) "Disconnect" else "Connect")
                        }
                    },
                    showDivider = false,
                )
            }
        }
        if (permission == AppPermissionState.Granted && connected && calendars.isNotEmpty()) {
            SettingsSectionHeader("Calendars on this phone")
            Column {
                calendars.forEachIndexed { index, calendar ->
                    if (index > 0) SettingsDivider()
                    ClickListRow(title = calendar.name, subtitle = calendar.accountName, showDivider = false)
                }
            }
        }
        Text(
            "Click reads only busy and free times on this phone for availability. Event titles, locations, and " +
                "attendees never leave your device, and Click never adds or changes calendar events.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Saved events split like iOS: upcoming or live first, then past or unavailable. */
internal data class SavedEventSections(
    val upcoming: List<EventBookmarkItemDto>,
    val past: List<EventBookmarkItemDto>,
)

internal fun savedEventSections(
    bookmarks: List<EventBookmarkItemDto>,
    nowEpochMs: Long,
): SavedEventSections {
    fun ms(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
    val (upcoming, past) =
        bookmarks.partition { bookmark ->
            val end = ms(bookmark.eventEndAt) ?: ms(bookmark.expiresAt)
            val start = ms(bookmark.eventStartAt)
            when {
                end != null -> end > nowEpochMs
                // No end: treat as live for 6 hours after the start (matches the discovery feed).
                start != null -> start + 6 * 60 * 60 * 1000L > nowEpochMs
                else -> false
            }
        }
    return SavedEventSections(
        upcoming = upcoming.sortedBy { ms(it.eventStartAt) ?: Long.MAX_VALUE },
        past = past.sortedByDescending { ms(it.eventStartAt) ?: 0L },
    )
}

internal fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()
